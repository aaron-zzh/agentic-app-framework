---
level: Practice
layer: Product
purpose: User Studio v0.1 MVP 完整设计——驾驶舱 + 五度空间多 tab + 助理常驻 + 全功能闭环
status: published
version: 0.2.0
date: 2026-06-22
author: AaronZZH & Kiro
changelog:
  - 2026-06-22 | v0.2.0 全功能完整化：补全多模型生图（3 国内 + 3 国外）/ 视频 2 模型 / 智能体技能矩阵 / 爆款 4 步 / 数据资产 4 类 / 助理装扮库存 / 项目资源 M:N 关联 / 其他小工具（logo/天气/热点/会议/画像）/ 模型能力收费说明；五度空间升级为多 tab 切换交互；D7 文档管理基础版（不上 PARA）；数据隔离硬约束（按 user_id 过滤）；后端缺口清单
  - 2026-06-22 | v0.1.2 决策落定：D1-D6/D8 确认；模板市场升核心；项目枚举初步扩展；周期 4 → 4.5 周
  - 2026-06-22 | v0.1.1 调整：根目录 `app/studio/` 而非路由组
  - 2026-06-22 | v0.1 初版
---

# User Studio v0.1 MVP 设计方案

> 上游：[user-studio.md](./user-studio.md)（产品定位与待对齐问题）
> 关联：[directory-structure.md](./directory-structure.md) | [tech-stack.md](./tech-stack.md) | [interaction-modes.md](./interaction-modes.md)
> 任务：[AAF-100](../../../task/v0.1.1/AAF-100/tasks.md)

## 一句话定位

User Studio 是**项目驱动 + 助理常驻 + 五度空间多 tab** 的 AI 创作驾驶舱，让个体经营者把"创意 → 成品"的链路缩短到一个对话框。

主画像优先级：b)个体经营者 → a)内容创作者 → c)知识工作者。

## 第一性原理

终端用户为什么开 App？答：**「我有个内容/项目要产出，AI 帮我从想法到成品最快搞定」**。三个真问题倒推：

| 真问题 | MVP 必须解决 |
|-------|-------------|
| 怎么开始？ | 项目卡片 + 模板一键开局 |
| 怎么做？ | 助理常驻 + 统一对话 + 工具内嵌 |
| 怎么积累？ | 资产/作品/会话进项目，可复用 |

座舱视觉、装扮、3D、虚拟空间是**风格层**而非骨架。

## MVP 范围

### 核心功能矩阵

#### A. 驾驶舱外壳（Studio Shell）

| # | 功能 | 闭环 | 后端 |
|---|------|-----|------|
| A1 | 加载动画首屏（D4 仅首次/间隔 7 天） | 入口品牌沉浸感 → 进驾驶舱 | — |
| A2 | 顶栏（品牌/积分/通知/头像/主题切换） | 全局信息 + 入口 | `/credits/balance` `/notifications/unread-count` `/auth/me` |
| A3 | 五度空间侧栏（创作/项目/资产/知识/我） | 5 工作区入口 | 客户端 nav-config |
| A4 | **多 tab 主区**（点侧栏菜单 → 在主区开/切 tab，可关闭、拖拽排序、持久化） | 多任务并行（核心交互） | sessionStorage + Zustand |
| A5 | 助理浮球（默认右下） + 抽屉/全屏（D3） | 全局常驻 → 任意页面唤起对话 | `chatter/*` |
| A6 | 风格系统（暗色默认 / 亮色可切，质感 token） | 视觉一致性 | 扩展 `global.css` |

**A4 多 tab 交互详细设计**：

```text
┌─ 顶栏 ──────────────────────────────────────────┐
│ Brand  ｜📁创作 ●  📦项目  💎资产  🧠知识   👤我 ｜ │ ← Tab Bar
├──────────────────────────────────────────────────┤
│ [当前 active tab 内容]                          │
└──────────────────────────────────────────────────┘
```

