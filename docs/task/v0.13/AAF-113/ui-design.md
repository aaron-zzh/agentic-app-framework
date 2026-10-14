---
level: Practice
layer: Product
purpose: 定义 AAF-113 项目创作与一次性 AI 定时提示词在现有 Content Studio 的交互
status: active
version: 1.1.0
date: 2026-09-14
author: AaronZZH & Kiro
tags:
  - AAF-113
  - UI
  - Content Studio
  - Assistant
  - Project Workbench
  - ProjectSchedule
related:
  - requirement.md
  - design.md
  - tasks.md
  - aigc-project-capability-gaps.md
  - ../../../design/ui/design-system.md
  - ../../../design/ui/ui-experience.md
---

# AAF-113 Content Studio 项目协作 UI 设计

## 设计目标

在现有 Content Studio 项目界面上完成最小增量改造：

- 保留项目头部、ContentActionBar、结构/图谱双视图、交付面板和底部状态栏；
- 支持使用目录 Blueprint 或由 Assistant 根据当前项目需求生成 BlueprintSnapshot；
- 将“生成图像、重新编辑、添加”等局部修改保留为直接、可见的界面操作；
- 将项目级批量生成统一改为调用现有 Assistant，创建或推进 canonical TaskPlan；
- 同项目存在未终结 Assistant Task 时禁用批量生成，阻止重复计划；
- 用 ProjectProgressSnapshot、ObjectReadiness 和 allowedActions 展示权威状态，不在前端复制业务状态机；
- 在现有项目页设置一个或多个一次性时间点和提示词，到点后在项目页与绑定 Assistant 对话查看同一 Task 状态。

## 设计原则

- **增量改造**：不重做项目详情页，不新增平行工作台。
- **局部修改优先**：用户通过对象行、详情面板、画布和现有 Dialog 完成具体微调。
- **智能与确定性分流**：需要理解、创作或编排时进入 Assistant；目标、输入和处理方式已确定时调用类型化 API。
- **单一任务入口**：项目级批量生成只进入现有 Assistant，不直接启动独立批处理。
- **禁用有原因**：按钮禁用必须同时显示可见原因和下一步，不能只变灰。
- **服务端权威**：Assistant 活跃状态、readiness、allowedActions、实际成本均来自服务端投影。
- **候选优先**：生成和重新编辑默认形成新 candidate，不自动覆盖 adopted。
- **时间点而非 cron**：用户只选择日期、时间和时区，不接触 cron、handler 或内部动作类型。

## 现有界面基线

当前项目详情页结构保持不变：

```text
ProjectWorkbenchHeader
├─ 项目信息、生命周期操作
├─ 结构 / 图谱切换
├─ 定时任务入口
└─ 项目文档入口

ContentActionBar
├─ 创作上下文
├─ Prompt、片段库、附件
├─ 目标预设
└─ 批量生成 / Assistant 状态

项目工作区
├─ ProjectStructureView
│  └─ 合同、移除、详情、生成、画布/标注
└─ ProjectGraphView
   └─ 与结构视图相同的对象操作语义

DeliveryWorkflowPanel
└─ 评估、冻结、审核、收录

底部状态栏
└─ 图谱、Task、readiness、Run、candidate、成本摘要
```

继续复用：

- `ProjectObjectGenerationDialog` 与 `MediaGenerationWorkspace`；
- `ProjectObjectStructureDialog`；
- `ObjectDetailPanel`、`CandidateComparisonDialog` 和 `ExecutionRunTree`；
- `ContentCanvasSheet` 的画布与图像标注模式；
- 现有 Assistant/AG-UI 面板和 Task 详情能力。

## 项目创建

### 创建方式选择

保留 `NewProjectLauncher` 的项目类型、品牌、渠道、简介和封面字段。在“项目模板”区域增加来源选择，不新增独立创建页：

```text
项目结构来源
[ 使用项目模板 ] [ 由 Assistant 规划 ]
```

| 选择 | 界面内容 | 提交结果 |
|---|---|---|
| 使用项目模板 | 复用现有 `ProjectBlueprintPicker`，展示产物和生成步骤 | `sourceType=CATALOG` BlueprintSnapshot |
| 由 Assistant 规划 | 展示项目目标、交付物、约束和参考信息输入，并提供“让 Assistant 规划结构” | `sourceType=ASSISTANT_GENERATED` BlueprintSnapshot proposal |

