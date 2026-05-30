# 核心功能模块审查报告

审查范围：entity-engine / chatter / agui / livechat
审查时间：2026-05-30
审查者：AI/architect

---

## 模块：entity-engine

### entity-engine/lib/

#### 问题

- [minor] `lib/component-registry.ts`:24,26 — 使用 `// biome-ignore lint/suspicious/noExplicitAny` 绕过 any 检查。虽有注释说明理由，但可用泛型约束替代（如 `ComponentType<FieldProps<unknown>>`）
- [minor] `lib/formula-engine.ts`:72 — `findOperator` 从右向左扫描查找加减运算符，对于 `1-2-3` 这类表达式会错误地先计算 `2-3`，导致结果为 `1-(-1)=2` 而非 `-4`。右结合语义不符合数学惯例
- [major] `lib/formula-engine.ts`:20 — `IF` 函数签名为 `(...args: number[]) => number`，但 IF 语义需要三个参数且 cond 应为布尔判断。当前实现 `IF(0, t, f)` 返回 f 是正确的，但 `IF(cond, t, f)` 中 cond 只能是数字，无法表达 `$record.status == 'active'` 这类条件
- [minor] `lib/build-zod-schema.ts`:55 — `select` 分支中 `field.options.length > 0` 后直接 `as [string, ...string[]]` 类型断言，若 options 运行时为空数组（动态加载场景）会导致 `z.enum([])` 运行时错误
- [minor] `lib/build-columns.tsx`:20 — `Number.parseInt(String(col.width), 10)` 对 width 做了 String 转换再 parseInt，若 width 已是 number 类型则多余；若是 `"100px"` 则 parseInt 会截断为 100，行为隐式

#### 建议

- formula-engine 的运算符解析应改为从左到右扫描（左结合），或明确文档说明右结合语义
- IF 函数建议拆分为独立的条件求值逻辑，支持字符串比较
- build-zod-schema 的 select 分支增加 `options.length === 0` 的 fallback

### entity-engine/types/

#### 问题

- [minor] `types/index.ts`:4 — 仅重导出 `@/lib/types/entity`，注释说"保持向后兼容"。项目未发布 v1.0，按 AGENTS.md 规范"禁兼容层"，应直接让消费方 import 真实路径

#### 建议

- 评估是否可删除此重导出层，让所有消费方直接 import `@/lib/types/entity`

### entity-engine/entities/

#### 问题

- [minor] `entities/index.ts`:130-131 — 模块级 side effect（`entityRegistry.registerAll`）在文件底部执行。import 顺序依赖可能导致注册时机问题，且不利于 tree-shaking
- [minor] `entities/billing-entities.ts` — 纯静态配置文件，无问题

#### 建议

- 将 side-effect 注册逻辑移到显式初始化函数中，由应用入口调用

### entity-engine/hooks/

#### 问题

- [minor] `hooks/use-conditional-fields.ts`:27 — `useWatch()` 无参数调用会监听整个表单所有字段变化，任何字段变更都会触发 useMemo 重算。对于大表单（>20 字段）可能造成性能问题

#### 建议

- 考虑只 watch 条件表达式中引用的字段名，减少不必要的重渲染

### entity-engine/components/

#### 问题

