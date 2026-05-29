# 本地开发环境搭建

## 环境要求

| 工具 | 版本 | 用途 |
|------|------|------|
| Java | 25+ | 后端编译运行 |
| Node.js | 22+ | 前端构建 |
| pnpm | 9+ | 包管理 |
| Maven | 3.9+（或用 mvnw） | Java 构建 |
| Docker | 24+ | 基础设施服务 |
| Git | 2.40+ | 版本控制 |

## 启动基础设施

使用 Docker 启动数据库等依赖服务：

```bash
docker compose up -d postgres neo4j redis
```

验证服务就绪：

```bash
docker compose ps  # 所有服务 healthy
```

## 后端启动

```bash
# 进入后端目录
cd apps/service

# 编译（首次较慢，下载依赖）
./mvnw clean compile -DskipTests

# 启动（开发模式）
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

或通过 Nx：

```bash
pnpm nx serve service
```

后端启动后访问：http://localhost:8080/actuator/health

## 前端启动

```bash
# 安装依赖（项目根目录）
pnpm install

# 启动前端开发服务器
pnpm nx dev webui
```

前端启动后访问：http://localhost:3000

## 环境变量

复制 `.env.example` 为 `.env`，按需修改：

```bash
cp .env.example .env
```

关键配置：

```bash
# 数据库（Docker 默认值即可）
DB_HOST=localhost
DB_PORT=5432
DB_NAME=aaf-dev
DB_USERNAME=postgres
DB_PASSWORD=postgres

# Neo4j
NEO4J_URI=bolt://localhost:7687

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# JWT（开发环境用默认值）
JWT_SECRET=aaf-dev-secret-must-be-at-least-32-bytes-long!!
```

## 常用开发命令

```bash
# 全量检查（lint + typecheck + test + build）
pnpm check

# 只检查受影响的项目
pnpm check:affected

# 格式化代码
pnpm format

# 运行后端测试
pnpm nx test service

# 运行前端测试
pnpm nx test webui
```

## IDE 配置

### IntelliJ IDEA（后端）

- 导入为 Maven 项目
- 设置 JDK 25
- 启用 Annotation Processing（Lombok/MapStruct）

### VS Code（前端）

推荐扩展：
- Biome（格式化 + Lint）
- Tailwind CSS IntelliSense
- TypeScript Nightly

## 故障排查

**Maven 编译失败**：确认 Java 版本 `java -version` 为 25+

**前端依赖安装失败**：删除 `node_modules` 和 `pnpm-lock.yaml` 重新安装

**数据库连接失败**：确认 Docker 容器运行中 `docker compose ps`

**端口冲突**：修改 `.env` 中对应端口或停止占用进程