- 默认优先展示可用目录 Blueprint。
- 当前项目类型没有已发布 Blueprint 时，不再阻断创建；自动选中“由 Assistant 规划”，说明“Assistant 将根据当前需求规划项目结构，确认后创建”。
- “不确定，让助手帮我选择”保留，但改为在当前创建上下文中打开 Assistant，不跳转到脱离表单状态的空白对话。
- ProjectSpecification 优先复用当前品牌、项目类型、渠道、项目简介和附件，用户无需重复输入。

### Assistant 结构提案

点击“让 Assistant 规划结构”后：

```text
冻结当前表单上下文
→ 提交结构化 Assistant objective
→ 原位显示规划状态
→ 返回 BlueprintSnapshot proposal
→ 展示对象树、交付物、依赖、预计范围和确认点
→ 用户确认或返回修改需求
→ 创建项目并原子物化 ProjectGraph
```

提案使用页面内语义卡片或现有 Assistant 面板展示，不使用不可编辑的最终确认弹窗。卡片提供：

- “按此结构创建项目”；
- “修改需求”；
- “取消规划”。

规划期间创建按钮显示“Assistant 正在规划…”，并禁用重复规划；用户可以安全关闭页面，恢复后按持久 Task/Execution 查询状态。

## 项目详情页

### ProjectWorkbenchHeader

页面头部保持现有布局，仅增加紧凑 Assistant 状态入口，放在项目状态 Chip 后或右侧操作区：

| 状态 | Chip 文案 | 操作 |
|---|---|---|
| 无活跃 Task | `Assistant 空闲` | 点击打开现有 Assistant 面板 |
| 处理中 | `Assistant 处理中 · 3/7` | 点击查看 Task |
| 等待外部结果 | `等待生成结果` | 点击查看等待对象和 Run |
| 待确认 | `1 项待确认` | 点击定位确认卡 |
| 已暂停 | `Task 已暂停` | 点击恢复、修订或取消 |
| 失败 | `Task 需要处理` | 点击查看失败摘要 |

状态 Chip 是入口和摘要，不承担启动批量生成。

头部右侧增加“定时任务”按钮，显示未触发数量，例如 `定时任务 · 2`。按钮只打开当前项目的 Schedule Sheet，不跳转到系统管理员计划任务页。

### 定时任务 Sheet

点击项目头部“定时任务”打开右侧 Sheet，复用现有 Sheet、Button、Input、Textarea、Select 和状态 Chip，不新增工作台。

```text
定时任务                                      新建

即将执行
09-15 09:00 · Asia/Shanghai    已计划          编辑 · 暂停 · 取消
09-18 14:30 · Asia/Shanghai    等待当前任务完成 查看任务 · 取消

历史
09-12 10:00 · 定时触发          Assistant 处理中 查看任务
09-10 09:00 · 触发失败          权限已失效       查看原因
```

列表按未触发时间升序、历史触发时间倒序展示。每项显示：

- 本地日期时间与 IANA 时区；
- 提示词首行摘要；
- schedule 状态；
- linkedTaskId 存在时的 canonical Task 状态和“查看任务”；
- DEFERRED/FAILED 的可见原因；
- 未触发记录的编辑、暂停/恢复和取消。

不把 canonical Task 的状态折叠写回 schedule Chip。例如 schedule 显示“已触发”，相邻 Task Chip 显示“等待确认”或“处理中”。

### 新建与编辑

Sheet 内使用同一表单：

```text
提示词
[ 分析项目当前进度，补齐缺失文案并生成所需图片…… ]

执行时间
[2026-09-15 09:00] [Asia/Shanghai] [移除]
[2026-09-18 14:30] [Asia/Shanghai] [移除]
+ 添加时间点

将在当前项目的 Assistant 对话中执行；若届时项目正忙，将等待当前任务完成。

                                      取消  保存定时任务
```

- 至少填写一个未来时间点；同一提交内不得有重复 Instant。
- 时区默认浏览器 IANA 时区，但提交和展示都使用服务端返回值；不提供系统默认时区猜测。
- 多个时间点共享同一提示词，保存后形成多条可独立管理的 schedule。
- 编辑只允许未触发状态；保存使用 schedule/project version CAS。冲突时刷新并要求复核，不静默覆盖。
- 提示词按现有 Assistant objective 长度、附件和安全合同校验；首期不在 schedule 中保存附件快照、模型参数或 inputVersionIds。
- 不提供 cron、周期重复、跳过/替换策略、立即执行或确定性动作选择。

