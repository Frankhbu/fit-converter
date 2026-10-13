#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""检查各运动类型样本 JSON 中分段数据（partTimeMap / paceMap / swimSegment 等）的原始存在情况。"""
import json
import os
import sys

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "output", "sample", "huawei_json")

KEYS = ["partTimeMap", "paceMap", "britishPartTimeMap", "britishPaceMap",
        "swimSegment", "swimSegmentList", "sectionList", "lapList"]


def walk(obj, found, path=""):
    if isinstance(obj, dict):
        for k, v in obj.items():
            p = path + "/" + k
            if k in KEYS:
                found.setdefault(k, []).append((p, v))
            walk(v, found, p)
    elif isinstance(obj, list):
        for i, v in enumerate(obj):
            walk(v, found, path + "[%d]" % i)


def brief(v):
    s = json.dumps(v, ensure_ascii=False)
    return s if len(s) <= 300 else s[:300] + " ...(截断)"


def main():
    for name in sorted(os.listdir(BASE)):
        if not name.endswith(".json"):
            continue
        with open(os.path.join(BASE, name), "r", encoding="utf-8") as f:
            data = json.load(f)
        found = {}
        walk(data, found)
        print("=" * 70)
        print(name)
        if not found:
            print("  （无任何分段相关字段）")
        for k in KEYS:
            if k in found:
                for p, v in found[k]:
                    print("  %s  @%s" % (k, p))
                    print("    " + brief(v))


if __name__ == "__main__":
    main()