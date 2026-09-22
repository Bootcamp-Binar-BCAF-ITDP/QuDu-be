if [ -z "${BASH_VERSION:-}" ]; then
    exec bash "$0" "$@"
fi

set -euo pipefail

IMAGE="${1:?usage: aws-deploy.sh <image> [registry-user]}"
REGISTRY_USER="${2:-}"

DEPLOY_DIR="$(cd "$(dirname "$0")/.." && pwd)"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-240}"
ALLOW_EMPTY_DB="${ALLOW_EMPTY_DB:-false}"

LEGACY_ENV=/etc/qudu-be.env
LEGACY_FIREBASE=/etc/qudu-be/firebase-credentials.json
LEGACY_UNIT=qudu-be.service
LEGACY_PG_UNIT=postgresql@18-main.service
LEGACY_UPLOADS=/home/ubuntu/app/uploads
LEGACY_DB=qudu2
BACKUP_DIR="$HOME/backups"

CURRENT_FILE="$DEPLOY_DIR/.image-current"
APP_CONTAINER=qudu-be
DB_CONTAINER=qudu-be-db
REDIS_CONTAINER=qudu-be-redis
UPLOADS_VOLUME=qudu-be_uploads
APP_UID=1001

say()  { printf '\n==> %s\n' "$1"; }
warn() { printf '::warning::%s\n' "$1"; }
die()  { printf '::error::%s\n' "$1"; exit 1; }

cd "$DEPLOY_DIR"
for f in docker-compose.yml docker-compose.aws.yml; do
    [ -f "$f" ] || die "$DEPLOY_DIR/$f is missing. The workflow uploads it."
done

sudo -n true 2>/dev/null || die "$(id -un) needs passwordless sudo on this box."

REGISTRY_TOKEN=""
if [ ! -t 0 ]; then
    IFS= read -r REGISTRY_TOKEN || true
fi

say "Docker"
command -v docker >/dev/null 2>&1 \
    || die "Docker is not installed. Install it once: curl -fsSL https://get.docker.com | sudo sh && sudo usermod -aG docker $(id -un)"

SUDO=()
if ! docker info >/dev/null 2>&1; then
    sudo -n docker info >/dev/null 2>&1 || die "Cannot talk to the Docker daemon. Check: sudo systemctl status docker"
    SUDO=(sudo -n)
fi

dk() { "${SUDO[@]}" docker "$@"; }
compose() {
    local image="$1"; shift
    "${SUDO[@]}" env APP_IMAGE="$image" \
        docker compose -f docker-compose.yml -f docker-compose.aws.yml "$@"
}

dk --version

legacy() {
    local v
    v=$(sudo -n sed -n "s/^$1=//p" "$LEGACY_ENV" | tail -n 1 | tr -d '\r')
    # systemd allows the value to be quoted; compose's quoting is added below.
    v="${v#\"}"; v="${v%\"}"; v="${v#\'}"; v="${v%\'}"
    printf '%s' "$v"
}

