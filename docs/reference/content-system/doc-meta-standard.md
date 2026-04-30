---
level: Practice
layer: Model
purpose: 明确定义文档元数据规范
status: published
version: 1.0.0
date: 2026-03-30
author: AaronZZH
tags:
  - 文档规范
  - 元数据
dependencies:
  - /doc-meta-explanation
related:
  - ./file-name-standard.md
scope:
  includes:
    - 维度定义
  excludes:
    - 具体写作规范
    - 文档模板
gains:
  - 能准确定位文档层级和类型
  - 能编写规范的元数据
---

# 文档元数据规范

## 维度 level

| 英文     | 定义                             | 示例                   |
| -------- | -------------------------------- | ---------------------- |
| Reality  | 知识存在的具体情境与客观限制     | 技术选型约束、团队现状 |
| Thought  | 对现实的主观解读与创造性判断     | 架构决策、设计哲学     |
| Theory   | 可解释、可预测的系统性知识结构   | DDD理论、CAP定理       |
| Practice | 将理论转化为可执行、可验证的行动 | 编码规范、部署指南     |
| Meaning  | 对行动价值与目的的主观判断       | 技术愿景、价值主张     |

### 层级 layer

| 英文      | 定义                               | 示例              |
| --------- | ---------------------------------- | ----------------- |
| Principle | 跨领域普适的底层规律、第一性原理   | 定律、DRY原则     |
| Paradigm  | 某领域主流的思维方式与世界观       | DDD领域驱动设计   |
| Pattern   | 可跨场景复用的解决方案模板         | 单例模式          |
| Model     | 特定领域可直接套用的操作模型、规范 | Git提交规范       |
| Product   | 单一场景下的具体实现与产出物       | API文档、用户手册 |

## 类型 type

**类型通过顶层分类目录设置**，不在元数据中重复定义。

### 核心类型

| 英文        | 定义                         | 对应目录        |
| ----------- | ---------------------------- | --------------- |
| Tutorial    | 带领新手从零完成一个具体任务 | `tutorial/`     |
| Explanation | 解释概念背后的原理与设计思路 | `explanation/`  |
| Guide       | 针对特定任务的分步操作说明   | `guide/`        |
| Reference   | 结构化的信息速查文档         | `reference/`    |
| Map         | 呈现知识体系全貌与关联关系   | `map/`          |

### 扩展类型

根据项目需要可扩展同类目录，遵循相同的元数据规范：

| 英文     | 定义                                   | 对应目录     |
| -------- | -------------------------------------- | ------------ |
| Design   | 架构与功能的设计方案、决策记录         | `design/`    |
| Task     | 版本计划、任务拆解与进度跟踪           | `task/`      |
| API      | 接口定义、协议规范与调用说明           | `api/`       |

## 组合定位示例

| level    | layer     | type        | 示例文章标题                      |
| -------- | --------- | ----------- | --------------------------------- |
| Thought  | Principle | Explanation | 从思想维度介绍 DRY 原则的原理     |
| Theory   | Principle | Explanation | DRY 原则的系统定义、边界与反例    |
| Practice | Principle | Guide       | 如何在代码中识别和消除重复        |
| Thought  | Paradigm  | Explanation | 为什么 DDD 改变了我们对软件的认知 |
| Practice | Model     | Reference   | Git 提交规范速查手册              |
| Reality  | Product   | Tutorial    | 从零搭建 TypeScript 项目入门教程  |
| Thought  | Paradigm  | Design      | 智能体系统架构设计方案              |
| Practice | Product   | Task        | AAF v1.0.0 版本任务计划          |

## 元数据规范

```yaml
---
# 核心定位
level: Practice # 维度（必填） Reality|Thought|Theory|Practice|Meaning
layer: Model # 层级（必填）Principle|Paradigm|Pattern|Model|Product
purpose: 一句话说明目的（必填）, 为什么需要这个文档？
status: 发布状态 # draft, published, deprecated
version: 1.0.0 # 语义化版本号
date: 2026-03-30 # YYYY-MM-DD格式 （必填）
author: 作者信息
tags: # 标签列表，便于检索和分类
dependencies: # 依赖文档，前置阅读
related: # 相关文档，延伸阅读
scope: # 内容的边界与依赖关系，包含什么，不包含什么？
  includes: # 包含内容列表
  excludes:  # 排除的概念或领域，仅在需要明确边界时使用
gains: # 预期收获列表（可验证），读者能获得什么？
---
```

## 注意事项

- **定位冲突**：按主要关注点选择，必要时拆分为多篇文档
- **定位迁移**：调整 level/layer 后，同步移动文件目录并更新相关链接
