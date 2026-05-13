---
level: Practice
layer: Product
purpose: AAF-029 数据交互层的技术任务清单
status: pending
version: 1.0.0
date: 2026-05-13
author: AaronZZH
---

# 数据交互层（AAF-029）

> 设计：[结构化交互模式设计](../../../design/apps/webui/interaction-mode-structured-view.md) 章节六、七、十七、二十一、二十二、三十一、三十四、三十九、四十八、四十九、五十二、六十三
> 负责人：architect + developer-web + developer-api | 创建：05-13

## 任务列表

> **执行策略**：先建通用 CRUD Hooks + 后端通用 API，再逐步叠加高级列表、搜索、导入导出等能力。
> 前置：AAF-028 #3（实体注册表）+ #7（ListView 基础）完成。

### 通用数据 Hooks

1. [ ] #1 useEntityList Hook — developer-web
   - 基于 TanStack Query 封装，自动拼接 `entity.apiPath` + 分页/排序/筛选参数
   - 返回 `{ data, pagination, isLoading, isFetching }`
   - queryKey 包含 entity.slug + 所有参数（自动缓存隔离）
   - verify: mock API 下列表数据正确获取和缓存

2. [ ] #2 useEntityRecord / useEntityMutation / useEntityDelete — developer-web (依赖: #1)
   - `useEntityRecord(entity, id)`：单条记录查询
   - `useEntityMutation(entity, id?)`：创建/更新，optimistic update
   - `useEntityDelete(entity)`：删除（支持批量 ids）
   - 自动 invalidateQueries 刷新列表
   - verify: CRUD 全链路 mock 测试通过

3. [ ] #3 后端通用 CRUD API — developer-api
   - 基于 AAF-023 #31 用户管理模式，抽象为通用 EntityController
   - 端点：`GET /api/{entity}` / `GET /api/{entity}/{id}` / `POST` / `PUT` / `DELETE`
   - 支持分页（page/pageSize）、排序（sort=field:asc）、筛选（field=value）
   - 统一 `Result<T>` / `PageResult<T>` 响应
   - verify: Swagger 中通用端点可调用

### URL 状态管理

4. [ ] #4 nuqs URL 状态集成 — developer-web (依赖: #1)
   - 安装配置 nuqs，定义类型安全的 URL 参数 schema
   - 参数：`view` / `page` / `pageSize` / `sort` / `search` + 动态筛选 key
   - useEntityList 从 URL 参数读取查询条件
   - 浏览器前进/后退正确恢复状态
   - verify: 修改 URL 参数 → 列表自动刷新；刷新页面状态保持

### 高级列表能力

5. [ ] #5 列配置与用户自定义列 — developer-web (依赖: #1)
   - 支持 `ColumnDef`：width / fixed / sortable / resizable / hidden
   - 列配置面板：拖拽排序 + 显示/隐藏勾选
   - 保存为用户偏好（localStorage）
   - verify: 隐藏/显示列生效，刷新后偏好保持

6. [ ] #6 虚拟滚动 — developer-web (依赖: #5)
   - 集成 @tanstack/react-virtual
   - 数据量 > 100 行自动启用
   - 保持 DOM 节点数恒定
   - verify: 1000 行数据流畅滚动，DOM 节点数 < 50

7. [ ] #7 行拖拽排序 — developer-web (依赖: #5)
   - `listView.draggable: true` 时启用
   - @dnd-kit 实现行拖拽
   - 拖拽完成批量更新 `orderField`
   - verify: 拖拽行后 sortOrder 正确更新

8. [ ] #8 列表分组 — developer-web (依赖: #5)
   - `listView.groupBy` 配置分组字段
   - 分组头：折叠/展开 + 聚合信息（计数）
   - 拖拽记录跨分组 = 修改分组字段值
   - verify: 按状态分组正确展示，跨组拖拽触发更新

### 搜索与筛选

9. [ ] #9 筛选构建器 — developer-web (依赖: #4)
   - [+ 添加筛选] 弹出筛选面板
   - 根据字段类型自动推断操作符（operatorsByType）
   - 多条件 AND/OR 组合
   - 筛选条件同步到 URL 参数
   - verify: 添加筛选条件后列表正确过滤

10. [ ] #10 筛选收藏 — developer-web (依赖: #9)
    - [保存为收藏] → 命名 → 存入 localStorage / 后端 user_preference
    - 收藏列表展示 + 一键应用 + 设为默认
    - 支持团队共享（后端存储）
    - verify: 保存/加载/删除收藏筛选正确

11. [ ] #11 全局搜索（跨实体） — developer-web + developer-api (依赖: #1)
    - ⌘K 命令面板增强：输入关键词跨所有实体搜索
    - 后端 `GET /api/search?q=keyword&entities=all&limit=5`
    - 前端聚合结果分组展示（实体/命令/导航/最近访问）
    - 键盘交互：上下选择 + Enter 跳转 + `>` 仅搜命令
    - verify: 搜索关键词返回跨实体结果并可跳转

### 导入导出

12. [ ] #12 列表导出 — developer-web + developer-api (依赖: #1)
    - 工具栏 [导出] 按钮 → 选择格式（CSV/XLSX）+ 选择字段
    - 后端 `GET /api/{entity}/export?format=csv&fields=...`
    - 前端触发下载
    - verify: 导出 CSV 文件内容与列表数据一致

13. [ ] #13 数据导入向导 — developer-web + developer-api (依赖: #12)
    - 向导流程：上传 → 字段映射 → 预览校验 → 冲突策略 → 执行 → 结果
    - AI 自动匹配列名到字段
    - 基于 EntityDef.fields 的 Zod Schema 校验每行
    - 后端 `POST /api/{entity}/import`
    - verify: 上传 CSV → 映射 → 导入成功，错误行报告正确

14. [ ] #14 嵌套导入（主从关联） — developer-web + developer-api (依赖: #13)
    - 支持 XLSX 多 Sheet / JSON 嵌套格式
    - 关系映射步骤：主实体 + 子实体 + 关联列
    - 事务性写入（主+子原子操作）
    - 孤儿检测 + 级联必填校验
    - verify: 多 Sheet XLSX 导入主从数据正确关联

### Server Actions

15. [ ] #15 Server Actions 前端触发 — developer-web (依赖: #2)
    - 从 `entity.actions` 读取操作配置
    - 按 `position` 渲染到 formHeader / listToolbar / rowAction / contextMenu
    - 执行流程：确认 → POST endpoint → loading → Toast + invalidate
    - `visibleWhen` 条件控制按钮显示
    - verify: 点击操作按钮正确调用后端并刷新数据

### 透视报表

16. [ ] #16 透视视图 — developer-web + developer-api (依赖: #1)
    - 视图切换器新增 [📊 透视] Tab
    - 维度面板：拖拽字段到行/列/值区域
    - 后端 `POST /api/{entity}/pivot` 执行 GROUP BY 聚合
    - 结果区域支持表格/图表切换
    - 保存为报表模板
    - verify: 拖拽维度/指标后透视表正确渲染

### 批量异步化

17. [ ] #17 批量操作异步化 — developer-web + developer-api (依赖: #2)
    - 阈值判断：≤100 同步，>100 异步
    - 异步流程：POST → 返回 taskId → 轮询进度 → 完成通知
    - 进度 UI：进度条 + 百分比 + 预计时间 + [取消]
    - 后端 `GET /api/tasks/{taskId}/progress`
    - verify: 批量删除 200 条触发异步，进度条正确更新

<!-- 状态标记：[ ] 待开始 | ⏳ 进行中 | ✅ 已完成 -->