- 行为：点侧栏 → 该工作区已开则切到对应 tab；未开则新建 tab 并 active
- 关闭：tab 右侧 × 关闭，最后一个 tab 不可关闭（保底"创作"）
- 持久化：Zustand store + sessionStorage（关浏览器丢失，刷新保留）
- URL：保留 `/studio/{tab}/...` 真实 URL，多 tab 仅是"主区视图缓存"，浏览器前进/后退仍可用
- 移动端：Tab Bar 转底部 5 项 Tab Bar（与桌面一致），单时刻只显示 active tab

#### B. 创作工作区 `/studio/create`

每个能力页头部 4 个 sub-tab，统一 Composer + Param Bar + Preview 三段布局。

| # | 能力 sub-tab | 功能闭环 | 后端 / 模型 |
|---|------------|---------|------------|
| B1 | **图像** | 文生图、图像编辑、风格预设、参数（比例/质量/数量/seed） | `/system/images/draw` 多模型路由 |
| B1.1 | 多模型支持（3 国内 + 3 国外） | 通过 `model` 参数路由：国内 `wanx/cogview/midjourney-cn` + 国外 `dalle3/sdxl/flux` | `/ai-models` 列表已有 |
| B2 | **视频** | 文生视频、图生视频、首尾帧视频、口播视频、品牌宣传 | `/aigc/video/text-to-video` `/aigc/video/image-to-video` `/aigc/video/edit` |
| B2.1 | 双模型可选 | Happyhorse / Seedance（前端 model 切换） | 后端 `videoTask.model` 已支持 |
| B2.2 | 参数 | 时长（5/10/15s）、风格（写实/动漫/赛博）、比例（16:9/9:16/1:1） | 已有 `VideoTaskRequest` |
| B3 | **文案（智能体技能）** | 内置 + 自定义 Agent 列表 → 选 → Composer 生成 | `/ai/skills`（按 priority 排序）+ `/ai/assistants/available` |
| B3.1 | 内置技能 | 口播文案、小红书、产品文案、商业分析、IP 定位、短视频脚本、标题选题推荐 | seed 进 `ai_skill_definition` |
| B3.2 | 自定义智能体 | 仅"使用"已有 Agent，**不做创建编排**（属 Admin/Dev Studio） | `/ai/agents` 列表 |
| B4 | **爆款复制（4 步向导）** | 上传爆款 → 分析 → 调整说明 → 生成 | `/aigc/copywriting`（CopywritingController 已实现 4 步） |
| B5 | **小工具箱**（折叠区） | logo 生成 / 天气查询 / 文案抽取 / 热点跟踪 / 会议记录 / 用户画像 | 详见后端缺口表 |

**B5 小工具复用策略**：

- **logo 生成** = 图像生成的预设模板（Prompt 模板：`a logo of [keyword], minimalist, vector style, white bg`），无新接口
- **文案抽取** = 已有 `/aigc/ocr`（OCR 提取文字）已实现
- **会议记录** = 已有 ASR + 摘要，复用 `/aigc/voice/asr` + Chatter 总结
- **用户画像** = `/system/user/profile` 已有，前端做"自我管理"页可视化
- **热点跟踪** 🚧 v0.1 仅做静态推荐位（mock 或人工运营），定时抓取属 v0.2（依赖 `automation/rules`）
- **天气查询** 🚧 后端补一个外部 API 代理 `/tools/weather?city=xxx`（轻量，1 个接口）

#### C. 项目工作区 `/studio/projects`

| # | 功能 | 闭环 | 后端 |
|---|------|-----|------|
| C1 | 项目列表（按状态分类：全部/进行中/草稿/完成/归档） | 卡片网格 | `/aigc/projects` |
| C2 | 新建项目（先选模板再填资料） | 模板挑选 → fork → 项目工作台 | 🚧 `/aigc/project-templates/{id}/fork` |
| C3 | 项目工作台（4 面板：生成/资产/文档/会话） | 单项目沉浸操作 | 已有 `aigc/project/AigcView.tsx` 重设计 |
| C4 | **项目类型扩展（D5）** | 加 `LIFE/STUDY/WORK/CONTENT_OPS` 枚举到 `aigc_project.type` | 🚧 单表 ALTER + 前端类型映射 |
| C5 | **项目-资源关联（D5 进阶）** | 项目可绑定专属助理 / 知识库 / 工作流 / 素材组 | 🚧 新建 `user_project_resource` M:N 表 |
| C6 | **模板市场（D6 初步）** | 系统官方模板列表 + 一键 fork 创建项目 | 🚧 新建 `user_project_template` 表 + CRUD + fork 接口 |

