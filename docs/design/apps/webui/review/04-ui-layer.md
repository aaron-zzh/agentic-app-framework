# UI 层审查报告

审查范围：components / sections / app 路由页面
审查时间：2026-05-30
审查者：AI/architect

---

## 模块：components/ui

### 问题

- [minor] `components/ui/empty.tsx:17` — ✅ 已修复 `EmptyDescription` 使用 `<div>` 但 props 类型声明为 `React.ComponentProps<"p">`，语义不一致。应使用 `<p>` 标签或修正类型声明。
- [minor] `components/ui/table.tsx` — 标准 shadcn table 组件，无 a11y 问题。但缺少 `aria-sort` 属性支持（排序由外部 TableHead 组件处理，此处可忽略）。
- [minor] `components/ui/drawer.tsx` — 组件内部无问题，但整个项目中 Sheet 和 Drawer 功能重叠（Sheet 基于 Dialog，Drawer 基于 Vaul），建议统一为一种抽屉方案避免混淆。

### 建议

- `EmptyDescription` 改为 `<p>` 标签以匹配类型声明和语义。
- 长期考虑统一 Sheet/Drawer 使用规范，避免开发者选择困难。

---

## 模块：components/form

### 问题

- [major] `components/form/field-signature.tsx:全文` — ✅ 已修复 Canvas 签名组件缺少键盘可访问性。`<canvas>` 元素无 `role`、`aria-label`，纯鼠标/触摸交互，键盘用户无法使用。修复建议：添加 `role="img"` + `aria-label="手写签名区域"`，并提供替代输入方式（如文件上传签名图片）。
- [major] `components/form/field-upload.tsx:143-155` — ✅ 已修复 DropZone 内部的 `<img>` 预览缺少有意义的 `alt` 文本。当前 `alt="preview"` 对屏幕阅读器无帮助。建议使用文件名作为 alt：`alt={value.split('/').pop() || '已上传图片'}`。
- [minor] `components/form/field-qrscanner.tsx` — 未读取完整内容，但从文件名推断为二维码扫描组件，需确认是否有 camera permission 错误处理和 a11y fallback。
- [minor] `components/form/relationship-picker.tsx:72` — `onChange` 类型使用 `as unknown as string | string[]` 强制类型转换，类型安全性差。建议重构 `FieldProps<T>` 泛型使 onChange 签名与 value 类型一致。
- [minor] `components/form/subtable.tsx:57` — 子表行使用 `(row._key as string) ?? i` 作为 key，当 `_key` 不存在时回退到 index，可能导致列表重排时状态错乱。建议在 `addRow` 时始终生成唯一 key。

### 建议

- 签名组件应提供"上传签名图片"作为键盘可访问的替代方案。
- 统一 form 组件的泛型设计，消除 `as unknown` 类型断言。
- 子表行 key 改为 `crypto.randomUUID()` 或递增 ID。

---

## 模块：components/common

### 问题

- [minor] `components/common/CommandPalette.tsx` — 组件设计良好，有 `title` 和 `description` 属性传递给 CommandDialog。无明显问题。
- [minor] `components/common/ThemeSettings.tsx:全文` — ✅ 已修复 主题色选择按钮缺少 `aria-label` 或 `aria-pressed` 状态标识。当前用户无法通过屏幕阅读器知道哪个颜色被选中。建议为每个颜色按钮添加 `aria-label={label}` + `aria-pressed={themeColor === value}`。
- [minor] `components/common/PageContainer.tsx` — 简单容器组件，无问题。

### 建议

- ThemeSettings 颜色选择器添加 aria 属性。

---

## 模块：components/table

### 问题

- [major] `components/table/TablePagination.tsx:42-50` — ✅ 已修复 使用原生 `<input type="checkbox">` 和 `<select>` 而非项目统一的 shadcn 组件（Checkbox / Select）。与项目其他地方的组件使用不一致，且原生元素缺少统一样式。
- [major] `components/table/TableHead.tsx:50-57` — ✅ 已修复 同上，使用原生 `<input type="checkbox">` 而非 `<Checkbox>` 组件。且全选复选框缺少 `aria-label="全选"`。
- [minor] `components/table/TablePagination.tsx:55-62` — 分页按钮 `‹` / `›` 缺少 `aria-label`（如"上一页"/"下一页"），屏幕阅读器只能读到符号。

### 建议

- 将 TablePagination 和 TableHead 中的原生表单元素替换为 shadcn 组件（Checkbox / Select）。
- 分页按钮添加 `aria-label="上一页"` / `aria-label="下一页"`。

---

## 模块：components/upload

### 问题