- [major] `components/EntityApproval.tsx`:131 — `StartApprovalButton` 参数中 `_currentUserId` 以下划线前缀标记未使用，但 props 接口定义了 `currentUserId`。实际未传递当前用户到发起审批请求中（`assignee: ""`），后端可能无法正确关联发起人
- ✅ 已修复 [major] `components/EntityApproval.tsx`:143 — `window.location.reload()` 强制刷新页面来更新状态，应使用 `queryClient.invalidateQueries` 实现无刷新更新
> 已修复｜2026-05-30｜提交：apps/webui/src/features/entity-engine/components/EntityApproval.tsx
- [minor] `components/EntityApproval.tsx`:62 — `useEntityWorkflowStatus` 中 fetch 失败时返回 `{ processInstanceId: "", status: "none" }`，但未区分网络错误和"确实无流程"的情况
- ✅ 已修复 [major] `components/EntityActions.tsx`:72 — catch 块为空（`catch {}`），错误被静默吞掉，用户无法感知操作失败。注释 `// TODO: Toast 通知` 表明这是已知未完成项
> 已修复｜2026-05-30｜提交：apps/webui/src/features/entity-engine/components/EntityActions.tsx
- [minor] `components/ViewEngine.tsx`:79-80 — `ConnectedListView` 和 `ConnectedFormView` 作为内部组件定义在同一文件中，每次 ViewEngine 重渲染都会重新创建这些组件引用（虽然 React 会通过 key 优化）
- [minor] `components/cells/index.tsx`:72 — `UploadCell` 中 `value` 参数类型为 `CellProps<unknown>`，内部通过 `typeof value === "object"` 和 `Array.isArray` 做运行时类型判断，缺少类型守卫
- [major] `components/form/FormView.tsx`:12 — 顶层调用 `registerDefaultComponents()` 作为模块 side effect。虽然注释说"幂等"，但这违反了组件纯函数原则，且在 SSR 环境下可能有问题
- [minor] `components/form/FormView.tsx`:167 — `FieldRenderer` 中 `labelLayout === "left"` 分支创建了 `fieldWithoutLabel = { ...field, label: undefined }`，每次渲染都创建新对象
- ✅ 已修复 [major] `components/kanban/KanbanView.tsx`:95 — `if (data !== localData && !activeId) { setLocalData(data) }` 在渲染期间调用 setState，这是 React 反模式，可能导致无限渲染循环或额外渲染
> 已修复｜2026-05-30｜提交：apps/webui/src/features/entity-engine/components/kanban/KanbanView.tsx
- [minor] `components/kanban/components/KanbanCard.tsx`:52 — `onKeyDown={undefined}` 显式设置为 undefined，但 role="button" 的元素应支持 Enter/Space 键盘交互（a11y）
- [minor] `components/list/ListView.tsx`:10 — 顶层调用 `registerDefaultComponents()`，与 FormView 相同的 side-effect 问题
- [minor] `components/list/components/DataTable.tsx`:72 — `useBoolean` 从 `@aaf/hooks` 导入，这是 packages 层的共享 hook，依赖方向正确

#### 建议

- EntityApproval 的 `window.location.reload()` 替换为 queryClient invalidation
- EntityActions 的空 catch 块添加 toast.error 通知
- KanbanView 的渲染期 setState 改为 useEffect 同步
- registerDefaultComponents 改为在 app 入口显式调用一次，不在组件文件中 side-effect 执行


---

## 模块：chatter

### chatter/ (根文件)

#### 问题

- [minor] `Chatter.tsx`:8 — import `ChatterLayout` 与本地类型 `ChatterLayout`（从 types.ts）同名，虽然一个是组件一个是类型，但容易混淆。实际代码中组件 import 名为 `ChatterLayout`，类型在 types.ts 中也叫 `ChatterLayout`
- [minor] `ChatterRuntime.tsx`:89-90 — `threadList.onSwitchToThread` 中 catch 块返回 `{ messages: [] }`，静默吞掉了加载历史消息的错误，用户无法感知历史加载失败
- [minor] `ChatterComposer.tsx`:42 — `handleServerStt` 中 catch 只 console.error，未向用户反馈 STT 失败
- [minor] `ChatterThread.tsx`:17 — `useMessageText` 中 `message.content.filter((p) => p.type === "text")` 假设 content 始终是数组，若 runtime 返回非数组 content 会崩溃
- [major] `TaskExecutionTimeline.tsx`:95 — SSE EventSource 的 `onerror` 只调用 `source.close()`，不尝试重连。网络抖动会导致实时更新永久中断，用户需手动刷新
- [minor] `TaskExecutionTimeline.tsx`:80 — 历史事件加载的 fetch 无错误处理（`.catch(() => {})`），加载失败时 items 保持空数组，用户无法区分"无事件"和"加载失败"
- [minor] `ChatterToolbar.tsx`:52 — `onValueChange` 中 `value.find((v) => v !== target.type)` 逻辑：当用户点击已选中的 toggle 时 value 为空数组，`find` 返回 undefined，fallback 到 `target.type`，行为正确但逻辑不直观

#### 建议

- TaskExecutionTimeline 的 SSE 应实现指数退避重连机制
- ChatterRuntime 的 onSwitchToThread 失败时应 toast 提示用户

### chatter/dnd/

#### 问题

- [minor] `useSemanticDraggable.ts`:30 — `truncate` 函数对 `item.title ?? item.content ?? ""` 做截断，但 ChatterDropItem 的 title/content 都是 optional string，若都为 undefined 则 summary 为空字符串，ContextChip 会显示空标签
- [minor] `DroppableComposer.tsx`:14 — `onDrop` prop 声明但未在组件内使用（实际 drop 逻辑在父级 DndContext.onDragEnd 中处理）。这是一个误导性的 prop

#### 建议

