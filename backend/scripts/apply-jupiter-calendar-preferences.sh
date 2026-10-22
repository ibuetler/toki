#!/usr/bin/env bash
# Defaults to a read-only rehearsal. --apply additionally deploys with verified backup.
set -euo pipefail
host=root@jupiter
app_dir=/opt/applic/pocketbase
container=pocketbase
mode=rehearse
while (($#)); do
  case "$1" in
    --host|--app-dir|--container)
      [[ $# -ge 2 ]] || exit 2
      case "$1" in --host) host=$2 ;; --app-dir) app_dir=$2 ;; --container) container=$2 ;; esac
      shift 2 ;;
    --apply) mode=apply; shift ;;
    -h|--help) echo 'Usage: apply-jupiter-calendar-preferences.sh [--host root@jupiter] [--app-dir /opt/applic/pocketbase] [--container pocketbase] [--apply]'; exit 0 ;;
    *) echo 'Unknown argument' >&2; exit 2 ;;
  esac
done
[[ "$host" =~ ^[a-zA-Z0-9_][a-zA-Z0-9_.@:-]*$ && "$container" =~ ^[a-zA-Z0-9][a-zA-Z0-9_.-]*$ && "$app_dir" =~ ^/[a-zA-Z0-9_./-]+$ ]] || exit 2
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
export COPYFILE_DISABLE=1
if tar --version 2>/dev/null | grep -q bsdtar; then flags=(--no-xattrs --no-acls --no-fflags); else flags=(); fi
remote="set -eu; umask 077; work=\$(mktemp -d /tmp/toki-calendars.XXXXXXXX); trap 'rm -rf -- \"\$work\"' EXIT; trap 'exit 130' INT; trap 'exit 143' HUP TERM; tar -xf - -C \"\$work\"; python3 \"\$work/birthday-migration-remote.py\" '$container' '$app_dir' \"\$work\" '$mode' calendar_preferences"
tar "${flags[@]}" -C "$script_dir" -cf - birthday-migration-remote.py -C "$script_dir/../pb_migrations" 1792713601_calendar_preferences.js |
  ssh -o BatchMode=yes -o ConnectTimeout=20 -o ServerAliveInterval=5 -o ServerAliveCountMax=3 "$host" "$remote"
