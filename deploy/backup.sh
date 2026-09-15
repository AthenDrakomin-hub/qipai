#!/usr/bin/env bash
# ============================================================
# V-POKER 数据库每日自动备份脚本
#
# 推荐 Crontab 定时任务（每天凌晨 03:00 执行）：
#   0 3 * * * /opt/vpoker/deploy/backup.sh >> /var/log/vpoker-backup.log 2>&1
# ============================================================

set -euo pipefail

# 默认配置
BACKUP_DIR="${BACKUP_DIR:-/backup}"
ENV_FILE="${ENV_FILE:-/opt/vpoker/.env.prod}"
DB_NAME="${DB_NAME:-poker_platform}"
DB_USER="${DB_USER:-poker}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"

# 从 .env.prod 读取数据库密码（若存在）
if [ -f "$ENV_FILE" ]; then
    ENV_PASS=$(grep -E '^[[:space:]]*SPRING_DATASOURCE_PASSWORD=' "$ENV_FILE" | head -n 1 | cut -d '=' -f2- | tr -d '"' | tr -d "'" || true)
    if [ -n "$ENV_PASS" ]; then
        DB_PASS="$ENV_PASS"
    fi
fi

DB_PASS="${DB_PASS:-Poker@2024}"

DATE_STR=$(date +"%F_%H%M%S")
TARGET_FILE="${BACKUP_DIR}/poker_${DATE_STR}.sql.gz"

echo "============================================================"
echo "[$(date '+%Y-%m-%d %H:%M:%S')] 开始执行 V-POKER 数据库备份..."

# 创建备份目录
mkdir -p "$BACKUP_DIR"

# 导出并压缩
MYSQL_PWD="$DB_PASS" mysqldump \
    -h "$DB_HOST" \
    -P "$DB_PORT" \
    -u "$DB_USER" \
    --single-transaction \
    --quick \
    --default-character-set=utf8mb4 \
    "$DB_NAME" | gzip > "$TARGET_FILE"

FILE_SIZE=$(du -h "$TARGET_FILE" | cut -f1)
echo "[$(date '+%Y-%m-%d %H:%M:%S')] 备份成功: $TARGET_FILE (大小: $FILE_SIZE)"

# 清理保留期之外的历史备份
echo "[$(date '+%Y-%m-%d %H:%M:%S')] 清理超过 ${RETENTION_DAYS} 天的旧备份文件..."
find "$BACKUP_DIR" -type f -name "poker_*.sql.gz" -mtime +"$RETENTION_DAYS" -print -delete || true

echo "[$(date '+%Y-%m-%d %H:%M:%S')] 数据库备份流程完成。"
echo "============================================================"
