---
level: Theory
layer: Paradigm
purpose: 状态管理器——元引擎四层状态的持久化与隔离
status: draft
version: 0.1.0
date: 2026-05-20
author: AaronZZH
---

# 状态管理器

> 元引擎维护四层状态，严格隔离，支持工作区多维并发。

## 四层状态

```text
会话状态（Session）      临时，会话结束销毁
  - 当前对话上下文
  - 执行中的任务列表
  - 暂存的执行结果

工作区状态（Workspace）  持久，多用户共享
  - 文档 / 代码 / 工作流（OT/CRDT 合并）
  - 在线用户列表（实时同步）
  - 会话列表（Session[]，每个绑定「用户 × Assistant」）
  - 组件布局状态

系统状态（System）       持久，全局共享
  - DSL 版本库
  - 规范文档
  - 知识库索引

元数据状态（Metadata）   持久，规范驱动更新
  - 模块元数据（边界、依赖、能力接口）
  - 插件元数据（注册点、契约、版本约束）
  - 工具元数据（参数 schema、权限、调用方式）
  - UI 组件元数据（类型、行为、约束、适用角色）
```

## 四维并发隔离

工作区状态支持四个维度并发，状态管理器负责隔离边界：

| 维度 | 隔离边界 | 状态管理器责任 |
|---|---|---|
| 多端 | 会话状态同步，渲染层各自适配 | 向各端推送会话快照 |
| 多用户 | 用户私有记忆隔离，工作区状态共享 | OT/CRDT 合并，冲突升级处理 |
| 多助理 | 各 Assistant 会话上下文独立 | Team 层协调，不跨 Session 直接读写 |
| 多对话 | 会话间上下文隔离 | 支持显式跨会话引用（只读快照） |

## 渐进提交原则

状态变更遵循渐进提交，防止未确认的执行结果污染持久状态：

```text
执行结果
  ↓ 先写入会话状态（暂存）
用户确认
  ↓ 提升到工作区状态或系统状态
持久化
```

未确认的结果只存在于会话状态，会话结束自动销毁，不影响其他用户。

## 存储映射

| 状态层 | 存储介质 | 说明 |
|---|---|---|
| 会话状态 | Redis | TTL 控制，会话结束自动过期 |
| 工作区状态 | PostgreSQL + Redis | PostgreSQL 持久化，Redis 热缓存 |
| 系统状态 | PostgreSQL | 版本化存储，支持回滚 |
| 元数据状态 | PostgreSQL + Redis | 规范变更触发刷新 |

## 包结构与核心接口

```text
core/state/
├── StateManager.java               核心接口
├── SessionState.java               会话状态（临时）
├── WorkspaceState.java             工作区状态（持久，多用户共享）
├── SystemState.java                系统状态（持久，全局）
├── MetadataState.java              元数据状态（持久，规范驱动）
└── impl/
    └── RedisPostgresStateManager.java
```

```java
public interface StateManager {
    SessionState getSession(String sessionId);
    WorkspaceState getWorkspace(String workspaceId);
    void commitToWorkspace(String sessionId, String workspaceId);  // 渐进提交
}
```

## 相关文档

- [元引擎设计](meta-engine.md)
- [元数据管理器](metadata-manager.md)
- [执行调度器](execution-dispatcher.md)