#### D. 资产中心 `/studio/assets`（数据资产 4 类）

| # | 类别 | 功能 | 后端 |
|---|------|-----|------|
| D1 | **作品** | 列表/分类/检索/下载/批量操作 | `/aigc/assets`（type=OUTPUT） |
| D2 | **素材** | 上传/分类/检索/标签/拖拽 | `/aigc/assets`（type=INPUT） + `/aigc/categories` `/aigc/tags` |
| D3 | **提示词** | 新建/分类/检索/收藏/复用到 Composer | `/aigc/prompt-templates`（沿用 `generation-templates`） |
| D4 | **任务历史** | 分类查看（图/视频/3D/语音）+ 重做/撤销/失败重试 | `/aigc/history` `/aigc/tasks` |

#### E. 知识空间 `/studio/knowledge`（D7 基础版，PARA 留 v0.2）

| # | 子模块 | 功能 | 后端 |
|---|------|-----|------|
| E1 | **文档管理** | CRUD + 树状分类 + 全文搜索 + Markdown/富文本编辑 + 分享 | `/docs/*` 已有 |
| E2 | **知识库** | 列表/详情/上传/检索/图谱（已实现） | `/knowledge-bases/*` 已有 |
| E3 | **收藏夹** | 文档/作品/对话片段统一收藏入口 | 🚧 新建 `user_favorite` 表（id/user_id/target_type/target_id/sort_order） |

#### F. 我 `/studio/me`（个人中心）

| # | 子模块 | 功能 | 后端 |
|---|------|-----|------|
| F1 | **账号** | 头像 / 昵称 / 手机 / 邮箱 / 微信绑定 / 密码 / 实名 | `/system/user/profile/*` 已有 + `/auth/oauth/wechat/*` |
| F2 | **会员套餐** | 套餐展示 + **模型能力说明 + 收费标准（用户强调）** + 续费/升级/降级（D8 复用 AAF-099） | `/subscriptions/*` `/credit-token-rules` 已有 |
| F3 | **积分** | 余额 + 充值 + 消费明细 + 兑换码 | `/credits/*` `/pay/orders/recharge` `/redeem-codes` 已有 |
| F4 | **邀请分销（D8）** | 邀请链接 / 战绩看板 / 提现入口（仅迁移 + 重做交互） | `/brokerage/*` 已有 |
| F5 | **助理装扮（简化版）** | 我的助理头像/服饰库存 + 装备切换；不做 3D/Lottie | 🚧 新建 `avatar_outfit` + `user_avatar_inventory` 两张表（仅 type=AVATAR/OUTFIT，不实现 COCKPIT_THEME/ACCESSORY） |
| F6 | **通知设置 / 设置** | 通知偏好 / 主题 / 语言 / 隐私 | `/notification-preferences` 已有 |

#### G. 助理常驻

| # | 形态 | 触发 | 行为 |
|---|------|-----|------|
| G1 | 浮球 | 默认右下 | 单击展开抽屉、长按拖拽位置 |
| G2 | 侧抽屉 | 浮球点开 | 480px 宽，覆盖右侧，不阻塞主区 |
| G3 | 全屏对话 | 抽屉点"全屏" | `/studio/chat`，沉浸创作 |
| G4 | 上下文吸附 | 当前 tab 是项目/资产 | 抽屉头自动显示"基于此项目" chip |
| G5 | 装扮渲染 | 抽屉头 + 浮球 | 显示装备的头像/服饰 |

### 关键交互设计

#### 五度空间多 tab 切换（A4 详解）

状态结构：

