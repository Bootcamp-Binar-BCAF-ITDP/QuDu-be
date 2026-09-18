#!/usr/bin/env bash

if [ -z "${BASH_VERSION:-}" ]; then
    if command -v bash >/dev/null 2>&1; then
        exec bash "$0" "$@"
    fi
    echo "This script needs bash. Install it with: apt-get install -y bash" >&2
    exit 1
fi

set -euo pipefail

DOCKER_USER=""
RUN_TEST=1
FORCE=0

usage() {
    cat <<'USAGE'
Install Docker Engine and the Compose plugin on Debian.

Usage: sudo ./install-docker-debian.sh [options]

  --user NAME    add NAME to the docker group so it can run docker without sudo
  --skip-test    do not run the hello-world container at the end
  --force        continue even if this is not Debian
  -h, --help     show this message

Safe to run more than once: every step checks before it acts.
USAGE
}

while [ $# -gt 0 ]; do
    case "$1" in
        --user)
            [ $# -ge 2 ] || { echo "--user needs a name" >&2; exit 2; }
            DOCKER_USER="$2"; shift 2 ;;
        --user=*) DOCKER_USER="${1#*=}"; shift ;;
        --skip-test) RUN_TEST=0; shift ;;
        --force) FORCE=1; shift ;;
        -h|--help) usage; exit 0 ;;
        *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
    esac
done

say() { printf '\n==> %s\n' "$1"; }
warn() { printf '\n!!  %s\n' "$1" >&2; }
die() { printf '\nXX  %s\n' "$1" >&2; exit 1; }

[ "$(id -u)" -eq 0 ] || die "Run this as root, for example: sudo $0"

say "Checking the machine"

[ -r /etc/os-release ] || die "/etc/os-release is missing, cannot identify this system."
. /etc/os-release

echo "    system : ${PRETTY_NAME:-unknown}"
echo "    arch   : $(dpkg --print-architecture 2>/dev/null || uname -m)"

if [ "${ID:-}" != "debian" ]; then
    if [ "$FORCE" -eq 1 ]; then
        warn "This is '${ID:-unknown}', not Debian. Continuing because --force was given."
    else
        die "This script is for Debian. Detected '${ID:-unknown}'. Use --force to override."
    fi
fi

CODENAME="${VERSION_CODENAME:-}"
if [ -z "$CODENAME" ]; then
    CODENAME="$(lsb_release -cs 2>/dev/null || true)"
fi
if [ -z "$CODENAME" ] && [ -n "${VERSION_ID:-}" ]; then
    case "${VERSION_ID%%.*}" in
        11) CODENAME="bullseye" ;;
        12) CODENAME="bookworm" ;;
        13) CODENAME="trixie" ;;
    esac
fi
[ -n "$CODENAME" ] || die "Could not work out the Debian release codename. Set VERSION_CODENAME in /etc/os-release."
echo "    release: $CODENAME"

ARCH="$(dpkg --print-architecture)"
case "$ARCH" in
    amd64|arm64|armhf|s390x|ppc64el) ;;
    *) die "Docker publishes no Debian packages for architecture '$ARCH'." ;;
esac

say "Removing packages that conflict with Docker Engine"

CONFLICTING="docker.io docker-doc docker-compose docker-compose-v2 podman-docker containerd runc"
FOUND=""
for pkg in $CONFLICTING; do
    if dpkg -l "$pkg" 2>/dev/null | grep -q '^ii'; then
        FOUND="$FOUND $pkg"
    fi
done

if [ -n "$FOUND" ]; then
    echo "    removing:$FOUND"
    # shellcheck disable=SC2086
    apt-get remove -y $FOUND
else
    echo "    none installed"
fi

say "Installing what the repository setup needs"
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y ca-certificates curl gnupg

say "Adding Docker's signing key"
install -m 0755 -d /etc/apt/keyrings
curl -fsSL --retry 3 https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc.tmp
mv /etc/apt/keyrings/docker.asc.tmp /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
echo "    /etc/apt/keyrings/docker.asc"

say "Adding the Docker apt repository"
printf 'deb [arch=%s signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian %s stable\n' \
    "$ARCH" "$CODENAME" > /etc/apt/sources.list.d/docker.list
cat /etc/apt/sources.list.d/docker.list | sed 's/^/    /'

say "Installing Docker Engine, the CLI, containerd, Buildx and Compose"
apt-get update
apt-get install -y \
    docker-ce \
    docker-ce-cli \
    containerd.io \
    docker-buildx-plugin \
    docker-compose-plugin

say "Starting Docker and enabling it at boot"
systemctl enable --now docker
systemctl is-active docker | sed 's/^/    docker: /'

if [ -n "$DOCKER_USER" ]; then
    say "Granting $DOCKER_USER access to the Docker socket"

    if ! id "$DOCKER_USER" >/dev/null 2>&1; then
        die "No such user: $DOCKER_USER"
    fi

    getent group docker >/dev/null || groupadd docker

    if id -nG "$DOCKER_USER" | tr ' ' '\n' | grep -qx docker; then
        echo "    already in the docker group"
    else
        usermod -aG docker "$DOCKER_USER"
        echo "    added to the docker group"
    fi

    warn "Membership in the docker group is equivalent to root on this machine."
    echo "    $DOCKER_USER must log out and back in before it takes effect."
fi

say "Versions installed"
docker --version | sed 's/^/    /'
docker compose version | sed 's/^/    /'
containerd --version | sed 's/^/    /'

if [ "$RUN_TEST" -eq 1 ]; then
    say "Running hello-world to prove the engine works"
    if docker run --rm hello-world >/dev/null 2>&1; then
        echo "    ok"
        docker image rm hello-world >/dev/null 2>&1 || true
    else
        die "Docker is installed but could not run a container. Check: journalctl -u docker -n 50"
    fi
fi

say "Done"
if [ -n "$DOCKER_USER" ]; then
    echo "    Log out and back in as $DOCKER_USER, then check with: docker run --rm hello-world"
else
    echo "    Docker commands need root for now. Re-run with --user NAME to change that."
fi
