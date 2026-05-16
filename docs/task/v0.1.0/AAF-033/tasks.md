---
level: Practice
layer: Product
purpose: AAF-033 平台能力的技术任务清单
status: pending
version: 1.0.0
date: 2026-05-13
author: AaronZZH
---

# 平台能力（AAF-033）

> 设计：[结构化交互模式设计](../../../design/apps/webui/interaction-mode-structured-view.md) 章节十六、二十三、三十五、三十七、三十八、四十一、四十三、五十三、五十八
> 负责人：architect + developer-webui + developer-service | 创建：05-13

## 任务列表

> **执行策略**：先建多租户隔离（所有后续功能的前提），再并行推进仪表盘、i18n、移动端适配，最后叠加 AI 感知和无代码。
> 前置：AAF-032 #1（RBAC 权限）完成。

### 多租户

1. [ ] #1 多租户后端 — developer-service
   - organization 表 + org_member 表
   - 所有业务表增加 org_id 列
   - 全局 JPA Filter：`WHERE org_id = :currentOrgId`（从请求头 X-Org-Id 获取）
   - 用户会话携带 currentOrgId + orgs 列表
   - 个人工作空间：每用户自动创建特殊组织
   - verify: 不同组织数据完全隔离

2. [ ] #2 组织切换前端 — developer-webui (依赖: #1)
   - AppHeader 左侧组织切换器
   - 切换组织 → 更新 session → 设置 X-Org-Id 请求头 → invalidateQueries
   - 组织管理页面：基本信息 / 成员管理 / 邀请
   - verify: 切换组织后数据正确刷新

### 仪表盘

3. [ ] #3 仪表盘后端 — developer-service
   - sys_dashboard 表 + sys_dashboard_widget 表
   - Widget 数据查询 API：根据 WidgetConfig 执行聚合查询
   - 支持 counter / chart / list / progress 类型
   - verify: Widget 查询返回正确聚合数据

4. [ ] #4 仪表盘前端 — developer-webui (依赖: #3)
   - `/workspace/dashboard` 路由
   - react-grid-layout 拖拽布局
   - Widget 组件：CounterWidget / ChartWidget / ListWidget / ProgressWidget / ShortcutWidget
   - [编辑布局] 模式 + [+ 添加 Widget]
   - 保存为个人/团队布局
   - 自动刷新（refreshInterval）
   - verify: 拖拽调整布局后保存，刷新后布局保持

### 国际化

5. [ ] #5 i18n 基础设施 — developer-webui
   - 集成 next-intl
   - 字段标签：`labelKey` → `t(labelKey)` 运行时解析
   - 选项国际化：`option.labelKey` 支持
   - 校验错误国际化：Zod error map 注入翻译函数
   - 语言切换器（AppHeader 用户菜单）
   - verify: 切换语言后字段标签/选项/错误信息正确翻译

### 响应式移动端

6. [ ] #6 响应式适配 — developer-webui
   - 断点规则：≥1280 桌面 / 768-1279 平板 / <768 手机
   - 手机：底部 Tab 导航 + 侧边栏折叠
   - 列表视图手机模式：卡片列表（2-3 关键字段）
   - 看板手机模式：单列滚动
   - 表单手机模式：tabs 改为垂直折叠
   - 触摸优化：长按批量选择、点击进入编辑
   - verify: 各断点下布局正确切换

### AI 感知

7. [ ] #7 AI 感知服务 — developer-webui
   - AIAwarenessService 全局单例
   - `collectContext()` 收集 AIPageContext（当前实体/视图/字段/表单值/操作历史）
   - 敏感字段 `aiExclude: true` 排除
   - 操作历史仅保留最近 50 步
   - verify: 不同页面 collectContext 返回正确上下文

8. [ ] #8 AI 建议 UI — developer-webui (依赖: #7)
   - 字段自动补全：输入框下方灰色建议，Tab 接受
   - 操作建议：右下角浮动气泡
   - 错误修复：错误信息旁 [AI 修复] 按钮
   - 用户可全局关闭（设置 → AI 辅助）
   - sensitivity 配置（low/medium/high）
   - verify: 聚焦空字段时显示 AI 建议