```ts
// features/studio/shell/store.ts
interface StudioTab {
  id: string                    // 唯一 id（创建时生成）
  workspace: 'create' | 'projects' | 'assets' | 'knowledge' | 'me'
  title: string                 // tab 标题（动态）
  icon: string                  // tab 图标
  url: string                   // 实际路由（保留前进后退能力）
  scrollY: number               // 滚动位置缓存（切回时恢复）
  pinned?: boolean              // 是否固定（保底创作不可关闭）
}

interface StudioShellState {
  tabs: StudioTab[]
  activeId: string | null
  open: (workspace, params?) => void   // 已开则切到，未开则新建
  close: (id) => void
  reorder: (fromIdx, toIdx) => void
  setActive: (id) => void
}
```

行为规则：

- 同一 workspace 多次打开**不重复**——只切，不开新 tab（除非用户长按"在新 tab 打开"，留接口 v0.2）
- 项目工作台例外：每个项目独立 tab（`/studio/projects/123` vs `/studio/projects/456` 是两个 tab）
- tab 满 9 个时禁开新 tab（提示用户先关闭）
- 关闭 active tab 自动 active 左侧邻居

#### 数据隔离硬约束（横切关注）

**所有 user-studio 范围内的接口必须按当前登录 `userId` 过滤**：

| 后端策略 | 实施 |
|---------|------|
| 读接口 | Service 层从 `SecurityContext` 取 `userId`，Repository 查询条件强制带 `userId = ?` |
| 写接口 | 同样取 `userId`，校验路径参数 entity 的 `userId == 当前用户`，否则 403 |
| 列表接口 | Specification/QueryDSL 强制注入 `userId` predicate |
| 跨用户操作 | 必须显式标注 `@AdminOnly` 注解，普通 endpoint 一律带 user_id 隔离 |

**技术任务 #100-DI**：审计 `module/ai/aigc/*` 所有 Controller，确保以下 endpoint 100% 带 user_id 过滤：

```text
GET    /aigc/projects                    ← 已带 ✅
GET    /aigc/assets                      ← 已带 ✅
GET    /aigc/history                     ← 待审计
GET    /aigc/tasks                       ← 待审计
GET    /docs                             ← 待审计
GET    /knowledge-bases                  ← 待审计
GET    /aigc/prompt-templates            ← 待审计
POST   /aigc/projects/{id}/...           ← 写入需校验 ownership
```

审计产物：`docs/task/v0.1.1/AAF-100/audit-data-isolation.md` 列表，标记每个接口"已隔离 / 需补 / 已修"。

### 扩展功能（v0.2-0.3，预留接口）

| # | 模块 | 触发条件 |
|---|------|---------|
| E1 | 模板市场进阶（用户上架 + 评分 + 多级分类 + 版本） | 模板生态启动 |
| E2 | 助理装扮完整版（COCKPIT_THEME / ACCESSORY / 3D / Lottie） | 商业化 |
| E3 | 知识地图 PARA + Zettelkasten + Concept Map（D7 进阶） | C 画像扩展 |
| E4 | 定时任务（自动捕获热点 / 定时生成） | 内容运营 |
| E5 | 移动端 H5 极简版独立页 | 高频页性能不达标 |
| E6 | 用户行为引导/成长任务 | 留存优化 |
| E7 | 多人协作项目 | 团队场景 |
| E8 | 无限画布 / 工作流编排 / 3D 虚拟空间 | 远期演进 |

### 明确不做

- 工作流/Agent/知识库可视化编排（属 Admin/Dev Studio）
- 模型管理 / API Key 管理 / 用户管理（管理员）
- 插件市场 / 第三方扩展
- 真 3D 座舱、Live2D 助理（v0.1 仅 2D 头像）
- 自动续费扣款（接口预留，AAF-099 已声明）

## 信息架构

