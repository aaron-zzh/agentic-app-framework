---
level: Practice
layer: Product
purpose: AAF-019 文档管理系统的技术任务清单
status: active
version: 1.1.0
date: 2026-05-22
author: AaronZZH
changelog:
  - 2026-05-22 | v1.1 所有任务完成，新增 #01907 编辑器 Markdown 模式
---

# 文档管理系统（AAF-019）

> 需求：[需求规格](requirement.md)
> 设计：[后端技术选型](../../../design/apps/service/tech-stack.md)
> 负责人：developer-service + developer-webui | 创建：05-22

## 任务列表

> **执行策略**：后端先行（迁移→骨架→导入→CRUD），前端并行（文档树→编辑→图谱）。

### 后端主线

1. ✅ #01901 Flyway 迁移 V3（doc_link 表 + doc_document.front_matter 字段） — developer-service
   - 新增 `doc_link` 表（source_id/target_id/link_type）
   - `doc_document` 补充 `front_matter JSONB` 字段 + GIN 全文检索索引

2. ✅ #01902 document 模块骨架（Entity/Repository/Service/Controller） — developer-service
   - `Document` / `DocLink` / `DocNode` Entity
   - `DocumentRepository`（含 tsvector 全文检索 native query）、`DocLinkRepository`、`DocRelationRepository`（Neo4j）
   - VO：`DocTreeNodeVO` / `DocSearchResultVO` / `DocRelationGraphVO` / `DocUpdateDTO`

3. ✅ #01903 文档导入服务（DocImportService） — developer-service
   - 启动时自动扫描 `docs/`，解析 YAML Front Matter（SnakeYAML）
   - 提取双链 `[[文档名]]` 和 Markdown 链接 `[text](path)`，写入 PostgreSQL + Neo4j

4. ✅ #01904 文档 CRUD + 本地文件同步 + 全文检索 — developer-service
   - 6 个 REST 接口：`/api/docs/tree|{id}|{id}/relations|search|import`
   - 保存时写回本地文件，重新提取链接关系

### 前端主线

5. ✅ #01905 文档树 + 弹窗编辑 — developer-webui
   - 页面路由：`/workspace/docs`
   - 左侧文档树（折叠展开）+ 右侧内容/图谱 Tab
   - 编辑弹窗：`RichTextEditor preset="document" mode="markdown"`

6. ✅ #01906 关系图谱可视化（React Flow） — developer-webui
   - `DocRelationGraph.tsx`：`@xyflow/react` 渲染文档引用关系
   - 点击节点切换文档

7. ✅ #01907 RichTextEditor Markdown 模式 — developer-webui
   - `converters/markdown.ts`：基于 `@lexical/markdown` 实现 Markdown ↔ Lexical 转换
   - `types.ts`：新增 `EditorMode`（html/markdown/plaintext）和 `mode` prop
   - `OnChangePlugin`：按 mode 序列化输出
   - `RichTextEditor`：按 mode 初始化编辑器内容

<!-- 状态标记：[ ] 待开始 | ⏳ 进行中 | ✅ 已完成 | ❌ 已取消 | 🚫 阻塞中 -->

## 新增任务

> 开发过程中发现需要新增的任务，由开发者提出，协调者评估后写入。

## 评审状态

| 阶段 | 执行次数 | 最后执行 | 状态 | 必须 |
|------|---------|---------|------|------|
| product（需求细化） | 1 | 05-22 | ✅ CLEAR | 🔴 是 |
| architect（技术设计） | 1 | 05-22 | ✅ CLEAR | 🔴 是 |
| designer（UI 审查） | — | — | — | 不涉及 |
| developer（编码） | 1 | 05-22 | ✅ CLEAR | 🔴 是 |
| architect（代码审查） | 0 | — | ⏳ PENDING | 🔴 是 |
| tester（验收测试） | 0 | — | ⏳ PENDING | 🔴 是 |
| qa（过程审计） | 0 | — | ⏳ PENDING | 🔴 是 |
