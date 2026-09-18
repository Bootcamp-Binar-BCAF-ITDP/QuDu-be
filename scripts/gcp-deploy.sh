#!/usr/bin/env bash
#
# Runs ON the GCP VM. Called by .github/workflows/deploy-gcp.yml after it has
# uploaded docker-compose.yml, docker-compose.gcp.yml, .env and this script into
# the deploy directory. Safe to run again: every step checks before it acts.
#
#   printf '%s\n' "$REGISTRY_TOKEN" | bash scripts/gcp-deploy.sh <image> [registry-user]
#
# The registry token comes on stdin, never as an argument, so it does not show
# up in `ps` on the VM. With nothing on stdin the pull is anonymous.
#
# What it does, in order:
#   1. installs Docker if the VM has none
#   2. pulls <image>
#   3. starts PostgreSQL and waits for it
#   4. restores the seed dump ONCE — only while the database has no tables
#   5. swaps the app container to <image>, waits for healthy, rolls back if not
#
# Environment:
#   SEED_FILE        dump to restore on first deploy (default ~/qudu2-dump.sql)
#   ALLOW_EMPTY_DB   true = start on an empty database when no dump is present
#   HEALTH_TIMEOUT   seconds to wait for the app to turn healthy (default 240)

# `sh script.sh` on Debian is dash; everything below needs bash.
if [ -z "${BASH_VERSION:-}" ]; then
    exec bash "$0" "$@"
fi

set -euo pipefail

IMAGE="${1:?usage: gcp-deploy.sh <image> [registry-user]}"
REGISTRY_USER="${2:-}"

DEPLOY_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SEED_FILE="${SEED_FILE:-$HOME/qudu2-dump.sql}"
ALLOW_EMPTY_DB="${ALLOW_EMPTY_DB:-false}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-240}"

CURRENT_FILE="$DEPLOY_DIR/.image-current"
APP_CONTAINER=qudu-be
DB_CONTAINER=qudu-be-db
# uid of the `qudu` user inside the image (Dockerfile).
APP_UID=1001

say()  { printf '\n==> %s\n' "$1"; }
warn() { printf '::warning::%s\n' "$1"; }
die()  { printf '::error::%s\n' "$1"; exit 1; }

cd "$DEPLOY_DIR"
for f in docker-compose.yml docker-compose.gcp.yml .env; do
    [ -f "$f" ] || die "$DEPLOY_DIR/$f is missing. The workflow uploads it; do not run this by hand without it."
done

# Read the token before anything else can consume stdin.
REGISTRY_TOKEN=""
if [ ! -t 0 ]; then
    IFS= read -r REGISTRY_TOKEN || true
fi

# ---------------------------------------------------------------- docker ----

say "Docker"

if ! command -v docker >/dev/null 2>&1; then
    sudo -n true 2>/dev/null \
        || die "Docker is not installed and $(id -un) has no passwordless sudo to install it. Install it once by hand: sudo bash $DEPLOY_DIR/scripts/install-docker-debian.sh --user $(id -un)"

    . /etc/os-release
    if [ "${ID:-}" = debian ]; then
        sudo -n bash "$DEPLOY_DIR/scripts/install-docker-debian.sh" --user "$(id -un)" --skip-test
    else
        # Ubuntu and friends: Docker's own installer picks the right repository.
        curl -fsSL https://get.docker.com | sudo -n sh
        sudo -n usermod -aG docker "$(id -un)"
    fi
fi

# A user added to the docker group only gets it on the next login, so the very
# first run goes through sudo. `env` carries APP_IMAGE past sudo's env reset.
SUDO=()
if ! docker info >/dev/null 2>&1; then
    sudo -n docker info >/dev/null 2>&1 \
        || die "Cannot talk to the Docker daemon, with or without sudo. Check: sudo systemctl status docker"
    SUDO=(sudo -n)
fi

dk() { "${SUDO[@]}" docker "$@"; }
compose() {
    local image="$1"; shift
    "${SUDO[@]}" env APP_IMAGE="$image" \
        docker compose -f docker-compose.yml -f docker-compose.gcp.yml "$@"
}

dk --version
dk compose version

# ----------------------------------------------------------------- image ----

say "Pulling $IMAGE"

REGISTRY="${IMAGE%%/*}"
if [ -n "$REGISTRY_TOKEN" ]; then
    printf '%s' "$REGISTRY_TOKEN" | dk login "$REGISTRY" -u "${REGISTRY_USER:-token}" --password-stdin >/dev/null
    # The token is the workflow's GITHUB_TOKEN and dies with the job anyway;
    # logging out keeps it from sitting in ~/.docker/config.json until then.
    trap 'dk logout "$REGISTRY" >/dev/null 2>&1 || true' EXIT
fi
dk pull "$IMAGE"
if [ -n "$REGISTRY_TOKEN" ]; then
    dk logout "$REGISTRY" >/dev/null 2>&1 || true
    trap - EXIT
fi

# --------------------------------------------------------------- secrets ----

