---
name: doc-writing
description: 'AAF 项目文档编写与管理。USE WHEN: (1) 创建新文档或新目录、(2) 修改文档结构、元数据或 Front Matter、(3) 为目录创建或更新 README 索引、(4) 用户提到"写文档"、"创建规范"、"更新文档"、(5) 需要判断文档放哪里或如何命名。不覆盖内容体系规范本身的修改——那属于协调者职责。'
---

## 规范文档

按需加载，不要一次性全读：

- 判断文档放哪里、目录如何组织时 → [文档路由规范](../../docs/reference/content-system/doc-route-standard.md)
- 填写 Front Matter 元数据时 → [文档元数据规范](../../docs/reference/content-system/doc-meta-standard.md)
- 确定文件命名时 → [文件命名规范](../../docs/reference/content-system/file-name-standard.md)
- 编写具体内容时（Reference/Guide/Tutorial/Explanation）→ [写作规范](../../docs/reference/content-system/content-standard/)
- 了解整体设计理念时 → [内容体系概述](../../docs/reference/content-system/Readme.md)

## 快速决策

### 文档放哪里？

```
是教程（手把手）？       → docs/tutorial/
是指南（解决具体问题）？  → docs/guide/
是规范/参考（查阅）？    → docs/reference/{domain}/
是原理/背景（理解为什么）？→ docs/explanation/
是设计方案？            → docs/design/{module}/
是需求文档？            → docs/prd/{module}/
是任务/迭代文档？        → docs/task/
```

### 文件如何命名？

```
指南类：how-to-{动词}-{名词}.md
规范类：{名词}-standard.md
说明类：{名词}-guide.md
概述类：README.md 或 Readme.md（目录索引）
需求类：{feature-name}.md（kebab-case）
```

## 创建新文档

1. 确定文档类型和目标读者
2. 查路由规范确定存放路径
3. 按命名规范确定文件名
4. 填写 Front Matter（必填：level、layer、purpose、status、version、date、author）
5. 按对应类型的写作规范撰写内容

## Front Matter 必填字段

```yaml
---
level: Practice          # Reality/Thought/Theory/Practice/Meaning
layer: Model             # Principle/Paradigm/Pattern/Model/Product
purpose: 一句话说明文档用途
status: draft            # draft/active/published/deprecated
version: 1.0.0
date: YYYY-MM-DD
author: AaronZZH
---
```

## 目录 README 规范

每个目录必须有 README.md（索引），包含：
- 目录用途说明
- 文档列表（标题 + 一句话说明）
- 与其他目录的关系

## Gotchas

- **五度空间约束**：任何目录 ≤ 5 个内容项（不含 README），超过则拆子目录
- **DRY**：同一知识只在一处定义，其他地方用链接引用，不复制粘贴
- **gains 可验证**：Front Matter 的 gains 字段必须是读者读完能做到的具体事情，不是泛泛的"了解 XX"
- **规范文档只能由协调者修改**：`docs/reference/` 下的规范文件，其他 agent 只能提出修改建议