- DroppableComposer 的 `onDrop` prop 要么在组件内使用，要么从接口中移除避免混淆

### chatter/hooks/

#### 问题

- [major] `hooks/use-task-board.ts`:48 — EventSource 无认证机制。SSE 端点通过 URL 暴露 sessionId，若 sessionId 可猜测则存在信息泄露风险。应通过 cookie 或 token 认证
- [minor] `hooks/use-task-board.ts`:37 — `useEffect` 依赖数组只有 `[sessionId]`，但内部引用了 `setTasks`/`setIsLoading`/`setRecovered` 等 setState。虽然 React 保证 setState 引用稳定，但 ESLint exhaustive-deps 可能报警

#### 建议

- SSE 端点应增加认证（如在 URL 中附加 token 或通过 cookie）


---

## 模块：agui

### agui/types.ts

#### 问题

- [minor] `types.ts` — 类型定义完整且结构清晰，无问题

### agui/generation/

#### 问题

- [major] `generation/ComponentGenerator.ts`:87 — `parseIntent` 使用简单的 `includes` 关键词匹配，对于"创建一个用户列表"会同时匹配到"列表"和"创建"，可能产生歧义。entityPatterns 正则 `/(?:一个|个)?(\w+?)(?:列表|表格|表单|看板|页面|管理)/` 中 `\w` 不匹配中文字符，导致中文实体名无法提取
- [minor] `generation/ComponentGenerator.ts`:155 — `buildListConfig` 中 `fields.slice(0, 6).map((f) => f.name)` 直接取前 6 个字段作为列表列，未考虑字段是否为布局字段（group/tabs/row 无 name）
- [minor] `generation/ConversationalBuilder.tsx`:95 — `reset` 函数中 `dispatch({ type: "SET_RESULT", result: null as unknown as GenerationResult })` 使用 `as unknown as` 双重断言绕过类型检查，应将 state 类型改为 `GenerationResult | null`
- [minor] `generation/GenerationHistory.ts`:72 — 内存存储（Map + Array），页面刷新后数据丢失。作为客户端状态可接受，但应在文档中说明持久化策略
- [minor] `generation/LayoutOptimizer.ts` — 纯算法逻辑，类型安全，无问题

#### 建议

- ComponentGenerator 的 entityPatterns 正则应支持中文字符：`/(?:一个|个)?([\u4e00-\u9fa5\w]+?)(?:列表|表格|表单|看板|页面|管理)/`
- ConversationalBuilder 的 `SET_RESULT` action payload 类型改为 `GenerationResult | null`

### agui/semantics/

#### 问题

- [minor] `semantics/SemanticRegistry.ts` — 全局单例模式，线程安全（JS 单线程），类型完整。`generateViewSemantics` 中 viewConfigs 对象每次调用都重新创建，可提取为模块级常量

#### 建议

- viewConfigs 提取为模块级常量避免重复创建

### agui/intent/

#### 问题

- [minor] `intent/IntentMapper.ts`:108 — `intentHistory` 使用模块级 Map 存储，无上限控制，长时间运行可能内存增长（虽然实际场景中意图种类有限）
- [minor] `intent/IntentMapper.ts`:52 — `mapIntentToActions` 中 confidence 计算 `0.5 + patternMatch * 0.2 + historyBoost` 可能超过 1.0，虽有 `Math.min(..., 1.0)` 保护

#### 建议

- 无重大问题

### agui/tracking/

#### 问题

- [minor] `tracking/TrackingProvider.tsx`:42 — `SESSION_ID = crypto.randomUUID()` 在模块顶层执行，SSR 环境下 `crypto` 可能不可用（Node 18+ 有 `crypto.randomUUID`，但需确认）
- [minor] `tracking/TrackingProvider.tsx`:89 — `document.addEventListener("click", handleClick, true)` 使用捕获阶段监听，可能与其他库的事件处理冲突
- [minor] `tracking/TrackingProvider.tsx`:78 — `window.dispatchEvent(new CustomEvent("agui:actions", ...))` 通过全局事件广播，消费方需要自行监听。缺少类型安全的订阅机制

#### 建议

- SESSION_ID 生成应包裹在 `typeof window !== "undefined"` 检查中，或使用 lazy 初始化

### agui/analytics/

#### 问题

- [minor] `analytics/HeatmapCollector.ts` — 纯内存存储，无持久化，页面刷新丢失。设计合理（客户端分析）
- [minor] `analytics/PatternDetector.ts`:55 — `detectPatterns` 在每次 `addAction` 时执行全量滑动窗口检测（O(n*w)），1000 条记录 × 窗口 5 = 5000 次迭代，性能可接受但不够高效
- [minor] `analytics/AnomalyDetector.ts` — 逻辑正确，去重机制合理

