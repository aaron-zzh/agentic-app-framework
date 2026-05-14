# next-ts 参考项目分析

> next-ts 是一个基于 MUI + App Router。本文分析其目录结构、路由设计、布局系统和表单组件模式，提炼对 AAF webui 的借鉴价值。

## 一、项目整体结构

```text
src/
├── app/                → Next.js App Router 路由层（极薄，仅 metadata + View 组件引用）
├── sections/           → 业务区块层（按领域组织，含 view/ 子目录作为页面组装入口）
├── components/         → 通用 UI 组件（hook-form / table / upload / editor / nav-section 等）
├── layouts/            → 布局系统（dashboard / main / auth-split / auth-centered / core）
├── auth/               → 认证模块（context / guard / hooks / view / components）
├── routes/             → 路由常量 + hooks（paths.ts 集中定义所有路径）
├── lib/                → 第三方客户端封装（axios / firebase / supabase）
├── locales/            → 国际化
├── theme/              → MUI 主题系统
├── types/              → TypeScript 类型定义
├── utils/              → 工具函数
├── assets/             → 静态资源（SVG 插画/图标组件）
├── actions/            → Server Actions（数据获取/变更）
└── _mock/              → Mock 数据
```

## 二、核心设计模式分析

### 2.1 路由层极薄化

**模式**：`app/` 目录中的 page.tsx 只做两件事——声明 metadata + 渲染对应的 View 组件。

```tsx
// app/dashboard/user/new/page.tsx
export const metadata: Metadata = { title: `Create a new user | Dashboard` };
export default function Page() {
  return <UserCreateView />;
}
```

**价值**：路由层零业务逻辑，所有页面组装逻辑下沉到 `sections/{domain}/view/` 中，使得：
- 路由文件极简，一眼看清路由结构
- 业务组件可独立测试，不依赖路由上下文
- 同一 View 可在不同路由复用

**AAF 对应**：AAF 的 `app/` 层已采用相同理念（page.tsx 仅组合 sections），一致。

### 2.2 sections/ 按领域组织 + view/ 子目录

**模式**：每个业务域一个目录，内含：
- 扁平的业务组件文件（`user-table-row.tsx`、`user-create-edit-form.tsx`）
- `view/` 子目录作为页面组装入口（`user-list-view.tsx`、`user-create-view.tsx`）
- 可选的 `hooks/`、`utils/`、`context/` 子目录

```text
sections/user/
├── view/
│   ├── index.ts                    → barrel export
│   ├── user-list-view.tsx          → 列表页组装
│   ├── user-create-view.tsx        → 创建页组装
│   └── user-edit-view.tsx          → 编辑页组装
├── user-create-edit-form.tsx       → 表单组件（创建/编辑复用）
├── user-table-row.tsx              → 表格行
├── user-table-toolbar.tsx          → 表格工具栏
└── user-card.tsx                   → 卡片视图
```

**价值**：
- `view/` 作为 sections 的"页面入口"，与 app/ 路由一一对应
- 业务组件扁平放置，无过度嵌套
- 创建/编辑共用一个 form 组件（`*-create-edit-form.tsx`）

**AAF 借鉴**：AAF sections/ 目前直接用 `ChatPage.tsx`、`DocumentEditor.tsx` 等作为页面组装入口。可考虑引入 `view/` 子目录模式，当同一领域有多个页面视图时（列表/详情/创建/编辑），用 view/ 统一管理入口，保持 barrel export。

### 2.3 布局系统分层

**模式**：layouts/ 分为 core 基础层 + 具体布局实现：

```text
layouts/
├── core/               → 布局原语（LayoutSection / HeaderSection / MainSection）
├── dashboard/          → 仪表板布局（侧边栏 + 顶栏，支持 vertical/horizontal/mini 三种导航模式）
├── main/               → 营销页布局（顶部导航 + 页脚）
├── auth-split/         → 认证布局（左右分栏）
├── auth-centered/      → 认证布局（居中卡片）
├── simple/             → 简单布局（仅 header + content）
├── components/         → 布局共享组件（account-drawer / searchbar / notifications 等）
├── nav-config-*.tsx    → 导航配置数据（dashboard / main / account / workspace）
```

**关键设计**：
- `core/layout-section.tsx` 是所有布局的基础骨架，通过 slot 模式（headerSection / sidebarSection / footerSection）组合
- CSS 变量驱动布局尺寸（`--layout-nav-vertical-width`、`--layout-nav-mini-width`）
- 导航配置与布局组件分离（`nav-config-dashboard.tsx` 纯数据）
- 布局组件支持通过 settings context 动态切换导航模式

