---
title: webui 代码审查索引
date: 2026-05-30
---

# webui 代码审查索引

审查时间：2026-05-30
审查方式：全量逐模块，4 组并行

## 文档目录

| 文档 | 审查范围 | blocker | major | minor |
|------|---------|---------|-------|-------|
| [01-infra.md](./01-infra.md) | next.config / middleware / providers / i18n / lib/api / lib/store / lib/hooks / lib/queries | 0 | 3 (7 原始, 4 已修复) | 22 |
| [02-core-modules.md](./02-core-modules.md) | entity-engine / chatter / agui / livechat | 0 | 7 (10 原始, 3 已修复) | 47 |
| [03-business-modules.md](./03-business-modules.md) | aigc / flow-editor / rich-text-editor / page-engine / entity-editor / knowledge / settings / dashboard / stats / ai-assist | 0 | 9 | 30 |
| [04-ui-layer.md](./04-ui-layer.md) | components / sections / app 路由页面 | 0 (2 已修复) | 9 | 25 |
| [code-review.md](../code-review.md) | 全局概览（首轮审查） | 2 | 7 | 7 |

## 全局汇总

| 级别 | 数量 | 质量门控 |
|------|------|---------|
| blocker | 0 (2 已修复) | ✅ 已清零 |
| major | 28 (35 原始, 7 已修复) | ❌ 需 ≤ 2 |
| minor | 124 | ✅ 不阻塞 |

**质量门控判定：未通过**（blocker=0 ✅，major=28 ❌）

---

## 跨模块系统性问题（优先处理）

### ✅ 已修复 S-1 API 调用层不统一（major × 5）

`lib/api/dashboard.ts`、`notification.ts`、`stats.ts`、`chat.ts`、`permission.ts` 各自实现了私有 `req()` 函数，绕过 `client.ts` 的统一封装，缺少：
- Authorization header 注入
- 401 自动 Token 刷新
- X-Workspace-Id / X-Org-Id header

~~**上线后必定大面积 401。**~~ 已统一改用 `client.ts` 的 `request()`。

> 已修复｜2026-05-30

### S-2 SSE 流式解析逻辑三处重复（major）

`flow-editor`、`entity-editor`、`rich-text-editor` 各自实现了 SSE 流解析，违反 DRY 原则。应提取为 `lib/utils/sse.ts` 共享工具函数。

### S-3 Mock 数据混入生产代码（major × 3）

`use-entity-list.ts`、`use-entity-detail.ts`、`aigc/VideoGenerationChat.tsx` 等多处硬编码 mock 数据，生产构建时会打包进 bundle 并屏蔽真实 API。

### S-4 WebSocket 无最大重连限制（major）

`use-websocket.ts` 无限重连，网络永久断开时持续消耗资源。

---

## 各模块 Top 问题速查

### 基础设施层（01-infra）

| 优先级 | 文件 | 问题 |
|--------|------|------|
| ~~major~~ | ~~`lib/api/client.ts:49-51`~~ | ~~SSR 环境下 Zustand store 未初始化崩溃~~ ✅ 已修复 |
| major | `lib/api/dashboard.ts` 等 | 私有 req() 缺少认证头 |
| ~~major~~ | ~~`lib/hooks/use-chatter-config.ts:24-35`~~ | ~~useEffect 依赖 configs 导致无限循环~~ ✅ 已修复 |
| major | `lib/hooks/use-websocket.ts:79-82` | 无限重连无上限 |

### 核心功能模块（02-core-modules）

| 优先级 | 文件 | 问题 |
|--------|------|------|
| ~~major~~ | ~~`entity-engine/components/KanbanView.tsx:95`~~ | ~~渲染期 setState 导致无限循环~~ ✅ 已修复 |
| ~~major~~ | ~~`entity-engine/components/EntityActions.tsx:72`~~ | ~~空 catch 块无错误通知~~ ✅ 已修复 |
| ~~major~~ | ~~`entity-engine/components/EntityApproval.tsx:143`~~ | ~~window.location.reload 应改为 queryClient invalidation~~ ✅ 已修复 |
| major | `livechat/LivechatProvider.tsx:75` | WebSocket 消息无 schema 校验 |
| major | `chatter/TaskExecutionTimeline.tsx:95` | SSE 无重连机制 |

### 业务功能模块（03-business-modules）

| 优先级 | 文件 | 问题 |
|--------|------|------|
| major | `aigc/AssetLibrary.tsx:131` | JSON.parse 无 try-catch |
| major | `aigc/AssetLibrary.tsx:237` | confirm() 阻塞弹窗 |
| major | `flow-editor/use-flow-state.ts:87-107` | 双重类型断言掩盖类型错误 |
| major | `flow-editor/use-workflow-runtime.ts:120-155` | fetch 无错误处理 + while(true) 无超时 |
| major | `rich-text-editor/AIWritePlugin` | registerCommand 无 cleanup 内存泄漏 |

### UI 层（04-ui-layer）

| 优先级 | 文件 | 问题 |
|--------|------|------|
| **blocker** | `sections/layout/AppHeader.tsx` | require() 动态导入破坏 ESM |
| **blocker** | `sections/layout/AppHeader.tsx` | 同上（2 处） |
| major | `components/form/field-signature.tsx` | Canvas 无键盘可访问性 |
| major | `components/form/field-upload.tsx:143` | img 缺少有意义 alt |
| major | `app/api/upload/route.ts` | 无文件大小上限校验 |

---

## 修复优先级建议

**第一批（blocker，必须先修）**
1. ✅ 已修复 `AppHeader.tsx` 中 2 处 `require()` → 改为 ESM import

**第二批（系统性 major，影响面广）**
2. ✅ 已修复 统一 API 调用层：dashboard/notification/stats/chat/permission 改用 `request()`
3. ✅ 已修复 修复 `use-chatter-config.ts` 无限循环
4. ✅ 已修复 `KanbanView.tsx` 渲染期 setState 移到 useEffect
5. ✅ 已修复 `EntityApproval.tsx` window.location.reload → queryClient.invalidateQueries
6. ✅ 已修复 `EntityActions.tsx` 空 catch 块添加 toast.error
7. ✅ 已修复 `client.ts` SSR 环境 Zustand store 崩溃

**第三批（安全/健壮性 major）**
6. `AssetLibrary.tsx` JSON.parse 加 try-catch
7. `AssetLibrary.tsx` confirm() 替换为 Dialog
8. `flow-editor` fetch 错误处理 + while(true) 超时
9. `rich-text-editor` AIWritePlugin cleanup
10. `use-websocket.ts` 添加最大重连次数

**第四批（minor，可迭代修复）**
- SSE 逻辑提取共享工具
- Mock 数据隔离
- a11y 补全（签名组件、主题色按钮等）
- 类型断言清理
