#!/usr/bin/env bash
# ============================================================
# V-POKER MySQL 数据库初始化脚本
#
# 功能：
#   1. 创建 poker_platform 数据库
#   2. 创建 poker 数据库用户并授权
#   3. 导入 schema.sql 表结构与初始数据
#
# 用法：
#   sudo ./init-db.sh [密码]
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
SCHEMA_SQL="${REPO_DIR}/backend/backend/src/main/resources/schema.sql"

if [ ! -f "$SCHEMA_SQL" ]; then
    SCHEMA_SQL="${REPO_DIR}/ddl.sql"
fi

DB_NAME="poker_platform"
DB_USER="poker"
DB_PASS="${1:-}"

if [ -z "$DB_PASS" ]; then
    read -r -s -p "请输入 MySQL 用户 poker 的密码（将写入数据库并授权）: " DB_PASS
    echo ""
fi

if [ -z "$DB_PASS" ]; then
    echo "错误：密码不能为空！"
    exit 1
fi

echo "==> 正在使用 root 权限创建数据库 ${DB_NAME} 及用户 ${DB_USER}..."

sudo mysql <<SQL
CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${DB_USER}'@'localhost' IDENTIFIED BY '${DB_PASS}';
ALTER USER '${DB_USER}'@'localhost' IDENTIFIED BY '${DB_PASS}';
GRANT ALL PRIVILEGES ON \`${DB_NAME}\`.* TO '${DB_USER}'@'localhost';
FLUSH PRIVILEGES;
SQL

echo "==> 正在导入初始表结构: ${SCHEMA_SQL}..."
mysql -u"${DB_USER}" -p"${DB_PASS}" "${DB_NAME}" < "${SCHEMA_SQL}"

echo "==> 数据库初始化完成！"
echo "提示：请将密码同步写入 /opt/vpoker/.env.prod 或启动参数中。"
