#!/usr/bin/env python3
"""Internal SSH worker: rehearse a reviewed additive migration, optionally deploy."""
import collections
import datetime
import hashlib
import json
import os
from pathlib import Path
import shutil
import sqlite3
import subprocess
import sys
import tempfile
import time

container, app_name, work_name, mode = sys.argv[1:5]
collection = sys.argv[5] if len(sys.argv) > 5 else 'birthdays'
if collection not in {'birthdays', 'tasks'}: raise RuntimeError('Unreviewed migration')
MIGRATION = {'birthdays': '1792540800_birthdays.js', 'tasks': '1792627200_tasks.js'}[collection]
required_fields = ({'owner', 'name', 'month', 'day', 'birth_year', 'reminder_hour', 'reminder_minute', 'leap_day_policy', 'enabled'} if collection == 'birthdays' else {'owner', 'title', 'notes', 'priority', 'completed', 'remind_at'})
app, work = Path(app_name), Path(work_name)
apply = mode == 'apply'


def command(*args):
    return subprocess.check_output(args, text=True, stderr=subprocess.STDOUT, timeout=120)


def snapshot(source, target, offline=False):
    if offline:
        wal = Path(str(source) + '-wal')
        if wal.exists() and wal.stat().st_size: raise RuntimeError('Unexpected uncheckpointed offline WAL; refusing to ignore it')
    uri = source.resolve().as_uri() + ('?mode=ro&immutable=1' if offline else '?mode=ro')
    with sqlite3.connect(uri, uri=True, timeout=15) as source_db:
        with sqlite3.connect(target) as destination:
            started = time.monotonic()
            def progress(*_):
                if time.monotonic() - started > 45: raise RuntimeError('Snapshot timed out')
            source_db.backup(destination, pages=256, progress=progress)
    with sqlite3.connect(target.resolve().as_uri() + '?mode=ro&immutable=1', uri=True) as db:
        if db.execute('PRAGMA integrity_check').fetchone()[0] != 'ok': raise RuntimeError('Database integrity check failed')


def quote(value): return '"' + value.replace('"', '""') + '"'


