#!/usr/bin/env python3
"""Move legacy private INI calendar sections/rules to PocketBase. Dry run by default.
Run with the importer's Python runtime, as the configuration file owner or root.
"""
import argparse
import configparser
import hashlib
import os
from pathlib import Path
import stat
from import_calendars import PocketBase, route_sources


def migrate(path, apply=False):
    path=Path(path)
    owner=path.stat()
    if stat.S_IMODE(owner.st_mode)&0o077:raise ValueError('Private configuration required')
    config=configparser.ConfigParser(interpolation=None);config.read(path)
    if config.get('sync','settings_source',fallback='config')=='pocketbase':
        print('Calendar settings already use PocketBase.');return
    api=PocketBase(config['pocketbase'])
    sections=[s for s in config.sections() if s.startswith('calendar:')]
    if not sections:raise ValueError('No legacy sources to migrate')
    routes=route_sources(config,sections,api)
    planned_sources={}
    planned_rules={}
    for section in sections:
        record_id=hashlib.sha256((routes[section]+section).encode()).hexdigest()[:15]
        planned_sources[record_id]={'owner':routes[section],'name':config[section]['name'],'url':config[section]['url'],'enabled':True}
    for user in set(routes.values()):
        for title in config.get('exclude','titles',fallback='').splitlines():
            title=title.strip()
            if not title:continue
            record_id=hashlib.sha256((user+'exact:'+title.casefold()).encode()).hexdigest()[:15]
            planned_rules[record_id]={'owner':user,'pattern':title,'source':'','match_type':'exact','enabled':True}
    # Verify both collections before writes. Refuse to overwrite app-managed changes.
    existing={name:{r['id']:r for r in api.all_records(name)} for name in ('calendar_sources','calendar_exclusions')}
    for collection,planned in [('calendar_sources',planned_sources),('calendar_exclusions',planned_rules)]:
        for record_id,row in existing[collection].items():
            if record_id not in planned or any(row.get(k)!=v for k,v in planned[record_id].items()):
                raise ValueError('Settings already managed in PocketBase; refusing to overwrite')
    print('Legacy migration: %s connections; %s exact-title rules; %s recipients.' % (len(planned_sources),len(planned_rules),len(set(routes.values()))))
    if not apply:return
    for collection,planned in [('calendar_sources',planned_sources),('calendar_exclusions',planned_rules)]:
        for record_id,payload in planned.items():
            if record_id not in existing[collection]:api.request('POST','/api/collections/'+collection+'/records',dict(payload,id=record_id))
    config['sync']['settings_source']='pocketbase'
    config['sync']['allowed_hosts']=config.get('sync','allowed_hosts',fallback='outlook.office365.com')
    for section in sections+['exclude']:
        config.remove_section(section)
    temporary=path.with_suffix('.ini.new')
    fd=os.open(temporary,os.O_CREAT|os.O_TRUNC|os.O_WRONLY,0o600)
    with os.fdopen(fd,'w') as stream:config.write(stream)
    os.chmod(temporary,0o600);os.chown(temporary,owner.st_uid,owner.st_gid);os.replace(temporary,path)
    print('Migrated settings; private INI now contains runtime settings and service credentials only.')


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--config',required=True);parser.add_argument('--apply',action='store_true');args=parser.parse_args()
    try:migrate(args.config,args.apply)
    except Exception as error:
        print('Settings migration failed (%s); no credentials or URLs logged.' % type(error).__name__)
        raise SystemExit(1)
