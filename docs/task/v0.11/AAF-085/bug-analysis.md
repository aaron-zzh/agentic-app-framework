# Bug 分析报告

执行者：AI/developer-webui
日期：2026-05-29

## TODO/FIXME 标记扫描

### 后端未就绪（待 API 对接）

| 文件 | 行号 | 内容 | 优先级 |
|------|------|------|--------|
| `lib/queries/use-entity-detail.ts` | 15 | 后端就绪后用 fetchDetail | P1 |
| `lib/queries/use-entity-list.ts` | 30 | 后端就绪后移除 mock | P1 |
| `lib/queries/use-notifications.ts` | 29,43 | 后端就绪后替换为真实 API | P1 |
| `lib/_mock/notifications.ts` | 5 | 后续替换为 GET /api/notifications | P2 |
| `features/entity-engine/entities/index.ts` | 5 | 后端 EntityDef API 就绪后删除 mock | P1 |

### 功能待实现

| 文件 | 行号 | 内容 | 优先级 |
|------|------|------|--------|
| `components/form/field-qrscanner.tsx` | 7,45 | 集成 html5-qrcode 库 | P2 |
| `features/livechat/voice/use-canvas-collaboration.ts` | 65 | 接入 Yjs WebSocket Provider | P2 |
| `features/entity-engine/components/EntityActions.tsx` | 92 | Toast 通知 | P2 |
| `app/api/upload/route.ts` | 18 | 生产环境替换为真实 OSS SDK | P1 |

### UI 待完善

| 文件 | 行号 | 内容 | 优先级 |
|------|------|------|--------|
| `sections/layout/AppHeader.tsx` | 146 | 面包屑根据路由自动生成 | P2 |
| `app/(workspace)/admin/.../page.tsx` | 32 | 从 auth store 获取当前用户 ID | P1 |

## Error Boundary 覆盖分析

### 已实现的错误边界（3 层）

| 层级 | 组件 | 位置 | 覆盖范围 |
|------|------|------|---------|
| Layer 1 | `app/error.tsx` | 应用根 | 整个应用崩溃兜底 |
| Layer 2 | `ViewErrorBoundary` | `components/common/` | 视图级渲染失败 |
| Layer 3 | `FieldErrorBoundary` | `components/common/` | 字段级组件报错 |

### 未覆盖的场景

| 场景 | 风险 | 建议 |
|------|------|------|
| 仪表盘 Widget 独立错误隔离 | 单个 Widget 崩溃影响整个仪表盘 | 每个 Widget 包裹 ViewErrorBoundary |
| 流程图编辑器节点渲染 | 自定义节点报错导致整个画布崩溃 | 节点组件包裹 FieldErrorBoundary |
| 富文本编辑器插件 | 第三方插件报错导致编辑器不可用 | Lexical 内部已有 ErrorBoundary，需确认覆盖 |
| 第三方组件（ECharts/XYFlow） | 数据异常导致图表崩溃 | 图表容器包裹 ViewErrorBoundary |
| API 请求失败的 UI 降级 | 网络异常时页面白屏 | Suspense + error.tsx 已覆盖，但部分客户端组件缺少 fallback |

## 总结

- **TODO 总计**：11 处
- **P1（阻塞正式发布）**：6 处——主要是后端 API 未对接的 mock 数据
- **P2（可延后）**：5 处——功能增强类
- **Error Boundary 覆盖率**：核心 3 层已实现，建议补充 Widget 级和图表级隔离
