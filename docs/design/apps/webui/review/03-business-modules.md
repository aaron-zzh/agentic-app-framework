# 业务功能模块审查报告

审查范围：aigc / flow-editor / rich-text-editor / page-engine / page-editor / entity-editor / knowledge / settings / dashboard / stats / ai-assist
审查时间：2026-05-30
审查者：AI/architect

---

## 模块：aigc

### 问题

- [major] ~~`AssetLibrary.tsx:131` — `JSON.parse(asset.generationParams)` 未做 try-catch 保护。若后端返回非法 JSON 字符串，整个卡片渲染崩溃。~~ ✅ 已修复（提取 `safeJsonParse` 工具函数）
  - ~~修复建议：用 try-catch 包裹或提取为安全解析工具函数。~~

- [major] ~~`AssetLibrary.tsx:237` — `handleDelete` 使用 `confirm()` 原生弹窗，不符合 UI 一致性且阻塞主线程。~~ ✅ 已修复（替换为 AlertDialog）
  - ~~修复建议：替换为项目已有的 Dialog 确认组件。~~

- [minor] `types.ts:63-72` — 存在 `MediaAsset`（旧类型）和 `MediaAssetVO`（新类型）两套并行类型定义，注释标注"保留兼容"。违反硬约束 5（不加兼容层）。
  - 修复建议：统一为 `MediaAssetVO`，全模块迁移后删除旧 `MediaAsset`。

- [minor] `store.ts` — Zustand store 管理的是纯 UI 状态（面板开关、prompt 文本等），符合规范。但 `model`/`resolution`/`aspectRatio` 默认值硬编码在 store 中，应提取为常量。
  - 修复建议：提取为 `const DEFAULT_MODEL = "GPT Image 2"` 等常量。

- [minor] `VideoGenerationChat.tsx` / `VideoTimeline.tsx` / `StoryboardPanel.tsx` — 组件内硬编码 MOCK 数据（`MOCK_SCENES`、`MOCK_ASSETS`、`MOCK_ELEMENTS`），生产代码中不应保留。
  - 修复建议：移至 `__mocks__/` 或 Storybook stories，组件 props 设为必填。

- [minor] `GenerationHistory.tsx:62` — `useGenerationHistory("1", ...)` 硬编码用户 ID `"1"`。
  - 修复建议：从认证上下文获取当前用户 ID。

- [minor] `StyleAdjustDialog.tsx:56` — `DialogTrigger` 使用 `render` prop 传入 `<span>{trigger}</span>`，非标准用法，可能导致无障碍问题。
  - 修复建议：使用 `asChild` 模式或直接传 children。

### 建议

1. 统一 `MediaAsset` 和 `MediaAssetVO` 类型，消除双类型并存。
2. 所有 `JSON.parse` 调用加安全保护。
3. Mock 数据从生产组件中移除。

---

## 模块：flow-editor

### 问题

- [major] ~~`use-flow-state.ts:87-107` — `undo`/`redo` 实现中使用 `as unknown as` 双重类型断言在 `HistoryEntry` 和 `Node[]`/`Edge[]` 之间转换。这掩盖了类型不匹配问题，运行时可能出错。~~ ✅ 已修复（HistoryEntry 改用 Node[]/Edge[] 类型）
  - ~~修复建议：让 `HistoryEntry` 直接使用 `Node[]` 和 `Edge[]` 类型，消除断言。~~

- [major] ~~`use-workflow-runtime.ts:120-155` — `startWorkflow` 中 `fetch` 的 `.then()` 回调未处理 reject 情况（网络错误等），且 `while(true)` 循环中无超时保护。~~ ✅ 已修复（添加 .catch + AbortController 5 分钟超时）
  - ~~修复建议：添加 `.catch()` 处理网络错误并更新状态为 `failed`；考虑添加 AbortController 超时机制。~~

- [major] `flow-editor.tsx:30-35` — `useEffect` 中直接调用 `useFlowState.getState()` 并循环调用 `updateNodeData`，每次调用触发 store 更新，可能导致 N 次重渲染。
  - 修复建议：批量更新节点数据，或使用 `set` 一次性更新所有节点。

- [major] `flow-editor.tsx:24-28` — `onChange` 的 `useEffect` 依赖了 `toDefinition` 函数引用，但 `toDefinition` 每次 render 都是新引用（来自 Zustand），会导致无限循环或频繁触发。
  - 修复建议：使用 `useRef` 缓存 `onChange`，或在 `useEffect` 中比较前后 definition 是否变化。