if [ ! -f .env ]; then
    say "Writing .env from $LEGACY_ENV (first run only)"
    sudo -n test -f "$LEGACY_ENV" || die "No .env in $DEPLOY_DIR and no $LEGACY_ENV to build one from."

    JWT_SECRET=$(legacy APP_SECURITY_JWT_SECRET)
    JWT_TTL_MINUTES=$(legacy APP_SECURITY_JWT_TTL_MINUTES)
    CORS_ALLOWED_ORIGIN=$(legacy APP_SECURITY_CORS_ALLOWED_ORIGIN)
    FRONTEND_RESET_PASSWORD_URL=$(legacy APP_FRONTEND_RESET_PASSWORD_URL)
    MAIL_USERNAME=$(legacy SPRING_MAIL_USERNAME)
    MAIL_PASSWORD=$(legacy SPRING_MAIL_PASSWORD)
    DDL_AUTO=$(legacy SPRING_JPA_HIBERNATE_DDL_AUTO)

    for k in JWT_SECRET CORS_ALLOWED_ORIGIN FRONTEND_RESET_PASSWORD_URL MAIL_USERNAME MAIL_PASSWORD; do
        [ -n "${!k}" ] || die "$k could not be derived from $LEGACY_ENV."
    done

    DB_PASSWORD=$(openssl rand -hex 24)

    nl=$'\n'
    entries=(
        "APP_PORT=127.0.0.1:8080"
        "DB_USERNAME=qudu"
        "DB_PASSWORD=$DB_PASSWORD"
        "JWT_SECRET=$JWT_SECRET"
        "JWT_TTL_MINUTES=${JWT_TTL_MINUTES:-30}"
        "REFRESH_TTL_DAYS=7"
        "CORS_ALLOWED_ORIGIN=$CORS_ALLOWED_ORIGIN"
        "FRONTEND_RESET_PASSWORD_URL=$FRONTEND_RESET_PASSWORD_URL"
        "MAIL_HOST=smtp.gmail.com"
        "MAIL_PORT=587"
        "MAIL_USERNAME=$MAIL_USERNAME"
        "MAIL_PASSWORD=$MAIL_PASSWORD"
        "MAIL_FROM=$MAIL_USERNAME"
        "API_DOCS_ENABLED=true"
        "FIREBASE_CREDENTIALS_PATH=file:/app/secrets/firebase-credentials.json"
        "SPRING_JPA_HIBERNATE_DDL_AUTO=${DDL_AUTO:-validate}"
        # 908 MB of RAM shared with PostgreSQL and nginx: the image default of
        # 75 % would let the heap push everything else into swap.
        "JAVA_OPTS=-XX:MaxRAMPercentage=35 -XX:+UseSerialGC"
    )

    umask 077
    {
        echo "# Written by scripts/aws-deploy.sh on $(date -Is) from $LEGACY_ENV."
        echo "# Owned by this server: deploys never overwrite it. Edit here, then redeploy."
        for e in "${entries[@]}"; do
            k="${e%%=*}"; v="${e#*=}"
            if [[ "$v" == *"'"* || "$v" == *"$nl"* ]]; then
                rm -f .env.tmp
                die "The value for $k contains a single quote or newline; fix it in $LEGACY_ENV first."
            fi
            printf "%s='%s'\n" "$k" "$v"
        done
    } > .env.tmp
    mv .env.tmp .env
    umask 022
    echo "    .env written ($(grep -c "^[A-Z]" .env) keys)"
fi

# --------------------------------------------------------------- secrets ----

# compose mounts ./secrets at /app/secrets and the app reads it as uid 1001, so
# the directory and the file both belong to 1001 — a 700 directory owned by the
# login user makes the file invisible inside the container.
say "Secrets"
sudo -n mkdir -p secrets
sudo -n chown "$APP_UID:$APP_UID" secrets
sudo -n chmod 700 secrets
if ! sudo -n test -f secrets/firebase-credentials.json && sudo -n test -f "$LEGACY_FIREBASE"; then
    # cp + chown, not `install -o`: install only takes user NAMES, and uid 1001
    # has no name on this host.
    sudo -n cp "$LEGACY_FIREBASE" secrets/firebase-credentials.json
    sudo -n chown "$APP_UID:$APP_UID" secrets/firebase-credentials.json
    sudo -n chmod 400 secrets/firebase-credentials.json
    echo "    installed secrets/firebase-credentials.json from $LEGACY_FIREBASE"
fi

if sudo -n test -f secrets/firebase-credentials.json; then FIREBASE=true; else FIREBASE=false; fi
if grep -q '^FIREBASE_ENABLED=' .env; then
    sed -i "s/^FIREBASE_ENABLED=.*/FIREBASE_ENABLED='$FIREBASE'/" .env
else
    echo "FIREBASE_ENABLED='$FIREBASE'" >> .env
fi
echo "    push notifications: $FIREBASE"

# ----------------------------------------------------------------- image ----

say "Image $IMAGE"

