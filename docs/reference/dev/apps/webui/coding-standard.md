---
level: Practice
layer: Model
purpose: AAF 前端组件与目录规范，开发时查阅
status: published
version: 1.1.0
date: 2026-05-17
author: AaronZZH
gains:
  - 能正确组织前端目录和组件
  - 能按规范编写组件和调用 API
---

# 前端编码规范（webui）

## 目录约定

```
app/                  Next.js App Router 页面（路由即目录）
  (auth)/             路由组，不影响 URL
  chat/
    page.tsx          页面组件（Server Component 优先）
    layout.tsx
components/           共享组件
  ui/                 shadcn/ui 原语（CLI 生成 + 自写原子组件）
  form/               表单控件（基于 ui/ 组合，value/onChange 接口）
  common/             通用非表单组件（基于 ui/ 组合）
features/
  entity-engine/
    components/
      fields/         EntityDef 驱动的字段渲染器（消费 form/ + ui/）
lib/
  api/                API 客户端（按模块分文件）
  hooks/              自定义 Hook（逻辑层）
  utils/              工具函数
```

### 组件分层依赖方向

```
components/ui/   ←   components/form/   ←   features/entity-engine/components/fields/
components/ui/   ←   components/common/
```

**各层职责**：

| 层 | 位置 | 职责 | 接口特征 |
|----|------|------|---------|
| `ui/` | `components/ui/` | shadcn 原语 + 自写原子组件，无状态，无业务语义 | 纯 props |
| `form/` | `components/form/` | 表单输入控件，可有内部 UI 状态，无业务语义 | `value / onChange / error / disabled` |
| `common/` | `components/common/` | 通用非表单组件，可有内部状态，无业务语义 | 按需 |
| `fields/` | `features/entity-engine/components/fields/` | EntityDef 驱动的字段渲染器，消费 `form/` + `ui/` | `FieldProps`（含 `FieldDef`） |

**新组件放哪——判断树**：

```
需要新组件？
├─ shadcn/ui 有 → pnpm dlx shadcn add xxx → components/ui/   ← 优先
├─ 原子级 UI，无状态，无业务语义 → components/ui/（自写）
├─ 表单输入控件（value/onChange 接口） → components/form/
├─ 通用非表单，无业务语义 → components/common/
├─ 需要 FieldDef，EntityDef 驱动 → features/entity-engine/components/fields/
└─ 有业务语义（知道具体业务概念） → features/ 或 sections/
```

## 状态管理

- TanStack Query 管服务器状态，Zustand 管客户端状态，React Context 仅用于平台管道
- 永远不把服务器数据复制到 Zustand
- WS 事件只 invalidate query，不直接写 store

## 组件三层分离

中等以上复杂度的组件必须分离为三层：

| 层 | 职责 | 位置 | 关心什么 |
|----|------|------|---------|
| **逻辑层**（hook） | 状态管理、API 调用、数据转换 | `lib/hooks/` 或 `packages/core/` | 不关心 UI 长什么样 |
| **UI 层**（组件） | 渲染、样式、布局、动画、无障碍 | `components/` | 不关心数据从哪来 |
| **业务层**（页面） | 组装逻辑 + UI，传递 props | `app/` 页面文件 | 不关心实现细节 |

```tsx
// ✅ 逻辑层：可复用、可测试、可跨端共享
function useChatMessages(agentId: string) {
  const { messages, append, stop } = useChat({ api: `/api/chat/${agentId}` });
  return { messages, send: append, stop };
}

// ✅ UI 层：纯渲染，通过 props 接收数据
function ChatPanel({ messages, onSend, onStop }: ChatPanelProps) {
  return (/* JSX */);
}

// ✅ 业务层：页面组装
export default function AgentChatPage({ params }: { params: { id: string } }) {
  const chat = useChatMessages(params.id);
  return <ChatPanel messages={chat.messages} onSend={chat.send} onStop={chat.stop} />;
}
```

**判断标准**：
- 简单组件（按钮/输入框/卡片）：不需要分离，直接写
- 中等组件（对话面板/表单/列表）：抽 hook 分离逻辑
- 复杂组件（工作流编辑器/协作面板）：必须分离，否则不可维护

**禁止**：在 UI 组件内直接调用 API、直接操作 store、包含业务判断逻辑。

**表单控件的逻辑分离规则**：

| 控件复杂度 | 是否抽 hook | 说明 |
|-----------|------------|------|
| 简单（Money/QRScanner） | ❌ | 直接写在组件内 |
| 中等（RelationshipPicker/Cascader/Signature） | ✅ | 数据逻辑抽到 `lib/hooks/use-xxx.ts` |
| 复杂（Subtable/Upload） | ✅ | 必须分离，否则不可维护 |

