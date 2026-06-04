# API Layer

前端 API 层按职责分组：

- `rest/core/`：HTTP 客户端、基础配置、错误模型等底层能力。
- `rest/query/`：TanStack Query 全局配置、Provider 和通用 mutation 辅助。
- `rest/endpoints.ts`：后端路径常量，作为 REST path 的集中入口。
- `rest/crud/`：标准 CRUD 请求、query options 和通用 hooks。
- `rest/modules/`：领域 API、query keys 和领域 hooks。非通用接口放在对应领域目录。

迁移时优先保留旧文件 re-export，按领域逐步移动，避免一次性修改大量 import。