### 定时触发与对话状态

到点且项目空闲后，绑定的现有 Assistant 对话出现来源为“定时触发”的 Task 卡片：

```text
定时触发 · 09-15 09:00
分析项目当前进度，补齐缺失文案并生成所需图片……
Task #... · Assistant 处理中                               查看任务
```

- 卡片的 Task 状态、步骤、外部等待、待确认和结果全部读取 canonical Task；不创建 schedule 专属对话状态。
- 项目页头部 Assistant Chip、ContentActionBar 状态条、底部状态栏和对话卡片链接到同一 Task。
- 到点时已有 current Task，schedule 显示“等待当前任务完成”，不向对话插入第二 Task 卡片；项目空闲并成功触发后才显示。
- Task 进入 AWAITING_CONFIRMATION 时，项目页和对话沿用既有“查看并确认”入口；schedule 不代替用户确认。
- 网络断开或页面关闭不影响服务端触发；重开 Sheet 或对话后重新查询权威状态。

### schedule 状态与操作

| 状态 | 用户文案 | 可用操作 |
|---|---|---|
| SCHEDULED | 已计划 | 编辑、暂停、取消 |
| PAUSED | 已暂停 | 编辑、恢复、取消 |
| DEFERRED | 等待当前任务完成 | 编辑、暂停、取消、查看当前 Task |
| DISPATCHING | 正在触发… | 查看；写操作禁用 |
| TRIGGERED | 已触发 | 查看 linked Task |
| FAILED | 触发失败 | 查看原因 |
| CANCELED | 已取消 | 查看历史 |

取消需确认“该时间点将不再执行”；它不取消已触发的 canonical Task。已触发 Task 只能通过既有 Task 控制入口暂停、修订或取消。

### ContentActionBar

保留现有创作上下文、Prompt、片段库和附件布局。调整右侧目标与主按钮语义：

- 动作下拉改为“目标范围/创作目标”预设，用于组装 Assistant objective，不代表浏览器直接执行项目级动作。
- 无聚焦对象或选择多个对象、当前阶段、全部未完成对象时，主按钮显示“批量生成”。
- 有聚焦对象且输入需要 Assistant 创作文本、组织 Prompt 或决定后续步骤时，主按钮显示“让 Assistant 处理”。
- 主按钮提交结构化 `projectId + objective + focusedObjectId? + explicitScope` 到 Assistant Task API。
- 项目级批量生成不调用直接专业执行 API。
- 提交成功后提示“已交给 Assistant”，显示稳定 Task 引用，并打开或聚焦现有 Assistant 面板。

主按钮附近增加单行范围摘要：

```text
本次范围：8 可执行 · 2 依赖阻断 · 1 待处理候选
```

数据来自 ProjectProgressSnapshot/ObjectReadiness。未选 OPTIONAL 和 EXCLUDED 不计入范围。

### Assistant 状态条

ContentActionBar 底部增加一行紧凑状态，不新增大型任务面板：

```text
Assistant 正在处理 · 3/7 步 · 2 个图像生成中          查看任务
等待外部结果 · 可安全关闭页面                        查看任务
需要确认 · 1 项                                      查看并确认
任务已暂停 · 不会继续消耗积分                        恢复或修订
```

- 状态条只显示当前项目的 current Task。
- Task 详情、确认、修订、恢复和取消继续在现有 Assistant 面板完成。
- Activity SSE 只触发 ProjectProgressSnapshot、graph、candidate 和 Run Query 失效重取。

## 对象级微调

### 操作优先级

结构视图和图谱视图必须提供相同语义。桌面端高频操作直接显示，低频操作可收入“更多”；移动端保持至少 44×44px 点击区域。