#### 建议

- PatternDetector 可考虑增量检测（只检测新增 action 相关的窗口）而非全量重算

### agui/ai-context/

#### 问题

- [minor] `ai-context/PageSemanticsCollector.ts`:10 — 模块级可变状态（`componentInstances` Map、`componentRelations` 数组、`currentPageMeta` 对象）。多个页面组件同时挂载时可能互相覆盖
- [minor] `ai-context/usePageSemantics.ts`:52 — `setInterval(collect, refreshInterval)` 每 2 秒轮询收集语义，对于大多数场景过于频繁。应改为事件驱动（组件注册/注销时触发）

#### 建议

- 语义收集改为事件驱动 + 防抖，而非固定间隔轮询


---

## 模块：livechat

### livechat/ (根文件)

#### 问题

- [minor] `LivechatProvider.tsx`:30 — `toThreadMessage` 中使用 `as ThreadMessage` 类型断言，绕过了 assistant-ui 的完整类型检查。若 ThreadMessage 接口变更，编译不会报错
- [major] `LivechatProvider.tsx`:75 — `handleWsMessage` 中 `JSON.parse(raw)` 无 schema 校验，恶意或格式错误的 WebSocket 消息会导致 `toThreadMessage` 内部崩溃（如 `msg.role` 为 undefined）
- [minor] `LivechatProvider.tsx`:62 — `historyLoaded` ref 防止重复加载，但若 `useChatMessages` 返回新数据（如后端数据变更），不会更新本地 messages
- [minor] `ChatLayout.tsx` — 桌面端和移动端分支中大量重复的 ThreadPrimitive + ComposerPrimitive 渲染代码（约 40 行重复），应提取为共享组件
- [minor] `index.ts` — 导出清晰，barrel file 组织合理

#### 建议

- WebSocket 消息应增加 schema 校验（如 zod parse），无效消息静默丢弃而非崩溃
- ChatLayout 中重复的 Thread+Composer 渲染逻辑提取为 `ChatThread` 组件

### livechat/runtime/

#### 问题

- [minor] `runtime/ag-ui-runtime.tsx`:42 — `classifyError` 函数与 `ChatterRuntime.tsx` 中的同名函数逻辑完全重复（对称性问题：两处维护同一逻辑）
- [minor] `runtime/ag-ui-runtime.tsx`:28 — `AGENT_URL` 使用模块级常量，若 `NEXT_PUBLIC_API_URL` 在运行时变更（如多环境切换）不会更新

#### 建议

- `classifyError` 提取到 `@/lib/utils/error` 共享，消除跨模块重复

### livechat/voice/

#### 问题

- [major] `voice/SpeechInput.tsx`:50-60 — Web Speech API 类型手动声明（`SpeechRecognitionEvent` 等），未使用 `@types/dom-speech-recognition`。手动类型可能与实际 API 不一致
- [minor] `voice/SpeechInput.tsx`:108 — `startVisualizer` 创建 `new AudioContext()` 但从未关闭。若组件多次挂载/卸载会泄漏 AudioContext（浏览器限制最多 6 个）
- [major] `voice/RealtimeVoice.tsx`:107 — `checkSilence` useCallback 依赖了 `stopRecording`（标注 `noInvalidUseBeforeDeclaration`），形成循环依赖。这是一个已知的 biome lint 绕过，但实际运行时 `stopRecording` 在 `checkSilence` 调用时已定义，不会出错
- [minor] `voice/RealtimeVoice.tsx`:120 — `connectWs` 中 WebSocket 连接无重连机制，断开后不恢复
- [minor] `voice/SpeechOutput.tsx`:55 — `autoPlay` 变更时 useEffect 会重新触发播放，若 text 未变但 autoPlay 从 false→true 会意外播放
- [minor] `voice/AudioMessage.tsx`:47 — `formatDuration` 函数可提取到 `@/lib/utils/time` 共享（与其他模块的时间格式化逻辑重复）

#### 建议

- SpeechInput 中 AudioContext 应在 stopVisualizer 时 close
- RealtimeVoice 的 WebSocket 应实现重连机制
- 安装 `@types/dom-speech-recognition` 替代手动类型声明

### livechat/enhance/

#### 问题