- [minor] `vertical-designer.tsx:15` — 文件 450+ 行，包含 `VerticalDesigner`、`NodeCard`、`ConditionBranches`、`NodeChain`、`AddButton`、`ConfigContent` 等多个组件。
  - 修复建议：将 `NodeCard`、`ConditionBranches`、`AddButton` 拆分为独立文件。

- [minor] `lib/registry.ts:52-60` — 使用 `export let` 可变变量（`approvalRegistry`、`workflowRegistry`、`chatbotRegistry`），违反函数式编程原则，可能导致模块加载顺序问题。
  - 修复建议：改为 Map 或 lazy getter 模式。

- [minor] `lib/bpmn-converter.ts:72-95` — `bpmnToFlow` 使用正则解析 XML，对复杂 BPMN 文件不可靠（属性顺序、多行、CDATA 等）。
  - 修复建议：使用 DOMParser 或轻量 XML 解析库。

- [minor] `nodes/workflow/index.tsx` — 单文件 17000+ 字节，包含所有工作流节点类型定义和 Inspector 组件。
  - 修复建议：按节点类型拆分为独立文件。

### 建议

1. 修复 `useEffect` 无限循环风险（flow-editor.tsx）。
2. 消除 `as unknown as` 类型断言。
3. 大文件拆分（vertical-designer、workflow nodes）。

---

## 模块：rich-text-editor

### 问题

- [major] ~~`AIWritePlugin.tsx:62` — `editor.registerCommand` 在组件函数体中直接调用（非 useEffect），每次渲染都会重复注册命令监听器，导致内存泄漏。~~ ✅ 已修复（移入 useEffect + cleanup）
  - ~~修复建议：将 `registerCommand` 移入 `useEffect` 并返回 cleanup 函数。~~

- [minor] `collaborative-editor.tsx:72` — `providerRef` 使用 `Map` 缓存 provider 实例，但组件卸载时未清理（未 disconnect WebSocket）。
  - 修复建议：在 `useEffect` cleanup 中遍历 `providerRef.current` 并调用 `destroy()`。

- [minor] `RichTextEditor.tsx:56` — `onError` 回调为空函数 `(_err: Error) => {}`，静默吞掉 Lexical 错误。
  - 修复建议：至少 `console.error` 或接入错误上报。

- [minor] `converters/markdown.ts` / `converters/html.ts` — 文件较小（~1000 字节），转换逻辑简单，但缺少对空输入和异常输入的边界处理。
  - 修复建议：添加空字符串和 malformed HTML 的防御性检查。

### 建议

1. 修复 `registerCommand` 内存泄漏（blocker 级别的运行时问题）。
2. 协同编辑器 WebSocket 连接需要在卸载时清理。

---

## 模块：page-engine

### 问题

- [minor] `PageEngine.tsx` — `SectionErrorBoundary` 使用 class 组件（已有 biome-ignore 注释），符合 React 要求。无问题。

- [minor] `sections/NavbarSection.tsx` 等 Section 组件 — 各 Section 组件接收 `data: Record<string, unknown>` 类型的 props，缺少具体类型定义。
  - 修复建议：为每个 Section 定义 Props 接口（如 `HeroSectionProps`），提升类型安全。

- [minor] `registry.ts:20` — `getSectionComponent` 返回值可能为 `undefined`，调用方已做 null check，但缺少 TypeScript 返回类型标注。
  - 修复建议：显式标注返回类型 `SectionEntry | undefined`。

### 建议

1. 为各 Section 组件定义具体 Props 类型，替代 `Record<string, unknown>`。

---

## 模块：page-editor

### 问题

- [minor] `PageEditorView.tsx:100` — `AddSectionDialog` 中 `DialogTrigger` 使用 `render` prop，同 aigc 模块的 `StyleAdjustDialog` 问题一致。
  - 修复建议：统一使用 `asChild` 模式。

- [minor] `SectionPropsPanel.tsx` — 属性面板根据 section type 动态渲染表单，但未对未知 type 做 fallback 处理。
  - 修复建议：添加默认 fallback UI。

### 建议

无重大问题，模块结构清晰。

---

## 模块：entity-editor

### 问题

- [major] `AIEntityDefGenerator.tsx:100-140` — 流式 SSE 解析逻辑与 `flow-editor/use-workflow-runtime.ts` 高度重复（相同的 `while(true)` + `reader.read()` + `data:` 行解析模式）。违反 No-Duplication 规则。
  - 修复建议：提取为共享的 `streamSSE` 工具函数到 `@/lib/utils/` 或 `packages/`。

- [minor] `AIEntityDefGenerator.tsx:30-50` — `SYSTEM_PROMPT` 常量硬编码在组件文件中（50+ 行），影响可读性。
  - 修复建议：移至独立的 `prompts.ts` 文件。

