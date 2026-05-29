# 技术债务清单

执行者：AI/developer-webui
日期：2026-05-29

## 统计概览

| 类型 | 数量 | 说明 |
|------|------|------|
| TODO | 11 | 待实现功能/待对接 API |
| FIXME | 0 | 无已知 Bug 标记 |
| HACK | 0 | 无临时方案标记 |
| Mock 数据 | 3 处 | 实体列表/通知/EntityDef 使用 mock |

## 债务分类

### 类型 A：后端未就绪导致的 Mock（6 项）

后端 API 就绪后需逐一替换为真实调用。

| 文件 | 描述 | 清理条件 |
|------|------|---------|
| `lib/queries/use-entity-detail.ts` | mock 详情数据 | 后端 EntityDef CRUD API 就绪 |
| `lib/queries/use-entity-list.ts` | mock 列表数据 | 同上 |
| `lib/queries/use-notifications.ts` | mock 通知列表和未读数 | 后端通知 API 就绪 |
| `lib/_mock/notifications.ts` | 通知 mock 数据文件 | 同上 |
| `lib/_mock/entities.ts` | 实体 mock 数据文件 | 后端 EntityDef API 就绪 |
| `features/entity-engine/entities/index.ts` | 硬编码 EntityDef 注册 | 改为从 API 加载 |

### 类型 B：功能未完成（3 项）

| 文件 | 描述 | 工作量 |
|------|------|--------|
| `components/form/field-qrscanner.tsx` | 二维码扫描未集成 html5-qrcode | 0.5d |
| `app/api/upload/route.ts` | 文件上传未对接 OSS | 1d |
| `features/livechat/voice/use-canvas-collaboration.ts` | Yjs 协同未接入 | 2d |

### 类型 C：UI 待完善（2 项）

| 文件 | 描述 | 工作量 |
|------|------|--------|
| `sections/layout/AppHeader.tsx` | 面包屑未自动生成 | 0.5d |
| `app/(workspace)/admin/.../page.tsx` | 用户 ID 未从 auth store 获取 | 0.5d |

## 架构层面债务

| 项目 | 当前状态 | 目标状态 | 优先级 |
|------|---------|---------|--------|
| next.config.ts | 空配置 | 完整性能+安全配置 | P1 |
| middleware.ts | 仅路由守卫 | 安全头 + 限流 | P1 |
| 错误边界 | 3 层基础覆盖 | Widget/图表级隔离 | P2 |
| i18n 覆盖率 | 基础 key 已定义 | 全组件覆盖 | P2 |
| 测试覆盖率 | 2 个 query hook 测试 | 核心逻辑 80%+ | P1 |
| Storybook | 未搭建 | 组件文档化 | P3 |

## 清理优先级建议

1. **Beta 前必须**：next.config.ts 性能配置 + middleware 安全头 + auth store 用户 ID
2. **正式版前**：Mock 数据全部替换 + 测试覆盖率提升
3. **可延后**：QR 扫描、Yjs 协同、Storybook
