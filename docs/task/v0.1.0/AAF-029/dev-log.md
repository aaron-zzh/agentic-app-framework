# 开发记录：数据交互层（AAF-029）

执行者：AI/developer-webui

## #2901 useEntityList Hook

✅ 2026-05-16 — developer-webui

- `lib/queries/use-entity-list.ts`：TanStack Query 封装，queryKey = `[slug, "list", params]`
- 返回 `{ data, pagination, isLoading, isFetching, error }`，`keepPreviousData` 翻页无闪烁
- `lib/api/client.ts`：`fetchList` 构建查询字符串，统一 `ApiResult<T>` 解包
- 测试 3 条：获取数据、参数缓存隔离、不同实体缓存隔离，全绿

## #2902 useEntityRecord / useEntityMutation / useEntityDelete

✅ 2026-05-16 — developer-webui

- `useEntityRecord`：`enabled: !!id`，id 为 undefined 时不发请求
- `useEntityMutation`：有 id → PUT，无 id → POST；成功后 invalidate list + record
- `useEntityDelete`：批量 ids，`DELETE` body `{ ids: [...] }`，成功后 invalidate list
- 测试 5 条：单条查询、id 为空、创建、更新、批量删除，全绿


## #2904 nuqs URL 状态集成

✅ 2026-05-16 — developer-webui

- `lib/queries/search-params.ts`：`searchParamsParsers`（view/page/pageSize/sort/search）+ `searchParamsCache`（RSC 用）
- `lib/queries/use-entity-search-params.ts`：`useEntitySearchParams` 客户端读写 hook，`shallow: false`
- `lib/queries/use-filter-params.ts`：筛选条件 `f_` 前缀编解码 + URL 同步
- `NuqsAdapter` 已挂根布局，`ViewEngine.ConnectedListView` 已接入


## #2905 列配置与用户自定义列

✅ 2026-05-16 — developer-webui

- `ColumnConfigPanel.tsx`：Popover 触发，勾选显示/隐藏 + 重置，接入 ListView 表头右侧
- `useColumnPreferences`：localStorage 持久化，`toggleColumn` / `reorderColumns` / `resetColumns`
- ListView 已接入，`visibleColumns` 驱动渲染

## #2906 虚拟滚动

✅ 2026-05-16 — developer-webui

- `ListView` 内置：`data.length > 100` 自动切换 `VirtualTable`（`@tanstack/react-virtual`）
- `overscan: 10`，sticky thead，absolute 定位行

## #2907 行拖拽排序

✅ 2026-05-16 — developer-webui

- `DraggableListView.tsx`：`listView.draggable: true` 时启用，@dnd-kit 实现
- 拖拽完成更新 `orderField` 字段

## #2908 列表分组

✅ 2026-05-16 — developer-webui

- `GroupedListView.tsx`：`listView.groupBy` 配置时启用，折叠/展开 + 计数聚合


## #2909 筛选构建器 / #2910 筛选收藏

✅ 2026-05-16 — developer-webui

- `FilterBuilder.tsx`：字段选择 + `operatorsByType` 推断 + 多条件 + URL 同步（`useFilterParams`）
- `FilterFavorites.tsx`：localStorage 持久化，保存/应用/删除/设为默认
- `FilterChips.tsx`：活跃筛选标签 + 单个删除 + 清除全部

## #2911 全局搜索

✅ 2026-05-16 — developer-webui

- `CommandPalette.tsx`：⌘K 触发，跨实体导航 + 命令搜索，键盘上下选择/Enter/Escape/`>` 仅搜命令
- 后端 `/api/search` 有 TODO 占位，前端框架完整

## #2912 列表导出 / #2913 数据导入向导

✅ 2026-05-16 — developer-webui

- `ExportButton.tsx`：格式选择 + 触发下载
- `ImportWizard.tsx`：上传→字段映射→预览校验→冲突策略→执行→结果

## #2915 Server Actions 前端触发

✅ 2026-05-16 — developer-webui

- `EntityActions.tsx`：按 `position` 过滤，确认弹窗，POST endpoint，loading，invalidateQueries

## #2914 嵌套导入（主从关联）

✅ 2026-05-17 — developer-webui

- `NestedImportWizard.tsx`：5 步向导（上传→关系映射→字段映射→预览校验→结果）
- 支持 JSON 嵌套格式（自动识别主从结构）和 CSV（单主表）
- 孤儿检测：子记录找不到主记录时预览阶段报错
- 主表 + 子表字段分别映射，POST `/import/nested` 事务性写入
- `EntityDef` 新增 `import.nested` 配置 + `NestedImportConfig` 类型

## #2916 透视视图

✅ 2026-05-17 — developer-webui

- `PivotView.tsx`：左侧维度/指标面板 + 右侧配置区 + 结果表格
- 点击维度加入行，点击指标加入值，POST `/pivot` 执行 GROUP BY 聚合
- `EntityDef` 新增 `pivotView?: PivotViewConfig` 配置
- ViewEngine 新增 `pivot` case；Toolbar 视图切换动态显示透视 Tab

## #2917 批量操作异步化

✅ 2026-05-17 — developer-webui

- `use-batch-operation.ts`：阈值 100 条，≤100 同步，>100 异步轮询 `/api/tasks/{taskId}/progress`
- `BatchProgressBar.tsx`：进度条 + 百分比 + 预计剩余时间 + [取消] 按钮
- 取消时调用 `/api/tasks/{taskId}/cancel`

## #2918 耗时导出 SSE 进度推送

✅ 2026-05-17 — developer-webui

- `use-export-progress.ts`：EventSource 接收 SSE 进度，完成后自动触发浏览器下载
- `ExportButton.tsx` 升级：接入 SSE 进度，导出中按钮显示百分比，完成后自动下载
- 小数据量（后端不返回 taskId）直接下载，无 SSE 开销