### 无代码编辑器

9. [ ] #9 EntityDef JSON 编辑器（v0.1 版） — developer-webui
   - `/workspace/admin/entities` 管理页面
   - Monaco Editor 编辑 EntityDef JSON
   - JSON Schema 校验 + 自动补全
   - 实时预览：右侧 ViewEngine 渲染编辑中的配置
   - 保存后 invalidate entityRegistry 缓存
   - verify: 编辑 JSON 后预览正确，保存后新实体可访问

10. [ ] #10 EntityDef 后端存储 — developer-service (依赖: #9)
    - sys_entity_def 表：slug / config(JSONB) / builtin / enabled / version
    - API：CRUD + 合并逻辑（内置配置不可被数据库覆盖）
    - 前端启动时 `GET /api/entity-defs` 加载全量
    - 保存时自动建表：检测表是否存在 → CREATE TABLE / ALTER TABLE ADD COLUMN
    - 自动注册 REST API 端点（通用 CRUD，无需重启）
    - verify: 新建 EntityDef → 数据库自动建表 → API 可读写数据

### 模板记录

11. [ ] #11 模板记录 — developer-webui + developer-service
    - sys_record_template 表：entity_slug / name / field_values(JSONB) / created_by / is_shared
    - [+ 新建] 按钮展开模板列表
    - 从现有记录 [另存为模板]
    - 模板管理页面：编辑/复制/删除/设为默认
    - verify: 使用模板新建记录字段正确预填

### 数据对比

12. [ ] #12 数据对比视图 — developer-webui
    - 列表选中 2 条 → 批量操作 [对比]
    - 并排展示 + 差异高亮（= / ≠ / ≈）
    - [仅显示差异] 筛选
    - 合并功能：逐字段选择保留哪侧 → 合并为一条 + 软删除另一条
    - verify: 对比两条记录差异正确高亮

### Livechat

13. [ ] #13 Livechat 基础架构 — developer-webui + developer-service
    - 基于 assistant-ui 统一组件（Thread/ThreadList/Composer）
    - ExternalStoreRuntime 对接 WebSocket 后端
    - 三种 runtime 占位：AgUiRuntime / LivechatRuntime / IMRuntime
    - 客服会话作为 EntityDef 注册（享受列表/看板能力）
    - verify: 基础聊天消息收发正确

### 运行时扩展

14. [ ] #14 用户自定义字段 — developer-webui + developer-service (依赖: #10)
    - 管理员在表单/列表视图 ⚙️ → [自定义字段]
    - 弹窗选择字段类型 → 填写标签和配置 → 确认
    - 后端自动 ALTER TABLE ADD COLUMN
    - UI 即时渲染新字段（无需刷新）
    - 删除为逻辑隐藏（数据保留）
    - verify: 添加自定义字段后表单和列表立即显示

15. [ ] #15 AI 对话生成 EntityDef — developer-webui (依赖: #9, #10)
    - 对话入口：无代码编辑器中 [AI 生成] 按钮 / ⌘K 命令面板
    - 用户描述需求 → AI 生成完整 EntityDef JSON
    - 实时预览生成结果（ViewEngine 渲染）
    - 支持追加修改："加个优先级字段""列表按创建时间倒序"
    - 用户确认 → 保存到 sys_entity_def → 自动建表 → 生效
    - verify: 对话生成实体后可正常 CRUD

16. [ ] #16 命令面板（⌘K） — developer-webui
    - 全局快捷键 ⌘K / Ctrl+K 唤起
    - 搜索：实体记录 + 命令 + 导航 + 最近访问
    - 输入 `>` 仅搜索命令
    - 键盘导航：上下选择 + Enter 执行 + Esc 关闭
    - 可注册自定义命令（插件扩展）
    - verify: ⌘K 唤起后搜索和跳转正确

<!-- 状态标记：[ ] 待开始 | ⏳ 进行中 | ✅ 已完成 -->