```text
/                              → 营销落地页（保留 (marketing) 不动）
/login /register ...           → 复用 (auth)，不在 /studio 重做（D1）
/aigc/*                        → 旧路由保留不动，新增页面只在 /studio/* 下（D1）
/studio                        → 驾驶舱首屏 = create tab 默认
/studio/create                 → 创作工作区
  /image                       → 图像 sub-tab
  /video                       → 视频 sub-tab
  /copy                        → 文案智能体 sub-tab
  /viral                       → 爆款 4 步向导
  /tools                       → 小工具箱（logo/天气/抽取/热点/会议/画像）
/studio/projects               → 项目列表
  /new                         → 新建（先选模板）
  /[id]                        → 项目工作台（4 面板）
/studio/templates              → 模板库
/studio/assets                 → 资产中心
  /works                       → 作品
  /materials                   → 素材
  /prompts                     → 提示词
  /history                     → 任务历史
/studio/knowledge              → 知识空间
  /docs                        → 文档管理（D7 基础）
  /bases                       → 知识库
  /favorites                   → 收藏夹
/studio/me                     → 我
  /account                     → 账号
  /membership                  → 会员套餐 + 模型收费
  /credits                     → 积分
  /invite                      → 邀请分销（D8）
  /outfits                     → 助理装扮（简化）
  /settings                    → 通知 + 主题 + 语言 + 隐私
/studio/chat                   → 助理全屏对话
```

迁移策略：保留 `/aigc/*` 旧路由不动，新增页面全部走 `/studio/*`；v0.2 拆完后下线旧路由（"禁兼容层"）。

## 设计语言

### 视觉基调（A 档）

参考 `apps/webui/src/app/(dev)/examples/style-showcase/page.tsx` 提炼 5 个基础组件到 `apps/webui/src/components/studio/`：

```text
GlassCard        玻璃质感 + 内描边 + 弱光晕（信息卡片基类）
GlowButton       渐变描边 + 悬浮发光（核心 CTA）
NeonChip         霓虹标签（技能/状态/类型）
DataCapsule      数据胶囊（含趋势小箭头）
SectionHaze      背景光雾（页面顶部装饰，移动端自动减弱）
```

### 颜色 token

```text
// 暗色（默认）
bg.base    : #0a0e1a
bg.surface : #111827 ~ #1a2332（玻璃叠加 12% 白）
accent     : #6366f1 → #a855f7（蓝紫渐变）
glow       : 紫蓝光晕 4-12px blur
text       : 60% / 40% / 24% 三层灰阶

// 亮色
bg.base    : #f8fafc
bg.surface : #ffffff + 4% 内阴影
accent     : 同上但饱和度 -10%
```

不新建 token 系统，扩展现有 Tailwind config + `global.css`。

### 动效

| 场景 | 动效 | 时长 |
|-----|------|------|
| Tab 切换 | 主区 fade + scale 0.98→1 | 200ms |
| 卡片 hover | 描边光晕 + Y -2px | 180ms |
| 数据更新 | 数字 tween + 微震动 | 600ms |
| 生成完成 | 卡片入场 stagger + 短促光波 | 400ms 每张 |

性能红线：粒子/Three.js 仅首屏一次性，路由切换不重新挂载，移动端自动降级纯 CSS。

### 移动端

Web 响应式而非独立 H5。断点：

- 顶栏 64→48px、Tab Bar 转底部、四面板转上下 swipe
- 项目网格 2 列、资产网格 3 列
- 助理浮球永远显示，抽屉占满全屏
- 优先 4 个核心页：首屏 / 项目 / 创作-图像 / 会员

## 交互体验亮点

| # | 体验 | 实现 |
|---|------|------|
| ⭐1 | 多 tab 主区 | 浏览器/IDE 风格，多任务并行 |
| ⭐2 | 拖拽即上下文 | 资产/文档拖入对话框自动转引用 chip |
| ⭐3 | 生成可中断/可继续 | SSE + 任务面板（已有 `aigc-task-stream`） |
| ⭐4 | 积分前置预估 | 生成按钮悬停显示"将消耗 X 积分"，余额不足软提示 |
| ⭐5 | 结果一键转项目 | 生成结果右上角"→保存到项目" |
| ⭐6 | 离开未保存提醒 | `useUnsavedGuard` |
| ⭐7 | 空状态即引导 | "做这个 / 看这个 / 学这个"三选项 |
| ⭐8 | 键盘优先 | `⌘K` 命令面板，`/` 聚焦输入 |
| ⭐9 | 错误兜底 | 生成失败保留参数+一键重试，扣积分自动回退 |
| ⭐10 | 模型收费可见 | 选模型时实时显示"X 积分/张"，付费透明 |

## 技术架构

### 前端组件分层

