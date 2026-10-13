#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""打印华为 JSON 中 SIMPLIFY 块的全部字段，以及 DETAIL 块各 tp 类型计数。"""
import json
import os
import sys
from collections import Counter

SRC = os.path.join(os.path.dirname(__file__), '..', 'source', 'huawei')
SIMPLIFY = 'HW_EXT_TRACK_SIMPLIFY@is'
DETAIL = 'HW_EXT_TRACK_DETAIL@is'


def dump(path):
    with open(path, encoding='utf-8') as f:
        data = json.load(f)
    rec = data[0] if isinstance(data, list) else data
    attr = rec.get('attribute') or ''
    print('=' * 70)
    print(os.path.basename(path))
    print('  顶层: totalSteps=%s totalDistance=%s totalTime=%s totalCalories=%s totalStoreys=%s realSteps=%s' % (
        rec.get('totalSteps'), rec.get('totalDistance'), rec.get('totalTime'),
        rec.get('totalCalories'), rec.get('totalStoreys'), rec.get('realSteps')))
    print('  partTimeMap=%s' % json.dumps(rec.get('partTimeMap'), ensure_ascii=False))
    for block in attr.split('&&'):
        if block.startswith(SIMPLIFY):
            body = block[len(SIMPLIFY):]
            try:
                obj = json.loads(body)
            except Exception as e:
                print('  SIMPLIFY 解析失败: %s' % e)
                continue
            print('  --- SIMPLIFY ---')
            for k, v in obj.items():
                s = json.dumps(v, ensure_ascii=False)
                if len(s) > 200:
                    s = s[:200] + '...'
                print('    %-38s = %s' % (k, s))
        elif block.startswith(DETAIL):
            body = block[len(DETAIL):]
            cnt = Counter(l.split(';')[0][3:] for l in body.split('\n') if l.startswith('tp='))
            print('  --- DETAIL tp 计数: %s' % dict(cnt))


targets = sys.argv[1:] or sorted(os.listdir(SRC))
for name in targets:
    if name.endswith('.json'):
        dump(os.path.join(SRC, name))