# The container runs as uid 1001, so a file owned by the login user with mode
# 600 is unreadable to it. The workflow uploads to a staging name; it is
# installed here with the right owner.
mkdir -p secrets
if [ -f secrets/.firebase-credentials.upload ]; then
    say "Firebase credentials"
    if sudo -n true 2>/dev/null; then
        sudo -n install -o "$APP_UID" -g "$APP_UID" -m 400 \
            secrets/.firebase-credentials.upload secrets/firebase-credentials.json
    else
        warn "No passwordless sudo: firebase-credentials.json is left world-readable (644) so the container can read it."
        install -m 644 secrets/.firebase-credentials.upload secrets/firebase-credentials.json
    fi
    rm -f secrets/.firebase-credentials.upload
    echo "    installed secrets/firebase-credentials.json"
fi

# -------------------------------------------------------------- database ----

wait_healthy() {
    local name="$1" timeout="$2" status="" waited=0
    while [ "$waited" -lt "$timeout" ]; do
        status=$(dk inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$name" 2>/dev/null || echo missing)
        case "$status" in
            healthy) echo "    $name healthy after ${waited}s"; return 0 ;;
            unhealthy|exited|dead|missing) break ;;
        esac
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

dbq() {
    dk exec -i "$DB_CONTAINER" sh -c \
        'psql -v ON_ERROR_STOP=1 -X -q -t -A -U "$POSTGRES_USER" -d "$POSTGRES_DB" "$@"' sh "$@"
}

# ------------------------------------------------------------------ seed ----

# "Once" is decided by the database itself, not by a marker file: the dump is
# restored only while public.users does not exist. After the first restore —
# or after the app has created its schema — this branch is never taken again,
# whatever files are lying around.
say "Seed data"

HAS_SCHEMA=$(dbq -c "select to_regclass('public.users') is not null")

if [ "$HAS_SCHEMA" = "t" ]; then
    echo "    database already has tables — seed skipped"
    if [ -f "$SEED_FILE" ]; then
        warn "$SEED_FILE is still on the VM but was NOT restored (the database is not empty). It holds customer data; delete it: rm $SEED_FILE"
    fi

elif [ -f "$SEED_FILE" ]; then
    echo "    empty database, restoring $SEED_FILE"

    # The extension says .sql, but pg_dump -Fc archives start with "PGDMP" and
    # only pg_restore reads them. Plain SQL dumps go through psql.
    if [ "$(head -c 5 "$SEED_FILE")" = "PGDMP" ]; then
        dk cp "$SEED_FILE" "$DB_CONTAINER:/tmp/seed.dump"
        # --no-owner/--no-acl: the dump was made as "postgres" on Windows; here
        # every object belongs to DB_USERNAME. No -C: the dump's CREATE DATABASE
        # names a Windows locale that does not exist on Linux.
        # --single-transaction: all or nothing, so a failure leaves the database
        # empty and the next deploy simply tries again.
        if ! dk exec "$DB_CONTAINER" sh -c \
            'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --no-acl --exit-on-error --single-transaction /tmp/seed.dump'; then
            dk exec "$DB_CONTAINER" rm -f /tmp/seed.dump || true
            die "pg_restore failed. Nothing was committed; the database is still empty."
        fi
        dk exec "$DB_CONTAINER" rm -f /tmp/seed.dump
    else
        dk exec -i "$DB_CONTAINER" sh -c \
            'psql -v ON_ERROR_STOP=1 -X -q --single-transaction -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < "$SEED_FILE" \
            || die "psql restore failed. Nothing was committed; the database is still empty."
    fi

    echo "    restored: $(dbq -c 'select count(*) from users') users, $(dbq -c 'select count(*) from customer') customers, $(dbq -c 'select count(*) from loan_application') loan applications"

    # The dump holds NIK numbers and password hashes. The original stays on the
    # laptop; the copy on the VM has done its job.
    rm -f "$SEED_FILE"
    echo "    removed $SEED_FILE"

elif [ "$ALLOW_EMPTY_DB" = "true" ]; then
    warn "No seed dump at $SEED_FILE; starting on an EMPTY database because ALLOW_EMPTY_DB=true. Once the app creates its tables the dump can no longer be restored automatically."

else
    die "The database is empty and there is no dump at $SEED_FILE. Upload it once from the laptop: scp qudu2-dump.sql $(id -un)@<vm-ip>:~/  — then re-run the workflow. (Or set the repository variable ALLOW_EMPTY_DB=true to start empty on purpose.)"
fi

# ------------------------------------------------------------------- app ----

say "Application"

PREVIOUS=$(cat "$CURRENT_FILE" 2>/dev/null || true)
echo "    serving now : ${PREVIOUS:-nothing}"
echo "    switching to: $IMAGE"

# Recreates the container when the image OR .env changed, so updated GitHub
# secrets reach the app on every deploy.
compose "$IMAGE" up -d --no-build app

if wait_healthy "$APP_CONTAINER" "$HEALTH_TIMEOUT"; then
    echo "$IMAGE" > "$CURRENT_FILE"

    # Keep the live image and the previous one for rollback; drop older tags.
    REPO="${IMAGE%:*}"
    dk images --format '{{.Repository}}:{{.Tag}}' "$REPO" | while read -r old; do
        [ "$old" = "$IMAGE" ] || [ "$old" = "$PREVIOUS" ] || [ "$old" = "$REPO:latest" ] && continue
        echo "    removing old image $old"
        dk rmi "$old" >/dev/null 2>&1 || true
    done
    dk image prune -f >/dev/null 2>&1 || true

    say "Deployed $IMAGE"
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

die "The new image never became healthy and there is no earlier image to roll back to. Read the log above."
