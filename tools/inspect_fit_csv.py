#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""校验 FitCSVTool 解码出的 CSV：打印 session 关键统计字段，并核对 Σlap 与 session 的爬升/下降是否一致。

用法：python3 inspect_fit_csv.py <decoded.csv> [...]
"""
import csv
import sys

KEYS = (
    "total_ascent", "total_descent",
    "avg_heart_rate", "max_heart_rate", "min_heart_rate",
    "enhanced_min_altitude", "enhanced_max_altitude",
    "avg_cadence", "max_cadence",
    "avg_running_cadence", "max_running_cadence",
    "avg_fractional_cadence", "max_fractional_cadence",
)


def fields(row):
    out = {}
    for i in range(3, len(row) - 1, 3):
        if row[i] in KEYS:
            out[row[i]] = row[i + 1]
    return out


def main(paths):
    for path in paths:
        with open(path, newline="", encoding="utf-8", errors="replace") as fh:
            rows = [r for r in csv.reader(fh)
                    if len(r) > 2 and r[0] == "Data" and r[2] in ("session", "lap")]
        sessions = [fields(r) for r in rows if r[2] == "session"]
        laps = [fields(r) for r in rows if r[2] == "lap"]
        print(path)
        for s in sessions:
            print("  session", s)
        asc = sum(int(l.get("total_ascent") or 0) for l in laps)
        desc = sum(int(l.get("total_descent") or 0) for l in laps)
        print("  laps=%d  Σlap_ascent=%d  Σlap_descent=%d" % (len(laps), asc, desc))


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    main(sys.argv[1:])