- [minor] `components/upload/Upload.tsx:155` — 文件预览列表使用 `key={i}`（index 作为 key），当文件被删除时可能导致 React 状态错乱。建议使用 `file.name + file.size` 或生成唯一 ID。
- [minor] `components/upload/Upload.tsx:全文` — Upload 组件与 `components/form/field-upload.tsx` 的 DropZone 存在大量重复逻辑（拖拽处理、进度条、预览列表）。违反 No-Duplication 原则。建议 field-upload 内部复用 Upload 组件。

### 建议

- `field-upload.tsx` 应基于 `Upload` 组件封装，而非重复实现拖拽逻辑。
- 文件列表 key 改为唯一标识。

---

## 模块：components/animate

### 问题

无明显问题。动画变体定义清晰，MotionLazy 正确使用 `LazyMotion` + `domAnimation` 实现代码分割。

### 建议

无。

---

## 模块：components/docs

### 问题

- [minor] `components/docs/DocTree.tsx:89-107` — FileNode 的 `button` 元素同时绑定了 `onClick` 和拖拽 `listeners`，当拖拽被禁用时 `ref` 仍然绑定。不影响功能但可优化为条件绑定。

### 建议

无紧急修复。

---

## 模块：components/brand

### 问题

无明显问题。Brand 组件简洁。

---

## 模块：sections/layout

### 问题

- [blocker] ✅ 已修复 `sections/layout/AppHeader.tsx:93-110` — `MobileSidebar` 函数内使用 `require()` 动态导入模块（`require("@/sections/layout/nav-config")`）。这在 Next.js App Router 的 `"use client"` 组件中是反模式：(1) 破坏 tree-shaking；(2) 可能导致 SSR/CSR 不一致；(3) 违反 ESM 规范。应改为顶层 `import` 或 `React.lazy` + `dynamic`。
- [blocker] ✅ 已修复 `sections/layout/AppHeader.tsx:225-228` — `ChatterToggle` 函数内使用 `require("@/stores/chatter-store")` 动态导入 store。同上问题。且 `WorkspaceLayoutClient.tsx` 已正常 import 了 `useChatterStore`，说明该 store 可以正常静态导入。

> 已修复｜2026-05-30｜提交：apps/webui/src/sections/layout/AppHeader.tsx
- [major] `sections/layout/AppHeader.tsx:全文` — 文件过大（~230 行），包含 10+ 个子组件（SidebarToggle, MobileSidebar, Breadcrumb, SearchButton, CalendarButton, ThemeToggle, UserAvatar, SettingsButton, SettingsIcon, ChatterToggle）。建议拆分为独立文件。
- [minor] `sections/layout/AppSidebar.tsx:全文` — 图标映射 `ICON_MAP` 硬编码了 6 个图标。当后端返回新图标名时无法渲染。建议使用 lucide-react 的动态图标加载或扩展映射表。
- [minor] `sections/layout/MobileTabBar.tsx:37-46` — "搜索" Tab 实际触发的是 `toggleSidebar`（打开菜单），图标也改为了 Menu，但 TABS 数组中 label 仍为 "搜索"、path 为 "#search"。语义混乱，建议修正 label 为 "菜单" 或恢复搜索功能。
- [minor] `sections/layout/ContactsPanel.tsx:全文` — 使用硬编码 mock 数据。注释说明"后端就绪后替换"，可接受，但建议添加 TODO 标记到 backlog。

### 建议

- **立即修复**：将 `require()` 替换为标准 ESM `import`。`ChatterToggle` 直接 `import { useChatterStore } from "@/lib/store/chatter-store"`；`MobileSidebar` 直接 `import { buildNavConfig } from "./nav-config"`。
- AppHeader 拆分为 `AppHeader.tsx`（主组件）+ `header-actions/` 目录（ThemeToggle, UserAvatar, SettingsButton 等）。
- MobileTabBar 修正 "搜索" Tab 的 label 和行为。

---

## 模块：sections/entity

### 问题

- [minor] `sections/entity/view/entity-list-view.tsx:38-44` — `viewSettings` 从 `localStorage` 读取时使用 `useEffect`，首次渲染会闪烁（先用空 settings 渲染，再用 localStorage 值重渲染）。建议使用 `useSyncExternalStore` 或在 `useState` 初始化函数中读取。

### 建议

- 将 localStorage 读取移到 `useState(() => { ... })` 惰性初始化中（注意 SSR 兼容性，可用 `typeof window !== 'undefined'` 守卫）。

---

## 模块：app 路由页面

### (auth) 页面

#### 问题

