---
level: Practice
layer: Product
purpose: AAF 页面级配置驱动设计——营销页/落地页的 DSL 描述与可视化编辑
status: draft
version: 1.0.0
date: 2026-05-13
author: AaronZZH

# 页面级配置驱动（PageDef）

## 一、设计理念

### 问题

Landing Page、产品介绍页、活动页等营销页面传统做法是手写 React 组件，每页独特、复用率低。但这类页面的结构高度模式化（Hero → 功能 → 定价 → Footer），完全可以配置驱动。

### 方案

**扩展 EntityDef 思路到页面级**：预定义一套营销组件（Section），用 PageDef 描述页面结构，AI 生成配置，可视化调整。

```text
PageDef（DSL/JSON 配置）
  → PageEngine 渲染（类比 ViewEngine）
  → 预定义 Section 组件（类比字段组件）
  → AI 对话生成 / 可视化编辑器调整
```

### 与 EntityDef 的关系

| 维度 | EntityDef（后台管理） | PageDef（营销页面） |
|------|---------------------|-------------------|
| 核心单元 | 字段（FieldDef） | 区块（SectionDef） |
| 渲染引擎 | ViewEngine | PageEngine |
| 组件注册表 | fieldComponents / cellComponents | sectionComponents |
| 数据来源 | 后端 API（动态） | 配置内嵌（静态）或 CMS API |
| 典型产出 | 列表/表单/看板 | Landing Page/活动页/产品介绍 |

**共享基础设施**：组件注册表机制、AI 生成流程、可视化编辑器框架、存储方案（sys_page_def 表）。


## 二、PageDef 核心结构

```typescript
interface PageDef {
  slug: string                    // URL 路径标识
  title: string                   // 页面标题（SEO title）
  description?: string            // SEO description
  layout: 'marketing' | 'docs' | 'blank'  // 布局模板
  theme?: PageTheme               // 页面级主题覆盖
  sections: SectionDef[]          // 区块列表（有序）
  metadata?: PageMetadata         // SEO + OG 元数据
}

interface PageTheme {
  primaryColor?: string
  darkMode?: boolean | 'system'
  fontFamily?: string
  maxWidth?: string               // 内容最大宽度
}

interface PageMetadata {
  ogImage?: string
  jsonLd?: Record<string, any>
  canonical?: string
  noindex?: boolean
}
```


## 三、Section 类型

### 预定义 Section 组件

| type | 用途 | 核心属性 |
|------|------|---------|
| `hero` | 首屏主视觉 | title, subtitle, cta[], backgroundType, media |
| `features` | 功能亮点网格 | columns, items[]{icon, title, description} |
| `showcase` | 产品截图/演示 | tabs[]{label, image, description}, autoplay |
| `pricing` | 定价方案对比 | plans[]{name, price, features[], cta, highlighted} |
| `testimonials` | 用户评价 | items[]{quote, author, avatar, company} |
| `stats` | 数据统计 | items[]{value, label, prefix?, suffix?} |
| `cta` | 行动号召横幅 | title, description, buttons[] |
| `faq` | 常见问题折叠 | items[]{question, answer} |
| `logos` | 客户/合作伙伴 Logo 墙 | items[]{name, logo, url?}, title? |
| `timeline` | 时间线/里程碑 | items[]{date, title, description} |
| `comparison` | 功能对比表 | headers[], rows[][]{text, check?} |
| `footer` | 页脚 | groups[]{title, links[]}, social[], copyright |
| `navbar` | 顶部导航 | logo, links[], cta, sticky |
| `custom` | 自定义组件 | component: string（注册表中的组件名） |

### SectionDef 通用属性

```typescript
interface SectionDef {
  id: string                      // 唯一标识（拖拽排序用）
  type: string                    // Section 类型
  props: Record<string, any>      // 类型特定属性
  style?: SectionStyle            // 样式覆盖
  visibleWhen?: PageCondition     // 条件显示（如 A/B 测试）
}

interface SectionStyle {
  backgroundColor?: string
  backgroundImage?: string
  padding?: string                // 'sm' | 'md' | 'lg' | 'xl'
  fullWidth?: boolean             // 是否突破 maxWidth
  animation?: 'fadeIn' | 'slideUp' | 'none'
}
```


## 四、PageEngine 渲染

### 路由

```text
app/(marketing)/[...slug]/page.tsx  → PageEngine 入口
```

### 渲染流程

```text
URL /about
  → 查找 PageDef('about')
  → PageEngine 遍历 sections
  → 每个 section 从 sectionComponents 获取组件
  → 注入 props + style
  → 静态生成（SSG）
```

### 与 ViewEngine 的区别

| 维度 | ViewEngine | PageEngine |
|------|-----------|-----------|
| 数据 | 运行时从 API 获取 | 构建时从配置读取 |
| 渲染 | CSR / SSR | SSG（静态生成） |
| 交互 | 重交互（CRUD/拖拽） | 轻交互（滚动/点击/动效） |
| 布局 | 固定模式（列表/表单） | 自由堆叠（Section 序列） |


## 五、AI 对话生成

### 交互流程

```text
用户："帮我创建一个 AAF 的产品介绍页，包含功能亮点、定价和 FAQ"
  → AI 生成 PageDef JSON
  → PageEngine 实时预览
  → 用户："把定价改成三列，加个企业版"
  → AI 修改 pricing section
  → 用户确认 → 保存到 sys_page_def → 生效
```

