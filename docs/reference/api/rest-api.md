# REST API 概览

## 基础信息

| 项目 | 值 |
|------|-----|
| Base URL | `http://localhost:8080/api` |
| 协议 | HTTPS（生产）/ HTTP（开发） |
| 格式 | JSON |
| 字符编码 | UTF-8 |

## 认证

所有 API（除登录/注册外）需携带 JWT Token：

```http
Authorization: Bearer <token>
```

### 获取 Token

```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "password"
}
```

响应：

```json
{
  "code": 0,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 7200
  }
}
```

### Token 刷新

Token 过期前调用刷新接口获取新 Token：

```http
POST /api/auth/refresh
Authorization: Bearer <current-token>
```

## 统一响应格式

```json
{
  "code": 0,
  "message": "success",
  "data": { ... }
}
```

错误响应：

```json
{
  "code": "AUTH_002",
  "message": "Token 已过期",
  "timestamp": "2026-05-29T15:00:00Z",
  "path": "/api/users/me"
}
```

## 分页

列表接口统一分页参数：

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `page` | int | 1 | 页码（从 1 开始） |
| `pageSize` | int | 20 | 每页条数（最大 100） |
| `sort` | string | — | 排序字段（如 `createdAt:desc`） |

分页响应：

```json
{
  "code": 0,
  "data": {
    "list": [...],
    "total": 150,
    "page": 1,
    "pageSize": 20
  }
}
```

## 核心端点

### 认证

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/auth/login` | 登录 |
| POST | `/auth/register` | 注册 |
| POST | `/auth/refresh` | 刷新 Token |
| POST | `/auth/logout` | 登出 |

### 用户

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/system/users` | 用户列表 |
| GET | `/system/users/:id` | 用户详情 |
| POST | `/system/users` | 创建用户 |
| PUT | `/system/users/:id` | 更新用户 |
| DELETE | `/system/users/:id` | 删除用户 |
| GET | `/system/users/me` | 当前用户信息 |

### 知识库

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/knowledge-bases` | 知识库列表 |
| POST | `/knowledge-bases` | 创建知识库 |
| GET | `/knowledge-bases/:id` | 知识库详情 |
| DELETE | `/knowledge-bases/:id` | 删除知识库 |
| POST | `/knowledge-bases/:id/documents` | 上传文档 |
| DELETE | `/knowledge-bases/:id/documents/:docId` | 删除文档 |
| POST | `/knowledge-bases/:id/search` | 语义检索 |

### 工作流

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/workflows` | 工作流列表 |
| POST | `/workflows` | 创建工作流 |
| GET | `/workflows/:id` | 工作流详情 |
| PUT | `/workflows/:id` | 更新工作流 |
| POST | `/workflows/:id/publish` | 发布工作流 |
| POST | `/workflows/:id/execute` | 执行工作流 |
| GET | `/workflows/:id/executions` | 执行历史 |

### Agent

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/agents` | Agent 列表 |
| POST | `/agents` | 创建 Agent |
| GET | `/agents/:id` | Agent 详情 |
| PUT | `/agents/:id` | 更新 Agent |
| DELETE | `/agents/:id` | 删除 Agent |
| POST | `/agents/:id/chat` | 与 Agent 对话（SSE 流式） |

### 对话

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/threads` | 对话列表 |
| POST | `/threads` | 创建对话 |
| GET | `/threads/:id/messages` | 消息历史 |
| POST | `/threads/:id/messages` | 发送消息（SSE 流式） |
| DELETE | `/threads/:id` | 删除对话 |

### 文件

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/files/upload` | 上传文件 |
| GET | `/files/:id` | 下载文件 |
| DELETE | `/files/:id` | 删除文件 |

## 错误码

详见 [FAQ 错误码说明](../../guide/user/faq.md#错误码说明)。
