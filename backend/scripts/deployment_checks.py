"""Internal deployment checks. Never prints records, settings, or credentials."""
import json
import pathlib
import sqlite3
import subprocess
import sys
import time
import hashlib


def docker_json(*args):
    return json.loads(subprocess.check_output(['docker', *args], text=True))


def preflight(container, app, work):
    app, work = pathlib.Path(app).resolve(), pathlib.Path(work)
    info = docker_json('inspect', container)[0]
    if not info['State']['Running']:
        raise SystemExit('PocketBase must be running before deployment')
    project = info['Config'].get('Labels', {}).get('com.docker.compose.project')
    if not project:
        raise SystemExit('Source is not a Docker Compose container')
    bases = [app / p for p in ['docker-compose.yml', 'docker-compose.yaml', 'compose.yml', 'compose.yaml'] if (app / p).exists()]
    overrides = [app / p for p in ['docker-compose.override.yml', 'docker-compose.override.yaml', 'compose.override.yml', 'compose.override.yaml'] if (app / p).exists()]
    if len(bases) != 1 or len(overrides) > 1:
        raise SystemExit('Expected one Compose base file and at most one conventional override')
    command = ['docker', 'compose', '--project-directory', str(app), '-p', project, '-f', str(bases[0])]
    if overrides:
        command += ['-f', str(overrides[0])]
    config = json.loads(subprocess.check_output(command + ['config', '--format', 'json'], text=True))
    service = config['services'].get('pocketbase', {})
    if service.get('container_name') != container:
        raise SystemExit('Compose pocketbase service does not match --container')
    resolved = subprocess.check_output(command + ['ps', '-q', 'pocketbase'], text=True).strip()
    if resolved != info['Id']:
        raise SystemExit('Compose does not select the running source container')
    if docker_json('image', 'inspect', service['image'])[0]['Id'] != info['Image']:
        raise SystemExit('Compose image tag differs from the running image; resolve this before migration')
    mounts = {m['Destination']: m for m in info['Mounts']}
    live = mounts.get('/pb/pb_data', {})
    if live.get('Type') != 'bind' or pathlib.Path(live.get('Source', '/')).resolve() != app / 'pb_data':
        raise SystemExit('Expected live data bind mount at APP_DIR/pb_data')
    volumes = {v['target']: v for v in service.get('volumes', [])}
    data = volumes.get('/pb/pb_data', {})
    if data.get('type') != 'bind' or pathlib.Path(data.get('source', '/')).resolve() != app / 'pb_data':
        raise SystemExit('Compose data mount differs from the running mount')
    migration = volumes.get('/pb/pb_migrations')
    needs_override = not migration
    if migration and (migration.get('type') != 'bind' or pathlib.Path(migration.get('source', '/')).resolve() != app / 'pb_migrations'):
        raise SystemExit('Existing migration mount points elsewhere; refusing to change it')
    if needs_override and overrides:
        raise SystemExit('Existing Compose override has no migration mount. Add APP_DIR/pb_migrations:/pb/pb_migrations there, then rerun.')
    target = app / 'pb_migrations/1792454401_group_events.js'
    if target.exists() and target.read_bytes() != (work / target.name).read_bytes():
        raise SystemExit('A different migration with this filename already exists')
    # SQL in read-only mode, with WAL awareness, never immutable on live data.
    with sqlite3.connect((app / 'pb_data/data.db').as_uri() + '?mode=ro', uri=True) as db:
        names = {r[0] for r in db.execute('SELECT name FROM _collections')}
        if names & {'groups', 'group_members', 'deadline_preferences'}:
            raise SystemExit('Group schema already exists; refusing a repeated/partial rollout')
    # Emit only shell-safe values to individual files, not environment/config dumps.
    for key, value in {'project': project, 'base': str(bases[0]), 'override': str(overrides[0]) if overrides else '',
                       'needs_override': 'yes' if needs_override else 'no', 'image': info['Image']}.items():
        (work / key).write_text(value)
    print('Preflight passed: Compose, image, source data, and migration filename match.')


def check_backup(restore):
    restore = pathlib.Path(restore)
    for name in ['data.db', 'auxiliary.db']:
        path = restore / 'pb_data' / name
        if not path.exists() and name == 'auxiliary.db':
            continue
        # This is the extracted backup, not live data. Recover/checkpoint any WAL.
        with sqlite3.connect(path) as db:
            if db.execute('PRAGMA integrity_check').fetchone()[0] != 'ok':
                raise SystemExit('Backup restore integrity check failed: ' + name)
            db.execute('PRAGMA wal_checkpoint(TRUNCATE)')
    print('Backup archive extracted successfully; restored database integrity checks passed.')


def verify(container, app, restore):
    # Health request is inside the container: no dependency on host port exposure.
    for _ in range(30):
        result = subprocess.run(['docker', 'exec', container, 'wget', '-q', '-O', '-',
                                 'http://127.0.0.1:8090/api/health'], capture_output=True, timeout=5)
        if result.returncode == 0:
            break
        time.sleep(1)
    else:
        raise SystemExit('PocketBase health check failed')
    source = pathlib.Path(app, 'pb_data/data.db').resolve()
    snapshot = pathlib.Path(restore, 'after.db')
    with sqlite3.connect(source.as_uri() + '?mode=ro', uri=True, timeout=10) as src:
        with sqlite3.connect(snapshot) as dst:
            src.backup(dst)
    before = sqlite3.connect(pathlib.Path(restore, 'pb_data/data.db'))
    after = sqlite3.connect(snapshot)
    if after.execute('PRAGMA integrity_check').fetchone()[0] != 'ok':
        raise SystemExit('Migrated database integrity failed')
    quote = lambda name: '"' + name.replace('"', '""') + '"'
    def rows(db, table, cols):
        query = 'SELECT ' + ','.join(map(quote, cols)) + ' FROM ' + quote(table)
        return sorted(hashlib.sha256(repr(row).encode()).digest() for row in db.execute(query))
    for (table,) in before.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE '\_%' ESCAPE '\\' AND name NOT LIKE 'sqlite_%'"):
        cols = [r[1] for r in before.execute('PRAGMA table_info(' + quote(table) + ')')]
        if rows(before, table, cols) != rows(after, table, cols):
            raise SystemExit('Record-preservation check failed: ' + table)
        print('PASS: existing records preserved in ' + table)
    if after.execute('SELECT COUNT(*) FROM deadlines WHERE "group" != "" OR "group" IS NULL').fetchone()[0]:
        raise SystemExit('Existing deadline group field is not empty')
    for name in ['groups', 'group_members', 'deadline_preferences']:
        rule = after.execute('SELECT listRule,viewRule,createRule,updateRule,deleteRule FROM _collections WHERE name=?', (name,)).fetchone()
        if not rule or any(not value for value in rule):
            raise SystemExit('Missing collection or access rules: ' + name)
    if not after.execute("SELECT 1 FROM _migrations WHERE file='1792454401_group_events.js'").fetchone():
        raise SystemExit('Migration not recorded in history')
    print('PASS: health, migration history, group collections, rules, and private deadlines verified.')


if __name__ == '__main__':
    {'preflight': preflight, 'backup': check_backup, 'verify': verify}[sys.argv[1]](*sys.argv[2:])
