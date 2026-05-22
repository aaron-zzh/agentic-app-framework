---
level: Practice
layer: Product
purpose: AAF-019 文档管理系统的技术任务清单
status: active
version: 1.0.0
date: 2026-05-22
author: AaronZZH
---

# 文档管理系统（AAF-019）

> 需求：[需求规格](requirement.md)
> 设计：[后端技术选型](../../../design/apps/service/tech-stack.md)
> 负责人：developer-service + developer-webui | 创建：05-22

## 任务列表

> **执行策略**：后端先行（迁移→骨架→导入→CRUD），前端并行（文档树→编辑→图谱）。

### 后端主线

1. ⏳ #01901 Flyway 迁移 V3（doc_link 表 + doc_document.front_matter 字段） — developer-service
   - 新增 `doc_link` 表（source_id/target_id/link_type）
   - `doc_document` 补充 `front_matter JSONB` 字段
   - verify: `pnpm nx test service` 编译通过，Flyway 迁移成功

2. [ ] #01902 document 模块骨架（Entity/Repository/Service/Controller） — developer-service (依赖: #01901)
   - 包路径：`com.xuejiai.aaf.module.document`
   - `Document` Entity（对应 doc_document）、`DocLink` Entity（对应 doc_link）
   - `DocumentRepository`（含全文检索查询）、`DocLinkRepository`
   - `DocumentService`（CRUD 骨架）、`DocumentController`（6 个接口骨架）
   - Neo4j：`DocNode` 节点 + `DocRelationRepository`
   - verify: 编译通过，`GET /api/docs/tree` 返回空列表

3. [ ] #01903 文档导入服务（DocImportService） — developer-service (依赖: #01902)
   - 扫描 `docs/` 目录（递归），读取 `.md` 文件
   - 解析 YAML Front Matter（用 snakeyaml，已在 BOM 中）
   - 提取双链 `[[文档名]]` 和 Markdown 链接 `[text](path)`
   - 写入 PostgreSQL（upsert by file_path）+ Neo4j（MERGE 节点和关系）
   - `@EventListener(ApplicationStartedEvent.class)` 启动时自动触发
   - verify: 启动后 `doc_document` 表有数据，Neo4j 有 DocNode 节点

4. [ ] #01904 文档 CRUD + 本地文件同步 + 全文检索 — developer-service (依赖: #01903)
   - `GET /api/docs/tree`：按 file_path 构建树结构
   - `GET /api/docs/{id}`：返回文档详情
   - `PUT /api/docs/{id}`：更新内容 + 写回本地文件 + 重新提取链接
   - `POST /api/docs/import`：手动触发全量导入
   - `GET /api/docs/{id}/relations`：查询 Neo4j 直接关系（1 跳），返回 nodes+edges
   - `GET /api/docs/search?q=`：PostgreSQL tsvector 全文检索
   - verify: 6 个接口 Swagger 可调用，单元测试覆盖 Service 层

### 前端主线

5. [ ] #01905 文档树 + Markdown 渲染 + 弹窗编辑 — developer-webui (依赖: #01902)
   - 页面路由：`/workspace/docs`
   - 左侧：文档树组件（按目录层级折叠展开）
   - 右侧：Markdown 渲染（使用 `react-markdown` + `remark-gfm`）
   - 编辑弹窗：Dialog + Textarea，保存调用 `PUT /api/docs/{id}`
   - TanStack Query 管理服务端状态
   - verify: 能浏览文档树，点击文档渲染内容，弹窗编辑保存成功

6. [ ] #01906 关系图谱可视化（React Flow） — developer-webui (依赖: #01905)
   - 在文档详情页增加"关系图"Tab
   - 调用 `GET /api/docs/{id}/relations` 获取 nodes+edges
   - 用 `@xyflow/react` 渲染关系图（节点=文档，边=引用关系）
   - 点击节点弹出文档预览弹窗（复用编辑弹窗的只读模式）
   - verify: 关系图正确渲染，点击节点弹出预览

<!-- 状态标记：[ ] 待开始 | ⏳ 进行中 | ✅ 已完成 | ❌ 已取消 | 🚫 阻塞中 -->

## 新增任务

> 开发过程中发现需要新增的任务，由开发者提出，协调者评估后写入。

## 评审状态

| 阶段 | 执行次数 | 最后执行 | 状态 | 必须 |
|------|---------|---------|------|------|
| product（需求细化） | 1 | 05-22 | ✅ CLEAR | 🔴 是 |
| architect（技术设计） | 1 | 05-22 | ✅ CLEAR | 🔴 是 |
| designer（UI 审查） | — | — | — | 不涉及 |
| developer（编码） | 0 | — | ⏳ PENDING | 🔴 是 |
| architect（代码审查） | 0 | — | ⏳ PENDING | 🔴 是 |
| tester（验收测试） | 0 | — | ⏳ PENDING | 🔴 是 |
| qa（过程审计） | 0 | — | ⏳ PENDING | 🔴 是 |