| 操作 | 入口 | 执行方式 | 结果 |
|---|---|---|---|
| 生成图像 | 对象行“生成图像” | `ProjectObjectGenerationDialog` → `MediaGenerationWorkspace` → 专业媒体 API | 新 MediaVersion 与 ObjectVersion/ref candidate |
| 重新生成 | candidate/adopted 的操作菜单 | 同一生成 Dialog，预填允许复用的 Prompt、模型和参数 | 新 Run 和 candidate，保留原版本 |
| 重新编辑 | 对象行或详情面板“重新编辑” | 文本/文档进入编辑器；图像进入 Canvas/Annotation | 保存新 candidate，不原地覆盖 adopted |
| 添加 | 阶段头部“添加内容”或对象菜单“添加下级” | `ProjectObjectStructureDialog` → 类型化 Project API | 新对象、ProjectRevision 和 graphRevision |
| 合同 | 对象行“合同” | 现有结构 Dialog | 更新 REQUIRED/OPTIONAL/EXCLUDED |
| 移除 | 对象行“移除” | 现有结构 Dialog，原因必填 | 删除或转 EXCLUDED，由服务端判定 |
| 详情 | 对象行“详情” | `ObjectDetailPanel` | 查看语义、版本、candidate、Run 和阻断原因 |
| 采用/否决 | 候选与版本页签 | 类型化 Project API + CAS | 更新候选决策，不改变历史版本 |

### 生成图像

- 对图像对象将现有通用“生成”文案明确为“生成图像”。
- 只有 ObjectReadiness 为 READY 且 `allowedActions` 包含媒体生成时启用。
- ACTIVE_RUN 时显示“生成中”并禁用；PENDING_CANDIDATE 时显示“查看候选”，避免默认重复生成。
- Dialog 标题使用对象名，例如“生成封面图”。说明固定为“结果将保存为候选版本，不会自动替换当前采用版”。
- 用户已经完整确定 Prompt、模型和参数时直接调用专业媒体 API；需要 Assistant 根据上下文组织 Prompt 时提供“让 Assistant 优化 Prompt”入口，返回同一 Assistant。
- 专业 Tool 返回 Run 后只提示“已提交生成”；MediaVersion 与 candidate 持久化后才提示“候选已生成”。

### 重新生成

- 从选中的 candidate 或 adopted 版本进入，显示来源版本号。
- 只预填服务端允许复用的 Prompt、参考资源和参数；不复制不可访问或已失效资源。
- 提交前展示“将创建新候选，原版本不会变化”。
- 新 Run 使用新的幂等键；同一次提交重试复用原幂等意图。

### 重新编辑

按对象资源类型路由：

| 类型 | 编辑器 |
|---|---|
| INLINE_OBJECT_VERSION | 结构化数据编辑器，不接受自由文本正文 |
| DOCUMENT_VERSION | 现有文本/内容编辑器、`DocumentEditor` 或项目文档编辑器 |
| MEDIA_VERSION 图像 | `ContentCanvasSheet` canvas/annotation |
| REFERENCE_ONLY | 打开来源资源，只允许修改项目引用元数据 |

- 保存始终创建新 ObjectVersion/DocumentVersion/MediaVersion candidate。
- adopted 指针只有在用户明确点击采用后变化。
- Assistant 正在处理同一对象或对象存在 ACTIVE_RUN 时，按服务端 allowedActions 禁用冲突编辑，并显示“Assistant 正在处理此对象”。
- 非重叠对象继续按各自 allowedActions 操作，不因项目有活跃 Task 而粗暴锁死整个页面。

### 添加内容

- 将阶段头部“追加对象”文案调整为“添加内容”，保留现有位置和 Dialog。
- 对允许父子关系的对象增加“添加下级”，打开同一 `ProjectObjectStructureDialog` 并预选父对象。
- 类型列表、父对象、contractRole 和 actionKey 必须来自服务端当前 BlueprintSnapshot/allowedActions，不由浏览器维护完整语义目录。
- 添加成功形成 ProjectRevision 并刷新 ProjectGraph。
- Assistant 正在处理时，若服务端允许添加，新对象不自动进入 current TaskPlan；状态条提示“已添加，未纳入当前任务”。用户可在 Task 完成后批量生成，或显式修订当前 Task。
- 若新增结构与 current Task 冻结范围冲突，服务端拒绝并返回稳定 blockedReason；UI 提供“查看并修订 Task”。

## readiness 展示

对象行在现有状态 Chip 旁增加 ObjectReadiness 文本或小型 Chip：

| ObjectReadiness | UI 文案 | 默认操作 |
|---|---|---|
| SATISFIED | 已满足 | 查看/重新生成 |
| READY | 可生成 | 生成图像或让 Assistant 处理 |
| BLOCKED_BY_DEPENDENCY | 依赖未满足 | 查看依赖 |
| BLOCKED_BY_POLICY | 策略阻断 | 查看原因 |
| ACTIVE_RUN | 生成中 | 查看 Run |
| PENDING_CANDIDATE | 待处理候选 | 查看候选 |
| AWAITING_CONFIRMATION | 待确认 | 查看并确认 |

