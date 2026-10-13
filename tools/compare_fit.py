#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""对比我们生成的 FIT 与佳明真实 FIT 的消息/字段覆盖差异。

FitCSVTool 导出的 CSV 为宽表格式:
Type,Local Number,Message,Field 1,Value 1,Units 1,Field 2,Value 2,Units 2,...
"""
import csv
import os
import re
import sys
from collections import OrderedDict

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OURS_DIR = os.path.join(BASE, 'output', 'csv', 'ours')
GARMIN_DIR = os.path.join(BASE, 'output', 'csv', 'garmin')

# 华为文件名 -> 佳明文件名(运动类型对应)
TYPE_MAP = OrderedDict([
    ('sportType_1', '爬楼'),
    ('sportType_3', '骑行'),
    ('sportType_4', '户外跑步'),
    ('sportType_5', '户外步行'),
    ('sportType_101', '室内跑步'),
    ('sportType_117', '自由训练'),
    ('sportType_118', '越野跑'),
    ('sportType_140', '力量训练'),
    ('sportType_281', '室内步行'),
    ('sportType_283', '跳绳'),
])


def load_csv(path):
    """返回 {message: {field: [非空值个数, 总出现次数]}} 与 message 计数。"""
    msgs = OrderedDict()
    counts = OrderedDict()
    with open(path, 'r', encoding='utf-8', errors='replace') as f:
        reader = csv.reader(f)
        header = next(reader, None)
        for row in reader:
            if len(row) < 3:
                continue
            if row[0] != 'Data':
                continue
            msg = row[2].strip()
            counts[msg] = counts.get(msg, 0) + 1
            fields = msgs.setdefault(msg, OrderedDict())
            i = 3
            while i + 1 < len(row):
                name = row[i].strip()
                val = row[i + 1].strip()
                if name:
                    st = fields.setdefault(name, [0, 0])
                    st[1] += 1
                    if val != '':
                        st[0] += 1
                i += 3
    return msgs, counts


def find_file(d, key):
    for n in sorted(os.listdir(d)):
        if not n.endswith('.csv'):
            continue
        if key in n:
            return os.path.join(d, n)
    return None


def find_garmin(d, cn):
    for n in sorted(os.listdir(d)):
        if n.endswith('.csv') and cn in n:
            return os.path.join(d, n)
    return None


def main():
    lines = []
    all_our_msgs = set()
    all_gar_msgs = set()
    for key, cn in TYPE_MAP.items():
        of = find_file(OURS_DIR, key + '_')
        gf = find_garmin(GARMIN_DIR, cn)
        lines.append('=' * 72)
        lines.append('运动类型: %s (%s)' % (cn, key))
        if not of:
            lines.append('  [缺失] 我们的 CSV 未找到')
            continue
        om, oc = load_csv(of)
        all_our_msgs |= set(om)
        if not gf:
            lines.append('  [无佳明参照] 仅列出我方消息:')
            for m, c in oc.items():
                lines.append('    %-24s x%d  字段: %s' % (m, c, ','.join(om[m])))
            continue
        gm, gc = load_csv(gf)
        all_gar_msgs |= set(gm)
        lines.append('  消息数量 我方=%d 种 / 佳明=%d 种' % (len(om), len(gm)))
        only_g = [m for m in gc if m not in om]
        only_o = [m for m in oc if m not in gm]
        if only_g:
            lines.append('  >> 佳明有、我方缺失的消息:')
            for m in only_g:
                lines.append('     - %-22s x%-6d 字段: %s' % (m, gc[m], ','.join(list(gm[m])[:14])))
        if only_o:
            lines.append('  >> 我方有、佳明无的消息:')
            for m in only_o:
                lines.append('     - %-22s x%d' % (m, oc[m]))
        lines.append('  >> 共有消息的字段差异:')
        for m in gc:
            if m not in om:
                continue
            gfs= gm[m]
            ofs = om[m]
            miss = [k for k in gfs if k not in ofs and gfs[k][0] > 0]
            extra = [k for k in ofs if k not in gfs]
            empty = [k for k in ofs if k in gfs and ofs[k][0] == 0 and gfs[k][0] > 0]
            if miss or extra or empty:
                lines.append('     [%s] 我方x%d 佳明x%d' % (m, oc[m], gc[m]))
                if miss:
                    lines.append('        缺字段(%d): %s' % (len(miss), ', '.join(miss)))
                if empty:
                    lines.append('        有字段但全空(%d): %s' % (len(empty), ', '.join(empty)))
                if extra:
                    lines.append('        多余字段(%d): %s' % (len(extra), ', '.join(extra)))
    lines.append('=' * 72)
    lines.append('全局汇总: 佳明出现过但我方从未写过的消息:')
    lines.append('  ' + ', '.join(sorted(all_gar_msgs - all_our_msgs)))
    lines.append('全局汇总: 我方写过但佳明从未出现的消息:')
    lines.append('  ' + ', '.join(sorted(all_our_msgs - all_gar_msgs)))
    out = '\n'.join(lines)
    print(out)
    with open(os.path.join(BASE, 'output', 'compare_report.txt'), 'w', encoding='utf-8') as f:
        f.write(out + '\n')


if __name__ == '__main__':
    main()