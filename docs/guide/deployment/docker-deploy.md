---
title: Docker 部署指南
description: 使用 Docker Compose 部署 AAF，支持本地开发与阿里云生产环境
---

# Docker 部署指南

AAF 采用多容器架构，自建镜像只有两个：

| 镜像 | 构建来源 | 基础镜像 |
|------|---------|---------|
| `aaf/service` | `apps/service/Dockerfile` | bellsoft/liberica（Spring 官方推荐） |
| `aaf/webui` | `apps/webui/Dockerfile` | node:24.16.0-alpine |

其余中间件（PostgreSQL、Neo4j、Redis、Nginx）直接使用官方镜像。

## 本地快速启动

```bash
# 1. 复制环境变量文件
cp .env.example .env

# 2. 按需修改 .env（数据库密码、API Key 等）

# 3. 启动所有服务
docker compose up -d

# 查看日志
docker compose logs -f aaf-service
```

访问：前端 http://localhost:3000 · 后端 http://localhost:8080

## 环境变量配置

AAF 使用两层环境变量机制：

```
.env 文件
 ├── Docker Compose 变量替换（替换 compose 文件中的 ${VAR}）
 └── env_file 注入（将变量传入容器内部）
```

**本地开发**：直接编辑 `.env`（已加入 `.gitignore`，不提交）

**生产环境（阿里云 ECS）**：
```bash
# 方式一：.env 文件（推荐，简单）
vi /opt/aaf/.env

# 方式二：系统环境变量
export OPENAI_API_KEY=sk-xxx
export JWT_SECRET=your-64-char-secret
```

敏感变量清单（生产必须修改）：

```bash
DB_PASSWORD=          # 数据库强密码
NEO4J_PASSWORD=       # Neo4j 强密码
JWT_SECRET=           # 至少 64 位随机字符串
OPENAI_API_KEY=       # LLM API Key（或填 DASHSCOPE_API_KEY）
AAF_WEB_ORIGIN=       # 前端域名，如 https://your-domain.com（CORS 白名单）
AAF_SERVER_NAME=      # Nginx 域名，如 your-domain.com
SSL_CERT_DIR=         # SSL 证书宿主机路径，如 /etc/ssl/aaf
```

生成随机 JWT_SECRET：
```bash
openssl rand -base64 64
```

## 阿里云生产部署

### 前置准备

在 GitHub 仓库 Settings → Secrets and variables → Actions → Repository secrets 中添加：

| Secret 名称 | 说明 |
|-------------|------|
| `ACR_REGISTRY` | ACR 地址，如 `registry.cn-hangzhou.aliyuncs.com/your-ns` |
| `ACR_USERNAME` | ACR 登录用户名 |
| `ACR_PASSWORD` | ACR 登录密码 |
| `ECS_HOST` | ECS 公网 IP |
| `ECS_USER` | SSH 用户名（如 `root` 或 `ubuntu`） |
| `ECS_SSH_KEY` | SSH 私钥（完整内容，`cat ~/.ssh/id_rsa`） |
| `PUBLIC_API_URL` | 前端编译时内联的后端公网地址，如 `https://your-domain.com` |

> `PUBLIC_API_URL` 在构建前端镜像时通过 build-args 注入，编译后固定在 JS bundle 中，运行时改环境变量无效，需重新构建镜像。

还需在 GitHub Settings → Environments 创建名为 `production` 的环境（deploy job 依赖此环境）。可在此处配置 Required reviewers 实现人工审批门控。

### ECS 初始化（首次）

```bash
# 安装 Docker
curl -fsSL https://get.docker.com | sh
systemctl enable --now docker

# 创建部署目录和 .env（部署文件由 CI 自动上传）
mkdir -p /root/aaf
cd /root/aaf
cp /path/to/.env.example .env
vi .env  # 填写生产配置
```

### 自动部署流程

推送到 `main` 分支后自动触发：

```
push to main
  → GitHub Actions (deploy.yml)
    → 构建 aaf/service 镜像 → 推送到 ACR
    → 构建 aaf/webui 镜像  → 推送到 ACR
    → [需要 production 环境审批]
    → SSH 到 ECS
      → docker compose pull（拉取新镜像）
      → docker compose up -d --no-deps（滚动更新，不重启 DB）
      → docker image prune（清理旧镜像）
```

> `production` 环境保护：在 GitHub Settings → Environments → production 中配置审批人，merge 后需人工确认才能部署到生产。

### Nginx 配置

生产环境 Nginx 通过 `docker-compose.prod.yml` 自动启动，配置文件位于：

```
scripts/deploy/nginx/
├── nginx.conf.template       # 主配置
└── conf.d/aaf.conf.template  # 站点配置（含 SSL、反向代理）
```

**SSL 证书**：将证书文件放到 ECS 的 `/etc/ssl/aaf/` 目录，或修改 `.env` 中的 `SSL_CERT_DIR`。

Nginx 路由规则：
- `/` → aaf-webui:3000（前端）
- `/api/` → aaf-service:8080（后端 API）
- `/ws/` → aaf-service:8080（WebSocket）
- `/agui/` → aaf-service:8080（SSE 流式输出）

## 手动操作

```bash
# 仅重新构建并更新某个服务（不影响 DB）
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --no-deps aaf-service

# 查看所有容器状态
docker compose ps

# 进入容器调试
docker exec -it aaf-service sh

# 数据库备份
docker exec aaf-postgres pg_dump -U postgres aaf > backup_$(date +%Y%m%d).sql
```

## 数据持久化

所有数据通过 Docker named volume 持久化，重启或更新镜像不丢失数据：

```
postgres_data  → PostgreSQL 数据
neo4j_data     → Neo4j 图数据
redis_data     → Redis 数据
nginx_logs     → Nginx 访问日志
```

## 健康检查

```bash
# 后端
curl http://localhost:8080/actuator/health

# 前端
curl http://localhost:3000
```
