# 开发记录：前端差距分析与重构（AAF-037）

执行者：AI/coordinator + developer-webui

## #3701 核心架构差距分析

✅ 2026-05-19 — coordinator

- 逐章对比 interaction-mode-structured-view.md 与 entity-engine 实现
- 发现 FieldDef 缺失 7 种类型、组件注册表不完整、ViewType 未含 pivot
- 架构三层模型（Registry/ComponentRegistry/ViewEngine）符合设计

## #3702 技术选型与目录结构合规检查

✅ 2026-05-19 — coordinator

- 核心依赖全部已安装，按需依赖后续引入
- 发现依赖方向违反 19 处（lib/→features/ 13 处 + components/→features/ 6 处）
- 根因：类型定义和 entityRegistry 放在 features/ 层

## #3703 已实现功能文档对比

✅ 2026-05-19 — coordinator

- P1 文档（rich-text-editor/page-engine/command-palette/change-history/data-dictionary）全部符合设计
- P2 UI 规范（OKLCH/加载状态/动效）全部符合
- 产出差距报告 `gap-analysis.md`

## #3704 FieldDef 类型补全

✅ 2026-05-19 — developer-webui

- 新增 7 种字段类型接口：Switch/Money/Quantity/Formula/Signature/Cascader/Subtable
- FieldDef 联合类型扩展为 22 种
- types/index.ts 导出同步更新

## #3705 目录结构与命名对齐

✅ 2026-05-19 — developer-webui

- 类型真实来源移至 `lib/types/entity/`，features/ 改为重导出
- entityRegistry/mixins/resolve 移至 `lib/modules/`
- 修复全部 19 处依赖方向违反，lib/ 和 components/ 零引用 features/
- PageDefRecord 提取到 `lib/types/page.ts`，FilterCondition 提取到 `lib/types/entity/filter.ts`

## #3706 组件注册表与视图引擎补全

✅ 2026-05-19 — developer-webui

- Field 注册新增 9 种（含适配器 adapters.tsx）
- Cell 注册新增 7 种
- ViewType 加入 pivot
