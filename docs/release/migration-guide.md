# 迁移指南

适用版本：v0.11.0-beta.1

## 从零开始安装

### 环境要求

| 依赖 | 最低版本 | 推荐版本 |
|------|---------|---------|
| Node.js | 20.x | 22.x |
| pnpm | 9.x | 9.x |
| Java | 25 | 25 |
| Maven | 3.9+ | 3.9+ |
| PostgreSQL | 16 | 17 |
| Redis | 7.x | 7.x |
| Neo4j | 5.x | 5.x（可选） |

### 安装步骤

```bash
# 1. 克隆仓库
git clone https://github.com/xuejiai/agentic-app-framework.git
cd agentic-app-framework

# 2. 安装前端依赖
pnpm install

# 3. 环境配置
cp apps/webui/.env.example apps/webui/.env.local
cp apps/service/src/main/resources/application-local.yml.example apps/service/src/main/resources/application-local.yml

# 4. 启动后端
pnpm nx serve service

# 5. 启动前端
pnpm nx dev webui
```

### 环境变量配置

**前端 (`apps/webui/.env.local`)**：

```env
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
NEXT_PUBLIC_WS_URL=ws://localhost:8080/ws
```

**后端 (`application-local.yml`)**：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/aaf
    username: postgres
    password: your_password
  redis:
    host: localhost
    port: 6379
  ai:
    openai:
      api-key: your_api_key
      base-url: https://api.openai.com/v1

aaf:
  storage:
    type: local  # local | oss | minio
    local:
      path: ./uploads
  sms:
    provider: aliyun
    access-key: your_key
    secret: your_secret
```

## 数据库迁移

### 初始化数据库

```bash
# 创建数据库
createdb aaf

# Flyway 自动迁移（启动后端时自动执行）
pnpm nx serve service
```

迁移脚本位于 `apps/service/src/main/resources/db/migration/`，按版本号顺序执行。

### 手动执行迁移

```bash
cd apps/service
mvn flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5432/aaf
```

## 配置迁移

### 从开发环境到生产环境

| 配置项 | 开发 | 生产 |
|--------|------|------|
| `spring.profiles.active` | `local` | `prod` |
| `aaf.storage.type` | `local` | `oss` 或 `minio` |
| `spring.redis.host` | `localhost` | Redis 集群地址 |
| `NEXT_PUBLIC_API_BASE_URL` | `http://localhost:8080` | 生产 API 域名 |
| Service Worker | 禁用 | 启用 |

### 必须修改的生产配置

- [ ] 数据库连接（使用连接池）
- [ ] Redis 密码
- [ ] JWT 密钥（`aaf.security.jwt-secret`）
- [ ] OSS/MinIO 凭证
- [ ] 短信/邮件服务凭证
- [ ] AI 模型 API Key
- [ ] CORS 允许域名

## 兼容性矩阵

### 浏览器支持

| 浏览器 | 最低版本 | 说明 |
|--------|---------|------|
| Chrome | 111+ | OKLCH 色彩空间 |
| Firefox | 113+ | OKLCH 支持 |
| Safari | 16.4+ | OKLCH 支持 |
| Edge | 111+ | 同 Chrome |
| 移动端 Safari | 16.4+ | iOS |
| 移动端 Chrome | 111+ | Android |

### 数据库兼容

| 数据库 | 版本 | 必需 |
|--------|------|------|
| PostgreSQL | 16+ | ✅ 必需 |
| PgVector 扩展 | 0.5+ | ✅ 知识库必需 |
| Neo4j | 5.x | ⚠️ 知识图谱可选 |
| Redis | 7.x | ✅ 必需 |

### Java 运行时

| JDK | 支持 |
|-----|------|
| OpenJDK 25 | ✅ 推荐 |
| GraalVM 25 | ✅ 支持 |
| Oracle JDK 25 | ✅ 支持 |
