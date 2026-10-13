#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""核对样本 JSON 中海拔相关的原始数据与程序派生值。

对每个样本输出：
  1) SIMPLIFY 块里与海拔有关的原始字段（creepingWave / mTotalDescent / mMaxAlti / mMinAlti）
  2) 由 DETAIL 块 alti 通道逐点累加得到的累计爬升/下降、海拔极值
用于判断「累计爬升/下降」到底该取原始字段还是逐点计算。
"""
import json
import os
import sys

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "output", "sample", "huawei_json")

DETAIL = "HW_EXT_TRACK_DETAIL@is"
SIMPLIFY = "HW_EXT_TRACK_SIMPLIFY@is"


def split_blocks(attr):
    """按 HW_XXX@is 标记拆块，返回 {marker: content}"""
    import re
    out = {}
    marks = [(m.start(), m.end(), m.group()) for m in re.finditer(r"HW_[A-Za-z0-9_]+@is", attr)]
    for i, (s, e, marker) in enumerate(marks):
        end = marks[i + 1][0] if i + 1 < len(marks) else len(attr)
        content = attr[e:end]
        if i + 1 < len(marks) and content.endswith("&&"):
            content = content[:-2]
        out[marker] = content
    return out


def parse_points(detail):
    pts = []
    for line in detail.split("\n"):
        line = line.strip()
        if not line:
            continue
        d = {}
        for seg in line.split(";"):
            if not seg or "=" not in seg:
                continue
            k, v = seg.split("=", 1)
            d[k] = v
        if d:
            pts.append(d)
    return pts


def alti_series(pts):
    out = []
    for p in pts:
        if p.get("tp") == "alti":
            try:
                out.append((int(float(p["k"])), float(p["v"])))
            except (KeyError, ValueError):
                pass
    out.sort()
    return out


def cumulative(series, threshold=0.0):
    up = down = 0.0
    last = None
    for _, v in series:
        if last is None:
            last = v
            continue
        d = v - last
        if abs(d) < threshold:
            continue
        if d > 0:
            up += d
        else:
            down += -d
        last = v
    return up, down


def main():
    names = sys.argv[1:] or sorted(n for n in os.listdir(BASE) if n.endswith(".json"))
    for name in names:
        path = name if os.path.sep in name else os.path.join(BASE, name)
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
        blocks = split_blocks(data.get("attribute") or "")
        simp = {}
        if SIMPLIFY in blocks:
            try:
                simp = json.loads(blocks[SIMPLIFY])
            except ValueError:
                simp = {}
        pts = parse_points(blocks.get(DETAIL, ""))
        ser = alti_series(pts)
        print("=" * 72)
        print(os.path.basename(path))
        print("  原始字段: creepingWave=%s  mTotalDescent=%s  mMaxAlti=%s  mMinAlti=%s"
              % (simp.get("creepingWave"), simp.get("mTotalDescent"),
                 simp.get("mMaxAlti"), simp.get("mMinAlti")))
        print("  totalDistance=%s  totalTime=%s  totalStoreys=%s"
              % (simp.get("totalDistance"), simp.get("totalTime"), data.get("totalStoreys")))
        if not ser:
            print("  alti 通道: 无数据点")
            continue
        vals = [v for _, v in ser]
        print("  alti 通道: 点数=%d  min=%.1f  max=%.1f  首=%.1f  末=%.1f"
              % (len(ser), min(vals), max(vals), vals[0], vals[-1]))
        for th in (0.0, 0.5, 1.0, 2.0, 3.0):
            up, down = cumulative(ser, th)
            print("    阈值%.1fm: 累计爬升=%.1f  累计下降=%.1f" % (th, up, down))


if __name__ == "__main__":
    main()