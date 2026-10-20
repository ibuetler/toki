#!/usr/bin/env bash
# Internal deployment worker. Called over SSH by apply-jupiter-migration.sh.
set -euo pipefail
umask 077
[[ $# == 3 ]] || exit 2
container=$1
app=$2
work=$3
migration=1792454401_group_events.js
for tool in docker python3 tar; do command -v "$tool" >/dev/null; done
[[ -d "$app/pb_data" ]] || { echo 'Missing app data directory' >&2; exit 1; }
lock="$app/.toki-group-migration.lock"
mkdir "$lock" || { echo 'Another rollout or stale lock exists; inspect .toki-group-migration.lock' >&2; exit 1; }
stopped=0
attempted=0
backup=''
cleanup() {
  status=$?
  trap - EXIT
  if [[ $status != 0 ]]; then
    if [[ $attempted == 1 ]]; then
      docker stop "$container" >/dev/null 2>&1 || true
      printf '\nDeployment failed. PocketBase has been stopped to protect the backup boundary.\nBackup: %s\nRestore the verified backup and prior Compose configuration before restarting.\n' "$backup" >&2
    elif [[ $stopped == 1 ]]; then
      docker start "$container" >/dev/null || echo 'Could not restart original container; start it manually.' >&2
      echo 'Deployment failed before migration; original container restart requested.' >&2
    fi
  fi
  rmdir "$lock" || true
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' HUP TERM
python3 "$work/deployment_checks.py" preflight "$container" "$app" "$work"
version=$(docker exec "$container" /pb/pocketbase --version)
[[ "$version" == 'pocketbase version 0.40.5' ]] || { echo 'Expected PocketBase 0.40.5' >&2; exit 1; }
compose=(docker compose --project-directory "$app" -p "$(cat "$work/project")" -f "$(cat "$work/base")")
override=$(cat "$work/override")
[[ -z "$override" ]] || compose+=(-f "$override")
backup=$(mktemp -d "$app/backups-toki-groups-$(date -u +%Y%m%dT%H%M%SZ).XXXXXXXX")
printf 'Backup directory (retain for rollback): %s\n' "$backup"
cp "$(cat "$work/base")" "$backup/compose.before"
[[ -z "$override" ]] || cp "$override" "$backup/override.before"
printf '%s\n' "$(cat "$work/image")" > "$backup/image.before"
# Mark the stop before invoking Compose so a partial stop still triggers recovery.
stopped=1
"${compose[@]}" stop pocketbase
# Confirm the writer exited before archiving SQLite and its WAL/SHM companions.
[[ "$(docker inspect --format '{{.State.Running}}' "$container")" == false ]] || { echo 'Source container is still running' >&2; exit 1; }
tar -C "$app" -czf "$backup/pb_data.tar.gz" pb_data
mkdir "$work/restore-check"
tar -C "$work/restore-check" -xzf "$backup/pb_data.tar.gz"
python3 "$work/deployment_checks.py" backup "$work/restore-check"
mkdir -p "$app/pb_migrations"
cp "$work/$migration" "$app/pb_migrations/$migration"
if [[ "$(cat "$work/needs_override")" == yes ]]; then
  case "$(basename "$(cat "$work/base")")" in
    docker-compose.*) override="$app/docker-compose.override.yml" ;;
    *) override="$app/compose.override.yaml" ;;
  esac
  [[ ! -e "$override" ]] || { echo 'Override appeared during rollout; refusing to overwrite it' >&2; exit 1; }
  cat > "$override" <<'EOF'
# Added by TOKI group migration rollout. Keep this persistent mount on recreation.
services:
  pocketbase:
    volumes:
      - ./pb_migrations:/pb/pb_migrations
EOF
  compose+=(-f "$override")
fi
"${compose[@]}" config --quiet
# Freeze this invocation to the exact preflight image even if :latest changes.
python3 - "$work" <<'PY'
import json,pathlib,sys
p=pathlib.Path(sys.argv[1])
(p/'image-pin.json').write_text(json.dumps({'services':{'pocketbase':{'image':(p/'image').read_text()}}}))
PY
attempted=1
"${compose[@]}" -f "$work/image-pin.json" up -d --no-build --pull never --no-deps pocketbase
python3 "$work/deployment_checks.py" verify "$container" "$app" "$work/restore-check"
printf '\nDeployment succeeded. Backup retained at %s\n' "$backup"
printf 'Persistent migration mount: %s/pb_migrations → /pb/pb_migrations\n' "$app"
printf 'Group sharing is now installed. No production test accounts or events were created.\n'
