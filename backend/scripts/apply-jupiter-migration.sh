#!/usr/bin/env bash
# Run locally on macOS/Linux. This command DEPLOYS to the selected SSH host.
set -euo pipefail
usage() {
  cat <<'EOF'
Usage: apply-jupiter-migration.sh [--host root@jupiter]
                                 [--app-dir /opt/applic/pocketbase]
                                 [--container pocketbase]

Runs the isolated rehearsal first, then stops PocketBase, creates and checks a
backup, installs only the group migration, persists its Compose mount, restarts,
and verifies health and record preservation. Requires SSH keys, remote Python 3,
tar, Docker Compose, and write access to the app directory. Causes brief downtime.
If migration/startup verification fails, stops PocketBase and retains the backup
for manual recovery; does not automatically overwrite production data.
EOF
}
host=root@jupiter
app_dir=/opt/applic/pocketbase
container=pocketbase
while (($#)); do
  case "$1" in
    --host|--app-dir|--container)
      [[ $# -ge 2 ]] || { usage >&2; exit 2; }
      case "$1" in
        --host) host=$2 ;;
        --app-dir) app_dir=$2 ;;
        --container) container=$2 ;;
      esac
      shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; exit 2 ;;
  esac
done
[[ "$host" =~ ^[a-zA-Z0-9_][a-zA-Z0-9_.@:-]*$ ]] || { echo 'Invalid SSH host' >&2; exit 2; }
[[ "$container" =~ ^[a-zA-Z0-9][a-zA-Z0-9_.-]*$ ]] || { echo 'Invalid container name' >&2; exit 2; }
[[ "$app_dir" =~ ^/[a-zA-Z0-9_./-]+$ ]] || { echo 'Use an absolute app path without spaces or shell characters' >&2; exit 2; }
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
backend_dir=$(cd -- "$script_dir/.." && pwd)
# Do not include macOS extended attributes in the SSH tar stream.
export COPYFILE_DISABLE=1
if tar --version 2>/dev/null | grep -q bsdtar; then
  tar_flags=(--no-xattrs --no-acls --no-fflags)
else
  tar_flags=()
fi
"$script_dir/test-jupiter-migration.sh" --host "$host" --container "$container" --data-dir "$app_dir/pb_data"
printf '\nRehearsal passed. Deploying to %s; PocketBase will be stopped for backup and migration.\n' "$host"
remote_command="set -eu; umask 077; work=\$(mktemp -d /tmp/toki-migration-apply.XXXXXXXX); trap 'rm -rf -- \"\$work\"' EXIT; trap 'exit 130' INT; trap 'exit 143' HUP TERM; tar -xf - -C \"\$work\"; bash \"\$work/apply-migration-remote.sh\" '$container' '$app_dir' \"\$work\""
tar "${tar_flags[@]}" -C "$script_dir" -cf - apply-migration-remote.sh deployment_checks.py \
  -C "$backend_dir/pb_migrations" 1792454401_group_events.js |
  ssh -o BatchMode=yes "$host" "$remote_command"
