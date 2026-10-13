#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""核查华为样本 JSON 中 tp=sec 分段点的字段与数值，判断能否直接作为 lap 数据源。"""
import json
import os
import sys

SAMPLE_DIR = os.path.join(os.path.dirname(__file__), "..", "output", "sample", "huawei_json")


def split_attr(attr):
    blocks = {}
    for blk in attr.split("&&"):
        if "@is" in blk:
            name, body = blk.split("@is", 1)
            blocks[name.strip()] = body
    return blocks


def parse_track(body):
    pts = []
    for line in body.splitlines():
        line = line.strip()
        if not line:
            continue
        kv = {}
        for item in line.split(";"):
            if "=" in item:
                k, v = item.split("=", 1)
                kv[k.strip()] = v.strip()
        if kv:
            pts.append(kv)
    return pts


def main():
    names = sorted(os.listdir(SAMPLE_DIR))
    for name in names:
        if not name.endswith(".json"):
            continue
        with open(os.path.join(SAMPLE_DIR, name), encoding="utf-8") as f:
            rec = json.load(f)
        attr = rec.get("attribute") or ""
        blocks = split_attr(attr)
        detail = blocks.get("HW_EXT_TRACK_DETAIL", "")
        simp_raw = blocks.get("HW_EXT_TRACK_SIMPLIFY", "")
        simp = {}
        try:
            simp = json.loads(simp_raw)
        except Exception:
            pass
        pts = parse_track(detail)
        secs = [p for p in pts if p.get("tp") == "sec"]
        print("=" * 70)
        print(name)
        print("  总距离(m)=%s 总时长(s)=%s 总卡(kcal)=%s 总步=%s"
              % (rec.get("totalDistance"), rec.get("totalTime"),
                 rec.get("totalCalories"), rec.get("totalSteps")))
        print("  SIMPLIFY: avgHR=%s maxHR=%s minHR=%s avgPace=%s bestPace=%s avgStepRate=%s bestStepRate=%s"
              % (simp.get("avgHeartRate"), simp.get("maxHeartRate"), simp.get("minHeartRate"),
                 simp.get("avgPace"), simp.get("bestPace"),
                 simp.get("avgStepRate"), simp.get("bestStepRate")))
        print("  sec 点数=%d" % len(secs))
        if not secs:
            continue
        keys = []
        for p in secs:
            for k in p:
                if k not in keys:
                    keys.append(k)
        print("  sec 字段=%s" % keys)
        for p in secs[:6]:
            print("    " + " ".join("%s=%s" % (k, p[k]) for k in keys if k in p))
        if len(secs) > 6:
            print("    ... 共 %d 段" % len(secs))
        # 合计校验
        def tot(k, scale=1.0):
            s = 0.0
            for p in secs:
                try:
                    s += float(p.get(k, 0))
                except Exception:
                    pass
            return s * scale
        print("  Σsd(m)=%.1f Σst=%.1f Σsc/1000=%.1f Σstd=%.0f"
              % (tot("sd") / 1000.0, tot("st"), tot("sc") / 1000.0, tot("std")))


if __name__ == "__main__":
    sys.exit(main())