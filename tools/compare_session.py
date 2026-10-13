#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""逐运动类型对比双方 session 关键字段取值 + record 采样节奏，用于判断量纲/数值正确性。"""
import csv
import os
from collections import OrderedDict

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OURS_DIR = os.path.join(BASE, 'output', 'csv', 'ours')
GARMIN_DIR = os.path.join(BASE, 'output', 'csv', 'garmin')

TYPE_MAP = OrderedDict([
    ('sportType_1', '爬楼'), ('sportType_3', '骑行'), ('sportType_4', '户外跑步'),
    ('sportType_5', '户外步行'), ('sportType_101', '室内跑步'), ('sportType_117', '自由训练'),
    ('sportType_118', '越野跑'), ('sportType_140', '力量训练'), ('sportType_281', '室内步行'),
    ('sportType_283', '跳绳'),
])

KEYS = ['sport', 'sub_sport', 'start_time', 'total_elapsed_time', 'total_timer_time',
        'total_distance', 'total_calories', 'avg_speed', 'max_speed', 'enhanced_avg_speed',
        'avg_heart_rate', 'max_heart_rate', 'total_ascent', 'total_descent',
        'num_laps', 'total_cycles', 'avg_cadence', 'avg_running_cadence', 'trigger']


def rows(path, want):
    out = []
    with open(path, 'r', encoding='utf-8', errors='replace') as f:
        for row in csv.reader(f):
            if len(row) > 2 and row[0] == 'Data' and row[2].strip() == want:
                d = OrderedDict()
                i = 3
                while i + 1 < len(row):
                    n = row[i].strip()
                    if n:
                        d[n] = (row[i + 1].strip(), row[i + 2].strip() if i + 2 < len(row) else '')
                    i += 3
                out.append(d)
    return out


def find(d, key):
    for n in sorted(os.listdir(d)):
        if n.endswith('.csv') and key in n:
            return os.path.join(d, n)
    return None


def fmt(d, k):
    if k not in d:
        return '-'
    v, u = d[k]
    if v == '':
        return '(空)'
    return v + (' ' + u if u else '')


def rec_stats(path):
    ts = []
    nonempty = {}
    with open(path, 'r', encoding='utf-8', errors='replace') as f:
        for row in csv.reader(f):
            if len(row) > 2 and row[0] == 'Data' and row[2].strip() == 'record':
                i = 3
                while i + 1 < len(row):
                    n, v = row[i].strip(), row[i + 1].strip()
                    if n and v != '':
                        nonempty[n] = nonempty.get(n, 0) + 1
                        if n == 'timestamp':
                            ts.append(v)
                    i += 3
    gaps = 'n/a'
    return len(ts), nonempty, (ts[0] if ts else ''), (ts[-1] if ts else '')


def main():
    lines = []
    for key, cn in TYPE_MAP.items():
        of, gf = find(OURS_DIR, key + '_'), find(GARMIN_DIR, cn)
        lines.append('=' * 88)
        lines.append('【%s】' % cn)
        if not of:
            continue
        os_ = rows(of, 'session')
        gs_ = rows(gf, 'session') if gf else []
        o = os_[0] if os_ else {}
        g = gs_[0] if gs_ else {}
        lines.append('  %-22s %-30s %s' % ('字段', '我方(华为转换)', '佳明真实'))
        for k in KEYS:
            if k in o or k in g:
                lines.append('  %-22s %-30s %s' % (k, fmt(o, k), fmt(g, k)))
        on, onf, ot0, ot1 = rec_stats(of)
        lines.append('  record: 我方 %d 条, %s ~ %s' % (on, ot0, ot1))
        if gf:
            gn, gnf, gt0, gt1 = rec_stats(gf)
            lines.append('  record: 佳明 %d 条, %s ~ %s' % (gn, gt0, gt1))
            lines.append('  record 字段非空计数对比:')
            allk = list(OrderedDict.fromkeys(list(gnf) + list(onf)))
            for k in allk:
                if k == 'unknown':
                    continue
                lines.append('    %-24s 我方=%-8d 佳明=%d' % (k, onf.get(k, 0), gnf.get(k, 0)))
        else:
            lines.append('  record 字段非空计数(我方): ' +
                         ', '.join('%s=%d' % (k, v) for k, v in onf.items()))
    out = '\n'.join(lines)
    print(out)
    with open(os.path.join(BASE, 'output', 'compare_session.txt'), 'w', encoding='utf-8') as f:
        f.write(out + '\n')


if __name__ == '__main__':
    main()