if [ -n "$REGISTRY_TOKEN" ]; then
    REGISTRY="${IMAGE%%/*}"
    printf '%s' "$REGISTRY_TOKEN" | dk login "$REGISTRY" -u "${REGISTRY_USER:-token}" --password-stdin >/dev/null
    trap 'dk logout "$REGISTRY" >/dev/null 2>&1 || true' EXIT
    dk pull "$IMAGE"
    dk logout "$REGISTRY" >/dev/null 2>&1 || true
    trap - EXIT
elif dk image inspect "$IMAGE" >/dev/null 2>&1; then
    echo "    already on this box, not pulling"
else
    dk pull "$IMAGE"
fi

# -------------------------------------------------------------- database ----

wait_healthy() {
    local name="$1" timeout="$2" status="" restarts=0 waited=0
    while [ "$waited" -lt "$timeout" ]; do
        status=$(dk inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$name" 2>/dev/null || echo missing)
        case "$status" in
            healthy) echo "    $name healthy after ${waited}s"; return 0 ;;
            unhealthy|exited|dead|missing) break ;;
        esac
        # restart: unless-stopped hides a crash loop behind health "starting".
        restarts=$(dk inspect -f '{{.RestartCount}}' "$name" 2>/dev/null || echo 0)
        if [ "$restarts" -gt 0 ]; then
            status="crashed and restarted $restarts time(s)"
            break
        fi
        sleep 3
        waited=$((waited + 3))
    done
    echo "    $name is '$status' after ${waited}s"
    return 1
}

say "Database"
compose "$IMAGE" up -d --no-build db
wait_healthy "$DB_CONTAINER" 120 || {
    dk logs --tail 60 "$DB_CONTAINER" || true
    die "PostgreSQL did not become healthy."
}

# ------------------------------------------------------------------ cache ----

# Started before the app, which declares depends_on: redis healthy. A Redis
# that fails to start is not fatal: the app logs the failed lookups and reads
# PostgreSQL instead (RedisConfig.errorHandler), so this warns rather than dies
# — a cache is not worth refusing a deploy over.
say "Cache"
if compose "$IMAGE" up -d --no-build redis; then
    wait_healthy "$REDIS_CONTAINER" 60 \
        || warn "Redis did not become healthy; the app will run without a cache."
else
    warn "Redis could not be started; the app will run without a cache."
fi

dbq() {
    dk exec -i "$DB_CONTAINER" sh -c \
        'psql -v ON_ERROR_STOP=1 -X -q -t -A -U "$POSTGRES_USER" -d "$POSTGRES_DB" "$@"' sh "$@"
}
legacyq() {
    sudo -n -u postgres psql -v ON_ERROR_STOP=1 -X -q -t -A -d "$LEGACY_DB" "$@"
}
# LoadState, not list-unit-files: the latter does not list template instances
# such as postgresql@18-main.service.
unit_exists() { [ "$(systemctl show -p LoadState --value "$1" 2>/dev/null)" = loaded ]; }

# ------------------------------------------------------------- migration ----

# "Once" is decided by the database itself: the live data is migrated only while
# the container database has no users table. After that this branch is never
# taken again.
MIGRATED_NOW=false

restart_legacy() {
    if unit_exists "$LEGACY_UNIT"; then
        warn "Restarting the legacy $LEGACY_UNIT so the site keeps serving."
        sudo -n systemctl start "$LEGACY_UNIT" || true
    fi
}

say "Data"
HAS_SCHEMA=$(dbq -c "select to_regclass('public.users') is not null")

if [ "$HAS_SCHEMA" = "t" ]; then
    echo "    container database already holds the data — migration skipped"

