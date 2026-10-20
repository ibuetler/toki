#!/usr/bin/env bash
# Internal worker invoked by test-jupiter-migration.sh; no live writes.
set -euo pipefail
umask 077
[[ $# == 3 ]] || { echo 'Invoke test-jupiter-migration.sh instead' >&2; exit 2; }
source_container=$1
data_dir=$2
work=$3
for binary in python3 docker; do command -v "$binary" >/dev/null; done
[[ -d "$work" && -f "$work/1792454401_group_events.js" ]] || exit 2
rehearsal="toki-migration-test-$(python3 -c 'import secrets; print(secrets.token_hex(8))')"
created=0
cleanup() {
  if [[ "$created" == 1 ]]; then
    docker rm -f "$rehearsal" >/dev/null || {
      printf 'Could not remove rehearsal container %s; remove it manually.\n' "$rehearsal" >&2
      return 1
    }
  fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' HUP TERM
mkdir -p "$work/runtime/pb_data" "$work/runtime/pb_migrations"
cp "$work/1792454401_group_events.js" "$work/runtime/pb_migrations/"

# Read only the relevant inspect metadata, not environment variables or settings.
python3 - "$source_container" "$data_dir" "$work" <<'PY'
import json, pathlib, sqlite3, subprocess, sys, time
name, source, work = sys.argv[1:]
work = pathlib.Path(work)
info = json.loads(subprocess.check_output(['docker', 'inspect', name], text=True))[0]
mounts = [m for m in info['Mounts'] if m['Destination'] == '/pb/pb_data']
if len(mounts) != 1 or mounts[0]['Type'] != 'bind':
    raise SystemExit('Expected a bind mount at /pb/pb_data; refusing to guess the live database location.')
if pathlib.Path(mounts[0]['Source']).resolve() != pathlib.Path(source).resolve():
    raise SystemExit('--data-dir differs from the live container mount; refusing to test a different database.')
if not info['State']['Running']:
    raise SystemExit('Source container is not running; check the selected container.')
(work / 'image-id').write_text(info['Image'])
source_db = pathlib.Path(source, 'data.db').resolve()
# SQLite backup API includes committed WAL transactions without copying a torn DB.
# No immutable=1: it would ignore a live WAL file.
with sqlite3.connect(source_db.as_uri() + '?mode=ro', uri=True, timeout=10) as src:
    with sqlite3.connect(work / 'before.db') as dst:
        started = time.monotonic()
        def progress(status, remaining, total):
            if time.monotonic() - started > 45:
                raise RuntimeError('Snapshot timed out; retry when database traffic is lower.')
        src.backup(dst, pages=256, progress=progress)
with sqlite3.connect(work / 'before.db') as db:
    if db.execute('PRAGMA integrity_check').fetchone()[0] != 'ok':
        raise SystemExit('Snapshot failed integrity check')
    names = {r[0] for r in db.execute('SELECT name FROM _collections')}
    if not {'users', 'deadlines'} <= names:
        raise SystemExit('Expected users/deadlines collections are missing')
    if names & {'groups', 'group_members', 'deadline_preferences'}:
        raise SystemExit('Group collections already exist; this script requires the pre-group schema.')
    cols = {r[1] for r in db.execute('PRAGMA table_info(deadlines)')}
    if 'group' in cols:
        raise SystemExit('Deadline group field already exists; refusing a partial/repeated migration.')
print('Consistent snapshot created and checked; production database was opened read-only.')
PY
image=$(cat "$work/image-id")
# Match the exact live image, not its mutable :latest tag. No pull or networking.
version=$(docker run --rm --pull never --network none --entrypoint /pb/pocketbase "$image" --version)
[[ "$version" == *'0.40.5'* ]] || {
  printf 'Expected PocketBase 0.40.5, got %s. Review compatibility before testing.\n' "$version" >&2
  exit 1
}
printf 'Using live image %s (%s).\n' "$image" "$version"
cp "$work/before.db" "$work/runtime/pb_data/data.db"
docker create --name "$rehearsal" --pull never --network none \
  --entrypoint /pb/pocketbase "$image" migrate up \
  --dir /rehearsal/pb_data --migrationsDir /rehearsal/pb_migrations >/dev/null
created=1
docker cp "$work/runtime" "$rehearsal:/rehearsal"
# Never serve: no HTTP listener, hooks, user login, or outgoing mail.
docker start -a "$rehearsal"
status=$(docker inspect --format '{{.State.ExitCode}}' "$rehearsal")
[[ "$status" == 0 ]] || { echo 'Migration failed in the rehearsal container' >&2; exit 1; }
mkdir -p "$work/after"
docker cp "$rehearsal:/rehearsal/pb_data/." "$work/after/"

python3 - "$work" <<'PY'
import hashlib, json, pathlib, sqlite3, sys
work = pathlib.Path(sys.argv[1])
# Both handles are offline copies with no outstanding WAL after process exit.
wal = work / 'after/data.db-wal'
if wal.exists() and wal.stat().st_size:
    raise SystemExit('Unexpected uncheckpointed WAL after migration; refusing an incomplete comparison.')
before = sqlite3.connect((work / 'before.db').as_uri() + '?mode=ro&immutable=1', uri=True)
after = sqlite3.connect((work / 'after/data.db').as_uri() + '?mode=ro&immutable=1', uri=True)
if after.execute('PRAGMA integrity_check').fetchone()[0] != 'ok':
    raise SystemExit('Migrated database failed integrity check')
def quote(name):
    return '"' + name.replace('"', '""') + '"'
def digest_rows(db, table, columns):
    # Never print rows: they may contain password hashes or personal deadlines.
    hashes = []
    for row in db.execute('SELECT ' + ','.join(map(quote, columns)) + ' FROM ' + quote(table)):
        hashes.append(hashlib.sha256(repr(row).encode()).digest())
    return sorted(hashes)
# Preserve all application records, including any custom collections.
# Internal migration/collection metadata is expected to change.
for (table,) in before.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE '\_%' ESCAPE '\\' AND name NOT LIKE 'sqlite_%'"):
    columns = [r[1] for r in before.execute('PRAGMA table_info(' + quote(table) + ')')]
    if digest_rows(before, table, columns) != digest_rows(after, table, columns):
        raise SystemExit('FAIL: existing records changed in application table ' + table)
    count = before.execute('SELECT COUNT(*) FROM ' + quote(table)).fetchone()[0]
    print(f'PASS: {table}: all {count} existing records preserved')
if after.execute('SELECT COUNT(*) FROM deadlines WHERE "group" != "" OR "group" IS NULL').fetchone()[0]:
    raise SystemExit('FAIL: an existing deadline was shared or has a null group')
for table in ['groups', 'group_members', 'deadline_preferences']:
    if after.execute('SELECT COUNT(*) FROM ' + quote(table)).fetchone()[0] != 0:
        raise SystemExit('FAIL: group migration unexpectedly seeded records')
    row = after.execute('SELECT listRule,viewRule,createRule,updateRule,deleteRule FROM _collections WHERE name=?', (table,)).fetchone()
    if not row or any(not rule for rule in row):
        raise SystemExit('FAIL: missing access rules for ' + table)
fields = json.loads(after.execute("SELECT fields FROM _collections WHERE name='deadlines'").fetchone()[0])
if not any(f['name'] == 'group' and f['type'] == 'relation' for f in fields):
    raise SystemExit('FAIL: deadline group relation missing')
print('PASS: new collections/access rules exist; all existing deadlines remain private.')
print('Migration rehearsal passed. Jupiter live service and database were not migrated.')
PY
