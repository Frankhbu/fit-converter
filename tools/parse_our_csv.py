#!/usr/bin/env python3
"""解析我们转换的 FIT CSV 文件（FitCSVTool 导出，需在仓库根目录执行，或传 CSV 所在目录作为参数）"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from parse_garmin_csv import analyze_file

base = sys.argv[1] if len(sys.argv) > 1 else 'output'
analyze_file(base + '/20231229_084253_户外跑步.csv', '我们转换的户外跑步')