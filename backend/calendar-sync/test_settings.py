import configparser
import datetime as dt
import unittest
from unittest.mock import patch
from zoneinfo import ZoneInfo
from import_calendars import title_matches, validate_feed_url, apply_rules, merge_entries, database_sync
from test_import_calendars import parse, feed, BASE

class SettingsTests(unittest.TestCase):
    def test_wildcards_are_case_insensitive_whole_title_matches(self):
        for title, pattern in [('Marina in CH','Marina *'),('MARINA IN CH','marina *'),('My Vacation plan','*Vacation*'),('Team A','Team ?'),('A [B] (C).','A [B] (C).')]:
            self.assertTrue(title_matches(title,pattern))
        for title, pattern in [('With Marina in CH','Marina *'),('Team AB','Team ?'),('Marina in CH','Marina'),('A','')]:
            self.assertFalse(title_matches(title,pattern))
        self.assertFalse(title_matches('Marina in CH','Marina *','exact'))

    def test_scoped_rule_applies_after_merge_and_not_to_other_sources(self):
        merged=merge_entries([parse(feed(BASE),'A'),parse(feed(BASE),'B')])
        sources=[{'id':'a','name':'A'},{'id':'b','name':'B'}]
        rule={'enabled':True,'source':'a','pattern':'Meet*','match_type':'wildcard'}
        self.assertEqual({},apply_rules(merged,[rule],sources))
        self.assertEqual(1,len(apply_rules(merged,[dict(rule,source='other')],sources)))
        self.assertEqual(1,len(apply_rules(merged,[dict(rule,enabled=False)],sources)))

    def test_unapproved_url_and_private_dns_are_rejected(self):
        for url in ['http://outlook.office365.com/a','https://evil.invalid/a','https://outlook.office365.com:8090/a','https://user@outlook.office365.com/a']:
            with self.assertRaises(ValueError):validate_feed_url(url,{'outlook.office365.com'})
        with patch('import_calendars.socket.getaddrinfo',return_value=[(0,0,0,'',('127.0.0.1',443))]):
            with self.assertRaises(ValueError):validate_feed_url('https://outlook.office365.com/a',{'outlook.office365.com'})

    def test_failed_owner_keeps_records_while_other_owner_can_sync(self):
        owner='a'*15;other='b'*15
        class API:
            def __init__(self):self.writes=[]
            def all_records(self,collection):
                return [{'id':'fail','owner':owner,'name':'Fail','url':'https://outlook.office365.com/fail','enabled':True},{'id':'ok','owner':other,'name':'OK','url':'https://outlook.office365.com/ok','enabled':True}] if collection=='calendar_sources' else []
            def records(self,user=None):return [{'id':'keep','owner':owner,'external_key':'old'}] if user in (None,owner) else []
            def request(self,method,path,payload):self.writes.append((method,path,payload))
        api=API();config=configparser.ConfigParser();config.read_dict({'sync':{}})
        def fetch(url,allowed):
            if url.endswith('/fail'):raise ValueError('Unavailable')
            return feed(BASE)
        with patch('import_calendars.fetch_feed',side_effect=fetch):
            counts=database_sync(api,config,ZoneInfo('Europe/Zurich'),dt.datetime(2026,10,20,tzinfo=dt.timezone.utc),dt.datetime(2026,11,1,tzinfo=dt.timezone.utc),20000,True)
        self.assertEqual(1,counts['created'])
        self.assertFalse(any(method=='DELETE' for method,_,_ in api.writes))
        self.assertTrue(any(payload.get('last_error') for _,_,payload in api.writes))

if __name__=='__main__':unittest.main()
