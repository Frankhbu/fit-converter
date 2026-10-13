#!/bin/bash
# 把双方 FIT 文件导出为 CSV，便于逐消息、逐字段对照分析。
#   output/sample/csv/huawei/*.csv  —— 本程序由华为样本 JSON 生成的 FIT
#   output/sample/csv/garmin/*.csv  —— 佳明官网下载的真实 FIT
set -e
cd "$(dirname "$0")/.."

TOOL=libs/FitCSVTool.jar
mkdir -p output/sample/csv/huawei output/sample/csv/garmin

for f in output/sample/garmin_fit/*.fit; do
  name=$(basename "$f" .fit)
  java -jar "$TOOL" -b "$f" "output/sample/csv/huawei/$name.csv" --defn none -e -iso8601 \
    > "output/sample/csv/huawei/$name.log" 2>&1 || echo "导出失败: $f"
done

for f in input/sample/garmin/*.fit; do
  name=$(basename "$f" .fit)
  java -jar "$TOOL" -b "$f" "output/sample/csv/garmin/$name.csv" --defn none -e -iso8601 \
    > "output/sample/csv/garmin/$name.log" 2>&1 || echo "导出失败: $f"
done

echo "CSV 导出完成:"
ls -1 output/sample/csv/huawei/*.csv | wc -l | xargs echo "  huawei:"
ls -1 output/sample/csv/garmin/*.csv | wc -l | xargs echo "  garmin:"