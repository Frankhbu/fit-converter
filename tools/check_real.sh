#!/bin/bash
# 对 output/real/fit 下的全部 FIT 文件跑 FitCSVTool 合法性校验
# 用法: bash tools/check_real.sh
set -u
cd "$(dirname "$0")/.."

TOOL=libs/FitCSVTool.jar
SRC_DIR="output/real/fit"
OUT_DIR="/tmp/fitcheck2"

mkdir -p "$OUT_DIR"

total=0
fail=0
bad=0

for f in "$SRC_DIR"/*.fit; do
  base=$(basename "$f" .fit)
  log="$OUT_DIR/$base.log"
  total=$((total + 1))
  if ! java -jar "$TOOL" -b "$f" "$OUT_DIR/$base.csv" > "$log" 2>&1; then
    fail=$((fail + 1))
    echo "FAIL(exit) $base"
  fi
  if grep -qi "warn\|error\|exception\|does not exist" "$log"; then
    bad=$((bad + 1))
    echo "BAD(log) $base"
  fi
done

echo "TOTAL=$total EXIT_FAIL=$fail LOG_BAD=$bad"
echo ALLDONE