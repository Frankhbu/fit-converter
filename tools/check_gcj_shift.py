#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""核对 GCJ-02 → WGS-84 纠偏：对比源 JSON 首个 lbs 点与产物 FIT 首个 record 坐标的偏移量与方向。

用法：python3 tools/check_gcj_shift.py <运动名，如 sportType_4_户外跑步>
前提：已用 FitCSVTool 把同名 .fit 解码为 /tmp/<运动名>.csv
"""
import csv
import json
import math
import os
import re
import sys

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "output", "sample")
SEMI = 2147483648.0 / 180.0


def first_lbs(name):
    """从源 JSON 的轨迹明文里取第一个有效 lbs 点（度）"""
    with open(os.path.join(BASE, "huawei_json", name + ".json"), "r", encoding="utf-8") as f:
        text = f.read()
    for m in re.finditer(r"tp=lbs;[^\"\\\\]*", text):
        seg = m.group(0)
        lat = re.search(r"lat=(-?[0-9.]+)", seg)
        lon = re.search(r"lon=(-?[0-9.]+)", seg)
        if not lat or not lon:
            continue
        la, lo = float(lat.group(1)), float(lon.group(1))
        if abs(la - 90.0) < 1e-6 and abs(lo + 80.0) < 1e-6:
            continue  # 无效哨兵点
        return la, lo
    return None


def first_record(name):
    """从 FitCSVTool 解码出的 CSV 里取第一个带坐标的 record（度）"""
    path = "/tmp/%s.csv" % name
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        for row in csv.reader(f):
            if len(row) < 3 or row[0] != "Data" or row[2] != "record":
                continue
            kv = {}
            for i in range(3, len(row) - 2, 3):
                kv[row[i]] = row[i + 1]
            if "position_lat" in kv and "position_long" in kv:
                return float(kv["position_lat"]) / SEMI, float(kv["position_long"]) / SEMI
    return None


def main():
    name = sys.argv[1] if len(sys.argv) > 1 else "sportType_4_户外跑步"
    src = first_lbs(name)
    dst = first_record(name)
    if not src or not dst:
        print("未取到坐标: src=%s dst=%s" % (src, dst))
        return 1
    # 偏移量（米）：纬度方向 1 度约 111320 m，经度方向按纬度收缩
    dlat_m = (dst[0] - src[0]) * 111320.0
    dlon_m = (dst[1] - src[1]) * 111320.0 * math.cos(math.radians(src[0]))
    dist = math.hypot(dlat_m, dlon_m)
    # 方位角：0=正北，90=正东
    brg = (math.degrees(math.atan2(dlon_m, dlat_m)) + 360) % 360
    dirs = ["北", "东北", "东", "东南", "南", "西南", "西", "西北"]
    label = dirs[int((brg + 22.5) % 360 // 45)]
    print("运动: %s" % name)
    print("  华为原始(GCJ-02): %.6f, %.6f" % src)
    print("  FIT 产物(WGS-84): %.6f, %.6f" % dst)
    print("  位移: %.1f m，方向 %.1f°(%s)" % (dist, brg, label))
    print("  分量: 南北 %+.1f m，东西 %+.1f m" % (dlat_m, dlon_m))
    return 0


if __name__ == "__main__":
    sys.exit(main())