- [minor] `entity-def-schema.ts` — JSON Schema 定义完整，但与 `entity-engine/types/` 中的 TypeScript 类型手动同步，存在不一致风险。
  - 修复建议：考虑从 TypeScript 类型自动生成 JSON Schema（如 `ts-json-schema-generator`）。

- [minor] `AddCustomFieldDialog.tsx:85` — `options` state 使用 `FieldOption[]` 但 key 用 `${opt.value}-${i}` 拼接 index，删除中间项时可能导致 React key 冲突。
  - 修复建议：为每个 option 生成唯一 ID。

### 建议

1. SSE 流式解析逻辑应提取为共享工具。
2. 长 prompt 字符串移出组件文件。

---

## 模块：knowledge

### 问题

- [minor] `DocumentUpload.tsx` — 文件上传缺少文件大小校验（仅通过 `accept` 限制类型）。大文件可能导致内存问题。
  - 修复建议：添加 `maxSize` 校验（如 50MB），超限时提示用户。

- [minor] `SearchTestPanel.tsx:35` — `useMutation` 的 `mutationFn` 闭包捕获了 `query`/`topK`/`threshold`/`mode` 状态，但 mutation key 未包含这些参数，可能导致缓存行为不符预期。
  - 修复建议：这是搜索操作，使用 mutation 是合理的（非幂等查询），但建议添加 `mutationKey` 以便调试。

- [minor] `KnowledgeGraph.tsx:28-33` — 节点位置使用简单的网格布局 `(i % 5) * 200`，对于真实图谱数据效果差。
  - 修复建议：使用力导向布局算法（如 `dagre` 或 ReactFlow 的 auto-layout）。

### 建议

1. 添加文件大小校验。
2. 图谱布局算法优化。

---

## 模块：settings

### 问题

- [major] `api-keys/ApiKeyList.tsx:35` — `handleDelete` 使用 `window.confirm()` 且直接调用 `request()` 删除 API Key，无乐观更新也无错误处理。删除失败时用户无感知。
  - 修复建议：使用 `useMutation` + toast 错误提示 + Dialog 确认。

- [minor] `api-keys/ApiKeyList.tsx:20` — `useQuery` 直接调用 `request<ApiKeyVO[]>("/v1/api-keys")`，未使用项目统一的 query hook 模式（其他模块都有 `use-xxx` hook 封装）。
  - 修复建议：提取为 `useApiKeys()` hook 到 `@/lib/queries/`。

### 建议

1. 删除操作需要错误处理和用户反馈。
2. 统一使用 query hook 封装模式。

---

## 模块：dashboard

### 问题

- [minor] `DashboardView.tsx` — 文件 250+ 行，`renderWidget` 函数使用 switch-case 分发，新增 widget 类型需修改此文件。
  - 修复建议：使用注册表模式（类似 flow-editor 的 nodeRegistry），widget 类型自注册。

- [minor] `DashboardView.tsx:85` — `createDefaultWidget` 中 `id` 使用 `Date.now()`，并发添加多个 widget 时可能重复。
  - 修复建议：使用 `crypto.randomUUID()`。

- [minor] `widgets/EChartsWidget.tsx` — ECharts 实例在组件内创建，但未在 `useEffect` cleanup 中 `dispose()`。
  - 修复建议：确认是否依赖 `BaseChart` 组件（BaseChart 已正确处理 dispose）。如果是，则无问题。

### 建议

1. 考虑 widget 注册表模式提升可扩展性。
2. ID 生成使用 `crypto.randomUUID()`。

---

## 模块：stats

### 问题

- [minor] `charts/BaseChart.tsx` — ECharts 按需引入模式正确，`ResizeObserver` 清理正确。无重大问题。

- [minor] `charts/RetentionChart.tsx` / `TrendChart.tsx` / `PieChart.tsx` / `FunnelChart.tsx` — 各图表组件较小（~1500 字节），结构清晰。但 `option` 构建逻辑直接写在组件内，复杂图表场景可能需要提取。
  - 修复建议：当前规模可接受，后续复杂化时提取为 `buildXxxOption` 工具函数。

### 建议

模块结构良好，无需立即修改。

---

## 模块：ai-assist

### 问题

- [minor] `AISuggestionInline.tsx:30-37` — `document.addEventListener("keydown", ...)` 全局监听键盘事件，可能与其他组件的快捷键冲突（如 flow-editor 的 Delete/Ctrl+Z）。
  - 修复建议：限制监听范围到最近的输入框容器，或检查 `e.target` 是否为关联输入框。

- [minor] `AIActionBubble.tsx:48` — 自动消失定时器 5 秒，但用户鼠标 hover 时不暂停计时，可能导致用户正在阅读时气泡消失。
  - 修复建议：添加 `onMouseEnter` 暂停计时、`onMouseLeave` 恢复计时。

