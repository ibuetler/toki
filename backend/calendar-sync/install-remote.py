#!/usr/bin/env python3
"""Install calendar service after the separately rehearsed schema migration.
Run as root, with extracted bundle path and owner email. Never logs credentials.
"""
import configparser
import json
import os
from pathlib import Path
import pwd
import secrets
import shutil
import sqlite3
import subprocess
import sys

bundle, email = Path(sys.argv[1]), sys.argv[2]


def command(*args):
    try:
        return subprocess.check_output(args, text=True, stderr=subprocess.PIPE, timeout=180)
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
        # Commands may carry a freshly generated password. Never include argv.
        raise RuntimeError('Calendar installation command failed; check service status and runtime permissions.') from None


# Resolve the explicit owner through the live DB read-only; no password is needed.
info = json.loads(command('docker', 'inspect', 'pocketbase'))[0]
data = next(m['Source'] for m in info['Mounts'] if m['Destination'] == '/pb/pb_data')
with sqlite3.connect(Path(data, 'data.db').resolve().as_uri() + '?mode=ro', uri=True) as db:
    owner = db.execute('SELECT id FROM users WHERE lower(email)=lower(?)', (email,)).fetchone()
    if not owner:
        raise RuntimeError('Requested TOKI owner does not exist')
    if not db.execute("SELECT id FROM _collections WHERE name='calendar_events'").fetchone():
        raise RuntimeError('Calendar migration must be deployed first')
try:
    account = pwd.getpwnam('toki-calendar')
except KeyError:
    command('useradd', '--system', '--home-dir', '/nonexistent', '--shell', '/usr/sbin/nologin', 'toki-calendar')
    account = pwd.getpwnam('toki-calendar')
config_dir = Path('/etc/toki-calendar')
config_dir.mkdir(mode=0o750, exist_ok=True)
config_dir.chmod(0o750)
os.chown(config_dir, 0, account.pw_gid)
config_file = config_dir / 'config.ini'
config = configparser.ConfigParser(interpolation=None)
config.read(bundle / 'config.ini')
config['pocketbase']['url'] = 'https://pocketbase.hacking-lab.com'
# Explicit per-calendar recipients take precedence over the CLI default.
for section in config.sections():
    if section.startswith('calendar:') and not config.get(section, 'user', fallback='').strip():
        config[section]['user'] = email
config.remove_option('pocketbase', 'owner')
if config_file.exists():
    previous = configparser.ConfigParser(interpolation=None)
    previous.read(config_file)
    # Keep remotely maintained exclusions unless supplied explicitly in the bundle.
    if previous.has_section('exclude') and not config.has_section('exclude'):
        config['exclude'] = dict(previous['exclude'])
    config['pocketbase']['email'] = previous['pocketbase']['email']
    config['pocketbase']['password'] = previous['pocketbase']['password']
else:
    identity, password = 'toki-calendar-' + secrets.token_hex(6) + '@example.invalid', secrets.token_urlsafe(48)
    command('docker', 'exec', 'pocketbase', '/pb/pocketbase', 'superuser', 'create', identity, password, '--dir=/pb/pb_data')
    config['pocketbase']['email'], config['pocketbase']['password'] = identity, password
os.umask(0o077)
with config_file.open('w') as output:
    config.write(output)
config_file.chmod(0o600)
os.chown(config_file, account.pw_uid, account.pw_gid)
os.umask(0o022)
app = Path('/opt/toki-calendar')
app.mkdir(mode=0o755, exist_ok=True)
app.chmod(0o755)
for name in ('import_calendars.py', 'requirements.txt'):
    shutil.copy2(bundle / name, app / name)
    (app / name).chmod(0o644)
if not (app / '.venv/bin/python').exists():
    command('python3', '-m', 'venv', str(app / '.venv'))
command(str(app / '.venv/bin/pip'), 'install', '-q', '-r', str(app / 'requirements.txt'))
# Runtime dependencies contain no configuration; allow the unprivileged reader.
for directory, subdirs, files in os.walk(app / '.venv'):
    Path(directory).chmod(0o755)
    for filename in files:
        runtime_file = Path(directory) / filename
        if not runtime_file.is_symlink():
            runtime_file.chmod(0o755 if runtime_file.stat().st_mode & 0o111 else 0o644)
for name in ('toki-calendar-sync.service', 'toki-calendar-sync.timer'):
    shutil.copy2(bundle / name, Path('/etc/systemd/system') / name)
    (Path('/etc/systemd/system') / name).chmod(0o644)
command('systemctl', 'daemon-reload')
# Verify feed parsing and backend access without writes before enabling automation.
command('runuser', '-u', 'toki-calendar', '--', str(app / '.venv/bin/python'), str(app / 'import_calendars.py'),
        '--config', str(config_file), '--lock', '/tmp/toki-calendar-install.lock')
command('systemctl', 'enable', '--now', 'toki-calendar-sync.timer')
command('systemctl', 'start', 'toki-calendar-sync.service')
print('Calendar service installed; first import succeeded; four-hour timer enabled.')