def verify(before_file, after_file):
    with sqlite3.connect(before_file.resolve().as_uri() + '?mode=ro&immutable=1', uri=True) as before, sqlite3.connect(after_file.resolve().as_uri() + '?mode=ro&immutable=1', uri=True) as after:
        if after.execute('PRAGMA integrity_check').fetchone()[0] != 'ok': raise RuntimeError('Migrated database integrity failed')
        tables = [r[0] for r in before.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")]
        for table in tables:
            columns = [r[1] for r in before.execute('PRAGMA table_info(' + quote(table) + ')')]
            sql = 'SELECT ' + ','.join(map(quote, columns)) + ' FROM ' + quote(table)
            def hashes(db):
                return collections.Counter(hashlib.sha256(repr(row).encode()).digest() for row in db.execute(sql))
            old, new = hashes(before), hashes(after)
            if table in {'_collections', '_migrations'}:
                if old - new or sum(new.values()) != sum(old.values()) + 1:
                    raise RuntimeError('Unexpected schema/history changes: ' + table)
            elif old != new: raise RuntimeError('Existing table changed: ' + table)
        row = after.execute("SELECT listRule,viewRule,createRule,updateRule,deleteRule FROM _collections WHERE name=?", (collection,)).fetchone()
        if not row or not all(row): raise RuntimeError(collection + ' rules missing')
        for rule in row:
            if 'owner = @request.auth.id' not in rule: raise RuntimeError(collection + ' owner isolation missing')
        fields = {r[1] for r in after.execute('PRAGMA table_info(' + quote(collection) + ')')}
        if not required_fields <= fields:
            raise RuntimeError(collection + ' fields missing')
        if after.execute('SELECT count(*) FROM ' + quote(collection)).fetchone()[0] != 0: raise RuntimeError('New ' + collection + ' collection is not empty')
    print('PASS: existing records, settings, schemas and rules preserved; private ' + collection + ' collection added.', flush=True)


info = json.loads(command('docker', 'inspect', container))[0]
if not info['State']['Running']: raise RuntimeError('PocketBase must be running at preflight')
mounts = {m['Destination']: m for m in info['Mounts']}
for destination, source in [('/pb/pb_data', app / 'pb_data'), ('/pb/pb_migrations', app / 'pb_migrations')]:
    mount = mounts.get(destination)
    if not mount or mount['Type'] != 'bind' or Path(mount['Source']).resolve() != source.resolve():
        raise RuntimeError('Expected persistent bind mount missing or mismatched: ' + destination)
image = info['Image']
if '0.40.5' not in command('docker', 'run', '--rm', '--pull', 'never', '--network', 'none', '--entrypoint', '/pb/pocketbase', image, '--version'):
    raise RuntimeError('Only reviewed PocketBase 0.40.5 is supported')
if (app / 'pb_migrations' / MIGRATION).exists(): raise RuntimeError('Migration file already exists; inspect history instead of redeploying')
print('Preflight passed; snapshotting databases for isolated rehearsal.', flush=True)
snapshot(app / 'pb_data/data.db', work / 'before.db')
with sqlite3.connect(work / 'before.db') as db:
    if db.execute("SELECT count(*) FROM _collections WHERE name=?", (collection,)).fetchone()[0]: raise RuntimeError(collection + ' already installed')
    names = {r[0] for r in db.execute('SELECT name FROM _collections')}
    if not {'users', 'deadlines', 'groups', 'group_members', 'deadline_preferences'} <= names: raise RuntimeError('Expected TOKI group schema missing')
runtime = work / 'runtime'
(runtime / 'pb_data').mkdir(parents=True)
(runtime / 'pb_migrations').mkdir()
shutil.copy2(work / 'before.db', runtime / 'pb_data/data.db')
if (app / 'pb_data/auxiliary.db').exists():
    snapshot(app / 'pb_data/auxiliary.db', runtime / 'pb_data/auxiliary.db')
shutil.copy2(work / MIGRATION, runtime / 'pb_migrations' / MIGRATION)
rehearsal = 'toki-birthday-rehearsal-' + os.urandom(6).hex()
try:
    command('docker', 'create', '--name', rehearsal, '--pull', 'never', '--network', 'none', '--entrypoint', '/pb/pocketbase', image,
            'migrate', 'up', '--dir', '/birthday/pb_data', '--migrationsDir', '/birthday/pb_migrations')
    command('docker', 'cp', str(runtime), rehearsal + ':/birthday')
    command('docker', 'start', '-a', rehearsal)
    if command('docker', 'inspect', '--format', '{{.State.ExitCode}}', rehearsal).strip() != '0': raise RuntimeError('Isolated migration failed')
    (work / 'after').mkdir()
    command('docker', 'cp', rehearsal + ':/birthday/pb_data/.', str(work / 'after'))
    snapshot(work / 'after/data.db', work / 'after.db', offline=True)
    verify(work / 'before.db', work / 'after.db')
finally:
    subprocess.run(['docker', 'rm', '-f', rehearsal], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
print('Rehearsal passed; no live changes made.', flush=True)
if not apply: sys.exit(0)

backup = Path(tempfile.mkdtemp(prefix='backups-toki-' + collection + '-' + datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '-', dir=app))
os.chmod(backup, 0o700)
print('Backup directory: ' + str(backup), flush=True)
stopped = False
migration_installed = False
try:
    command('docker', 'stop', container)
    stopped = True
    command('tar', '--numeric-owner', '-czf', str(backup / 'pb_data.tar.gz'), '-C', str(app), 'pb_data')
    for filename in ['docker-compose.yml', 'docker-compose.override.yml']:
        if (app / filename).exists(): shutil.copy2(app / filename, backup / filename)
    (backup / 'image.before').write_text(image)
    (work / 'restore').mkdir()
    command('tar', '-xzf', str(backup / 'pb_data.tar.gz'), '-C', str(work / 'restore'))
    for database in (work / 'restore/pb_data').glob('*.db'):
        snapshot(database, work / ('verified-' + database.name), offline=True)
    snapshot(work / 'restore/pb_data/data.db', work / 'deployment-before.db', offline=True)
    # Match the working data owner for Docker userns-remap; never make files public.
    owner = (app / 'pb_data').stat()
    target = app / 'pb_migrations' / MIGRATION
    os.chown(app / 'pb_migrations', owner.st_uid, owner.st_gid)
    migration_installed = True  # Any partial installation must leave the service stopped on failure.
    shutil.copy2(work / MIGRATION, target)
    os.chown(target, owner.st_uid, owner.st_gid)
    os.chmod(target, 0o600)
    migration_installed = True
    command('docker', 'start', container)
    for attempt in range(30):
        response = subprocess.run(['docker', 'exec', container, 'wget', '-q', '-O', '-', 'http://127.0.0.1:8090/api/health'], capture_output=True)
        if response.returncode == 0: break
        time.sleep(1)
    else: raise RuntimeError('Health check failed')
    snapshot(app / 'pb_data/data.db', work / 'deployment-after.db')
    verify(work / 'deployment-before.db', work / 'deployment-after.db')
    print(collection + ' migration deployed; PocketBase is healthy. Retain backup: ' + str(backup), flush=True)
except Exception:
    if migration_installed:
        subprocess.run(['docker', 'stop', container], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        print('Deployment failed; PocketBase stopped. Preserve failed data and restore the verified backup before restarting. Backup: ' + str(backup), flush=True)
    elif stopped:
        command('docker', 'start', container)
        print('Preparation failed; original service restarted without installing the migration.', flush=True)
    raise
