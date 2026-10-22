#!/usr/bin/env bash
# Installs importer after apply-jupiter-calendars.sh --apply. Configuration is private.
set -euo pipefail
host=root@jupiter
owner_email=
config=
while (($#)); do
  case "$1" in
    --host|--owner-email|--config)
      [[ $# -ge 2 ]] || exit 2
      case "$1" in --host) host=$2 ;; --owner-email) owner_email=$2 ;; --config) config=$2 ;; esac
      shift 2 ;;
    *) echo 'Usage: install-jupiter-calendars.sh --owner-email EMAIL --config PATH [--host root@jupiter]' >&2; exit 2 ;;
  esac
done
[[ "$host" =~ ^[a-zA-Z0-9_][a-zA-Z0-9_.@:-]*$ && "$owner_email" =~ ^[a-zA-Z0-9_.+%-]+@[a-zA-Z0-9.-]+$ && -f "$config" ]] || exit 2
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
# Build a private bundle; never include runtime files or real configuration in Git.
umask 077
work=$(mktemp -d)
trap 'rm -rf -- "$work"' EXIT
cp "$script_dir/../calendar-sync/"{import_calendars.py,requirements.txt,install-remote.py,toki-calendar-sync.service,toki-calendar-sync.timer} "$work/"
cp "$config" "$work/config.ini"
export COPYFILE_DISABLE=1
if tar --version 2>/dev/null | grep -q bsdtar; then flags=(--no-xattrs --no-acls --no-fflags); else flags=(); fi
remote="set -eu; umask 077; work=\$(mktemp -d /tmp/toki-calendar-install.XXXXXXXX); trap 'rm -rf -- \"\$work\"' EXIT; tar -xf - -C \"\$work\"; python3 \"\$work/install-remote.py\" \"\$work\" '$owner_email'"
tar "${flags[@]}" -C "$work" -cf - . | ssh -o BatchMode=yes -o ConnectTimeout=20 "$host" "$remote"