- [minor] `app/(auth)/login/page.tsx:全文` — 登录表单缺少全局错误提示（如"邮箱或密码错误"）。`onSubmit` 中 `authApi.login` 失败时无 try-catch，错误会被 React 错误边界捕获而非友好提示。
- [minor] `app/(auth)/register/page.tsx:全文` — 同上，`onRegister` 和 `onVerify` 缺少 try-catch 错误处理。
- [minor] `app/(auth)/forgot-password/page.tsx:全文` — 同上，`onSendCode` 和 `onReset` 缺少 try-catch。
- [minor] `app/(auth)/login/oauth-callback/page.tsx` — 实现良好，有 CSRF state 验证、错误处理、Suspense 包裹。无问题。

### (marketing) 页面

#### 问题

- 无明显问题。`page.tsx` 使用 PageEngine 渲染，`[...slug]/page.tsx` 正确处理 notFound。

### (workspace) 页面

#### 问题

- [major] `app/(workspace)/admin/menus/page.tsx:130` — 删除确认使用 `confirm()` 原生弹窗。与项目 Dialog 组件风格不一致，且无法自定义样式。建议使用 AlertDialog 组件。
- [major] `app/(workspace)/admin/automations/page.tsx:85-93` — ✅ 已修复 条件和操作使用原始 JSON 文本框编辑（`<Textarea>` + `JSON.parse`）。JSON 解析失败时仅 `return`，无错误提示给用户。建议添加 JSON 校验错误提示。
- [major] `app/(workspace)/admin/automations/page.tsx:127` — ✅ 已修复 删除操作 `remove(rule.id)` 无确认弹窗，直接删除。高风险操作应有确认步骤。
- [minor] `app/(workspace)/workflow/page.tsx:30` — `currentUserId` 硬编码为 `"current-user"`，注释标注 TODO。可接受但需跟踪。
- [minor] `app/(workspace)/settings/delegation/page.tsx:全文` — 表单使用 `useState` 管理而非 react-hook-form + zod。与项目其他表单（auth 页面）风格不一致。建议统一使用 Form + Field 组件。
- [minor] `app/(workspace)/admin/data-access/page.tsx:全文` — 同上，表单使用 `useState` 而非 react-hook-form。
- [minor] `app/(workspace)/notifications/page.tsx:全文` — 实现良好，有批量操作、全选、Tab 切换。无明显问题。
- [minor] `app/(workspace)/dev/stats/page.tsx` — 开发工具页面，审查优先级低。

### API 路由

#### 问题

- [major] ~~`app/api/upload/route.ts:全文` — 文件上传接口将整个文件转为 base64 返回。对于大文件（如 10MB）会导致响应体极大、内存占用高。虽然注释标注为 Mock，但应添加文件大小上限校验（当前无校验）。~~ ✅ 已修复（添加 50MB 上限，超出返回 413）
- [minor] `app/api/chat/route.ts:全文` — Mock SSE 实现，无安全问题。生产替换时需注意输入校验。
- [minor] `app/api/upload/presign/route.ts:全文` — Mock 实现，无问题。
- [minor] `app/api/hello/route.ts` — 健康检查端点，无问题。

### global.css

#### 问题

- 无问题。CSS 变量定义清晰，使用 oklch 色彩空间，主题预设完整，暗色模式覆盖齐全。布局变量命名规范。

---

## 汇总

| 模块 | blocker | major | minor |
|------|---------|-------|-------|
| components/ui | 0 | 0 | 3 |
| components/form | 0 | 2 | 3 |
| components/common | 0 | 0 | 2 |
| components/table | 0 | 2 | 1 |
| components/upload | 0 | 0 | 2 |
| components/animate | 0 | 0 | 0 |
| components/docs | 0 | 0 | 1 |
| components/brand | 0 | 0 | 0 |
| sections/layout | 0 (2 已修复) | 1 | 3 |
| sections/entity | 0 | 0 | 1 |
| app/(auth) | 0 | 0 | 3 |
| app/(marketing) | 0 | 0 | 0 |
| app/(workspace) | 0 | 3 | 4 |
| app/api | 0 | 1 | 2 |
| global.css | 0 | 0 | 0 |
| **合计** | **0 (2 已修复)** | **3** (9 原始, 6 已修复) | **25** |

---

## 结论

**需修复后通过**。

存在 ~~2 个 blocker~~（已修复）和 9 个 major 问题。~~blocker 必须立即修复~~，major 建议在当前迭代内处理。

### 优先修复清单

1. **[blocker]** ✅ 已修复 `AppHeader.tsx` 中 2 处 `require()` → 改为标准 ESM import
2. **[major]** `TableHead` / `TablePagination` 原生表单元素 → 替换为 shadcn 组件
3. **[major]** `field-signature.tsx` 键盘可访问性
4. **[major]** `field-upload.tsx` img alt 文本
5. **[major]** `admin/menus` confirm() → AlertDialog
6. **[major]** `admin/automations` JSON 解析错误提示 + 删除确认
7. **[major]** `api/upload/route.ts` 添加文件大小上限校验
