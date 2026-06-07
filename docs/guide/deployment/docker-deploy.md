---
title: Docker 部署指南
description: 使用 Docker Compose 部署 AAF，支持本地开发与阿里云演示/生产环境
---

# Docker 部署指南

AAF 采用多容器架构，自建镜像两个：

| 镜像 | 构建来源 | 基础镜像 |
|------|---------|---------|
| `aaronzzh/service` | `apps/service/Dockerfile` | bellsoft/liberica-openjdk-alpine:25 |
| `aaronzzh/webui` | `apps/webui/Dockerfile` | node:24-alpine |

其余中间件（PostgreSQL、Neo4j、Redis、Nginx）直接使用官方镜像。

## 本地开发（基础设施 only）

`docker-compose.dev.yml` 只启动中间件，前后端在本地 JVM/Node 直接跑（支持热重载）：

```bash
docker compose -f docker-compose.dev.yml up -d

pnpm nx serve service   # 后端 :8080
pnpm nx dev webui       # 前端 :3000
```

## 演示/生产环境（全栈）

`docker-compose.yml` 启动完整服务栈（含从 ACR 拉取的应用镜像）：

```bash
# ECS 上
cd /root/aaf
export ACR_REGISTRY=registry.cn-hangzhou.aliyuncs.com
export ACR_NAMESPACE=aaronzzh
docker compose pull
docker compose up -d
```

## 环境变量配置

```
.env 文件
 ├── Docker Compose 变量替换（${VAR} 替换）
 └── env_file 注入（传入容器内部）
```

生产必须修改的敏感变量：

```bash
DB_PASSWORD=          # 数据库强密码
REDIS_PASSWORD=       # Redis 密码
NEO4J_PASSWORD=       # Neo4j 密码
JWT_SECRET=           # 至少 64 位随机字符串（openssl rand -base64 64）
OPENAI_API_KEY=       # LLM API Key
AAF_WEB_ORIGIN=       # 前端域名，如 https://your-domain.com（CORS 白名单）
SSL_CERT_DIR=         # SSL 证书宿主机路径，如 /etc/ssl/aaf
```

## 阿里云 CI/CD 自动部署

### GitHub Secrets 配置

在 GitHub 仓库 Settings → Secrets and variables → Actions 中添加：

| Secret | 说明 | 示例 |
|--------|------|------|
| `ACR_REGISTRY` | ACR 地址含命名空间 | `registry.cn-hangzhou.aliyuncs.com/aaronzzh` |
| `ACR_USERNAME` | ACR 登录用户名 | `5634xxx@qq.com` |
| `ACR_PASSWORD` | ACR 固定密码（在 ACR 控制台单独设置） | — |
| `ECS_HOST` | ECS 公网 IP | — |
| `ECS_USER` | SSH 用户名 | `root` |
| `ECS_SSH_KEY` | SSH 私钥完整内容 | `cat ~/.ssh/id_rsa` |
| `PUBLIC_API_URL` | 前端编译时内联的后端地址 | `https://your-domain.com` |
| `CODECOV_TOKEN` | 覆盖率上报 token（可选） | — |

> `PUBLIC_API_URL` 在构建时通过 build-args 注入，编译后固定在 JS bundle 中，改了需重新构建镜像。

还需在 GitHub Settings → Environments 创建 `production` 环境，可配置 Required reviewers 实现人工审批门控。

### ACR 仓库初始化（首次）

在阿里云容器镜像服务控制台手动创建：
- 命名空间：`aaronzzh`
- 镜像仓库：`service`、`webui`

### ECS 初始化（首次）

```bash
# 安装 Docker
curl -fsSL https://get.docker.com | sh
systemctl enable --now docker

# 创建部署目录
mkdir -p /root/aaf/logs/service /root/aaf/logs/nginx /root/aaf/ssl
cd /root/aaf

# 配置环境变量
cp .env.example .env
vi .env
```

### 自动部署流程

推送到 `main` 分支后自动触发：

```
push to main
  → CI（check）：lint + typecheck + 单测
  → Deploy（deploy.yml）：
      → 构建 aaronzzh/service 镜像 → 推送 ACR
      → 构建 aaronzzh/webui 镜像  → 推送 ACR
      → [production 环境审批]
      → SSH 到 ECS
          → docker compose pull aaf-service aaf-webui
          → docker compose up -d --no-deps aaf-service aaf-webui nginx
          → docker image prune
```

## 手动操作

```bash
# 重新拉取并更新单个服务（不影响 DB/Redis）
docker compose pull aaf-service
docker compose up -d --no-deps aaf-service

# 查看容器状态
docker compose ps

# 查看日志
docker compose logs -f aaf-service

# 进入容器调试
docker exec -it aaf-service sh

# 数据库备份
docker exec aaf-postgres pg_dump -U postgres aaf > backup_$(date +%Y%m%d).sql
```

## 数据持久化

所有数据通过 Docker named volume 持久化，更新镜像不丢失数据：

| Volume | 内容 |
|--------|------|
| `postgres_data` | PostgreSQL 数据 |
| `neo4j_data` | Neo4j 图数据 |
| `redis_data` | Redis 数据 |
| `service_heap` | JVM heap dump（故障排查） |

## 健康检查

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:3000
```
