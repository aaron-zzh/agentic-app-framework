# Magic-DSL 设计文档

> AAF 的核心领域语言体系。贯穿开发时与运行时的统一中间表示。

## 文档索引

| 文档 | 内容 | 域 |
|------|------|-----|
| [magic-dsl.md](magic-dsl.md) | 语言总体设计（语法、语义、分层、分域、范式） | 全局 |
| [dsl-engine.md](dsl-engine.md) | 后端引擎实现（解析、L1→L2→L3 转化、路由分发） | 全局 |
| [dsl-runtime.md](dsl-runtime.md) | 前端运行时（DSL 解析→UI 渲染、命令面板、指令执行） | 全局 |
| [page-dsl.md](page-dsl.md) | 页面级 DSL（营销页/落地页的声明式描述） | doc/layout |

## 按域规划

| 域 | 文档 | 状态 |
|----|------|------|
| **doc/layout** | [page-dsl.md](page-dsl.md) | ✅ 已设计 |
| dev/schema | entity-dsl.md（实体定义语法） | 待设计 |
| dev/flow | flow-dsl.md（工作流定义语法） | 待设计 |
| runtime/policy | policy-dsl.md（权限规则语法） | 待设计 |
| runtime/agent | agent-dsl.md（智能体配置语法） | 待设计 |

## 相关文档

- [元引擎设计](../meta-engine.md)
- [页面引擎设计](../../apps/webui/page-engine.md)
- [结构化交互模式](../../apps/webui/interaction-mode-structured-view.md)