- readiness 文字、图标和颜色同时呈现，不只依赖颜色。
- 前端不根据 object.status、Run 或 candidate 自行合成 readiness。
- 按钮最终是否可用以服务端 `allowedActions`、权限、lifecycle 和当前版本为准。
- ProjectObject 内容成熟度只显示 EMPTY、CANDIDATE_AVAILABLE、ADOPTED；不得混入 blocked、done 或 review 状态。

## 批量生成与禁用规则

### 批量生成调用合同

点击“批量生成”时提交：

```text
projectId
objective
explicitScope
focusedObjectId?
projectVersion
 graphRevision
idempotencyKey
```

Assistant 在服务端重新读取 ProjectContextSnapshot 和 ObjectReadiness，创建或推进 canonical TaskPlan。浏览器不提交自行计算的依赖顺序、inputVersionIds 或并行度。

### Assistant 活跃判定

按钮禁用依据是 ProjectProgressSnapshot 中同项目 current Task 的权威投影，不依据以下客户端信号：

- AG-UI 是否仍在流式输出；
- 浏览器是否保留提交 Promise；
- SSE 是否已连接；
- 页面是否仍打开。

只要同项目存在未终结 TaskPlan，批量生成保持禁用。UI 聚合状态如下：

| UI 状态 | 权威含义 | 批量生成按钮 | 按钮/状态文案 |
|---|---|---|---|
| IDLE | 无 current Task 或 Task 已终态 | 满足权限、lifecycle、范围时启用 | 批量生成 |
| SUBMITTING | 本地请求尚未返回稳定 Task 引用 | 禁用 | 正在提交… |
| PROCESSING | current Task 正在规划或执行 | 禁用 | Assistant 处理中… |
| AWAITING_EXTERNAL | Task 正等待专业资源 | 禁用 | 等待生成结果… |
| RECOVERING | 系统正在恢复同一 Execution | 禁用 | 正在恢复任务… |
| AWAITING_CONFIRMATION | current Task 有未解决确认 | 禁用 | 等待确认 |
| PAUSED | current Task 已暂停但未终结 | 禁用 | Task 已暂停 |
| TERMINAL | Task COMPLETED/FAILED/CANCELED | 可重新按权威状态判断 | 批量生成 |
| STALE/OFFLINE | 无法确认 current Task 或 allowedActions | 禁用所有写入口 | 刷新项目状态 |

`PROCESSING` 至少覆盖 Task 的规划、执行和结果汇总阶段；`AWAITING_EXTERNAL` 即使没有活跃 Agent 线程，也属于 Assistant 正在处理的同一业务任务。

### 禁用后的可操作入口

禁用按钮旁必须提供：

- 当前原因；
- “查看任务”；
- 待确认时的“查看并确认”；
- 暂停时的“恢复或修订”；
- 失败时的“查看失败项”。

不得通过再次点击批量生成创建竞争 Task。用户需要改变目标时，应修订或取消 current Task。

### 对象操作禁用

Assistant 活跃不等于全页面只读：

| 对象状态 | 生成图像 | 重新编辑 | 添加/合同/移除 |
|---|---|---|---|
| 非 current Task 范围且 allowedActions 允许 | 可用 | 可用 | 可用 |
| current Task 正处理同一对象 | 禁用 | 由 allowedActions 决定，冲突写入禁用 | 由 allowedActions/CAS 决定 |
| ACTIVE_RUN | 禁用 | 冲突编辑禁用 | 由服务端决定 |
| PENDING_CANDIDATE | 默认禁用，优先查看候选 | 可基于明确版本创建新 candidate | 由服务端决定 |
| 图谱/项目版本过期 | 禁用 | 禁用保存 | 禁用并刷新 |
| lifecycle 或权限只读 | 禁用 | 禁用 | 禁用 |

## 状态栏与进度

底部状态栏沿用现有单行布局，数据改为 ProjectProgressSnapshot：

```text
图谱 r12 · Task 3/7 · 定时 2 · 可生成 8 · 阻断 2 · Run 2 · 候选 1 · 待确认 1 · 实际 36 积分
```