```text
apps/webui/src/
├── app/studio/                      ← 新建普通目录（不用路由组）
│   ├── layout.tsx                   ← 驾驶舱外壳（侧栏 + 顶栏 + Tab Bar + 助理浮球）
│   ├── page.tsx                     ← 默认重定向到 create
│   ├── create/                      ← 创作（5 sub-tabs）
│   ├── projects/                    ← 项目列表 / 工作台
│   ├── templates/                   ← 模板库
│   ├── assets/                      ← 资产 4 类
│   ├── knowledge/                   ← 知识空间 3 类
│   ├── me/                          ← 个人中心 6 类
│   └── chat/                        ← 助理全屏
├── features/studio/                 ← 驾驶舱专属逻辑
│   ├── shell/                       ← 外壳（Sidebar/Topbar/TabBar/AssistantDock + Zustand store）
│   ├── home/                        ← 首屏组件（DataCapsule / ChatLauncher / ProjectGrid）
│   ├── theme/                       ← 主题切换 token
│   ├── nav-config.ts                ← 五度空间配置
│   └── tools/                       ← 小工具箱（logo / weather / extract / hot / meeting / profile）
└── components/studio/               ← 风格层基础组件
    ├── GlassCard.tsx
    ├── GlowButton.tsx
    ├── NeonChip.tsx
    ├── DataCapsule.tsx
    └── SectionHaze.tsx
```

复用不动：`features/aigc/`、`features/chatter/`、`features/billing/`、`features/dashboard/`、`features/knowledge/`、`components/ui/`。

### 后端缺口清单（需补强 / 新增）

| # | Endpoint / 表 | 类型 | 优先级 |
|---|---------------|------|-------|
| BE-1 | `aigc_project.type` ALTER 加枚举 LIFE/STUDY/WORK/CONTENT_OPS | DDL | P0 |
| BE-2 | `user_project_template` 表 + Repo + Service + Controller + 5-8 个种子数据 | 新表 + 4 endpoint | P0 |
| BE-3 | `user_project_resource` 表（项目 ↔ 助理/KB/工作流/素材组）+ Repo + Service + Controller | 新表 + 3 endpoint | P0 |
| BE-4 | `avatar_outfit` + `user_avatar_inventory` 表（简化：仅 AVATAR/OUTFIT）+ Repo + Service + Controller + 种子数据 | 新表 + 5 endpoint | P0 |
| BE-5 | `user_favorite` 表 + Repo + Service + Controller | 新表 + 3 endpoint | P1 |
| BE-6 | `/tools/weather?city=` 外部 API 代理（缓存 30 分钟） | 1 endpoint | P1 |
| BE-7 | `/aigc/prompt-templates` 个人提示词模板 CRUD（沿用 GenerationTemplate 加 user_id 过滤） | 加用户视图 endpoint | P0 |
| BE-8 | **数据隔离审计** ：`module/ai/aigc/*` 全部 Controller 检查 user_id 过滤 | 横切修复 | P0 |
| BE-9 | `ai_skill_definition` 种子补全（口播/小红书/产品文案/IP定位/标题选题等 7 个） | DML 种子 | P0 |
| BE-10 | `aigc_task` 模型收费查询接口 `/credit-token-rules?modelType=...`（已有但需校验前端用法） | 校验 | P1 |

### 后端 API 完整映射

