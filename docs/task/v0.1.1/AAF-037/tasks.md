---
level: Practice
layer: Product
purpose: AAF-037 前端差距分析与重构的技术任务清单
status: pending
version: 1.1.0
date: 2026-05-19
author: AaronZZH
---

# 前端差距分析与重构（AAF-037）

> 设计：[结构化交互模式设计](../../../design/apps/webui/interaction-mode-structured-view.md) | [技术栈](../../../design/apps/webui/tech-stack.md) | [目录结构](../../../design/apps/webui/directory-structure.md)
> 负责人：architect + developer-webui | 创建：05-19

## 目标

对照前端设计文档审查 v0.1.0 实现，产出差距清单，按优先级完成重构对齐。

## 检查范围

> 仅检查 v0.1.0 已实现功能对应的设计文档。后续版本功能（对话/工作流/权限/插件商业化等）不在本次范围。

### 核心文档（P0，必须逐章对比）

| 文档 | 检查重点 |
|------|---------|
| `interaction-mode-structured-view.md` | EntityDef/ViewEngine/ComponentRegistry 三层模型、FieldDef 类型覆盖度、视图引擎路由、数据层 hooks |
| `tech-stack.md` | 依赖清单 vs package.json、架构决策、状态边界硬规则 |
| `directory-structure.md` | 分层结构、依赖方向、文件命名、各层职责 |

### 已实现功能文档（P1，检查骨架与核心接口）

| 文档 | 对应实现 | 检查重点 |
|------|---------|---------|
| `rich-text-editor.md` | features/rich-text-editor/ | 插件体系、preset 模式、导出接口 |
| `page-engine.md` | features/page-engine/ | Section 注册表、预设、SEO |
| `command-palette.md` | components/common/CommandPalette | 命令注册、快捷键、搜索范围 |
| `structured-view-supplements.md` | features/entity-engine/ 补充 | 补充章节与实现对应 |
| `change-history-design.md` | VersionHistoryDrawer | Diff 渲染、版本时间线 |
| `data-dictionary-design.md` | features/entity-editor/ | 字段管理、AI 生成 |

### UI 规范（P2，检查基础遵循度）

| 文档 | 检查重点 |
|------|---------|
| `design-system.md` | OKLCH 色彩系统、间距/字体 token、组件风格 |
| `ui-experience.md` | 加载状态、错误处理、响应式断点 |
| `sense-ui.md` | 动效规范、反馈模式 |

### 不在本次范围

chat-livechat-module / copilot-plugin / flow-editor / permission-ui / embed-sdk / inspector-panel / user-awareness-semantic-ui / interaction-modes / plugin-commercialization / tech-design/*

## 任务列表

> **执行策略**：先产出差距报告，再根据报告结果决定重构范围和优先级。

### 阶段一：差距分析

1. [ ] #3701 核心架构差距分析
   - 逐章对比 `interaction-mode-structured-view.md` 与 entity-engine 实现
   - 重点检查：FieldDef 联合类型覆盖度（设计文档有 subtable/formula/money/signature/cascader 等，types/field.ts 是否定义）
   - 重点检查：ViewEngine 视图类型完整性（list/form/kanban/pivot 已有，graph/calendar 是否需要占位）
   - 重点检查：ComponentRegistry 字段→组件映射是否完整
   - 重点检查：数据层 hooks（useEntityList/useEntityRecord/useEntityMutation）与设计一致性
   - 产出：差距清单（按"类型缺失/接口偏离/实现遗漏"分类）
   - verify: 清单覆盖设计文档核心章节（二~六章 + 十一章共享层）

2. [ ] #3702 技术选型与目录结构合规检查
   - 对比 `tech-stack.md` 依赖清单与 package.json 实际依赖
   - 检查是否有未按选型引入的替代库或遗漏的核心依赖（如 zod、react-hotkeys-hook、fuse.js 等）
   - 对比 `directory-structure.md` 与实际目录结构
   - 检查依赖方向是否有违反（features/ 引用 sections/、components/ 引用 features/ 等）
   - 检查 workspace packages（@aaf/core, @aaf/hooks, @aaf/tailwind-config）是否符合 packages/ 设计
   - 产出：偏离清单 + 处理建议（立即修正 / 后续版本处理 / 设计文档需更新）
   - verify: 每项偏离有明确处理建议

3. [ ] #3703 已实现功能文档对比
   - 对比 P1 文档（rich-text-editor / page-engine / command-palette / structured-view-supplements / change-history / data-dictionary）与对应实现
   - 检查核心接口和骨架是否符合设计，不要求功能完整
   - 检查 UI 规范基础遵循度（P2 文档）
   - 产出：功能偏离清单（标注严重程度：blocker / major / minor）
   - verify: 每个 P1 文档至少有一条检查结论

### 阶段二：重构执行（依据阶段一报告决定具体内容）

4. [ ] #3704 FieldDef 类型补全与对齐
   - 根据 #3701 报告，补全 types/field.ts 中缺失的字段类型定义
   - 确保类型定义与 components/form/ 已有组件实现同步
   - verify: TypeScript 类型完整，`pnpm nx typecheck webui` 通过

5. [ ] #3705 目录结构与命名对齐
   - 根据 #3702 报告，调整不符合规范的目录/文件
   - 修正依赖方向违反
   - verify: 目录结构与设计文档一致，typecheck 通过

6. [ ] #3706 组件注册表与视图引擎补全
   - 根据 #3701 报告，补全 ComponentRegistry 映射
   - 确保已注册字段类型都有对应的 Field 和 Cell 组件
   - verify: 所有已注册实体可正常渲染，typecheck 通过