- [minor] `enhance/InlineActions.tsx`:47 — `CreateEntityCard` 的 `onConfirm`/`onCancel` 在 ToolUI 注册中传入空函数 `() => {}`，实际操作无效果。这是占位实现，应标注 TODO
- [minor] `enhance/InlineActions.tsx` — 整体设计合理，ToolUI 注册模式正确

#### 建议

- ToolUI 中的空回调应连接到实际的 mutation 逻辑或标注 TODO

### livechat/components/

#### 问题

- [minor] `components/FileUploadArea.tsx`:73 — 上传进度模拟（硬编码 50%→100%），实际应由 XMLHttpRequest 或 fetch 的 progress 事件驱动
- [minor] `components/FileUploadArea.tsx`:56 — `validate` 函数中 MIME 类型匹配使用 `startsWith` 对 `image/*` 模式，但 `application/json` 不会匹配 `application/*`（因为 replace 后是 `application/`，startsWith 正确）

#### 建议

- 上传进度应接入真实的 progress 回调

### livechat/kiro/

#### 问题

- [minor] `kiro/KiroAgentProvider.tsx` — 结构清晰，与 ag-ui-runtime.tsx 模式一致
- [minor] `kiro/KiroAgentDrawer.tsx`:25 — `useKiroAgents` 查询无 staleTime 配置，每次组件挂载都会重新请求 agent 列表

#### 建议

- useKiroAgents 添加 `staleTime: 60_000` 避免频繁请求

---

## 汇总

| 模块 | blocker | major | minor |
|------|---------|-------|-------|
| entity-engine | 0 | 2 (5 原始, 3 已修复) | 14 |
| chatter | 0 | 2 | 8 |
| agui | 0 | 1 | 13 |
| livechat | 0 | 2 | 12 |
| **合计** | **0** | **7** (10 原始, 3 已修复) | **47** |

## 关键 Major 问题清单

| # | 模块 | 文件 | 问题 |
|---|------|------|------|
| ~~1~~ | ~~entity-engine~~ | ~~`EntityApproval.tsx:143`~~ | ~~window.location.reload() 替代 queryClient invalidation~~ ✅ 已修复 |
| 2 | entity-engine | `EntityApproval.tsx:131` | 发起审批未传递 currentUserId |
| ~~3~~ | ~~entity-engine~~ | ~~`EntityActions.tsx:72`~~ | ~~空 catch 块静默吞错误~~ ✅ 已修复 |
| 4 | entity-engine | `form/FormView.tsx:12` | 模块级 side-effect registerDefaultComponents |
| ~~5~~ | ~~entity-engine~~ | ~~`kanban/KanbanView.tsx:95`~~ | ~~渲染期间调用 setState~~ ✅ 已修复 |
| 6 | chatter | `TaskExecutionTimeline.tsx:95` | SSE 断开无重连机制 |
| 7 | chatter | `hooks/use-task-board.ts:48` | SSE 端点无认证 |
| 8 | agui | `ComponentGenerator.ts:87` | 正则 `\w` 不匹配中文实体名 |
| 9 | livechat | `LivechatProvider.tsx:75` | WebSocket 消息无 schema 校验 |
| 10 | livechat | `voice/SpeechInput.tsx:50` | 手动声明 Web Speech API 类型，可能与实际不一致 |

## 对称性检查

| # | 检查项 | 结果 |
|---|--------|------|
| 1 | 生产者 vs 消费者 | ⚠ SSE/WebSocket 连接无重连（TaskExecutionTimeline、RealtimeVoice、use-task-board） |
| 2 | 创建 vs 删除 | ✅ 组件注册/注销对称（agui PageSemanticsCollector） |
| 3 | 资源申请 vs 释放 | ⚠ AudioContext 未 close（SpeechInput）；EventSource 有 close |
| 4 | 状态变更 vs 通知 | ⚠ EntityActions 操作失败未通知用户 |
| 5 | 缓存写入 vs 失效 | ✅ EntityActions 操作后 invalidateQueries |
| 6 | 已有模式 vs 新建抽象 | ⚠ classifyError 在 ChatterRuntime 和 ag-ui-runtime 中重复实现 |

## 架构合理性总评

- **模块边界**：四个模块职责清晰，entity-engine 负责数据视图、chatter 负责对话、agui 负责 AI 语义基础设施、livechat 负责实时通信。依赖方向合理（chatter 的 dnd 被 entity-engine 引用）
- **状态管理**：服务端数据通过 TanStack Query 管理，未发现违反"服务端数据不进 Zustand"规范的情况
- **可测试性**：entity-engine/lib 下有完善的单元测试；chatter/dnd 有测试；agui 和 livechat 缺少测试覆盖
