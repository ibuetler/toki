# PocketBase backend

## Captured configuration

The files in `docker/Dockerfile` and `docker/docker-compose.yml` were copied from Jupiter. The Dockerfile defaults to PocketBase **0.40.5**, with Alpine 3.22 and amd64/arm64 support. This identifies the build configuration, not an independently inspected running Jupiter binary or image digest. Compose uses Traefik's external `transit` network, the `websecure` entrypoint, and `pocketbase.hacking-lab.com`, forwarding to port 8090.

`schema/collections.json` contains the `users` and `deadlines` collection definitions extracted read-only from the supplied local database copy. `pb_migrations/1792454400_toki_schema.js` imports the same definitions using PocketBase's migration API. It preserves collection IDs, fields, indexes, access rules, password-auth configuration, and authentication feature enablement. It excludes records, server settings, token signing secrets, OAuth provider credentials, email templates, and custom token lifetimes; omitted settings use PocketBase defaults. This is a reproducible application schema, not a complete server backup.

The copied `docker/pb_data/` remains ignored by Git. `.dockerignore` also excludes it from image build contexts. Never commit database files, backups, real environment files, account records, or SMTP credentials.

## Validate a fresh local instance

Run from the repository root. Docker must be running. The local Compose file uses a separate named volume, localhost port 8090, and no Traefik configuration or production data mount.

```sh
docker compose -p toki-validation -f backend/docker/compose.local.yaml build
docker compose -p toki-validation -f backend/docker/compose.local.yaml create
docker cp backend/pb_migrations toki-validation-pocketbase-1:/pb/pb_migrations
docker compose -p toki-validation -f backend/docker/compose.local.yaml start
```

Migrations run automatically when PocketBase starts. Copying them into the stopped container avoids requiring Docker host directory sharing. Repeat the copy step if the container is recreated. Open `http://127.0.0.1:8090/_/` to provision a local superuser and two disposable users. Configure a separate ignored test configuration with the local endpoint and those users, then run:

```sh
cd backend/pocketbase-tests
python testing.py -all -config config.local.ini
```

On October 20, 2026, a fresh local arm64 build successfully applied this migration and passed **54 checks with 0 failures**, using two disposable users. All four temporary deadlines were deleted. SMTP, delivery of email, Traefik routing, and amd64 execution were not tested.

Stop the validation instance when finished:

```sh
docker compose -p toki-validation -f backend/docker/compose.local.yaml down
```

The named volume is retained for inspection. Use a new project name and adjust the container name in the copy command when validating another empty installation.

## Access rules

- `users`: users can list/view only themselves; create/update/delete are superuser-only.
- `deadlines`: list/view/delete require ownership; creation requires an authenticated matching owner; updates require ownership and an unchanged owner.
- Anonymous list queries return an empty list under the captured rules, which is a valid denial of record access.

## Deployment and operations

The copied production Compose file does not include a migration mount. Before deploying a fresh server, arrange for the captured migrations to be present in `/pb/pb_migrations` before startup. Review migrations against backups before applying them to an existing instance. Do not run the local validation configuration against Jupiter's data.

SMTP credentials, server settings, the exact running image digest, backup scheduling, and a tested restore procedure still need to be captured separately. Keep backup archives outside Git; verify restoration in a separate environment before upgrades. The supplied database copy includes WAL files and should not be treated as a verified restorable backup.

References: [PocketBase migrations](https://pocketbase.io/docs/js-migrations/), [API rules](https://pocketbase.io/docs/api-rules-and-filters/), and [deployment guidance](https://pocketbase.io/docs/going-to-production/).
