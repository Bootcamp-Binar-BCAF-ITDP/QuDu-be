# Deploying QuDu-be

Push to `main` builds an image, pushes it to GHCR, and rolls it out to the EC2
instance. The image is built on a GitHub runner, never on the instance: a
1 GB instance cannot run `mvnw package` inside Docker without being killed by
the OOM reaper, and the error it produces blames Docker rather than memory.

Two workflows:

| Workflow | Runs on | Does |
|---|---|---|
| `ci.yml` | every push and pull request | unit tests only |
| `deploy.yml` | push to `main`, or manual | test, build, push, deploy, verify, roll back on failure |

## One-time setup on the instance

Docker and the compose plugin must be installed, and the deploy user must be
able to run Docker without sudo.

```bash
sudo mkdir -p /opt/qudu-be
sudo chown "$USER":"$USER" /opt/qudu-be
cd /opt/qudu-be
```

Copy `docker-compose.yml` and `.env.example` there, then:

```bash
cp .env.example .env
chmod 600 .env
# fill every blank in .env
```

`.env` never leaves the instance. CI does not write it, read it, or know what
is in it. It is the only place the database password, the JWT secret and the
mail credentials exist.

Generate a JWT secret with at least 32 characters, or the app refuses to start:

```bash
openssl rand -base64 48
```

## One-time setup in GitHub

A deploy key pair, created on your machine, not on the instance:

```bash
ssh-keygen -t ed25519 -f qudu-deploy -C "github-actions-deploy" -N ""
ssh-copy-id -i qudu-deploy.pub <user>@<instance-ip>
ssh-keyscan -H <instance-ip>          # output goes into EC2_KNOWN_HOSTS
```

Repository secrets, under Settings then Secrets and variables then Actions:

| Secret | Value |
|---|---|
| `EC2_HOST` | the instance address |
| `EC2_USER` | `ubuntu` on Ubuntu AMIs, `ec2-user` on Amazon Linux |
| `EC2_SSH_KEY` | the whole private key, `qudu-deploy`, including both BEGIN and END lines |
| `EC2_KNOWN_HOSTS` | the `ssh-keyscan` output above |

Optional repository variable:

| Variable | Default |
|---|---|
| `EC2_APP_DIR` | `/opt/qudu-be` |

The host key is pinned from `EC2_KNOWN_HOSTS` on purpose. Disabling the check
instead would let anything answering on that address collect the deploy key.

There is no registry credential to create. The workflow passes its own
`GITHUB_TOKEN` over SSH for the duration of one run and logs out afterwards, so
no long lived token is left on the instance.

## Deploying

Push to `main`. Nothing else.

The deploy pulls the image tagged with the commit, starts it, and waits up to
five minutes for the container's healthcheck to pass. If it never does, the
logs are printed and the previous image is started again. A failed deploy
leaves the previous version serving.

## Rolling back

Actions, then Deploy, then Run workflow, and give the twelve character commit
prefix of a build that worked. That skips the test and build jobs and deploys
the existing image directly.

## What is deliberately not automated

**Database migrations.** There are none. `ddl-auto=update` means Hibernate
reshapes the schema on startup, so a deploy can change the database without
anyone approving it. Worth replacing before this holds real money.

**The frontend.** `QuDu-fe` is a separate repository and needs its own
workflow.

**`.env` changes.** Editing it on the instance requires a restart:
`docker compose up -d --force-recreate app`.
