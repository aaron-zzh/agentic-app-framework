# Docker Compose 一键启动

## 前置条件

- Docker ≥ 24.0
- Docker Compose ≥ 2.20
- 可用内存 ≥ 4GB

## 快速启动

```bash
# 克隆项目
git clone https://github.com/xuejiai/agentic-app-framework.git
cd agentic-app-framework

# 复制环境变量
cp .env.example .env

# 编辑 .env，配置模型 API Key（必须）
# OPENAI_API_KEY=sk-xxx

# 一键启动
docker compose up -d
```

## 访问服务

| 服务 | 地址 | 默认账号 |
|------|------|---------|
| Web 前端 | http://localhost:3000 | admin / admin123 |
| 后端 API | http://localhost:8080 | — |
| Neo4j Browser | http://localhost:7474 | neo4j / neo4j |
| PgAdmin（可选） | http://localhost:5050 | — |

## 服务组成

```text
docker compose up -d
  ├── aaf-webui      → Next.js 前端（:3000）
  ├── aaf-service    → Spring Boot 后端（:8080）
  ├── postgres       → PostgreSQL + PgVector（:5432）
  ├── neo4j          → Neo4j 图数据库（:7687）
  └── redis          → Redis 缓存（:6379）
```

## 常用命令

```bash
# 查看日志
docker compose logs -f aaf-service

# 停止所有服务
docker compose down

# 停止并清除数据
docker compose down -v

# 重新构建
docker compose up -d --build

# 只启动基础设施（数据库）
docker compose up -d postgres neo4j redis
```

## 环境变量说明

编辑 `.env` 文件配置：

```bash
# 必须配置
OPENAI_API_KEY=sk-xxx          # 或其他模型提供商 Key

# 可选配置
DB_PASSWORD=postgres            # 数据库密码
JWT_SECRET=your-secret-key      # JWT 密钥（生产环境必须修改）
```

## 故障排查

**端口冲突**：修改 `docker-compose.yml` 中的端口映射

**启动超慢**：首次拉取镜像需要时间，确保网络通畅

**服务启动顺序**：后端依赖数据库就绪，已配置 `depends_on` + healthcheck

## 下一步

启动成功后，参考 [Hello World](./hello-world.md) 创建你的第一个 Agent。
