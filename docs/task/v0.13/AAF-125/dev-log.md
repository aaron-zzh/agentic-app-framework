# 开发记录：AAF-125 Studio 首页座舱化与项目主页布局拆分

执行者：AI/developer-webui + AI/Kiro

## #12501 积分趋势与分类派生 hook

✅ 2026-11-09 — AI/developer-webui

- 新增 `useCreditSpendTrend`、`useCreditSpendCategories`，`select` 派生共用缓存
- 分类改按近 30 天过滤，与"30 天总消耗"文案一致
- 抽 `CreditSpendTrendChart`，积分中心与首页共用

## #12502 首页骨架、页头与资源概览

✅ 2026-11-09 — AI/developer-webui

- 移除最近项目与模板，按 `HOME_WIDGETS` 分三行渲染
- 资源胶囊改为角色、技能、知识库、积分、项目、任务
- `DataCapsule` 增 `size="lg"`；快速创作抽为 `HomeQuickCreate`
- 水合后再渲染页头与偏好，避免 SSR 不一致

## #12503 首页积分图表卡片

✅ 2026-11-09 — AI/developer-webui

- 新增趋势与分类卡片，各自骨架、空态、失败态
- `ExpensesCategoryWidget` 增可选 `action`

## #12504 运行中智能体与运行统计（示例数据）

✅ 2026-11-09 — AI/developer-webui

- 示例数据集中于 `home-mock-data.ts`，hook 同 Query 形状
- 失败数取真实 `home-summary`，卡片标"示例数据"

## #12505 首页自定义显示

✅ 2026-11-09 — AI/developer-webui

- Zustand `persist` 只存隐藏 key，读取时过滤非法值
- Popover 逐卡开关与恢复默认；隐藏后同行补位

## #12506 项目主页承接最近项目与模板

✅ 2026-11-09 — AI/developer-webui

- 未筛选时列表上方显示最近项目与项目模板
- `RecentProjectGrid` 增 `showViewAll`，项目主页隐藏

## #12508 首页铺满一屏与全屏投屏

✅ 2026-11-09 — AI/Kiro

- 宽屏或全屏时取消限宽，中、下两行只拉伸不压缩
- 全屏对首页容器调用 Fullscreen API，不含侧栏顶栏
- 全屏时隐藏"自定义"，因 Popover 渲染在全屏元素外
- 图表改 `min-h-44`，随卡片高度拉伸

## 首页座舱风格调整（#12502、#12503 修订）

✅ 2026-11-09 — AI/Kiro

- 五张胶囊并为一条指标条，竖线分隔，逐项可跳转
- 面板改座舱样式：四角刻线、强调竖条、渐隐分隔线
- 快速创作去内层卡片，改扁平发光入口格
- 积分余额并入环形积分面板，分类悬停才显示
- 撤回 `DataCapsule` 的 `size` 与 `ExpensesCategoryWidget` 的 `action`，改导出分类解析 hook

## 首页页面文件瘦身

✅ 2026-11-09 — AI/Kiro

- 面板编排移入 `HomeWidgetGrid`，骨架移入 `HomeSkeleton`
- `useHydrated` 移入 `lib/hooks/use-hydrated.ts`
- `page.tsx` 只留容器与屏幕模式

## #12509 首页创作面板与最近生成

✅ 2026-11-09 — AI/Kiro

- 工作台加 `embedded`：不跳路由、不显示结果卡、不拉任务
- `HomeCreate` 嵌入工作台，替代快速创作入口
- 最近生成改座舱面板，15 个小缩略图，点击预览
- 文案经面板标题"文案创作"进入；数字人不上首页
- 创作区去掉外层座舱面板，工作台直接上首页；移除"文案创作"链接

## #12510 首页顶栏收起把手

✅ 2026-11-09 — AI/Kiro

- Shell store 增 `homeTopbarCollapsed`，默认收起
- 顶栏仅在 `/studio` 可收起，收起时 `inert`
- 把手置于裁剪层外，挂顶栏下沿正中
- 把手加大；收起时主题色发光，箭头跳动与外圈扩散各 3 次，尊重减少动效设置
- 把手缩小 40%（56×14px），去掉波纹与箭头动画，只留主题色光晕

## #12511～#12517 首页座舱大屏 2.0

✅ 2026-11-10 — AI/developer-webui + AI/Kiro

- `HomeCockpitLayout`：画布作背景，四周浮层；窄屏堆叠
- 画布用 xyflow：协作关系真实数据，无绑定用示例
- 运行实况示例任务按节拍推进，审批仅演示
- 右栏面板可折叠；底部区半透明可折叠，最近生成 30px
- 布局 store 增折叠、底部区、画布标签；自定义留"添加图表"
- 左栏去掉"我的资源"标题面板，每行改为 30% 透明小卡片
- 资源行平时无背景与边框，悬停才显示 20% 背景和细边框
- 资源名缩为两字，全称放悬停提示；左栏宽改 160～190px

## #12518 画布标签页改为助理 / 项目任务 / 工作流

✅ 2026-11-10 — AI/Kiro

- 三个标签页均为总览→下钻→面包屑返回
- 助理下钻复用任务流程画布，模拟器改收任务列表
- 项目任务接执行记录与执行树，无数据回退示例
- 工作流先展示一个示例 AI 工作流
- 座舱面板去掉四角刻线