只拆**数据逻辑**（API 调用、状态计算、副作用）；open/close/hover 等纯 UI 交互状态留在组件内。

## 组件规范

- 页面级组件（`page.tsx`）默认 Server Component，需要交互时加 `'use client'`
- 组件文件名 PascalCase：`ChatPanel.tsx`
- 每个文件只导出一个组件

```tsx
// ✅ Server Component（默认）
export default async function ChatPage() {
  const data = await fetchData()
  return <ChatPanel data={data} />
}

// ✅ Client Component（需要时）
'use client'
export function ChatInput({ onSend }: Props) { ... }
```

## API 调用规范

统一在 `lib/api/` 下按模块封装，不在组件内直接 fetch：

```ts
// lib/api/chat.ts
export async function sendMessage(content: string) {
  const res = await fetch('/api/chat', { method: 'POST', body: JSON.stringify({ content }) })
  if (!res.ok) throw new Error(await res.text())
  return res.json()
}
```

## SSE 流式接收

```ts
const source = new EventSource('/api/chat/stream')
source.onmessage = (e) => setContent(prev => prev + e.data)
source.onerror = () => source.close()
```

> 技术选型见 webui 技术选型

## 注释规范

- **注释语言统一中文**，禁止中英混用（与 `docs/` 真理源一致）
- TypeScript 类型必须显式，禁 `any` / 禁 `@ts-ignore`（特殊情况加注释解释）
- **文件级注释**：每个模块文件顶部加 JSDoc，说明用途 + `@author`
- **导出函数/类**：一句话说明，复杂逻辑加用法示例
- **公共 API 文件**（注册表、工具函数、Provider 等被多处消费的模块）：文件头注释必须包含用法示例（`@example` 或代码块）
- **设计文档不全或代码逻辑复杂时**：在对应用户故事目录（如 `docs/task/v0.1.0/AAF-028/`）或 `dev-log.md` 中补充说明，确保后续维护者能理解决策背景
- **不加** `@since` / `@version`（用 git blame 追溯）

```ts
/**
 * 实体注册表：管理所有 EntityDef 的注册、解析和查找
 * @author AaronZZH & Kiro
 */
```


## 响应式布局

- 组件内部响应式优先使用 container queries（`@container` + `@断点:`），仅在需要响应视口时使用传统断点（`md:` / `lg:`）
- 判断标准：组件可能被放在不同宽度的容器中 → 用 `@container`；组件始终占满视口宽度 → 用传统断点

## 常见 Lint 问题规范

### a11y（无障碍）

**`noLabelWithoutControl`**：`<label>` 必须通过 `htmlFor` 关联控件，或直接包裹控件。
```tsx
// ✅
<label htmlFor="email">邮箱</label>
<input id="email" />

// ❌
<label>邮箱</label>
<input />
```

**`useSemanticElements`**：有交互行为的元素用语义标签，不用 `div role="button"`。
```tsx
// ✅ 点击触发操作
<button type="button" onClick={handleClick}>操作</button>

// ❌
<div role="button" onClick={handleClick}>操作</div>

// 例外：拖拽区域、role="group" 等无对应语义元素时，加 biome-ignore 注释
// biome-ignore lint/a11y/useSemanticElements: 拖拽上传区域需要 div
```

**`noStaticElementInteractions`**：非交互元素（div/span）有事件处理时，必须同时有 `role` 和 `onKeyDown`。
```tsx
// ✅ 遮罩层用 button
<button type="button" className="overlay" onClick={onClose} onKeyDown={(e) => e.key === "Escape" && onClose()} />

// ❌
<div onClick={onClose} onKeyDown={undefined} />
```

**`useUniqueElementIds`**：同一页面内 `id` 必须唯一，组件内用 `useId()` 生成。
```tsx
// ✅
const uid = useId()
<label htmlFor={`${uid}-email`}>邮箱</label>
<input id={`${uid}-email`} />

// ❌ 静态 id 在组件多次渲染时重复
<label htmlFor="email">邮箱</label>
<input id="email" />
```

### correctness（正确性）

**`useExhaustiveDependencies`**：`useCallback`/`useEffect` 依赖数组必须完整。纯函数（不依赖外部状态）不需要加入依赖，加 biome-ignore 注释说明原因。
```tsx
// ✅ 纯函数不加入依赖
// biome-ignore lint/correctness/useExhaustiveDependencies: getPos 是纯函数
const draw = useCallback((e) => { ... }, [drawing])

// ✅ stable setter 不需要加入依赖
const stop = useCallback(() => setDrawing(false), [])
```

