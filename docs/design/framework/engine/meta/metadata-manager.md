---
level: Theory
layer: Paradigm
purpose: 元数据管理器——统一管理四类元数据，规范变更自动触发同步
status: draft
version: 0.1.0
date: 2026-05-20
author: AaronZZH
---

# 元数据管理器

> 元数据管理器统一管理四类元数据，规范变更时自动触发同步，并负责语义漂移检测。

## 四类元数据

| 类型 | 内容 | 更新触发 |
|---|---|---|
| 模块元数据 | 边界、依赖、能力接口 | 规范文档变更 |
| 插件元数据 | 注册点、契约、版本约束 | 插件注册/更新 |
| 工具元数据 | 参数 schema、权限要求、调用方式 | 工具注册/更新 |
| UI 组件元数据 | 类型、行为、约束、适用角色 | 组件注册/更新 |

## 规范变更触发链

```text
规范变更（docs/ 写入）
  ↓ 元引擎检测
元数据管理器更新对应元数据
  ├─ 模块元数据：边界、依赖、能力接口
  ├─ 插件元数据：注册点、契约、版本约束
  ├─ 工具元数据：参数 schema、权限要求、调用方式
  └─ UI 组件元数据：类型、行为、约束、适用角色
  ↓ 同步刷新
工作区界面 + 对话区可用能力 + Agent 可调用工具
```

## 工具元数据规范

每个工具注册时必须提供完整元数据：

```text
基础元数据：
  name          工具唯一标识
  description   语义描述（供 AI 理解何时调用）
  domain        所属域（dev / runtime / doc）
  version       版本号，支持多版本共存

参数元数据：
  inputSchema   输入参数 JSON Schema（类型、约束、示例）
  outputSchema  输出结果 JSON Schema
  requiredParams 必填参数列表

权限元数据：
  requiredPermissions  调用所需权限列表
  allowedRoles         允许调用的角色
  dataScope            数据访问范围（用户私有 / 工作区 / 全局）

执行元数据：
  timeout        最大执行时长
  retryPolicy    重试策略（次数、间隔、退避）
  sandboxRequired 是否需要沙箱隔离
  costEstimate   预估 Token / 资源消耗

知识绑定元数据：
  knowledgeRefs  关联知识库文档 ID 列表
  exampleRefs    关联示例文档 ID 列表
  specRef        使用规范文档 ID
```

## 语义漂移检测

元数据管理器定期对比工具行为与文档描述的一致性：

```text
定期扫描：工具实际行为 vs 文档描述
  ↓ 发现不一致
触发告警，暂停该工具
  ↓ 等待人工修正
修正后重新激活
```

不依赖开发者自觉遵守，由引擎在运行时强制执行。

## 知识能力绑定

每个工具必须与知识库中的领域文档强绑定：

- 注册时关联使用规范文档、示例文档、领域知识文档
- 知识库更新时，关联工具的调用规范同步校验一致性
- Agent 调用工具后，执行结果自动归档到对应知识库条目，形成「工具执行 → 知识生长」正向闭环

## 包结构与核心接口

```text
core/metadata/
├── MetadataManager.java            核心接口
├── ModuleMetadata.java             模块元数据
├── ToolMetadata.java               工具元数据（含知识绑定）
├── PluginMetadata.java             插件元数据
├── ComponentMetadata.java          UI 组件元数据
├── DriftDetector.java              语义漂移检测
└── impl/
    └── DefaultMetadataManager.java
```

## 相关文档

- [元引擎设计](meta-engine.md)
- [状态管理器](state-manager.md)
- [执行调度器](execution-dispatcher.md)
