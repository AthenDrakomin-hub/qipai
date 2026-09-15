#!/usr/bin/env bash
# ============================================================
# V-POKER 后端版本一键回滚脚本
#
# 功能：
#   将 /opt/vpoker/poker-platform-1.0.0.jar.bak 恢复为当前运行版本并重启
# ============================================================

set -euo pipefail

TARGET_DIR="/opt/vpoker"
JAR_CURRENT="${TARGET_DIR}/poker-platform-1.0.0.jar"
JAR_BAK="${TARGET_DIR}/poker-platform-1.0.0.jar.bak"
SERVICE_NAME="vpoker-backend"
HEALTH_URL="http://127.0.0.1:8080/api/auth/health"

if [ ! -f "$JAR_BAK" ]; then
    echo "错误：未找到历史备份版本 $JAR_BAK，无法执行回滚！"
    exit 1
fi

echo "==> [回滚] 正在将备份版本恢复至运行位置..."
cp -f "$JAR_BAK" "$JAR_CURRENT"

echo "==> [回滚] 重启 ${SERVICE_NAME} 服务..."
systemctl restart "$SERVICE_NAME"

echo "==> [回滚] 正在校验服务健康状态..."
sleep 3
resp=$(curl -s "$HEALTH_URL" || true)
if echo "$resp" | grep -q '"code":200'; then
    echo "==> [回滚] 回滚成功，健康检查通过！"
else
    echo "警告：健康检查未通过，请检查日志："
    journalctl -u "$SERVICE_NAME" -n 50 --no-pager
    exit 1
fi