**`noUnusedFunctionParameters`**：未使用的参数加 `_` 前缀。
```tsx
// ✅
function Component({ value, onChange: _onChange }: Props) { ... }

// ❌
function Component({ value, onChange }: Props) { ... } // onChange 未使用
```

### performance（性能）

**`noImgElement`**：优先用 `next/image`。对 blob URL / data URL 等 next/image 不支持的场景，加 biome-ignore 行注释（不是 JSX 注释）。
```tsx
// ✅ 动态 URL 用 next/image
import Image from "next/image"
<Image src={url} alt="..." width={100} height={100} />

// ✅ blob/data URL 加注释
// biome-ignore lint/performance/noImgElement: blob URL，next/image 不支持
<img src={blobUrl} alt="预览" />

// ❌ JSX 注释位置错误（三元表达式内）
{condition ? (
  {/* biome-ignore ... */}  // ← 语法错误
  <img />
) : null}
```

### suspicious（可疑）

**`noConsole`**：生产代码禁用 `console.*`。调试完成后删除，或用 Toast/日志服务替代。

**`noArrayIndexKey`**：列表 key 不用数组索引，用稳定的业务 id 或字段组合。
```tsx
// ✅
items.map((item) => <div key={item.id}>...</div>)
filters.map((f) => <span key={`${f.field}-${f.operator}-${String(f.value)}`}>...</span>)

// ❌
items.map((item, i) => <div key={i}>...</div>)
```

### Base UI 特有问题

shadcn/ui 使用 Base UI（`@base-ui/react`）而非 Radix，API 有差异：

| Radix | Base UI |
|-------|---------|
| `asChild` prop | `render` prop（接受 ReactElement） |
| `onValueChange={(v) => fn(v)}` | `onValueChange={(v) => fn(v ?? "")}` （v 可能为 null） |
| `openDelay` | 不支持（PreviewCard 无此 prop） |

```tsx
// ✅ Base UI Button asChild 等效写法
<Button
  nativeButton={false}
  render={<Link href="/path">链接</Link>}
/>

// ✅ Select onValueChange 处理 null
<Select onValueChange={(v) => onChange(v ?? "")}>
```


## shadcn/ui 优先使用规则

### 硬规则：写 UI 前先查 shadcn

**每次需要 UI 元素时，必须先执行以下检查顺序，不得跳过：**

```
1. components/ui/ 目录已有？ → 直接用
2. shadcn 官方有？ → npx shadcn@latest add <name>，再用
3. 都没有 → 自己写，放 components/ui/（原子）或 components/common/（复合）
```

查询命令：`npx shadcn@latest search "@shadcn" --query <关键词>`

### 必须用 shadcn 的场景（不得用原生 HTML）

| 场景 | 禁止 | 应该用 |
|------|------|--------|
| 复选框 | `<input type="checkbox">` | `<Checkbox>` |
| 单选框 | `<input type="radio">` | `<RadioGroup>` |
| 下拉选择 | `<select>` | `<Select>` |
| 开关 | `<input type="checkbox">` 模拟 | `<Switch>` |
| 对话框/弹窗 | `<dialog>` 或自定义 div | `<Dialog>` / `<AlertDialog>` |
| 抽屉 | 自定义 fixed div | `<Sheet>` / `<Drawer>` |
| 提示气泡 | `title` 属性 | `<Tooltip>` |
| 空状态 | 自定义 `<p>` 或 div | `<Empty>` |
| 用户头像 | 自定义 div + 首字母 | `<Avatar>` + `<AvatarFallback>` |
| 选项组（2-7 个） | 手写 button 循环 + active 状态 | `<ToggleGroup>` |
| 滚动区域 | `overflow-y-auto` div | `<ScrollArea>`（内容动态/需自定义滚动条时） |
| 加载占位 | 自定义 `animate-pulse` div | `<Skeleton>` |
| 标签/徽标 | 自定义 span + 样式 | `<Badge>` |
| 分割线 | `<hr>` 或 `<div className="border-t">` | `<Separator>` |

### 允许用原生 HTML 的场景

- 语义明确且无交互的纯展示元素：`<p>` `<span>` `<ul>` `<li>` `<time>` 等
- shadcn 无对应组件的自定义布局结构
- 性能敏感的长列表行内元素（避免组件开销）

### 发现遗漏时的处理

代码审查或自查发现用了原生 HTML 替代 shadcn 组件时：
1. 安装对应 shadcn 组件：`npx shadcn@latest add <name>`
2. 替换原生 HTML
3. 在 `dev-log.md` 记录（如在任务范围内）
