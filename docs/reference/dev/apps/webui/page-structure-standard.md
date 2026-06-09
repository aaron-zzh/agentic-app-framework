# 工作区页面结构规范

> 本文档是 webui `(workspace)` 路由组下所有页面的编写规范，供后续新建页面和存量页面重构参考。

## 核心原则

- `app/` 下的 `page.tsx` 只做路由接线，不写业务逻辑
- 业务逻辑和 UI 放在 `features/{domain}/` 下的 View 组件
- 需要 Chatter 嵌入的页面，在 View 组件里声明；无声明则默认浮动

## 标准结构

### 目录层次

```
app/(workspace)/{路由}/
  page.tsx          ← 路由入口（Server Component，极简）
  layout.tsx        ← 仅在该路由需要私有布局时创建
  loading.tsx       ← 仅在需要独立骨架屏时创建

features/{domain}/
  {Domain}View.tsx  ← 主视图（"use client"，业务逻辑和 UI 全在这里）
  {Domain}Layout.tsx ← 有复杂子布局时使用，否则直接用 View
  components/       ← 该功能内部复用的子组件
  hooks/            ← 该功能私有 hooks
  store.ts          ← 仅管 UI 状态的 Zustand store（可选）
  types.ts          ← 类型定义
```

### page.tsx 模板

```tsx
/**
 * {功能名}页面
 * @author AaronZZH & Kiro
 */

import { {Domain}View } from "@/features/{domain}"

export default function {Domain}Page() {
  return <{Domain}View />
}
```

`page.tsx` 规则：
- 不加 `"use client"`（保持 Server Component）
- 不写任何 hooks、状态、业务逻辑
- 只做一件事：渲染对应的 View 组件
- 如果需要传路由参数（`params`、`searchParams`），透传给 View

### View 组件模板

```tsx
/**
 * {功能名}视图
 * @author AaronZZH & Kiro
 */

"use client"

// 可选：需要嵌入式 Chatter 时声明
// import { useChatterLayoutPreference } from "@/features/chatter"

export function {Domain}View() {
  // 需要嵌入对话面板时，在这里声明
  // useChatterLayoutPreference("panel")

  return (
    <div className="flex h-full flex-col">
      {/* 页面内容 */}
    </div>
  )
}
```

## Chatter 布局声明

工作区 Chatter 有两种模式，**默认浮动**，无需任何配置：

| 模式 | 触发方式 | 适用场景 |
|------|---------|---------|
| `dialog`（浮动） | 默认，无需声明 | 普通信息页、表单、列表页 |
| `panel`（嵌入） | View 里调用 `useChatterLayoutPreference("panel")` | 需要边操作边对话的工具类页面 |

```tsx
// 需要嵌入时，在 View 组件顶层调用
import { useChatterLayoutPreference } from "@/features/chatter"

export function AigcView() {
  useChatterLayoutPreference("panel")  // 进入时嵌入，离开时自动恢复浮动
  // ...
}
```

当前声明嵌入模式的页面：

- `aigc`（图像生成）— 需要边生成边对话

## 常见错误模式

**不要在 page.tsx 里写业务逻辑：**

```tsx
// ❌ 错误：page.tsx 里写 hooks 和状态
"use client"
export default function Page() {
  const [data, setData] = useState([])
  useEffect(() => { fetch(...) }, [])
  return <div>...</div>
}

// ✅ 正确：page.tsx 只做路由入口
export default function Page() {
  return <DomainView />
}
```

**不要把 Chatter 嵌入声明放在 page.tsx：**

```tsx
// ❌ 错误：page.tsx 是 Server Component，不能调 hooks
export default function Page() {
  useChatterLayoutPreference("panel")  // 报错
  return <DomainView />
}

// ✅ 正确：在 View 组件（"use client"）里声明
export function DomainView() {
  useChatterLayoutPreference("panel")
  return ...
}
```

**不要在多个地方管同一份布局状态：**

```tsx
// ❌ 错误：页面自己管 open 状态，和 chatter-store 双真理源
const [chatOpen, setChatOpen] = useState(false)
<Chatter open={chatOpen} onOpenChange={setChatOpen} />

// ✅ 正确：只声明偏好，open 状态由 chatter-store 统一管理
useChatterLayoutPreference("panel")
```

## 现有页面重构指引

存量页面按以下优先级逐步重构，不强制一次性完成：

- **P0**：page.tsx 里有大量 hooks 和业务逻辑的 → 抽 View 组件
- **P1**：page.tsx 标了 `"use client"` 的 → 去掉标记，逻辑移到 View
- **P2**：已有 View 但和 page.tsx 职责混乱的 → 整理分层

重构时遵守 AGENTS.md 批量修改规则：单次 PR 不超过 5 个文件。
