# 产品经理智能体上下文设计（仅示例）

## 角色概述

产品经理负责需求分析、用户故事细化、验收标准制定、版本规划。需要掌握项目全局信息（路线图、backlog）和需求规范，但不需要源码和技术实现细节。

## 上下文分层

### Steering（自动加载，所有智能体共享）

| 文件                              | 说明                         | 产品经理是否需要               |
| --------------------------------- | ---------------------------- | ------------------------------ |
| `.kiro/steering/collaboration.md` | 协作规则、任务管理、质量门控 | ✅ 需要，了解协作流程和审核机制 |

### Agent Resources（`file://`，启动时全量加载）

只放产品经理**每次任务都需要**的文件，控制总量。

| 资源                           | 说明                       | 必要性                   |
| ------------------------------ | -------------------------- | ------------------------ |
| `file://AGENTS.md`             | 项目概述、技术栈、团队架构 | ✅ 了解项目全貌和协作对象 |
| `file://docs/roles/product.md` | 角色职责和输出要求         | ✅ 核心指导文件           |
| `file://docs/prd/Readme.md`   | 需求管理规范（来源、生命周期、变更流程） | ✅ 需求工作的核心规范     |

**预估上下文占用**：~3 个文件，约 2500-3500 tokens（< 5%）

### Skills（`skill://`，按需加载）

产品经理偶尔需要参考但不必每次加载的内容。

| Skill                     | 触发场景       | 说明                                                                 |
| ------------------------- | -------------- | -------------------------------------------------------------------- |
| `requirement-development` | 编写需求文档时 | 需求编写规范、用户故事格式、Gherkin 语法、需求层级说明、需求文档模板 |

**Skill 内容来源**：从 `docs/prd/Readme.md`（需求管理规范）和 `docs/prd/requirement-management.md`（需求生命周期）中提取关键规则。

### Knowledge Base（搜索时加载）

产品经理需要检索但不应全量加载的大数据集。

| 知识库          | 内容                        | 用途                                     |
| --------------- | --------------------------- | ---------------------------------------- |
| `backlog`       | `docs/task/backlog.md`      | 查询当前任务状态、已有需求、依赖关系     |
| `user-feedback` | `docs/prd/user-feedback.md` | 检索用户反馈作为需求来源                 |
| `improvements`  | `docs/prd/improvements.md`  | 检索改进意见作为需求来源                 |

## 不需要加载的内容

| 内容                         | 原因                                        |
| ---------------------------- | ------------------------------------------- |
| 源码（`src/`）               | 产品经理不做编码，不需要源码上下文          |
| 设计文档（`docs/design/`）   | 由 architect 负责，产品经理只需在需求中链接 |
| 测试报告（`test-report.md`） | 由 tester 负责，产品经理在验收阶段按需读取  |
| 编码规范、架构文档           | 技术细节，非产品经理职责范围                |
| Nx/Vercel 相关 skill         | 构建部署工具，与需求分析无关                |

## 智能体配置

```json
{
  "name": "product",
  "description": "产品经理，负责需求分析和验收标准",
  "prompt": "你是 AAF 框架的产品经理，负责将用户想法细化为结构化需求并定义验收标准。",
  "tools": ["read", "write", "code", "knowledge"],
  "resources": [
    "file://AGENTS.md",
    "file://docs/reference/team/roles/product.md",
    "file://docs/prd/Readme.md",
    "skill://.kiro/skills/requirement-development/SKILL.md"
  ],
  "knowledgeBase": [
    "docs/task/backlog.md",
  ],
  "hooks": {
    "stop": [{ "command": "node .kiro/hooks/log-output.js product" }]
  }
}
```

## 待创建的 Skill

### requirement-development

```
.kiro/skills/requirement-development/
├── SKILL.md              # 需求编写指南（触发词、核心规则）
└── references/
    ├── requirement-spec.md  # 完整需求管理规范（从 docs/prd/Readme.md 提取）
    └── requirement-template.md  # 需求文档模板（从 docs/task/_template/requirement.md 提取）
```

**SKILL.md frontmatter**：

```yaml
---
name: requirement-development
description: 编写需求文档时的规范指南。包含用户故事格式、Gherkin 验收标准语法、需求层级（用户故事→需求规格）、文件命名和目录结构。当需要编写 requirement.md、细化用户故事、定义验收标准时激活。
---
```