elif systemctl is-active --quiet "$LEGACY_PG_UNIT" \
     && [ "$(sudo -n -u postgres psql -X -A -t -c "select 1 from pg_database where datname='$LEGACY_DB'" 2>/dev/null)" = "1" ]; then

    echo "    empty container database; migrating live $LEGACY_DB from the native cluster"

    # Stop writes first. From here until the container is healthy the API is down.
    if systemctl is-active --quiet "$LEGACY_UNIT"; then
        echo "    stopping $LEGACY_UNIT (downtime starts)"
        sudo -n systemctl stop "$LEGACY_UNIT"
    fi
    trap 'restart_legacy' ERR

    mkdir -p "$BACKUP_DIR"
    chmod 700 "$BACKUP_DIR"
    STAMP=$(date +%Y%m%d-%H%M%S)
    DUMP="$BACKUP_DIR/$LEGACY_DB-pre-docker-$STAMP.dump"
    sudo -n -u postgres pg_dump -Fc "$LEGACY_DB" > "$DUMP"
    chmod 600 "$DUMP"
    echo "    dumped to $DUMP ($(du -h "$DUMP" | cut -f1))"

    # The other database on the cluster is not QuDu's, but it goes down with
    # the native PostgreSQL, so it gets a backup too.
    for other in $(sudo -n -u postgres psql -X -A -t -c "select datname from pg_database where not datistemplate and datname not in ('postgres','$LEGACY_DB')"); do
        sudo -n -u postgres pg_dump -Fc "$other" > "$BACKUP_DIR/$other-pre-docker-$STAMP.dump"
        chmod 600 "$BACKUP_DIR/$other-pre-docker-$STAMP.dump"
        echo "    also backed up $other"
    done

    dk cp "$DUMP" "$DB_CONTAINER:/tmp/migrate.dump"
    # --no-owner/--no-acl: objects belonged to the native app role; here they
    # belong to DB_USERNAME. --single-transaction: all or nothing.
    if ! dk exec "$DB_CONTAINER" sh -c \
        'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --no-acl --exit-on-error --single-transaction /tmp/migrate.dump'; then
        dk exec "$DB_CONTAINER" rm -f /tmp/migrate.dump || true
        restart_legacy
        die "pg_restore failed; nothing was committed. The legacy service was restarted."
    fi
    dk exec "$DB_CONTAINER" rm -f /tmp/migrate.dump

    # Every table, every row: the migration counts as done only if they match.
    mismatch=0
    for t in $(legacyq -c "select tablename from pg_tables where schemaname='public' order by 1"); do
        a=$(legacyq -c "select count(*) from public.\"$t\"")
        b=$(dbq -c "select count(*) from public.\"$t\"")
        if [ "$a" != "$b" ]; then
            echo "    MISMATCH $t: native $a, container $b"
            mismatch=1
        fi
    done
    if [ "$mismatch" = 1 ]; then
        dbq -c "drop schema public cascade; create schema public;" || true
        restart_legacy
        die "Row counts differ after restore. Container database emptied again; legacy service restarted."
    fi
    echo "    row counts match on all $(legacyq -c "select count(*) from pg_tables where schemaname='public'") tables"

    # Uploaded documents: the stored paths are relative (uploads/...), and the
    # container's working directory is /app, so they resolve to /app/uploads/...
    # which is this volume.
    compose "$IMAGE" up --no-start app
    if [ -d "$LEGACY_UPLOADS" ]; then
        dk run --rm --user 0 --entrypoint sh \
            -v "$UPLOADS_VOLUME:/dst" -v "$LEGACY_UPLOADS:/src:ro" "$IMAGE" \
            -c "cp -a /src/. /dst/ && chown -R $APP_UID:$APP_UID /dst"
        echo "    copied $(find "$LEGACY_UPLOADS" -type f | wc -l) uploaded files into $UPLOADS_VOLUME"
    fi

    trap - ERR
    MIGRATED_NOW=true

elif [ "$ALLOW_EMPTY_DB" = "true" ]; then
    warn "Nothing to migrate from; starting on an EMPTY database because ALLOW_EMPTY_DB=true."
else
    die "The container database is empty and the native $LEGACY_DB is not running to migrate from. Refusing to start an empty production database (set ALLOW_EMPTY_DB=true only if that is really intended)."
fi