### AI 生成能力

| 场景 | 用户输入 | AI 产出 |
|------|---------|---------|
| 从零创建 | "做一个 SaaS 产品首页" | 完整 PageDef（hero + features + pricing + faq + footer） |
| 添加区块 | "加一个客户评价区" | 追加 testimonials section |
| 修改内容 | "把主标题改成..." | 修改 hero.props.title |
| 调整样式 | "功能区背景改成深色" | 修改 section.style.backgroundColor |
| 生成文案 | "帮我写功能描述" | 填充 features.items[].description |


## 六、可视化编辑器

### 编辑模式

```text
┌─────────────────────────────────────────────────────────┐
│ 📄 Landing Page 编辑器          [预览] [保存] [发布]     │
├──────────┬──────────────────────────────────────────────┤
│ 区块列表  │  实时预览                                    │
│          │                                              │
│ [≡ Hero] │  ┌────────────────────────────────────┐     │
│ [≡ 功能] │  │        Hero 区域预览                │     │
│ [≡ 定价] │  └────────────────────────────────────┘     │
│ [≡ FAQ]  │  ┌────────────────────────────────────┐     │
│ [≡ 页脚] │  │        功能亮点预览                 │     │
│          │  └────────────────────────────────────┘     │
│ [+ 添加] │                                              │
│          │                                              │
├──────────┤  点击区块 → 右侧属性面板                     │
│ 属性面板  │                                              │
│ ──────── │                                              │
│ 标题：   │                                              │
│ [输入框] │                                              │
│ 副标题： │                                              │
│ [输入框] │                                              │
│ CTA：    │                                              │
│ [配置]   │                                              │
└──────────┴──────────────────────────────────────────────┘
```

### 编辑能力

| 操作 | 交互 |
|------|------|
| 添加区块 | [+ 添加] → 选择 Section 类型 → 插入 |
| 排序区块 | 左侧列表拖拽排序 |
| 删除区块 | 区块右键 → [删除] |
| 编辑内容 | 点击区块 → 属性面板表单编辑 |
| 样式调整 | 属性面板 → 样式 Tab（背景/间距/动效） |
| 实时预览 | 右侧实时渲染当前配置 |
| AI 辅助 | 属性面板底部 [AI 生成文案] / [AI 优化] |


## 七、存储与发布

### 数据库

```sql
CREATE TABLE sys_page_def (
  id          BIGINT PRIMARY KEY,
  slug        VARCHAR(128) UNIQUE NOT NULL,
  config      JSONB NOT NULL,              -- 完整 PageDef
  status      VARCHAR(16) DEFAULT 'draft', -- draft / published
  published_at TIMESTAMP,
  version     INT DEFAULT 1,
  created_by  BIGINT,
  created_at  TIMESTAMP,
  updated_at  TIMESTAMP
);
```

### 发布流程

```text
编辑（draft）→ 预览确认 → [发布] → status='published' + 触发 SSG 重建
  → Next.js ISR / 按需重验证 → CDN 缓存更新
```

### 版本管理

- 每次发布保存版本快照
- 支持回滚到历史版本
- 草稿与已发布版本独立（编辑不影响线上）


## 八、与结构化视图引擎的共享

| 共享层 | 说明 |
|--------|------|
| 组件注册表机制 | `registerSectionType()` 类比 `registerFieldType()` |
| AI 生成流程 | 对话 → JSON → 预览 → 确认，完全相同 |
| 可视化编辑器框架 | 左侧列表 + 右侧预览 + 属性面板，复用布局 |
| 后端存储 | sys_page_def 类比 sys_entity_def，同一套 CRUD |
| 插件扩展 | 第三方可注册自定义 Section 类型 |
| 错误边界 | Section 级 ErrorBoundary，单区块报错不影响整页 |


## 九、PageDSL 语法

> 📄 **独立文档**：[page-dsl.md](../../framework/dsl/page-dsl.md)

PageDSL 是 Magic-DSL 在 `doc/layout` 域的具体实现，提供比 JSON 更简洁的人类友好语法。核心特征：

- 区块名即类型，无需声明 type
- Markdown 子集表达内容（`#` 标题、`>` 描述、`-` 列表、`[]()` 链接）
- `|` 管道分隔同行多字段，位置语义由注册表约定
- `$` 表达式支持动态数据绑定
- 编译时解析为 JSON PageDef，零运行时开销


## 十、核心优势

| 优势 | 说明 |
|------|------|
| **AI 原生** | 对话生成完整页面配置，自然语言修改内容/样式/结构 |
| **配置即页面** | JSON PageDef 描述页面结构，无需写 React 组件 |
| **CDN 级性能** | Next.js SSG 静态生成，构建时渲染，全球 CDN 分发 |
| **类型安全** | TypeScript 接口定义，编辑时 JSON Schema 校验 + 自动补全 |
| **可视化编辑** | 区块拖拽排序 + 属性面板 + 实时预览，非技术人员可操作 |
| **插件扩展** | `registerSectionType()` 注册自定义区块，第三方可贡献 |
| **版本管理** | 草稿/已发布分离，历史版本可回滚 |
| **与实体引擎统一** | 共享组件注册表、AI 生成流程、存储方案，一套基础设施两种产出 |
