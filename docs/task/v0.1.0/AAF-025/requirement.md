---
level: Practice
layer: Model
purpose: 在线源码查看系统需求规格
status: active
version: 1.0.0
date: 2026-05-06
author: AaronZZH
---

<!-- ⚠️ 早期需求，未经过六问分析。进入开发前由 product agent 补充需求分析章节 -->
<!-- scope_mode: hold -->

# 在线源码查看系统

任务编号：AAF-025

## 背景

Auto Dev 平台生成/修改代码后，开发者需要在 Web 上直接查看和 review，不用切换到本地 IDE。同时 Agent 在对话中引用代码时，用户能直接跳转查看上下文。

## 用户故事

### US-1：源码目录浏览

**作为** 框架开发者，**我希望** 在 Web 上浏览项目源码目录结构，**以便** 快速了解项目组织和定位文件。

#### 验收标准

```gherkin
Feature: 源码目录浏览

  Scenario: 浏览目录树
    Given 项目 Git 仓库已配置
    When 打开源码查看页面
    Then 左侧展示目录树（按文件夹/文件层级）
    And 点击文件在右侧展示内容

  Scenario: 切换分支
    Given 项目有多个分支
    When 选择不同分支
    Then 目录树和文件内容切换到对应分支
```

### US-2：语法高亮显示

**作为** 框架开发者，**我希望** 源码文件以语法高亮方式展示，**以便** 快速阅读代码。

#### 验收标准

```gherkin
Feature: 语法高亮

  Scenario: Java 文件高亮
    Given 打开一个 .java 文件
    Then 关键字、字符串、注释等以不同颜色高亮
    And 显示行号

  Scenario: 行号链接
    Given 查看某个文件
    When 点击行号
    Then URL 更新为包含行号的链接（可分享）
```

### US-3：文件搜索

**作为** 框架开发者，**我希望** 能按文件名或内容搜索源码，**以便** 快速定位目标代码。

#### 验收标准

```gherkin
Feature: 文件搜索

  Scenario: 按文件名搜索
    Given 输入文件名关键词 "UserService"
    Then 返回匹配的文件路径列表

  Scenario: 按内容搜索
    Given 输入代码片段 "ActorType.HUMAN"
    Then 返回包含该内容的文件列表，高亮匹配行
```

### US-4：Git 历史查看

**作为** 框架开发者，**我希望** 查看文件的 Git 提交历史和 blame 信息，**以便** 了解代码变更原因和责任人。

#### 验收标准

```gherkin
Feature: Git 历史

  Scenario: 查看文件历史
    Given 打开某个文件
    When 点击"历史"
    Then 展示该文件的提交记录列表（时间、作者、消息）

  Scenario: Blame 视图
    Given 打开某个文件
    When 切换到 blame 视图
    Then 每行显示最后修改的提交信息和作者
```

### US-5：Agent 代码引用跳转

**作为** 框架开发者，**我希望** Agent 在对话中引用代码时能直接跳转到源码查看页面，**以便** 快速查看完整上下文。

#### 验收标准

```gherkin
Feature: Agent 代码引用跳转

  Scenario: 对话中代码引用
    Given Agent 在对话中提到 "修改了 UserService.java:42"
    When 点击该引用
    Then 跳转到源码查看页面，定位到第 42 行并高亮

  Scenario: Auto Dev 生成结果跳转
    Given Auto Dev 生成了新文件
    When 在监控面板点击生成的文件名
    Then 跳转到源码查看页面展示该文件内容
```

## 约束

- 只读查看，不支持在线编辑（编辑走文档系统或本地 IDE）
- 支持 Git 仓库，通过 JGit 读取
- 大文件（>1MB）不渲染，提示下载
- 二进制文件不展示内容

## 相关设计

- Auto Dev 平台：[AAF-021 requirement.md](../AAF-021/requirement.md)
