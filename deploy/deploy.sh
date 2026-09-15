#!/usr/bin/env bash
# ============================================================
# V-POKER 服务器一键更新与发布脚本
#
# 用法：
#   # 部署后端新 jar 包：
#   sudo ./deploy.sh backend /path/to/poker-platform-1.0.0.jar
#
#   # 部署前端静态资源：
#   sudo ./deploy.sh web /path/to/dist/build/h5
#
#   # 同时部署后端与前端：
#   sudo ./deploy.sh all /path/to/poker-platform-1.0.0.jar /path/to/dist/build/h5
# ============================================================

set -euo pipefail

TARGET_DIR="/opt/vpoker"
WEB_DIR="${TARGET_DIR}/web"
SERVICE_NAME="vpoker-backend"
HEALTH_URL="http://127.0.0.1:8080/api/auth/health"

ACTION="${1:-}"

deploy_backend() {
    local jar_src="${1:-}"
    if [ -z "$jar_src" ] || [ ! -f "$jar_src" ]; then
        echo "错误：未找到有效的后端 jar 文件: $jar_src"
        exit 1
    fi

    echo "==> [后端部署] 检查并创建工作目录 ${TARGET_DIR}..."
    mkdir -p "$TARGET_DIR"

    if [ -f "${TARGET_DIR}/poker-platform-1.0.0.jar" ]; then
        echo "==> [后端部署] 备份上一版本 -> ${TARGET_DIR}/poker-platform-1.0.0.jar.bak..."
        cp -f "${TARGET_DIR}/poker-platform-1.0.0.jar" "${TARGET_DIR}/poker-platform-1.0.0.jar.bak"
    fi

    echo "==> [后端部署] 拷贝新 jar 到 ${TARGET_DIR}/poker-platform-1.0.0.jar..."
    cp -f "$jar_src" "${TARGET_DIR}/poker-platform-1.0.0.jar"
    chmod 755 "${TARGET_DIR}/poker-platform-1.0.0.jar"

    echo "==> [后端部署] 重启 ${SERVICE_NAME} 服务..."
    systemctl restart "$SERVICE_NAME"

    echo "==> [后端部署] 等待服务启动并检查健康状态..."
    local attempts=0
    local max_attempts=30
    local success=0

    while [ $attempts -lt $max_attempts ]; do
        sleep 2
        attempts=$((attempts + 1))
        local resp
        resp=$(curl -s "$HEALTH_URL" || true)
        if echo "$resp" | grep -q '"code":200'; then
            echo "==> [后端部署] 健康检查通过！响应: $resp"
            success=1
            break
        else
            echo "    等待服务就绪... ($attempts/$max_attempts)"
        fi
    done

    if [ $success -ne 1 ]; then
        echo "警告：服务在 $((max_attempts * 2)) 秒内未返回健康响应，请检查日志："
        journalctl -u "$SERVICE_NAME" -n 50 --no-pager
        exit 1
    fi
}

deploy_web() {
    local web_src="${1:-}"
    if [ -z "$web_src" ] || [ ! -d "$web_src" ]; then
        echo "错误：未找到有效的前端目录: $web_src"
        exit 1
    fi

    echo "==> [前端部署] 同步静态文件到 ${WEB_DIR}..."
    mkdir -p "$WEB_DIR"
    rsync -av --delete "${web_src}/" "${WEB_DIR}/"
    chmod -R 755 "$WEB_DIR"
    echo "==> [前端部署] 前端静态资源发布完成！"
}

case "$ACTION" in
    backend)
        deploy_backend "${2:-}"
        ;;
    web)
        deploy_web "${2:-}"
        ;;
    all)
        deploy_backend "${2:-}"
        deploy_web "${3:-}"
        ;;
    *)
        echo "用法："
        echo "  $0 backend <jar路径>      # 仅部署后端"
        echo "  $0 web <h5目录>           # 仅部署前端"
        echo "  $0 all <jar路径> <h5目录> # 同时部署前端和后端"
        exit 1
        ;;
esac

echo "============================================================"
echo "发布流程执行完成！"
echo "============================================================"
