#!/usr/bin/env bash
# Rehearse the group migration over SSH. Never migrate the live database.
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: test-jupiter-migration.sh [--host jupiter] [--container pocketbase]
                                [--data-dir /opt/applic/pocketbase/pb_data]

Run from any directory on the machine containing this repository.
Requires local ssh/tar and remote bash, python3 (with sqlite3), and Docker access.
Uses SSH keys/config (BatchMode=yes). For another SSH user, use --host user@jupiter.
No live service stop, production migration, SMTP, ports, or persistent test volume.
EOF
}
host=jupiter
source_container=pocketbase
data_dir=/opt/applic/pocketbase/pb_data
while (($#)); do
  case "$1" in
    --host|--container|--data-dir)
      [[ $# -ge 2 ]] || { usage >&2; exit 2; }
      case "$1" in
        --host) host=$2 ;;
        --container) source_container=$2 ;;
        --data-dir) data_dir=$2 ;;
      esac
      shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) printf 'Unknown argument: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
done
# Keep remote-shell arguments literal; unsupported paths fail before connecting.
[[ "$host" =~ ^[a-zA-Z0-9_][a-zA-Z0-9_.@:-]*$ ]] || { echo 'Invalid SSH host' >&2; exit 2; }
[[ "$source_container" =~ ^[a-zA-Z0-9][a-zA-Z0-9_.-]*$ ]] || { echo 'Invalid container name' >&2; exit 2; }
[[ "$data_dir" =~ ^/[a-zA-Z0-9_./-]+$ ]] || { echo 'Use an absolute data path without spaces or shell characters' >&2; exit 2; }
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
backend_dir=$(cd -- "$script_dir/.." && pwd)
migration=1792454401_group_events.js
[[ -f "$backend_dir/pb_migrations/$migration" ]] || { echo 'Migration file is missing' >&2; exit 1; }
command -v ssh >/dev/null
command -v tar >/dev/null
printf 'Rehearsing %s on %s; production container %s is read-only input.\n' "$migration" "$host" "$source_container"
# The remote outer trap removes the uploaded script, snapshot, and comparison copy.
# The worker's trap removes only the uniquely named rehearsal container it created.
remote_command="set -eu; umask 077; work=\$(mktemp -d /tmp/toki-migration-test.XXXXXXXX); trap 'rm -rf -- \"\$work\"' EXIT HUP INT TERM; tar -xf - -C \"\$work\"; bash \"\$work/rehearse-migration-remote.sh\" '$source_container' '$data_dir' \"\$work\""
tar -C "$script_dir" -cf - rehearse-migration-remote.sh \
  -C "$backend_dir/pb_migrations" "$migration" |
  ssh -o BatchMode=yes "$host" "$remote_command"