- “实际积分”使用 settled-refunded。
- estimated、reserved、settled、refunded 在 Task/成本详情中分别展示。
- 不以 `AigcProject.costUsed` 作为独立结算事实。
- 点击 Task、Run、候选、待确认或积分摘要，分别打开已有 Assistant、RunTree、候选详情或成本明细。
- 点击“定时”打开 Schedule Sheet；数量只统计 SCHEDULED、PAUSED、DEFERRED 和 DISPATCHING。

## 反馈与恢复

### 成功反馈

| 时点 | 文案 |
|---|---|
| Assistant Task 已持久化 | 已交给 Assistant，可在任务面板查看进度 |
| 专业 Run 已受理 | 已提交生成，结果将保存为候选版本 |
| candidate 已持久化 | 候选已生成，查看或采用 |
| 新对象已创建 | 内容已添加，未自动纳入当前任务 |
| 新编辑版本已保存 | 已保存为候选版本，当前采用版未变化 |

不得在 Tool accepted 时显示“生成完成”。

### 错误与恢复

- **Assistant 提交失败**：保留 Prompt、附件和范围，原位展示错误并允许重试；同一重试复用幂等意图。
- **版本或图谱冲突**：提示“项目已更新，已刷新权威状态”，刷新后要求用户复核范围，不静默重放旧请求。
- **部分失败**：按对象展示成功、失败、等待项；“重试失败项”修订/恢复同一 TaskPlan，不重复成功对象。
- **权限或策略阻断**：展示服务端 blockedReason，并提供处理候选、完成确认、调整范围或联系管理员等下一步。
- **网络断开**：显示“连接中断，任务继续在服务端运行”；重连后重新查询 ProjectProgressSnapshot。
- **页面关闭**：不取消 Task；重新进入项目时恢复 current Task、readiness、Run、candidate 和待确认状态。
- **候选采用冲突**：候选继续保留，刷新 adoptedVersionId 后要求重新确认。

## 视觉规范

- 沿用现有 `GlassCard`、`NeonChip`、Button、Dialog、Sheet 和 Table，不新增平行组件体系。
- 主操作继续使用智能蓝/现有主按钮；Assistant 状态使用知识紫或中性色，不与危险操作颜色混用。
- PROCESSING 使用轻量旋转或脉冲图标；`prefers-reduced-motion` 下停止循环动效，仅保留文字状态。
- AWAITING_CONFIRMATION 使用 warning/amber，BLOCKED_BY_POLICY 和失败使用 danger/rose，完成使用 success/emerald。
- 状态变化动效不超过 300ms，不移动主要按钮位置，避免处理中布局跳动。
- 按钮异步文案预留宽度：`批量生成` → `正在提交…` → `Assistant 处理中…`。

## 响应式设计

### 桌面端

- 继续使用工作区 + Assistant 面板双栏；Assistant 状态条固定在 ContentActionBar 内。
- 对象行直接显示“生成图像”“重新编辑”“详情”，低频结构操作收入更多菜单亦可。

### 平板端

- Assistant 使用右侧抽屉；点击状态条或“查看任务”打开抽屉。
- ContentActionBar 的范围摘要换行显示，主按钮保持右对齐。

### 移动端

- Assistant 面板全屏；返回项目页后保留 focusedObject 和 current Task。
- 对象行保留主要动作“生成图像/查看候选”，其余进入“更多”。
- 底部状态栏只显示 Task 状态、Run、候选和实际积分，点击展开完整摘要。

## 无障碍

- Assistant 状态区域使用 `role="status"` 和 `aria-live="polite"`；阻断性错误使用 `role="alert"`。
- 禁用按钮必须有相邻可见原因，并通过 `aria-describedby` 关联；不能只依赖 tooltip。
- readiness 同时提供文字、图标和颜色，颜色对比符合现有设计系统。
- 图标按钮的 aria-label 包含对象名，例如“为封面图生成图像”“重新编辑封面图”。
- Dialog 打开后焦点进入标题或首个输入；关闭后返回原触发按钮。
- 结构和图谱视图通过键盘提供同等操作，图谱不是唯一入口。
- 状态轮询不得重复播报相同 aria-live 文案。

## 组件改动映射