| 前端模块 | endpoint | 状态 |
|---------|---------|------|
| 首屏数据胶囊 | `/dashboard/*` `/credits/balance` `/aigc/projects?pageSize=4` | ✅ |
| 项目工作台 | `/aigc/projects/{id}` `/aigc/projects/{id}/summary` `/aigc/projects/{id}/docs` | ✅ |
| 项目类型枚举 ALTER | `aigc_project.type` 加 LIFE/STUDY/WORK/CONTENT_OPS | 🚧 BE-1 |
| 项目模板 | `/aigc/project-templates` (list/detail/fork) | 🚧 BE-2 |
| 项目-资源关联 | `/aigc/projects/{id}/resources` (list/link/unlink) | 🚧 BE-3 |
| 创作-图像（多模型） | `/system/images/draw?model=wanx\|cogview\|...` | ✅ 路由复用 |
| 创作-视频 | `/aigc/video/text-to-video` `/aigc/video/image-to-video` `/aigc/video/edit` | ✅ |
| 创作-文案 | `/system/chat/messages` + `/ai/skills` + `/ai/agents` | ✅ |
| 爆款 4 步 | `CopywritingController`（已实现） | ✅ |
| 小工具-OCR | `/aigc/ocr` | ✅ |
| 小工具-天气 | `/tools/weather` | 🚧 BE-6 |
| 资产-作品/素材 | `/aigc/assets` `/aigc/categories` `/aigc/tags` | ✅ |
| 资产-提示词 | `/aigc/prompt-templates` | 🚧 BE-7 |
| 资产-任务历史 | `/aigc/history` `/aigc/tasks` | ✅ |
| 知识-文档 | `/docs/*` | ✅ |
| 知识-知识库 | `/knowledge-bases/*` | ✅ |
| 知识-收藏 | `/user-favorites` | 🚧 BE-5 |
| 我-账号 | `/system/user/profile/*` | ✅ |
| 我-会员 | `/subscriptions/*` `/credit-token-rules` | ✅ |
| 我-积分 | `/credits/*` `/pay/orders/recharge` | ✅ |
| 我-邀请 | `/brokerage/*` | ✅ |
| 我-装扮 | `/avatar-outfits` `/user-avatar-inventory` | 🚧 BE-4 |
| 助理对话 | `/chat/conversations` `/system/chat/sessions` `/system/chat/messages` `/system/chat/suggestions` | ✅ |
| 数据隔离 | 横切：所有 user-facing endpoint 校验 user_id | 🚧 BE-8 |

**后端新增工作量**：5 张新表 + ~20 个新接口 + 1 张表 ALTER + 数据隔离横切审计 + 种子数据。

### 状态管理边界（硬规则）

| 类型 | 工具 | 例 |
|-----|------|---|
| 服务端缓存 | TanStack Query | 项目、资产、积分、对话历史 |
| 客户端 UI | Zustand | Studio Shell tab 状态、侧栏折叠、主题、对话面板形态 |
| 表单 | react-hook-form + zod | 已有 |
| URL 流（生成参数） | nuqs | 已有 |

**禁止**：把项目列表/积分余额复制进 Zustand。

## 实施路径（5 个 Sprint，6 周可交付）

### Sprint 1（驾驶舱外壳 + 多 tab）— 1 周

- 风格层 5 基础组件 `components/studio/*`
- `app/studio/` 路由 + Layout（侧栏 + 顶栏 + 多 tab Bar + 助理浮球占位）
- Studio Shell Zustand store + sessionStorage 持久化
- 主题切换 token 扩展
- 首屏脚手架（数据胶囊 + 对话入口卡 + 项目网格三个区，先 mock 数据）

**交付**：可访问 `/studio`，外壳完整、tab 切换流畅、风格统一

### Sprint 2（创作工作区 + 项目工作台）— 1.5 周

- `/studio/create/{image,video,copy,viral,tools}` 5 个 sub-tab 页（迁移 `features/aigc/generation/*`）
- 多模型选择器（图像 6 模型 + 视频 2 模型）
- 爆款 4 步向导（复用 `CopywritingPanel`）
- `/studio/projects/[id]` 4 面板（迁移 `AigcView`）
- 拖拽资产入对话
- 生成结果一键保存到项目
- 积分预估 + 失败回退

**交付**：完整完成"新建项目 → 选模板 → 生图/视频/文案 → 保存"

### Sprint 3（后端补强 + 模板 + 资源关联 + 数据隔离）— 2 周（含 architect 评审）

- 🔴 architect 出 design.md + Flyway v16/v17/v18 评审通过
- 后端 BE-1 ~ BE-9 全部实施
- 数据隔离审计 BE-8 + 修复（每个 user-facing 接口测试通过）
- 前端：`/studio/templates` + `/studio/projects/new` 选模板交互
- 前端：项目工作台资源面板挂接资源关联
- 种子数据：7 个文案智能体 + 5-8 个项目模板 + 装扮 starter pack

**交付**：模板市场 + 项目资源关联 + 装扮 + 用户数据严格隔离

