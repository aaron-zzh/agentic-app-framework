---
level: Practice
layer: Model
purpose: 用户感知与语义界面设计——让 AI 理解界面、让界面理解用户、让交互数据可追溯
status: draft
version: 1.0.0
date: 2026-05-19
author: AaronZZH
scope:
  includes:
    - 用户操作感知与交互数据记录
    - 组件语义化元数据（语义界面）
    - AI 可理解的界面描述
    - AIUI 动态组件生成
gains:
  - 理解用户感知/语义界面/交互数据记录的设计动机和架构
  - 能在开发组件时正确添加语义描述
  - 能理解 AI 如何感知和操作界面
---

# 用户感知与语义界面（AGUI + AIUI）

> AI 不只是在对话框里回答问题——它能看见用户在做什么、理解界面是什么、主动操作界面完成任务。
>
> **与已有设计的关系**：本文档是以下已有设计的**演进与统一**：
> - [AI 感知能力（第三十五章）](./interaction-mode-structured-view.md#三十五ai-感知能力ai-context-awareness) — 定义了 `AIPageContext` / `UserAction` / `AIAwarenessService`，本文档在此基础上扩展为全面埋点体系
> - [Copilot 插件](./copilot-plugin.md) — 定义了 Copilot 体验层架构，本文档为其提供底层语义基础设施
> - [高带宽协作原则](../../../explanation/design-principles.md#高带宽协作) — 本文档是该原则在组件层面的具体落地
>
> **演进方向**：v0.1.0 的 AI 感知（第三十五章）聚焦于"AI 辅助当前页面操作"，本文档将其扩展为三层完整体系——从被动感知到主动理解到自主行动。

## 一、问题与动机

### 当前 AI 交互的局限

```text
传统模式：用户描述需求 → AI 生成文本回复 → 用户手动操作界面
                    ↑ 信息损失巨大 ↑

理想模式：AI 感知用户操作 → 理解当前界面状态 → 直接操作界面完成任务
```

三个断裂点：
1. **AI 看不见用户在做什么**：用户在表单中填了什么、点了哪里、停留多久——AI 一无所知
2. **AI 不理解界面是什么**：一个按钮叫"提交"，但 AI 不知道它会触发什么业务操作
3. **AI 无法操作界面**：即使 AI 知道该做什么，也只能输出文字指导用户手动操作

### AAF 的解法：四层能力

| 层 | 名称 | 解决的问题 | 对应版本 |
|----|------|-----------|---------|
| 感知层 | 全面用户感知 | AI 看不见用户在做什么 | v0.10 AAF-081 |
| 记录层 | 交互数据记录 | 操作不可追溯、无法分析优化 | v0.10 AAF-081 |
| 理解层 | 语义界面（AGUI） | AI 不理解界面是什么 | v0.10 AAF-081 |
| 行动层 | AI 组件生成（AIUI） | AI 无法操作界面 | v0.10 AAF-082 |

## 二、全面用户感知

### 设计目标

让 AI 实时感知用户的操作上下文，不依赖用户主动描述。

### 感知数据模型

> 基于第三十五章 `AIPageContext` 和 `UserAction` 扩展。原设计定义了页面级感知，本文档扩展为全局操作流感知。

```typescript
// 扩展自第三十五章 UserAction，增加组件语义关联
interface UserAction {
  // 原有字段（兼容第三十五章）
  type: 'click' | 'input' | 'drag' | 'scroll' | 'navigate' | 'submit' | 'select' | 'edit' | 'search' | 'filter';
  target: string;                      // 操作目标（字段名/按钮/记录ID）
  value?: any;
  timestamp: number;

  // 扩展：组件语义关联
  semantics: {
    componentId: string;               // 组件唯一标识
    semanticRole: string;              // 语义角色（如 "entity-form-field"）
    entitySlug?: string;               // 关联实体
    fieldName?: string;                // 关联字段
  };
  // 扩展：全局上下文
  context: {
    page: string;                      // 当前页面路由
    view: string;                      // 当前视图类型（list/form/kanban）
    entity?: string;                   // 当前操作的实体
    recordId?: string;                 // 当前记录 ID
  };
  sessionId: string;
  // 推断的意图（由 AI 后处理填充）
  inferredIntent?: string;
}
```

### 与 AIPageContext 的关系

```text
第三十五章 AIPageContext（页面级快照）
  ├── currentEntity / currentView / visibleFields    ← 页面结构
  ├── focusedField / selectedRecords / formValues    ← 用户状态
  └── recentActions: UserAction[]                    ← 最近操作

本文档扩展（全局操作流）
  ├── 全面埋点：不只是 recentActions，而是所有操作的持续流
  ├── 语义关联：每个 UserAction 关联到组件语义描述
  ├── 模式识别：操作序列 → 意图推断 → 主动建议
  └── 跨页面追踪：用户旅程级别的行为分析
```

### 埋点策略

| 策略 | 说明 | 适用场景 |
|------|------|---------|
| 声明式埋点 | 组件上添加 `data-track` 属性，框架自动采集 | 按钮、链接、表单提交 |
| 自动埋点 | 框架层拦截路由切换、表单变更、拖拽操作 | 导航、数据变更 |
| 语义埋点 | 基于组件语义描述自动推断操作含义 | 所有已注册语义组件 |

### 零侵入原则

开发者不需要手动写埋点代码。框架通过以下机制自动采集：

```text
ViewEngine 渲染组件时
  → 自动注入 data-track-* 属性（基于 EntityDef + FieldDef）
  → 全局事件委托捕获交互事件
  → 事件 + 组件语义描述 → 结构化 UserAction
  → 本地队列批量上报
```

## 三、交互数据记录

### 设计目标

将用户操作持久化为可查询、可分析、可回放的结构化数据。不只是给 AI 看，也服务于：
- **用户行为分析**：漏斗转化、留存、功能使用热力图
- **操作审计**：谁在什么时间做了什么操作（合规需求）
- **操作回放**：复现用户问题、培训演示
- **个性化优化**：基于历史行为优化界面布局和推荐

### 数据存储

```typescript
interface InteractionRecord {
  id: string;
  userId: string;
  sessionId: string;
  actions: UserAction[];              // 批量写入（每 N 秒或页面切换时 flush）
  
  // 会话级元数据
  startTime: number;
  endTime?: number;
  device: 'desktop' | 'mobile' | 'tablet';
  browser: string;
  
  // 聚合指标（写入时计算）
  pageViews: number;
  totalActions: number;
  activeTime: number;                 // 有效操作时间（排除空闲）
}
```

### 存储策略

| 数据类型 | 存储位置 | 保留期 | 用途 |
|---------|---------|--------|------|
| 实时操作流 | Redis Stream | 24h | AI 实时感知、即时建议 |
| 会话记录 | PostgreSQL | 90 天 | 行为分析、漏斗统计 |
| 聚合指标 | PostgreSQL | 永久 | 趋势报表、产品决策 |
| 审计日志 | PostgreSQL（不可变） | 按合规要求 | 操作审计、合规 |

### 隐私与合规

- 敏感字段值不记录原文（只记录"字段 X 被修改"，不记录修改为什么值）
- 用户可关闭行为采集（设置页开关，关闭后只保留审计级日志）
- 数据本地化存储，不外传第三方
- 提供数据导出和删除接口（GDPR 合规）

### 压缩与脱敏策略

记录数据量大，必须压缩存储和传输成本。同时敏感信息需规则化脱敏。

**压缩原则**：只记结果不记过程，用最少字符表达完整语义。

```text
原始记录：用户在客户表单的"联系电话"字段输入了 "13800138000"，然后点击了保存按钮
压缩后：  客户.电话=***0000|存

原始记录：AI 根据历史数据自动补全了"行业"字段为"互联网"，置信度 0.92
压缩后：  AI填.行业=互联网|c.92
```

**压缩规则（DSL 可配置）**：

```yaml
# 交互记录压缩规则
compression:
  # 操作类型简写
  action_aliases:
    navigate: "→"
    edit: "改"
    click: "点"
    submit: "存"
    delete: "删"
    search: "搜"
    filter: "筛"
    select: "选"
    ai_autocomplete: "AI填"
    ai_suggest: "AI荐"
    ai_execute: "AI执"
  
  # 字段值处理
  field_value:
    mode: hash          # hash | mask | omit | keep
    keep_fields: [status, priority, type]  # 枚举值可保留原文
    mask_fields: [phone, email, name]      # 敏感字段掩码
    omit_fields: [password, token]         # 绝对不记录
  
  # 连续操作合并
  merge:
    same_field_edits: true    # 同一字段连续编辑只保留最终态
    rapid_clicks: 500ms       # 500ms 内连续点击合并为一次
    scroll_compress: true     # 滚动事件压缩为方向+距离
  
  # 文言简化（可选，进一步压缩）
  terse_mode: true            # 启用精简文言风格
```

**文言简化示例**：

```text
标准格式：  user:edit customer.phone mask=***0000 | user:submit customer
文言简化：  改客户.电话***0000|存

标准格式：  ai:autocomplete customer.industry value=互联网 confidence=0.92 | user:approve
文言简化：  AI填.行业=互联网c92|准

标准格式：  user:navigate /workspace/order | user:filter status=pending | user:select [3 records] | user:batch-delete
文言简化：  →订单|筛待处理|选3|批删
```

**脱敏等级（按字段安全标记自动应用）**：

| 等级 | 策略 | 适用字段 |
|------|------|---------|
| L0 保留 | 原文记录 | 枚举值（状态/类型/优先级） |
| L1 掩码 | 保留首尾，中间 `***` | 手机号、邮箱、姓名 |
| L2 哈希 | 只记录 hash 用于去重 | 地址、身份证 |
| L3 忽略 | 完全不记录值 | 密码、Token、密钥 |

脱敏等级从 EntityDef 的 `fieldDef.security` 属性自动继承，无需额外配置。

### 双层存储：字段 + 摘要文档

交互数据同时以两种形式存储——结构化字段用于查询统计，摘要文档用于 AI 理解和人类审阅。

```text
字段存储（PostgreSQL 列式）：
  精确查询、聚合统计、筛选排序
  → "查找所有修改过客户电话的操作"
  → "统计本周 AI 自动补全的次数"

摘要文档（Markdown/文言文，存知识库）：
  AI 语义理解、上下文回溯、人类快速浏览
  → "这个用户今天做了什么？"
  → "这次审批流程经历了哪些决策？"
```

**摘要文档生成规则**：

```yaml
summary:
  # 生成频率
  trigger:
    - session_end          # 会话结束时生成
    - every: 30min         # 长会话每 30 分钟生成一次
    - on_milestone         # 关键操作（提交/审批/发布）时立即生成
  
  # 摘要格式（精简文言风格）
  format: terse_markdown
  max_length: 500          # 单次摘要不超过 500 字符
```

**摘要文档示例**：

```markdown
## 会话摘要 2026-05-19 14:00~14:30 | 用户: AaronZZH

### 操作轨迹
- →客户列表|筛行业=互联网|选5条|批量改状态=跟进中
- →客户#123详情|改联系人=***明|改电话=***8000|存
- AI填.行业标签=SaaS c.91|准
- →订单列表|新建订单|关联客户#123|存

### AI 决策
- AI填客户#123.行业标签=SaaS（依据：客户描述含"云服务"关键词）c.91 ✓已确认
- AI荐：批量跟进提醒（依据：5条客户超7天未联系）c.85 待审

### 摘要
客户批量跟进+#123详情更新+新建关联订单。AI辅助标签分类1次。
```

**双层关联**：摘要文档通过 `sessionId` 关联到字段记录，需要精确数据时从字段查，需要语义理解时从摘要查。摘要文档同时入知识库向量索引，支持语义检索（如"上周处理过哪些互联网客户"）。

### AI 决策记录

交互数据不只记录"用户做了什么"，还记录"AI 做了什么以及为什么"——这是异步审查通道的数据基础。

```typescript
interface AIDecisionRecord {
  id: string;
  timestamp: number;
  sessionId: string;
  
  // AI 做了什么
  action: string;                      // "auto-complete-field" / "route-to-agent" / "suggest-action"
  target: string;                      // 操作目标（字段/组件/流程）
  result: any;                         // 执行结果
  
  // 为什么这么做
  reasoning: string;                   // 决策理由（自然语言摘要）
  confidence: number;                  // 置信度
  verifiable: boolean;                 // 该决策是否可自动验证
  
  // 上下文快照
  contextSnapshot: {
    triggerEvent: string;              // 触发事件（用户操作/定时/系统事件）
    inputData: Record<string, any>;    // 输入数据摘要（脱敏）
    alternativeOptions?: string[];     // 其他可选方案
  };
  
  // 审查状态
  reviewStatus: 'pending' | 'approved' | 'rejected' | 'auto-verified';
  reviewedBy?: string;
  reviewedAt?: number;
  reviewNote?: string;
}
```

**前端审查入口**：

```text
结构化视图中 AI 操作的痕迹：
  字段被 AI 自动补全 → 字段右侧显示 "AI" 标记，hover 展示决策理由
  AI 推荐了操作 → 建议卡片底部 "为什么推荐这个？" 展开链接
  AI 自动执行了流程 → 活动流中显示 AI 决策条目，可点击查看详情

决策日志面板（异步审查通道）：
  时间线展示所有 AI 决策 → 按状态筛选（待审/已审/自动验证）
  批量审查 → 选中多条 → 批量通过/拒绝
  拒绝后回滚 → 撤销 AI 操作，恢复到决策前状态
```

## 四、语义界面（AGUI）

### 核心概念

语义化组件 = 视觉呈现 + 交互行为 + **语义描述** + 约束规则

传统组件只有前两者，AI 无法理解。AGUI 为每个组件附加机器可读的语义描述。

### 语义描述 Schema

```typescript
interface ComponentSemantics {
  // 基本信息
  name: string;                    // 组件名称（如 "EntityListView"）
  description: string;             // 自然语言描述（如 "展示实体记录的表格列表，支持排序、筛选、分页"）
  category: 'view' | 'form' | 'action' | 'navigation' | 'display';

  // 能力声明
  capabilities: string[];          // ["list-records", "sort", "filter", "paginate", "select"]
  
  // 输入/输出
  inputs: {
    name: string;
    type: string;
    description: string;
    required: boolean;
  }[];
  outputs: {
    name: string;
    type: string;
    description: string;
    trigger: string;               // 什么时候产生输出（如 "on-row-click"）
  }[];

  // 可执行的操作
  actions: {
    name: string;                  // 操作标识（如 "create-record"）
    description: string;           // "创建新记录"
    params?: Record<string, string>;
    sideEffects: string[];         // ["persist-to-database", "trigger-workflow"]
    reversible: boolean;           // 是否可撤销
  }[];

  // 约束
  constraints: {
    requiredPermissions?: string[];
    maxItems?: number;
    validStates?: string[];
  };
}
```

### 语义注册表

所有组件的语义描述集中注册，AI 可通过能力搜索找到合适的组件：

```typescript
// 注册
semanticRegistry.register('EntityListView', {
  name: 'EntityListView',
  description: '展示实体记录的表格列表，支持排序、筛选、分页',
  capabilities: ['list-records', 'sort', 'filter', 'paginate', 'batch-action'],
  actions: [
    { name: 'create', description: '创建新记录', reversible: true, sideEffects: ['persist'] },
    { name: 'delete', description: '删除选中记录', reversible: false, sideEffects: ['persist'] },
    { name: 'export', description: '导出为 CSV/Excel', reversible: true, sideEffects: ['download'] },
  ],
  // ...
});

// AI 查询：我需要一个能展示列表并支持筛选的组件
semanticRegistry.findByCapabilities(['list-records', 'filter']);
```

### 页面状态语义化

AI 不仅理解单个组件，还能理解整个页面的状态：

```typescript
interface PageSemantics {
  route: string;
  title: string;
  description: string;                    // "正在编辑客户 #123 的详细信息"
  currentEntity?: string;
  currentRecord?: string;
  activeView: string;
  availableActions: string[];             // 当前页面可执行的操作列表
  pendingChanges: boolean;                // 是否有未保存的修改
  components: ComponentInstance[];         // 页面上所有组件实例及其当前状态
}
```

## 五、操作意图映射

### 双向映射

```text
正向：用户自然语言 → 匹配组件操作
  "帮我把这个客户的状态改成已签约"
  → 意图：update-field
  → 目标：entity=customer, record=current, field=status, value=signed
  → 操作：FormView.setFieldValue('status', 'signed') + submit

反向：用户操作序列 → 推断意图
  用户连续点击 3 个客户的"详情"按钮，每次都查看"合同"标签页
  → 推断：用户在批量检查客户合同状态
  → 建议："要不要我帮你筛选出所有合同即将到期的客户？"
```

### 意图消歧

同一表述可能有多种理解，结合上下文选择最佳：

```text
用户说："删除这个"
  当前在列表页，选中了 3 条记录 → 批量删除 3 条记录
  当前在表单页，光标在某个字段 → 清空该字段值
  当前在看板页，拖拽中 → 取消拖拽操作
```

## 六、AI 组件生成（AIUI）

> 基于 [Copilot 插件](./copilot-plugin.md) 的 `CopilotService` + `ToolRegistry` 架构，AIUI 是 Copilot 的"界面生成"能力扩展。

### 从理解到行动

当 AI 理解了界面语义，下一步是直接生成或操作界面。这与第三十五章的 `AISuggestion.apply()` 机制一脉相承，但从"建议单个操作"升级为"生成完整界面"：

```text
第三十五章 AISuggestion：
  AI 建议 "自动补全这个字段" → 用户确认 → apply()

AIUI 升级：
  AI 生成 "一个完整的客户列表页面" → 沙箱预览 → 用户确认 → 注入页面
```

| 能力 | 说明 | 示例 |
|------|------|------|
| 组件推荐 | 根据上下文推荐合适的组件方案 | "这个场景用看板视图比列表更合适" |
| 参数推断 | 自动填充组件配置 | 根据实体字段自动生成表单布局 |
| 动态生成 | 根据意图生成新组件 | "给我一个客户分布地图" → 生成地图组件 |
| 布局优化 | 自动调整界面布局 | 检测到信息密度过高 → 建议分组折叠 |

### 生成流程

```text
用户意图（"给我一个用户列表页面，带搜索和分页"）
  ↓
意图解析 → { type: 'generate-view', entity: 'user', features: ['search', 'paginate'] }
  ↓
语义注册表查询 → 匹配 EntityListView + SearchFilter + Pagination
  ↓
参数推断 → 从 EntityDef('user') 提取字段列表、默认排序、筛选条件
  ↓
组件组装 → 生成 EntityDef 配置 或 React 组件代码
  ↓
沙箱预览 → 隔离环境渲染预览
  ↓
用户确认 → 注入页面 / 保存为模板
```

### 与结构化视图的关系

AIUI 不是替代结构化视图，而是**生成结构化视图配置**的能力：

```text
AIUI 生成 → EntityDef 配置 → ViewEngine 渲染 → 结构化视图
                                    ↑
                          用户在结构化视图中操作（主交互面）
                                    ↓
                          操作感知 → AI 理解 → 优化建议
```

## 七、审查辅助（AI 帮用户审查）

> 瓶颈迁移：执行近乎免费，规划与审查是新瓶颈。语义界面不只帮用户"操作"，更帮用户"审查"。

### 设计理念

```text
传统审查：人工逐行阅读 → 发现问题 → 手动标注 → 反馈修改（慢、易遗漏）
AAF 审查：AI 预审 → 结构化标注焊入文档 → 人类只审 AI 标注的风险点（快、精准）
```

### 审查能力矩阵

| 能力 | 说明 | 界面表现 |
|------|------|---------|
| 结构化 diff | 变更前后对比，按语义分组而非逐行 | 左右分栏 + 变更摘要卡片 |
| 风险标注 | AI 识别高风险变更并内联标注 | 变更行旁红色风险标记 + 理由 tooltip |
| 合规检查 | 自动检查规范/合同/政策合规性 | 合规检查清单（✓/✗）内联在文档中 |
| 影响分析 | 变更影响范围可视化 | 关联实体/接口/下游高亮 |
| 审查建议 | AI 预审后给出"建议通过/建议关注/建议拒绝" | 顶部审查摘要卡片 + 置信度 |

### 与语义界面的关系

审查辅助依赖语义界面提供的能力：
- **组件语义描述** → AI 理解"这个字段是金额"才能做合规检查
- **操作意图映射** → AI 理解"这次修改的意图是调整价格"才能评估风险
- **交互数据记录** → AI 知道"谁在什么上下文下做了这个修改"才能判断是否异常

### 实现原则

- AI 审查结果"焊"进结构化视图（内联标注），不在对话中输出审查报告
- 人类只需审查 AI 标注的风险点，而非全量内容（降低审查成本）
- 审查结论本身也是决策日志的一部分，支持追溯

## 八、与高带宽协作的关系

```text
高带宽协作原则：
  结构化视图为主（用户在此决策和审查）
  对话为辅（意图表达和快速指令）

AGUI/AIUI 的作用：
  让 AI 能"看见"结构化视图中发生了什么（感知层）
  让 AI 能"理解"结构化视图中每个组件的含义（理解层）
  让 AI 能"操作"结构化视图完成任务（行动层）
  
最终效果：
  用户在结构化视图中操作 → AI 实时感知 → 主动建议/辅助
  用户在对话中表达意图 → AI 直接操作结构化视图 → 结果即时可见
  不再需要"AI 输出文本 → 用户手动操作"的低效循环
```

## 九、实现路径

| 阶段 | 内容 | 版本 |
|------|------|------|
| P1 基础感知 | ViewEngine 自动注入 data-track、全局事件委托、UserAction 采集 | v0.10 |
| P2 语义注册 | ComponentSemantics Schema、核心组件语义描述、语义注册表 | v0.10 |
| P3 意图映射 | 正向映射（NL→操作）、反向映射（操作序列→意图推断） | v0.10 |
| P4 组件生成 | 意图→组件推荐→参数推断→沙箱预览→确认注入 | v0.10 |
| P5 闭环优化 | 行为数据分析→UI 优化建议→A/B 方案→持续改进 | v0.11+ |

## 十、与其他模块的关系

| 模块 | 关系 |
|------|------|
| 结构化视图（AAF-028） | AGUI 为其组件附加语义描述，AIUI 生成其配置 |
| AI 感知第三十五章 | 本文档是其演进——从页面级辅助扩展为全局感知+语义理解+自主行动 |
| Copilot 插件 | AGUI 是 Copilot 的底层语义基础设施，Copilot 是 AGUI 的用户体验层 |
| LiveChatter（AAF-045） | 对话中的意图通过 AGUI 映射为结构化视图操作 |
| 五层智能体（AAF-048~052） | Assistant 层通过 AGUI 感知用户状态，Agent 层通过 AGUI 执行界面操作 |
| 工作流编排（AAF-060） | 工作流节点可引用 AGUI 操作作为人工任务的自动化替代 |
| 知识库（AAF-053） | 组件语义描述本身是知识，可被 RAG 检索用于组件推荐 |

### 演进路径

```text
v0.1.0  AI 感知（第三十五章）
        └── AIPageContext + UserAction + AISuggestion
        └── 能力：字段补全、操作建议、错误修复（页面级、被动响应）

v0.10   AGUI 语义化（本文档 AAF-081）
        └── ComponentSemantics + 语义注册表 + 全面埋点
        └── 能力：AI 理解所有组件含义、操作意图双向映射、跨页面行为分析

v0.10   AIUI 组件生成（本文档 AAF-082）
        └── 意图→组件推荐→参数推断→沙箱预览→注入
        └── 能力：对话式界面搭建、AI 自主操作界面、布局自动优化

v0.11+  闭环自进化
        └── 行为数据→模式识别→UI 优化建议→A/B 测试→持续改进
```
