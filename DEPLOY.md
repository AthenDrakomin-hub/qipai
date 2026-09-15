# V-POKER 服务器部署流程方案

> 适用仓库版本：前端 uni-app Vue3（HBuilderX 工程）+ 后端 `backend/backend`（Spring Boot 2.7 / Java 11 / MyBatis-Plus / MySQL）

## 1. 总体架构

```
                ┌────────────────────────────────────────────┐
  玩家浏览器 ──▶│  Nginx (:80 / :443 HTTPS)                  │
  (H5 横屏)     │  ├─ /            → 前端 H5 静态资源         │
                │  ├─ /api/*       → 反向代理 → :8080         │
                │  └─ /ws/*        → WebSocket 升级 → :8080   │
                └────────────────────────────────────────────┘
                                    │
                     ┌──────────────┴──────────────┐
                     ▼                             ▼
              Spring Boot (8080)            MySQL 8 (3306)
              context-path: /api            库: poker_platform
              WS: /ws/room/{roomId}
```

- **同源部署**：前端 `baseURL` 只需填 `/api`，由 Nginx 反代到后端，天然规避 CORS，且 WebSocket 可用 `wss://域名/ws/...`。
- 后端所有 REST 路由都带 `/api` 前缀（`server.servlet.context-path: /api`）；
  WebSocket 端点（JSR-356 `@ServerEndpoint`）**不带** `/api` 前缀，直接是 `/ws/room/{roomId}`。

## 2. 服务器要求

| 项目 | 最低 | 建议 |
|---|---|---|
| 系统 | Ubuntu 20.04+ / CentOS 7+ | Ubuntu 22.04 LTS |
| CPU / 内存 | 2C / 4G | 4C / 8G |
| 磁盘 | 40G | 80G（日志+备份） |
| 带宽 | 5M | 10M+（WebSocket 长连接） |
| 开放端口 | 80、443、22 | 3306/8080 **不要**对公网开放 |

## 3. 环境准备（一次性）

```bash
# JDK 11（后端 java.version=11）
sudo apt update && sudo apt install -y openjdk-11-jdk maven nginx

# MySQL 8
sudo apt install -y mysql-server
sudo systemctl enable --now mysql

# 建库建账号（与 application-prod.yml 保持一致，密码务必更换）
sudo mysql <<'SQL'
CREATE DATABASE poker_platform DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'poker'@'localhost' IDENTIFIED BY '换成强密码';
GRANT ALL PRIVILEGES ON poker_platform.* TO 'poker'@'localhost';
FLUSH PRIVILEGES;
SQL

# 导入表结构（首次；程序启动时也会自动执行 schema-locations）
mysql -upoker -p poker_platform < backend/backend/src/main/resources/schema.sql
# 亦可使用 deploy/init-db.sh 脚本一键初始化
```

## 4. 后端构建与部署

### 4.1 打包

```bash
cd backend/backend
mvn clean package -DskipTests        # 产物: target/poker-platform-1.0.0.jar
```

### 4.2 生产配置（重要，勿用默认值）

`application-prod.yml` 中上线前必须修改：

| 配置项 | 默认值 | 要求 |
|---|---|---|
| `spring.datasource.password` | `Poker@2024` | 换成强密码 |
| `jwt.secret` | `poker-platform-super-secret-key-2024-...` | **必须更换**为随机长串（≥32位），否则 JWT 可伪造 |
| `spring.sql.init.mode` | `always` | 首次部署可保留；稳定后建议改 `never`，用备份恢复 |

推荐用启动参数覆盖敏感项，不落盘到 yml：

```bash
java -jar poker-platform-1.0.0.jar \
  --spring.profiles.active=prod \
  --spring.datasource.password=xxx \
  --jwt.secret=xxx
```

另注意：`application.yml`（基础配置）里 `h2.console.enabled=true` 会被合并进 prod，
上线时加参数关闭：`--spring.h2.console.enabled=false`。

### 4.3 systemd 常驻（见 `deploy/vpoker-backend.service`）

```bash
sudo cp deploy/vpoker-backend.service /etc/systemd/system/
sudo mkdir -p /opt/vpoker && cp target/poker-platform-1.0.0.jar /opt/vpoker/
sudo systemctl daemon-reload
sudo systemctl enable --now vpoker-backend
sudo systemctl status vpoker-backend      # 应为 active (running)
```

### 4.4 验证

```bash
curl http://127.0.0.1:8080/api/auth/health    # → {"code":200,"msg":...,"data":"ok"}
```

## 5. 前端构建与部署

前端是 **HBuilderX 工程**（无 npm scripts），两种方式出 H5 包：

