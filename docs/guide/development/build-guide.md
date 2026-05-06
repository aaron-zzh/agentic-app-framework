---
level: Practice
layer: Product
purpose: 指导 AAF 项目的构建与开发流程
status: published
version: 1.0.0
date: 2026-03-30
author: AaronZZH
scope:
  includes:
    - 构建命令
    - 开发流程
    - 启动方式
gains:
  - 能独立构建和运行 AAF 项目
---

# 构建与开发指南

## 构建说明

### 构建整个项目

```bash
cd agentic-app-framework
mvn clean install
```

### 构建单个模块

```bash
cd aaf-framework
mvn clean install
```

### 跳过测试构建

```bash
mvn clean install -DskipTests
```

## 开发流程

### 1. 框架开发者

在以下模块开发框架核心功能：
- `aaf-common` - 公共能力
- `aaf-framework` - 核心框架
- `aaf-auto-dev` - AI 自动开发
- `aaf-starter` - 自动配置

### 2. 业务开发者（用户）

在 `aaf-modules` 目录下开发业务代码：
- 使用现有模块（system、agent、workflow、knowledge）
- 或创建新模块（参考 [如何创建模块](how-to-create-module.md)）

### 3. 启动应用

在 `aaf-server` 模块启动应用：
```bash
cd aaf-server
mvn spring-boot:run
```
