#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""逐项核对「程序自算的派生指标」与「华为 SIMPLIFY 原始统计」的差异。

用法：python3 tools/inspect_derived.py
输出每个样本的 原始值 / 自算值 对照表，用于判断哪些字段应改为直接取原始值。
"""
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


def fnum(v):
    try:
        return float(v)
    except Exception:
        return None


def chan(pts, tp, key="v"):
    out = []
    for p in pts:
        if p.get("tp") != tp:
            continue
        v = fnum(p.get(key))
        if v is not None:
            out.append(v)
    return out


def row(name, raw, calc, unit=""):
    def fmt(x):
        if x is None:
            return "-"
        if isinstance(x, float):
            return "%.2f" % x
        return str(x)
    mark = ""
    if raw not in (None, 0) and calc not in (None, 0):
        try:
            if abs(float(raw) - float(calc)) > max(0.01, abs(float(raw)) * 0.01):
                mark = "  <= 不一致"
        except Exception:
            pass
    print("    %-22s 原始=%-10s 自算=%-10s %s%s" % (name, fmt(raw), fmt(calc), unit, mark))


def main():
    for name in sorted(os.listdir(SAMPLE_DIR)):
        if not name.endswith(".json"):
            continue
        with open(os.path.join(SAMPLE_DIR, name), encoding="utf-8") as f:
            rec = json.load(f)
        blocks = split_attr(rec.get("attribute") or "")
        pts = parse_track(blocks.get("HW_EXT_TRACK_DETAIL", ""))
        try:
            simp = json.loads(blocks.get("HW_EXT_TRACK_SIMPLIFY", "{}"))
        except Exception:
            simp = {}

        print("=" * 78)
        print(name)
        hr = [v for v in chan(pts, "h-r") if v > 0]
        row("平均心率", simp.get("avgHeartRate"),
            sum(hr) / len(hr) if hr else None, "bpm")
        row("最大心率", simp.get("maxHeartRate"), max(hr) if hr else None, "bpm")
        row("最小心率", simp.get("minHeartRate"), min(hr) if hr else None, "bpm")

        sr = [v for v in chan(pts, "s-r") if v > 0]
        row("平均步频", simp.get("avgStepRate"),
            sum(sr) / len(sr) if sr else None, "步/分")
        row("最大步频", simp.get("bestStepRate"), max(sr) if sr else None, "步/分")

        cad = [v for v in chan(pts, "cad") if v > 0]
        if cad:
            row("平均踏频(cad)", None, sum(cad) / len(cad), "转/分")

        alti = chan(pts, "alti")
        row("最高海拔", (simp.get("mMaxAlti") or 0), max(alti) if alti else None, "m")
        row("最低海拔", (simp.get("mMinAlti") or 0), min(alti) if alti else None, "m")

        asc = desc = 0.0
        for i in range(1, len(alti)):
            d = alti[i] - alti[i - 1]
            if d > 0:
                asc += d
            else:
                desc -= d
        row("累计爬升", (simp.get("creepingWave") or 0) / 10.0, asc or None, "m")
        row("累计下降", (simp.get("mTotalDescent") or 0) / 10.0, desc or None, "m")

        dist = fnum(rec.get("totalDistance")) or 0
        tsec = (fnum(rec.get("totalTime")) or 0) / 1000.0
        avg_pace = fnum(simp.get("avgPace")) or 0
        row("平均速度", 1000.0 / avg_pace if avg_pace > 0 else None,
            dist / tsec if dist > 0 and tsec > 0 else None, "m/s")
        best_pace = fnum(simp.get("bestPace")) or 0
        rspd = [v / 10.0 for v in chan(pts, "rs") if v > 0]
        row("最快配速->速度", 1000.0 / best_pace if best_pace > 0 else None,
            max(rspd) if rspd else None, "m/s(最大瞬时)")


if __name__ == "__main__":
    sys.exit(main())