**AAF 借鉴**：
- AAF 使用 Next.js 路由组 `(workspace)/(canvas)/(auth)` 的 layout.tsx 实现布局切换，比 next-ts 的手动 Layout 组件更原生
- 但 next-ts 的 **CSS 变量驱动布局尺寸** 和 **导航配置数据分离** 值得借鉴
- AAF 可在 `sections/layout/` 中采用类似的 slot 组合模式构建 AppSidebar/AppHeader

### 2.4 路由常量集中管理

**模式**：`routes/paths.ts` 集中定义所有路由路径，支持动态参数函数化：

```ts
const ROOTS = { DASHBOARD: '/dashboard' };

export const paths = {
  dashboard: {
    user: {
      root: `${ROOTS.DASHBOARD}/user`,
      new: `${ROOTS.DASHBOARD}/user/new`,
      edit: (id: string) => `${ROOTS.DASHBOARD}/user/${id}/edit`,
    },
  },
};
```

**价值**：
- 路径变更只改一处
- 动态路由参数类型安全
- IDE 自动补全友好

**AAF 借鉴**：AAF 的 `lib/constants/` 可采用相同模式，定义 `paths.ts` 集中管理所有路由常量。

### 2.5 表单组件体系（hook-form）

**模式**：`components/hook-form/` 提供完整的表单组件封装：

```text
components/hook-form/
├── form-provider.tsx    → Form 组件（包裹 RHF FormProvider + <form>）
├── fields.tsx           → Field 命名空间对象（Field.Text / Field.Select / Field.Upload ...）
├── schema-utils.ts      → Zod schema 工具函数（email / phone / file / nullableInput）
├── help-text.tsx        → 错误提示组件
├── rhf-text-field.tsx   → 文本输入
├── rhf-select.tsx       → 下拉选择
├── rhf-checkbox.tsx     → 复选框
├── rhf-upload.tsx       → 文件上传
├── rhf-date-picker.tsx  → 日期选择
├── rhf-phone-input.tsx  → 电话输入
├── rhf-editor.tsx       → 富文本编辑器
└── ...                  → 其他表单控件
```

**关键设计**：
1. **Field 命名空间**：通过 `fields.tsx` 导出统一的 `Field` 对象，使用时 `<Field.Text name="email" />`，语义清晰
2. **Form 包装器**：`Form` 组件封装 `FormProvider` + `<form>` 标签，简化使用
3. **schema-utils**：提供 Zod schema 工厂函数（`schemaUtils.email()`、`schemaUtils.file()`），统一校验规则
4. **Controller 封装**：每个 RHF 组件内部使用 `Controller` + `useFormContext()`，外部只需传 `name`

**使用示例**：
```tsx
<Form methods={methods} onSubmit={onSubmit}>
  <Field.Text name="name" label="Full name" />
  <Field.Phone name="phoneNumber" label="Phone" />
  <Field.CountrySelect name="country" label="Country" />
  <Field.UploadAvatar name="avatarUrl" />
</Form>
```

**AAF 借鉴**：AAF 的 `components/form/` 应采用相同模式：
- 提供 `Form` 包装器（集成 react-hook-form + zod）
- 提供 `Field` 命名空间对象统一导出所有表单控件
- 提供 `schemaUtils` 工厂函数统一校验规则
- 每个控件基于 shadcn/ui 原语 + Controller 封装，外部只需传 `name` + `label`

### 2.6 认证模块独立

**模式**：`auth/` 作为顶层目录独立存在，内部按职责分层：

```text
auth/
├── context/            → 多种认证策略实现（jwt / firebase / supabase / auth0 / amplify）
├── guard/              → 路由守卫（AuthGuard / GuestGuard / RoleBasedGuard）
├── hooks/              → useAuthContext / useMockedUser
├── view/               → 登录/注册页面视图
├── components/         → 认证表单共享组件（form-head / form-socials / sign-up-terms）
├── utils/              → 错误消息处理
└── types.ts            → 认证类型定义
```

**价值**：认证作为横切关注点独立于 sections，支持多策略切换。

**AAF 借鉴**：AAF 的 `lib/auth/` 已规划类似结构，一致。Guard 组件放在 auth 模块内而非 components/ 中是合理的。

## 三、与 AAF 目录结构的对应关系