# ------------------------------------------------------------------- app ----

say "Application"

# The legacy JVM holds :8080 on every interface; the container cannot bind
# 127.0.0.1:8080 while it runs.
if systemctl is-active --quiet "$LEGACY_UNIT"; then
    echo "    stopping $LEGACY_UNIT, which holds port 8080"
    sudo -n systemctl stop "$LEGACY_UNIT"
fi

PREVIOUS=$(cat "$CURRENT_FILE" 2>/dev/null || true)
echo "    serving now : ${PREVIOUS:-legacy systemd service}"
echo "    switching to: $IMAGE"

compose "$IMAGE" up -d --no-build app

if wait_healthy "$APP_CONTAINER" "$HEALTH_TIMEOUT"; then
    echo "$IMAGE" > "$CURRENT_FILE"

    # Retire the host services only once the container has proven itself.
    # Stopped and disabled, never uninstalled: packages and data stay on disk.
    for unit in "$LEGACY_UNIT" "$LEGACY_PG_UNIT" postgresql.service redis-server.service; do
        unit_exists "$unit" || continue
        if systemctl is-enabled --quiet "$unit" 2>/dev/null || systemctl is-active --quiet "$unit"; then
            sudo -n systemctl disable --now "$unit" >/dev/null 2>&1 || true
            echo "    retired $unit (stopped + disabled)"
        fi
    done
    # Debian's postgresql generator re-creates the cluster unit on every boot
    # ("enabled-runtime") from start.conf, whatever `disable` said. manual =
    # never auto-start; the data directory itself is untouched.
    for conf in /etc/postgresql/*/main/start.conf; do
        [ -f "$conf" ] || continue
        if sudo -n grep -qx auto "$conf"; then
            sudo -n sed -i 's/^auto$/manual/' "$conf"
            echo "    $conf: auto -> manual"
        fi
    done

    # Keep the live image and the previous one for rollback; drop older tags.
    REPO="${IMAGE%:*}"
    dk images --format '{{.Repository}}:{{.Tag}}' "$REPO" | while read -r old; do
        [ "$old" = "$IMAGE" ] || [ "$old" = "$PREVIOUS" ] || [ "$old" = "$REPO:latest" ] && continue
        echo "    removing old image $old"
        dk rmi "$old" >/dev/null 2>&1 || true
    done
    dk image prune -f >/dev/null 2>&1 || true

    say "Deployed $IMAGE"
    [ "$MIGRATED_NOW" = true ] && echo "    live data migrated from the native cluster; backup in $BACKUP_DIR"
    compose "$IMAGE" ps
    df -h / | tail -1
    exit 0
fi

echo
echo "---- last 80 lines of the new container ----"
dk logs --tail 80 "$APP_CONTAINER" || true
echo "--------------------------------------------"

if [ -n "$PREVIOUS" ] && [ "$PREVIOUS" != "$IMAGE" ]; then
    say "Rolling back to $PREVIOUS"
    compose "$PREVIOUS" up -d --no-build app
    if wait_healthy "$APP_CONTAINER" "$HEALTH_TIMEOUT"; then
        die "The new image never became healthy. Rolled back to $PREVIOUS, which is serving again."
    fi
    die "The new image never became healthy, and the rollback to $PREVIOUS did not recover either. The app is DOWN."
fi

# First container deploy: fall back to the systemd service, which still has
# its jar, its env file and the native database (untouched by the migration).
if unit_exists "$LEGACY_UNIT"; then
    say "Falling back to the legacy $LEGACY_UNIT"
    compose "$IMAGE" stop app || true
    sudo -n systemctl start "$LEGACY_UNIT"
    if [ "$MIGRATED_NOW" = true ]; then
        # Next run migrates again from scratch instead of trusting a restore
        # the app never managed to serve.
        dbq -c "drop schema public cascade; create schema public;" || true
    fi
    die "The container never became healthy; the legacy systemd service is serving again."
fi

die "The new image never became healthy and there is nothing to roll back to. Read the log above."