**方式 A（推荐）：HBuilderX**
1. 打开项目 → 修改 `api/index.js`：`USE_MOCK = false`、`utils/request.js`：`baseURL = '/api'`
2. 菜单「发行 → 网站-PC Web 或手机 H5」，网站标题填 V-POKER，路径默认 `unpackage/dist/build/h5`

**方式 B：CLI**（服务器/CI 上用）
```bash
npx degit dcloudio/uni-preset-vue#vite vpoker-cli-build   # 或本机已有 uni-app CLI 环境
# 将本仓库源码拷入后:
npm i && npm run build:h5    # 产物 dist/build/h5
```

> 打包前必须改的两处：`api/index.js` 的 `USE_MOCK=false`；`utils/request.js` 的 `baseURL='/api'`。

上传产物：

```bash
scp -r unpackage/dist/build/h5/* user@server:/opt/vpoker/web/
```

## 6. Nginx 配置（见 `deploy/nginx-vpoker.conf`）

关键点：
- `/api/` 反代到 `127.0.0.1:8080`
- `/ws/` 反代并带 `Upgrade`/`Connection` 头（WebSocket 必须）
- 前端为 hash 路由（`manifest.json` 里 `h5.router.mode=hash`），无需 try_files 重写
- 开启 gzip 与静态资源缓存

```bash
sudo cp deploy/nginx-vpoker.conf /etc/nginx/sites-available/vpoker
sudo ln -sf /etc/nginx/sites-available/vpoker /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx
```

## 7. HTTPS（强烈建议）

无 HTTPS 时浏览器在部分环境会拦截混合内容，且 `wss` 无法使用：

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d your-domain.com    # 自动改写 Nginx 配置并续期
```

配置好后前端实际请求路径：
- REST：`https://your-domain.com/api/auth/login`
- WebSocket：`wss://your-domain.com/ws/room/{roomId}`

## 8. 上线检查清单

- [ ] `curl https://域名/api/auth/health` 返回 code=200
- [ ] 浏览器打开首页 → 启动页 → 登录，Network 面板确认请求走 `/api/*`
- [ ] 进入牌桌页，确认 WebSocket 连接成功（控制台无 1006/404）
- [ ] `jwt.secret`、MySQL 密码已更换为强密码
- [ ] `h2-console` 已关闭；8080/3306 未暴露公网（防火墙仅放行 80/443/22）
- [ ] 注册一个测试账号，完整走一遍：登录→大厅→建房→入座→打一局→结算→流水记录

## 9. 日常运维

### 发版（后端）
```bash
mvn clean package -DskipTests
scp target/poker-platform-1.0.0.jar user@server:/opt/vpoker/
ssh user@server "sudo systemctl restart vpoker-backend"
```
### 发版（前端）
重新构建 H5 → `rsync -a --delete dist/ server:/opt/vpoker/web/`（hash 路由无缓存包袱）

### 回滚
保留上一版 jar：`/opt/vpoker/poker-platform-1.0.0.jar.bak`，回滚即换回并 `systemctl restart`。
亦可使用 `deploy/rollback.sh` 脚本一键执行。

### 备份（每日）
```bash
mysqldump -upoker -p'密码' poker_platform | gzip > /backup/poker_$(date +%F).sql.gz
```
建议 crontab 每日 03:00 执行并保留 14 天（可使用 `deploy/backup.sh` 定时调用）。

### 日志与监控
- 后端日志：`journalctl -u vpoker-backend -f`（prod 级别为 info）
- Nginx：`/var/log/nginx/access.log`，重点观察 `/ws/` 的 upgrade 状态码应为 101
- 可选：`fail2ban` 防爆破、云监控对 8080 端口存活打点

## 10. 常见问题

| 现象 | 原因 / 处理 |
|---|---|
| 前端接口 404 | `baseURL` 没填 `/api`，或 Nginx 没配 `/api/` 反代 |
| WebSocket 连不上（1006） | Nginx 缺 `Upgrade/Connection` 头；或误把 `/ws` 写进 `/api` 前缀 |
| 401 反复跳登录 | 服务器时间与客户端偏差过大导致 JWT 校验失败；`jwt.secret` 前后端不一致不存在（仅后端持有）则检查是否重启后 secret 变化 |
| 登录成功但页面空白 | 确认打包时 `USE_MOCK=false`，且域名同源 |
| MySQL `Public Key Retrieval is not allowed` | 连接串已带 `allowPublicKeyRetrieval=true`，勿删 |

## 附：部署目录约定

```
/opt/vpoker/
├── poker-platform-1.0.0.jar      # 后端
├── poker-platform-1.0.0.jar.bak  # 上一版本（回滚用）
├── web/                          # 前端 H5 静态资源
└── .env.prod                     # 可选：存放敏感启动参数，权限 600
```