| 现有组件 | 增量改动 |
|---|---|
| `NewProjectLauncher` | 增加 CATALOG/ASSISTANT_GENERATED 来源选择、结构规划和提案确认 |
| `ProjectWorkbenchHeader` | 增加 current Assistant Task 状态 Chip、定时任务按钮和查看入口 |
| 项目 Schedule Sheet | 复用现有 Sheet 与表单组件，管理一次性提示词时间点 |
| 现有 Assistant/AG-UI 面板 | 展示“定时触发”Task 卡片并读取 canonical Task 状态 |
| `ContentActionBar` | 项目级主按钮改为 Assistant Task；增加范围摘要、状态条和禁用原因 |
| `ProjectStructureView` | 明确“生成图像/重新编辑/添加内容”，增加 readiness 展示 |
| `ProjectGraphView` | 与结构视图共享同一 action model 和禁用原因 |
| `ProjectObjectGenerationDialog` | 支持生成/重新生成、来源版本和不覆盖 adopted 提示 |
| `ProjectObjectStructureDialog` | 支持添加内容/添加下级，选项来自服务端 allowedActions |
| `ContentCanvasSheet` | 保存重新编辑结果为新 candidate |
| `ObjectDetailPanel` | 展示 readiness、语义字段、候选操作和“重新生成/重新编辑”入口 |
| `AigcEventConsumer` | 全局事件 SSE 只失效服务端 Query，不推进 Task |
| 项目底部状态栏 | 改读 ProjectProgressSnapshot 和实际积分摘要 |

## UI 状态数据合同

页面至少需要：

```text
ProjectProgressSnapshot
├─ currentTask[id, status, completedNodes, totalNodes, pendingConfirmations]
├─ readiness[objectId, state, blockedReason, allowedActions]
├─ activeExecutionRefs
├─ pendingCandidatesAndDecisions
├─ actualCreditSummary[estimated, reserved, settled, refunded, netActual]
├─ schedules[id, executeAt, timezone, status, linkedTaskId, lastError]
└─ projectionVersion / updatedAt
```

前端可以保存：

- 当前视图、focusedObjectId；
- Dialog/Sheet 开关；
- 未提交 Prompt、附件和表单；
- SUBMITTING 本地瞬时状态；
- 未提交的 schedule 提示词和时间点表单。

前端不得保存为独立真理源：

- current Task 是否活跃；
- ObjectReadiness；
- Run terminal；
- candidate/adopted；
- 实际积分；
- Assistant 是否完成。

## UI 验收标准

```gherkin
Scenario: UI-AC-01 无目录 Blueprint 时使用 Assistant 规划结构
Given 用户选择的项目类型没有已发布 Blueprint
When 用户进入项目模板区域
Then 页面自动选择“由 Assistant 规划”
And 页面不再因没有目录 Blueprint 阻断创建流程
When 用户提交结构规划并确认 proposal
Then 系统创建 ASSISTANT_GENERATED BlueprintSnapshot 并进入统一项目工作台
```

```gherkin
Scenario: UI-AC-02 项目级批量生成调用现有 Assistant
Given 项目可写且没有未终结 current Task
When 用户填写目标和范围并点击“批量生成”
Then UI 提交结构化 Assistant Task 请求
And 请求包含 projectId、objective 和 explicitScope
And 成功后显示稳定 Task 引用并打开现有 Assistant 面板
```

```gherkin
Scenario: UI-AC-03 Assistant 处理中禁用批量生成
Given ProjectProgressSnapshot 表示 current Task 正在 PROCESSING
When 用户查看 ContentActionBar
Then “批量生成”按钮禁用并显示“Assistant 处理中…”
And 相邻状态条显示当前进度与“查看任务”
And 用户不能通过重复点击创建竞争 Task
```

```gherkin
Scenario: UI-AC-04 等待、恢复、确认和暂停期间保持单任务
Given current Task 状态为 AWAITING_EXTERNAL、RECOVERING、AWAITING_CONFIRMATION 或 PAUSED
When 用户查看项目级批量入口
Then 批量生成按钮保持禁用
And 页面分别提供查看任务、确认、恢复、修订或取消原 Task 的入口
```

```gherkin
Scenario: UI-AC-05 对象级图像生成复用现有工作区
Given 图像对象 ObjectReadiness 为 READY
And allowedActions 包含图像生成
When 用户点击“生成图像”
Then 页面打开 ProjectObjectGenerationDialog
And 复用 MediaGenerationWorkspace 提交专业媒体执行
And 完成后显示新 candidate 而不自动替换 adopted
```

