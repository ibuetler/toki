#!/usr/bin/env python3
"""Offline reference check of the TSV shared with Android's recurrence tests."""
import csv
import datetime as dt
from pathlib import Path
from zoneinfo import ZoneInfo
from dateutil.rrule import rrulestr, rruleset


def parse(value, zone):
    value = dt.datetime.fromisoformat(value.replace('Z', '+00:00'))
    return value.replace(tzinfo=zone) if value.tzinfo is None else value


def main():
    passed = 0
    with (Path(__file__).parents[1] / 'fixtures/recurrence.tsv').open() as stream:
        for case in csv.DictReader(stream, delimiter='\t'):
            zone = ZoneInfo(case['zone'])
            start = parse(case['start'], zone).astimezone(zone)
            schedule = rruleset()
            schedule.rdate(start)
            schedule.rrule(rrulestr(case['rrule'], dtstart=start))
            for value in filter(None, case['rdates'].split(',')):
                schedule.rdate(parse(value, zone))
            for value in filter(None, case['exdates'].split(',')):
                schedule.exdate(parse(value, zone))
            expected = case['expected'].split(',')
            threshold = parse(case['after'], zone) + dt.timedelta(minutes=5)
            actual = [value.astimezone(dt.timezone.utc).isoformat().replace('+00:00', 'Z')
                      for value in schedule.xafter(threshold, count=len(expected) + 1, inc=False)]
            if actual != expected:
                raise AssertionError(f"{case['name']}: {actual} != {expected}")
            print(f"[PASS] {case['name']}")
            passed += 1
    print(f'{passed} shared recurrence fixtures passed')


if __name__ == '__main__':
    main()
