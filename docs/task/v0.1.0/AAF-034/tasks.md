---
level: Practice
layer: Product
purpose: AAF-034 企业 Landing Page 技术任务清单
status: pending
version: 2.0.0
date: 2026-05-13
author: AaronZZH
---

# 企业 Landing Page（AAF-034）

> 需求：[requirement.md](requirement.md)
> 设计：[页面级配置驱动（PageDef）](../../../design/apps/webui/page-engine.md)
> 负责人：architect + developer-web | 创建：05-13

## 任务列表

> **执行策略**：先建 PageEngine 基础设施（与 ViewEngine 并行的页面渲染引擎），再实现预定义 Section 组件，最后用 PageDef 配置生成 Landing Page。
> 前置：AAF-028 #4（组件注册表机制）完成。

### PageEngine 基础设施

1. [ ] #1 PageDef 类型定义 — developer-web
   - 定义 `PageDef` / `SectionDef` / `SectionStyle` / `PageTheme` / `PageMetadata` 接口
   - 定义 `sectionComponents` 注册表
   - `registerSectionType()` 扩展 API
   - verify: TypeScript 类型检查通过

2. [ ] #2 PageEngine 渲染器 — developer-web (依赖: #1)
   - `app/(marketing)/[...slug]/page.tsx` 动态路由
   - PageEngine 组件：遍历 sections → 从注册表获取组件 → 注入 props + style
   - Section 级 ErrorBoundary
   - 静态生成（SSG）支持
   - verify: 配置一个简单 PageDef 后页面正确渲染

3. [ ] #3 PageDef 存储与加载 — developer-api + developer-web (依赖: #2)
   - sys_page_def 表（slug / config / status / version）
   - API：CRUD + 发布/回滚
   - 前端启动时加载已发布的 PageDef
   - 草稿与已发布版本独立
   - verify: 保存 PageDef 后页面可访问

### 预定义 Section 组件

4. [ ] #4 NavbarSection + FooterSection — developer-web (依赖: #2)
   - Navbar：logo + 导航链接 + CTA + sticky + 移动端汉堡菜单
   - Footer：链接分组 + 社交媒体 + 版权
   - 响应式适配
   - verify: 各断点布局正确

5. [ ] #5 HeroSection — developer-web (依赖: #2)
   - 主标题 + 副标题 + CTA 按钮组
   - backgroundType：gradient / image / particles / plain
   - 响应式：桌面居中 / 移动端全宽
   - verify: 不同 backgroundType 正确渲染

6. [ ] #6 FeaturesSection — developer-web (依赖: #2)
   - 功能卡片网格（图标 + 标题 + 描述）
   - columns 配置（2/3/4 列）
   - 响应式：桌面多列 / 手机单列
   - verify: 6 个卡片在各断点正确排列

7. [ ] #7 PricingSection — developer-web (依赖: #2)
   - 定价卡片（方案名 + 价格 + 功能列表 + CTA）
   - highlighted 推荐标记
   - 功能对比 ✓/✗
   - verify: 三列定价卡片正确渲染

8. [ ] #8 ShowcaseSection + StatsSection + FAQSection — developer-web (依赖: #2)
   - Showcase：Tab 切换产品截图/动画
   - Stats：数据统计（数字动画）
   - FAQ：折叠面板（Accordion）
   - verify: 各组件交互正确

9. [ ] #9 CTASection + TestimonialsSection + LogosSection — developer-web (依赖: #2)
   - CTA：行动号召横幅
   - Testimonials：用户评价卡片轮播
   - Logos：客户 Logo 墙
   - verify: 各组件渲染正确

### Landing Page 配置生成

10. [ ] #10 AAF Landing Page PageDef 配置 — developer-web (依赖: #4~#9)
    - 编写 AAF 产品首页的 PageDef JSON 配置
    - 内容：navbar → hero → features → showcase → stats → pricing → faq → cta → footer
    - SEO metadata 配置
    - verify: `/` 路由渲染完整 Landing Page

11. [ ] #11 深色模式 + 滚动动效 — developer-web (依赖: #10)
    - PageTheme.darkMode 支持（跟随系统 + 手动切换）
    - SectionStyle.animation 实现（Intersection Observer 触发）
    - 平滑滚动锚点导航
    - verify: 深色/浅色切换无闪烁，滚动动效流畅

12. [ ] #12 SEO 优化 — developer-web (依赖: #10)
    - metadata 自动生成（title / description / og:image）
    - JSON-LD 结构化数据
    - sitemap.xml 包含页面
    - verify: Lighthouse SEO > 90

### 可视化编辑器（基础版）

13. [ ] #13 页面编辑器 UI — developer-web (依赖: #3)
    - `/workspace/admin/pages` 页面管理列表
    - 编辑器：左侧区块列表（拖拽排序）+ 右侧实时预览
    - 点击区块 → 属性面板（表单编辑 props）
    - [+ 添加区块] → 选择 Section 类型
    - [预览] [保存] [发布] 操作
    - verify: 拖拽排序区块后预览实时更新

<!-- 状态标记：[ ] 待开始 | ⏳ 进行中 | ✅ 已完成 -->