```gherkin
Scenario: UI-AC-06 active Run 和 pending candidate 阻止默认重复生成
Given 对象 readiness 为 ACTIVE_RUN 或 PENDING_CANDIDATE
When 用户查看结构或图谱视图
Then 生成按钮分别显示“生成中”或“查看候选”
And UI 不再次提交默认生成
```

```gherkin
Scenario: UI-AC-07 重新生成保留原版本
Given 对象已有 candidate 或 adopted 版本
When 用户选择“重新生成”并提交
Then Dialog 显示来源版本和新候选提示
And 系统创建新 Run 与 candidate
And 原版本和 adopted 指针保持不变
```

```gherkin
Scenario: UI-AC-08 重新编辑保存新候选
Given 用户打开文本、文档或图像对象的“重新编辑”
When 用户修改并保存
Then 系统创建对应类型的新 candidate/version
And 当前 adopted 版本在明确采用前保持不变
```

```gherkin
Scenario: UI-AC-09 Assistant 处理中添加对象不隐式扩展计划
Given current Task 正在处理已确认范围
And 服务端允许用户添加对象
When 用户通过“添加内容”创建新对象
Then 项目产生 ProjectRevision 并刷新 ProjectGraph
And 新对象不自动进入 current TaskPlan
And UI 提示“已添加，未纳入当前任务”
```

```gherkin
Scenario: UI-AC-10 断线后恢复权威任务状态
Given current Task 正在 AWAITING_EXTERNAL
When 用户关闭页面后重新进入项目
Then 页面从 ProjectProgressSnapshot 恢复 Task、readiness、Run、candidate 和待确认状态
And 批量生成仍保持禁用
And 页面不依赖原 AG-UI 或 SSE 连接恢复业务事实
```

```gherkin
Scenario: UI-AC-11 状态栏展示实际成本
Given 项目存在 estimated、reserved、settled 和 refunded 数据
When 用户查看状态栏和成本详情
Then 状态栏实际积分等于 settled 减 refunded
And 成本详情分别展示四类值
And UI 不把 costUsed 当作独立结算真理源
```

```gherkin
Scenario: UI-AC-12 禁用原因可被辅助技术理解
Given 批量生成因 current Task 活跃而禁用
When 键盘或屏幕阅读器用户浏览 ContentActionBar
Then 按钮关联一个可见禁用原因
And 状态变化通过不重复的 aria-live 文案播报
And 用户无需依赖颜色或鼠标悬浮理解状态
```

```gherkin
Scenario: UI-AC-13 设置一个或多个一次性时间点
Given 用户在可写项目打开定时任务 Sheet
When 用户填写提示词、一个或多个未来时间点和 IANA 时区并保存
Then Sheet 为每个时间点显示一条独立的已计划记录
And 页面不要求用户填写 cron、内部动作类型或并发策略
And 用户可在触发前编辑、暂停、恢复或取消记录
```

```gherkin
Scenario: UI-AC-14 定时触发后项目页与对话显示同一 Task
Given 一条 schedule 到点且项目没有未终结 current Task
When 系统创建 canonical Assistant Task
Then schedule 显示已触发和稳定 Task 引用
And 绑定的现有 Assistant 对话显示“定时触发”Task 卡片
And 项目头部、状态条、底部状态栏与对话卡片显示同一 canonical Task 状态
And Task 待确认时两处都提供既有查看并确认入口
```

```gherkin
Scenario: UI-AC-15 项目忙时显示延后且不创建第二 Task
Given schedule 到点时项目已有未终结 current Task
When 用户查看项目页或定时任务 Sheet
Then schedule 显示“等待当前任务完成”及当前 Task 入口
And 对话中不出现第二个定时 Task
When 项目空闲且 schedule 成功触发
Then Sheet 刷新为已触发并显示新 canonical Task
And 断线或重开页面不会丢失该状态
```

## 非目标

- 重做 Content Studio 项目详情页信息架构；
- 新建项目专属 Assistant 面板、任务系统或媒体工作台；
- 在前端自行计算 readiness、inputVersionIds、Task 终态或实际积分；
- Assistant 活跃时将整个项目页面一律锁死；
- 生成后自动采用 candidate；
- 在 UI 中实现复杂 Blueprint 可视化编辑器；
- 通过装饰性动效替代明确状态文字；
- 项目 cron/周期编辑器、定时确定性动作、立即执行、跳过/替换策略和 schedule 专属对话系统。