### 建议

1. 全局键盘监听需要作用域限制。
2. 气泡 hover 暂停是常见 UX 模式，建议实现。

---

## 汇总

| 模块 | blocker | major | minor |
|------|---------|-------|-------|
| aigc | 0 | 2 | 5 |
| flow-editor | 0 | 4 | 4 |
| rich-text-editor | 0 | 1 | 3 |
| page-engine | 0 | 0 | 3 |
| page-editor | 0 | 0 | 2 |
| entity-editor | 0 | 1 | 3 |
| knowledge | 0 | 0 | 3 |
| settings | 0 | 1 | 1 |
| dashboard | 0 | 0 | 3 |
| stats | 0 | 0 | 1 |
| ai-assist | 0 | 0 | 2 |
| **合计** | **0** | **9** | **30** |

## 对称性检查

| # | 检查项 | 结果 |
|---|--------|------|
| 1 | 生产者 vs 消费者 | ⚠️ `use-workflow-runtime` SSE 连接无心跳/重连机制 |
| 2 | 创建 vs 删除 | ⚠️ `settings/ApiKeyList` 删除无错误处理 |
| 3 | 加密 vs 解密 | ✅ 无相关场景 |
| 4 | 事务开启 vs 提交/回滚 | ✅ 前端无事务 |
| 5 | 监听器注册 vs 注销 | ⚠️ `AIWritePlugin` registerCommand 无 cleanup；`collaborative-editor` WebSocket 无 cleanup |
| 6 | 资源申请 vs 释放 | ⚠️ `use-workflow-runtime` EventSource 引用未在所有路径关闭 |
| 7 | 状态变更 vs 通知 | ✅ Zustand store 变更自动触发 React 重渲染 |
| 8 | 认证 vs 鉴权 | ✅ API 调用通过统一 `request` 函数，鉴权由后端处理 |
| 9 | 成功路径 vs 错误路径 | ⚠️ 多处 fetch 调用缺少错误路径处理 |
| 10 | 入队 vs 出队 | ✅ 无消息队列场景 |
| 11 | 缓存写入 vs 失效 | ✅ TanStack Query 的 `invalidateQueries` 使用正确 |
| 12 | 前端乐观更新 vs 回滚 | ✅ 未使用乐观更新模式 |
| 13 | 已有模式 vs 新建抽象 | ⚠️ SSE 流式解析在 3 处重复实现（workflow-runtime、AIEntityDefGenerator、AIWritePlugin） |

## 跨模块重复问题

| 重复逻辑 | 出现位置 | 建议 |
|----------|---------|------|
| SSE 流式读取 + data: 行解析 | `flow-editor/use-workflow-runtime.ts`、`entity-editor/AIEntityDefGenerator.tsx`、`rich-text-editor/plugins/AIWritePlugin.tsx` | 提取为 `@/lib/utils/stream-sse.ts` 共享工具 |
| `DialogTrigger render` 非标准用法 | `aigc/StyleAdjustDialog.tsx`、`page-editor/PageEditorView.tsx`、`entity-editor/CustomFieldManager.tsx` | 统一为 `asChild` 模式 |
| `window.confirm()` 原生弹窗 | `aigc/AssetLibrary.tsx`、`settings/ApiKeyList.tsx` | 统一使用 AlertDialog 组件 |
| Mock 数据硬编码在组件中 | `aigc/VideoTimeline.tsx`、`aigc/VideoStoryboard.tsx`、`aigc/StoryboardPanel.tsx`、`aigc/VideoGenerationChat.tsx` | 移至 Storybook 或 `__fixtures__/` |

## 总结

**通过条件**：blocker = 0，major = 9（> 2，**未通过质量门控**）

**需优先修复的 major 问题**：

1. `flow-editor/flow-editor.tsx` — useEffect 无限循环风险
2. `flow-editor/use-workflow-runtime.ts` — fetch 无错误处理
3. `flow-editor/use-flow-state.ts` — 双重类型断言
4. `flow-editor/flow-editor.tsx` — 批量 updateNodeData 性能问题
5. `rich-text-editor/AIWritePlugin.tsx` — registerCommand 内存泄漏
6. `aigc/AssetLibrary.tsx` — JSON.parse 无保护
7. `aigc/AssetLibrary.tsx` — confirm() 阻塞
8. `entity-editor/AIEntityDefGenerator.tsx` — SSE 逻辑重复（No-Duplication）
9. `settings/ApiKeyList.tsx` — 删除操作无错误处理

**结论**：需修改后通过。退回 developer 修复上述 9 个 major 问题后重新提交审查。