| next-ts 目录 | AAF 对应 | 差异说明 |
|-------------|---------|---------|
| `app/` | `app/` | 一致，AAF 额外使用路由组 `(workspace)/(canvas)/(auth)` |
| `sections/` | `sections/` | AAF 已采用。next-ts 有 `view/` 子目录模式 |
| `components/` | `components/` | 一致。next-ts 按功能域分子目录（hook-form/table/upload） |
| `layouts/` | `sections/layout/` + 路由组 layout.tsx | AAF 用 Next.js 原生路由组替代手动 Layout 组件 |
| `auth/` | `lib/auth/` | AAF 放在 lib/ 下，next-ts 作为顶层目录 |
| `routes/paths.ts` | `lib/constants/` | AAF 可引入集中路由常量 |
| `actions/` | `lib/api/` + Server Actions | AAF 用 lib/api/ 做 fetch 封装，Server Actions 按需在 app/ 中定义 |
| `lib/` | `lib/` | 一致 |
| `theme/` | `lib/theme/` | AAF 放在 lib/ 下 |
| `_mock/` | `lib/_mock/` | AAF 放在 lib/ 下，`_` 前缀一致 |
| 无 | `features/` | AAF 独有，用于复合功能模块（流程图编辑器/富文本等） |
| 无 | `providers/` | AAF 独有，集中管理 Context Providers |

## 四、可借鉴的具体设计点

### 4.1 已采纳（AAF directory-structure.md 已体现）

| 设计点 | 说明 |
|--------|------|
| 路由层极薄 | page.tsx 仅 metadata + View 引用 |
| sections 按领域组织 | 每个业务域一个目录 |
| `_mock/` 前缀约定 | 内部/开发用目录 |
| 表单用 react-hook-form + zod | 技术选型一致 |
| 认证 guard 模式 | AuthGuard / GuestGuard |

### 4.2 建议新增借鉴

| 设计点 | next-ts 做法 | AAF 建议 | 优先级 |
|--------|-------------|---------|--------|
| **sections/view/ 子目录** | 每个 section 有 `view/` 作为页面入口 + barrel export | sections 中多页面域引入 `view/` 子目录（如 `sections/document/view/`） | P2 |
| **Field 命名空间** | `Field.Text` / `Field.Select` 统一入口 | `components/form/fields.ts` 导出 `Field` 对象 | P1（v0.1） |
| **Form 包装器** | `<Form methods={methods} onSubmit={onSubmit}>` | `components/form/form.tsx` 封装 FormProvider + form 标签 | P1（v0.1） |
| **schemaUtils 工厂** | `schemaUtils.email()` / `schemaUtils.file()` | `lib/schemas/utils.ts` 提供常用 schema 工厂 | P1（v0.1） |
| **路由常量集中** | `routes/paths.ts` 含动态路由函数 | `lib/constants/paths.ts` 集中定义 | P1（v0.1） |
| **CSS 变量驱动布局** | `--layout-nav-vertical-width` 等 | `global.css` 中定义布局 CSS 变量 | P1（v0.1） |
| **导航配置数据分离** | `nav-config-dashboard.tsx` 纯数据文件 | `sections/layout/nav-config.ts` 分离导航数据 | P1（v0.1） |
| **创建/编辑表单复用** | `*-create-edit-form.tsx` 同一组件 | sections 中表单组件命名 `*-form.tsx`，通过 props 区分创建/编辑 | P2 |

### 4.3 不采纳（AAF 有更好方案）

| next-ts 做法 | AAF 选择 | 理由 |
|-------------|---------|------|
| MUI 组件库 | shadcn/ui + Tailwind | 零依赖锁定、RSC 友好、体积更小 |
| 手动 Layout 组件 | Next.js 路由组 layout.tsx | 更原生、自动代码分割 |
| axios 请求库 | 原生 fetch + graphql-request | Next.js 扩展 fetch 有缓存/重验证能力 |
| `layouts/` 顶层目录 | `sections/layout/` | AAF 布局是 sections 的一种，不需要独立顶层 |
| 无 features 层 | `features/` 复合功能模块 | AAF 有流程图/富文本等复杂引擎需求 |

## 五、总结

next-ts 是一个成熟的管理后台框架，其核心优势在于**清晰的分层**和**表单组件体系**。AAF 的目录结构已借鉴了其大部分架构理念（路由薄层、sections 按域、组件分层），在此基础上 AAF 额外引入了 `features/` 层和 `providers/` 层以应对 AI 原生应用的复杂度。

最值得 AAF 在 v0.1 实现中直接落地的是：
1. **Field 命名空间 + Form 包装器 + schemaUtils** — 表单开发效率提升
2. **路由常量集中管理** — 路径变更安全
3. **CSS 变量驱动布局尺寸** — 主题/响应式灵活
4. **导航配置数据分离** — 菜单可动态化

---

> 相关文档：[目录结构设计](./directory-structure.md) | [技术选型](./tech-stack.md)
