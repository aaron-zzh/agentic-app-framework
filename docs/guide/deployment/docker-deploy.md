# Docker 部署

## 单机部署

### 构建镜像

```bash
# 构建后端镜像
docker build -t aaf-service:latest ./apps/service

# 构建前端镜像
docker build -t aaf-webui:latest ./apps/webui
```

### 生产环境配置

创建 `.env.production`：

```bash
# 数据库（使用强密码）
DB_HOST=postgres
DB_PORT=5432
DB_NAME=aaf
DB_USERNAME=aaf_user
DB_PASSWORD=<strong-password>

# Neo4j
NEO4J_URI=bolt://neo4j:7687
NEO4J_USERNAME=neo4j
NEO4J_PASSWORD=<strong-password>

# Redis
REDIS_HOST=redis
REDIS_PORT=6379

# JWT（必须修改）
JWT_SECRET=<random-64-char-string>
JWT_EXPIRE_SECONDS=7200

# 模型配置
OPENAI_API_KEY=sk-xxx
```

### 启动

```bash
docker compose --env-file .env.production up -d
```

## 镜像优化

### 后端 Dockerfile 示例

```dockerfile
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY target/aaf-api.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 前端 Dockerfile 示例

```dockerfile
FROM node:22-alpine AS builder
WORKDIR /app
COPY . .
RUN pnpm install --frozen-lockfile && pnpm nx build webui

FROM node:22-alpine AS runner
WORKDIR /app
COPY --from=builder /app/apps/webui/.next/standalone ./
COPY --from=builder /app/apps/webui/.next/static ./.next/static
COPY --from=builder /app/apps/webui/public ./public
EXPOSE 3000
CMD ["node", "server.js"]
```

## 数据持久化

确保 volume 映射到宿主机或外部存储：

```yaml
volumes:
  postgres_data:
    driver: local
    driver_opts:
      type: none
      o: bind
      device: /data/aaf/postgres
```

## 备份策略

```bash
# PostgreSQL 备份
docker exec aaf-postgres pg_dump -U postgres aaf > backup_$(date +%Y%m%d).sql

# Neo4j 备份
docker exec aaf-neo4j neo4j-admin database dump neo4j --to-path=/backups

# Redis 备份（RDB 快照）
docker exec aaf-redis redis-cli BGSAVE
```

## 健康检查

```bash
# 后端健康
curl http://localhost:8080/actuator/health

# 前端健康
curl http://localhost:3000/api/health
```

## 日志管理

```bash
# 查看实时日志
docker compose logs -f aaf-service

# 限制日志大小（docker-compose.yml）
services:
  aaf-service:
    logging:
      driver: json-file
      options:
        max-size: "50m"
        max-file: "3"
```

## 升级流程

```bash
# 拉取最新代码
git pull origin main

# 重新构建
docker compose build

# 滚动更新（不停机）
docker compose up -d --no-deps aaf-service
docker compose up -d --no-deps aaf-webui
```
