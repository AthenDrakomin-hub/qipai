# V-POKER 服务器部署资产清单

本目录包含 V-POKER 在生产 Linux 服务器（推荐 Ubuntu 22.04 LTS）上线部署所需的全部配置文件与自动化运维脚本。

详细部署操作手册请参见根目录文档：[DEPLOY.md](../DEPLOY.md)。

---

## 文件列表

| 文件名 | 类型 | 说明 |
|---|---|---|
| `vpoker-backend.service` | systemd 服务文件 | 后端 Spring Boot 守护进程配置，开机自启与异常重启 |
| `nginx-vpoker.conf` | Nginx 配置文件 | 前端 H5 静态资源托管 + `/api/` 反代 + `/ws/` WebSocket 升级 |
| `.env.prod.example` | 生产环境配置模板 | 敏感项环境变量模板（数据库密码、JWT 密钥、JVM 参数等） |
| `init-db.sh` | Shell 脚本 | 自动化建库、创建 MySQL 用户、授权及导入初始表结构 |
| `deploy.sh` | Shell 脚本 | 服务器一键发布/更新脚本（自动备份上一版 jar + 热重启 + 健康检测） |
| `rollback.sh` | Shell 脚本 | 后端一键回滚脚本（快速还原上一版 `.bak` 并重启） |
| `backup.sh` | Shell 脚本 | 每日数据库自动全量备份 + 自动清理超过 14 天旧备份 |

---

## 快速上手

### 1. 服务器环境准备
```bash
sudo apt update && sudo apt install -y openjdk-11-jdk maven nginx mysql-server
```

### 2. 数据库初始化
```bash
sudo ./init-db.sh
```

### 3. 配置生产环境变量
```bash
sudo mkdir -p /opt/vpoker
sudo cp .env.prod.example /opt/vpoker/.env.prod
sudo chmod 600 /opt/vpoker/.env.prod
# 编辑配置，务必填入强密码与随机生成的 JWT 密钥：
sudo nano /opt/vpoker/.env.prod
```

### 4. 安装 systemd 服务并启动后端
```bash
sudo cp vpoker-backend.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now vpoker-backend
sudo systemctl status vpoker-backend
```

### 5. 安装 Nginx 配置
```bash
sudo cp nginx-vpoker.conf /etc/nginx/sites-available/vpoker
# 修改其中的域名或 IP:
sudo nano /etc/nginx/sites-available/vpoker
sudo ln -sf /etc/nginx/sites-available/vpoker /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx
```

### 6. 配置 SSL 证书（HTTPS + WSS）
```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d your-domain.com
```

### 7. 配置每日数据库备份 Crontab
```bash
# 每日凌晨 03:00 自动备份，保留 14 天
(crontab -l 2>/dev/null; echo "0 3 * * * /opt/vpoker/deploy/backup.sh >> /var/log/vpoker-backup.log 2>&1") | crontab -
```