### Sprint 4（资产 + 知识 + 我）— 1 周

- `/studio/assets/*` 4 类视图（含提示词管理 BE-7）
- `/studio/knowledge/*` 3 子模块（文档 + 知识库迁移 + 收藏 BE-5）
- `/studio/me/*` 6 子模块（账号/会员/积分/邀请/装扮/设置）
- 模型能力说明 + 收费标准展示页

**交付**：资产/知识/我三大工作区可用

### Sprint 5（小工具 + 移动适配 + 打磨）— 0.5 周

- 小工具箱 6 工具（logo 模板 / 天气 BE-6 / OCR / 用户画像 / 会议记录 / 热点静态推荐）
- 移动端断点适配
- 加载动画首次进入（D4）
- 全链路：登录 → 引导 → 生成 → 保存 → 充值
- 性能：Lighthouse 移动端 ≥80，桌面 ≥90
- 错误兜底、空状态、加载状态全覆盖
- E2E 用例：登录、生图、视频生成、项目创建、扣积分、充值
- `pnpm check` + `pnpm acceptance` 全绿

**交付**：v0.1 生产可用

总计：**6 周（含 Sprint 3 后端补强 2 周）**。

## 验收标准（生产可用 ≠ demo 的硬指标）

- [ ] 首屏 LCP < 1.5s（桌面）/ < 2.5s（4G 移动）
- [ ] 首屏到完成第一次生图 ≤ 4 步操作
- [ ] **任何 user-facing 查询接口必须按当前 userId 过滤，跨用户访问返回 403**
- [ ] 任何接口失败有兜底 UI（不显示原始 error）
- [ ] 任何长任务（>3s）有进度 + 可中断 + 恢复
- [ ] 任何耗积分操作有事前预估 + 事后明细
- [ ] 多 tab 状态刷新保留（sessionStorage），关浏览器丢失符合预期
- [ ] 暗/亮主题切换无闪烁，状态持久
- [ ] 移动端 4 个核心页可用，无横向滚动
- [ ] PWA 可安装，离线壳能展示登录页
- [ ] E2E 关键 5 流程稳定通过
- [ ] Lighthouse a11y ≥ 90，键盘可达全部核心 CTA
- [ ] `pnpm check` + `pnpm acceptance` 全绿，blocker=0、major≤2

## 决策点（已确认）

| # | 决策 | 终选 |
|---|------|-----|
| D1 | 旧 `/aigc/*` 路由 | ✅ 保留不动，新页面只在 `/studio/*`；登录复用 `(auth)` |
| D2 | 五度空间命名 | ✅ "创作 / 项目 / 资产 / 知识 / 我" |
| D3 | 助理浮球 | ✅ 默认右下显示 |
| D4 | 落地动画 | ✅ 仅首次 + 间隔 7 天 |
| D5 | 项目类型扩展 | ✅ v0.1 扩枚举 + 新建 user_project_resource M:N 关联表 |
| D6 | 模板市场 | ✅ v0.1 初步（系统官方模板 + 一键 fork） |
| D7 | 知识地图 | ✅ v0.1 仅基础文档管理（CRUD + 分类 + 搜索 + 知识库 + 收藏），PARA 留 v0.2 |
| D8 | 邀请分销 | ✅ v0.1 仅迁移 + 优化交互 |
| D9 | 多 tab 切换 | ✅ 五工作区可同时打开多 tab，sessionStorage 持久化 |
| D10 | 数据隔离 | ✅ 硬约束：所有 user-facing endpoint 按 userId 过滤 |

## 下一步

启动条件已就绪：

- 设计方案已 published（v0.2.0）
- 任务编号 AAF-100，归 v0.1.1 迭代
- 任务清单：[docs/task/v0.1.1/AAF-100/tasks.md](../../../task/v0.1.1/AAF-100/tasks.md)
- Sprint 1（外壳 + 首屏）立即启动，开发者加载 `.kiro/agents/developer-webui.json` 资源后开工
- Sprint 3 涉及数据库新表 + 数据迁移 + 数据隔离横切，属 🔴 高风险，需 architect 出 design.md + Flyway 脚本评审
