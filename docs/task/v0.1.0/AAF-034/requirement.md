---
level: Practice
layer: Product
purpose: AAF-034 企业 Landing Page 需求规格
status: draft
version: 1.0.0
date: 2026-05-13
author: AaronZZH
---

# AAF-034 企业 Landing Page 需求规格

## 业务背景

AAF 作为开源框架需要一个对外展示的产品首页，让潜在用户（开发者、技术决策者、企业 CTO）快速了解产品价值并转化为使用者或付费客户。

**实现方式**：基于 [页面级配置驱动（PageDef）](../../../design/apps/webui/page-engine.md) 设计，通过 DSL 配置描述页面结构，AI 对话生成初始配置，可视化编辑器调整细节。不采用传统手写 React 组件方式。

## 目标受众

| 角色 | 关注点 | 期望行为 |
|------|--------|---------|
| 独立开发者 | 能做什么、怎么用、是否免费 | 点击"快速开始" → 文档站 |
| 技术负责人/CTO | 架构能力、生产级、安全性 | 点击"查看架构" → 深入了解 |
| 企业采购 | 定价、支持、合规 | 点击"联系销售" → 表单/邮件 |

## 页面结构

### 1. Hero 区域

- 主标题：一句话传达核心价值（AI 原生多智能体应用开发框架）
- 副标题：2-3 行补充说明
- CTA 按钮：[快速开始]（→ 文档站）+ [GitHub]（→ 仓库）
- 视觉：简洁背景动效，不喧宾夺主

### 2. 核心能力展示

6-8 个功能卡片，每个包含图标 + 标题 + 一句话描述：

- 多智能体协作
- 配置驱动视图引擎
- AI 感知与辅助
- 工作流引擎
- 知识库管理
- 无代码开发
- 规范驱动开发
- 外部生态整合

### 3. 产品演示区

- 产品界面截图或动画展示
- 可切换展示不同功能模块（结构化视图 / 对话式交互 / 工作流编排）

### 4. 技术亮点

面向技术决策者的架构卖点：

- TypeScript 全链路类型安全
- Spring Boot 4 + Spring AI 后端
- 五层智能架构
- 生产级安全（RBAC + 行级权限 + 审计）

### 5. 定价方案

| 方案 | 价格 | 定位 |
|------|------|------|
| 社区版 | 免费开源 | 个人开发者 / 学习 |
| 专业版 | 按月/年 | 中小团队 |
| 企业版 | 联系销售 | 大型企业 / 定制需求 |

每个方案列出包含的功能清单（✓/✗ 对比）。

### 6. 社区与生态

- GitHub Star 数 + 贡献者数
- 社区链接（Discord/微信群）
- "谁在使用"（Logo 墙，初期可省略）

### 7. Footer

- 链接分组：产品 / 开发者 / 公司
- 社交媒体图标
- 版权声明

## 非功能需求

| 维度 | 要求 |
|------|------|
| 性能 | Lighthouse Performance > 90，首屏 < 2s |
| SEO | Lighthouse SEO > 90，metadata + JSON-LD + sitemap |
| 响应式 | 桌面 / 平板 / 手机三断点适配 |
| 主题 | 支持深色/浅色模式（跟随系统 + 手动切换） |
| 可访问性 | WCAG 2.1 AA 级别 |
| 技术实现 | Next.js 静态生成（SSG），不依赖后端 API |

## 验收标准（Gherkin）

```gherkin
Feature: 企业 Landing Page

  Scenario: 首页加载
    Given 用户访问根路径 "/"
    Then 页面在 2 秒内完成首屏渲染
    And 显示 Hero 区域（主标题 + CTA 按钮）

  Scenario: CTA 跳转
    Given 用户在 Hero 区域
    When 点击 [快速开始] 按钮
    Then 跳转到文档站首页

  Scenario: 响应式布局
    Given 用户使用手机访问（宽度 < 768px）
    Then 功能卡片变为单列布局
    And 导航变为汉堡菜单

  Scenario: 深色模式
    Given 用户系统设置为深色模式
    Then 页面自动渲染深色主题
    And 所有文字和背景对比度满足 WCAG AA

  Scenario: SEO
    Given 搜索引擎爬虫访问页面
    Then 页面包含正确的 title / description / og:image
    And 存在 JSON-LD 结构化数据
    And sitemap.xml 包含该页面
```
