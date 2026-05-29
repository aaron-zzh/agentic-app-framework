# 开发记录：AAF-077 运营统计前端

执行者：AI/developer-webui

## #7701 监控图表

✅ 2026-05-29

- 引入 echarts ^5.6.0（按需注册 Line/Bar/Pie/Funnel/Gauge + 核心组件），不用 echarts-for-react 减少中间层
- 新建 features/stats/charts/：BaseChart（ResizeObserver 自适应）+ TrendChart + FunnelChart + RetentionChart + PieChart
- 新建 EChartsWidget 注册到 dashboard widgets 体系，与原 ChartWidget 并存（原 ChartWidget 保留为简单占位，EChartsWidget 为完整 ECharts 实现）
- 数据获取走 TanStack Query（lib/queries/use-stats.ts），支持 refetchInterval 自动刷新

## #7705 仪表盘配置

✅ 2026-05-29

- 新增 EChartsWidgetConfig 类型到 lib/api/dashboard.ts，WidgetType 联合类型扩展 "echarts"
- DashboardView renderWidget/createDefaultWidget 增加 echarts 分支
- AddWidgetDialog 增加"ECharts 统计"选项
- 新建 features/dashboard/presets.ts：运营/技术/财务三套预设模板（含 widgets 布局 + refreshInterval）

## 实现文件

| 文件 | 说明 |
|------|------|
| `apps/webui/package.json` | 新增 echarts ^5.6.0 |
| `src/lib/api/stats.ts` | 运营统计 API 客户端（trend/funnel/retention/overview） |
| `src/lib/queries/use-stats.ts` | TanStack Query hooks |
| `src/features/stats/charts/BaseChart.tsx` | ECharts 基础包装（按需注册 + ResizeObserver） |
| `src/features/stats/charts/TrendChart.tsx` | 折线/柱状趋势图 |
| `src/features/stats/charts/FunnelChart.tsx` | 漏斗图 |
| `src/features/stats/charts/RetentionChart.tsx` | 留存率折线图 |
| `src/features/stats/charts/PieChart.tsx` | 饼图/环形图 |
| `src/features/stats/charts/index.ts` | 图表 barrel export |
| `src/features/stats/index.ts` | feature barrel export |
| `src/features/dashboard/widgets/EChartsWidget.tsx` | ECharts 仪表盘卡片 |
| `src/features/dashboard/widgets/index.ts` | 新增 EChartsWidget 导出 |
| `src/features/dashboard/presets.ts` | 运营/技术/财务预设模板 |
| `src/features/dashboard/DashboardView.tsx` | 增加 echarts 渲染分支 |
| `src/features/dashboard/AddWidgetDialog.tsx` | 增加 ECharts 统计选项 |
| `src/lib/api/dashboard.ts` | 新增 EChartsWidgetConfig 类型 |

## 实现决策

- 选择原生 echarts 按需引入而非 echarts-for-react：减少一层包装，BaseChart 组件直接管理实例生命周期，更可控
- EChartsWidget 与原 ChartWidget 并存：原 ChartWidget 是占位实现（注释写"后续引入 recharts"），EChartsWidget 是完整 ECharts 实现，两者通过 config.type 区分，不互相干扰
- 预设模板为纯数据（presets.ts），后续可通过 API 或 UI 让用户选择模板一键创建仪表盘

## 注意事项

- echarts 包体较大（~800KB gzipped ~250KB），已通过按需注册（仅 Line/Bar/Pie/Funnel/Gauge + Canvas）控制实际 bundle
- 后端 /api/stats/* 接口需返回与 TrendPoint/FunnelStage/RetentionData/OverviewMetrics 对齐的结构
- presets.ts 目前为静态数据，后续可接入后端 API 实现用户自定义模板持久化


## 审查修复：StatsService SQL 字段名/类型修正

✅ 2026-05-29 — developer-service

- tokens 指标：`create_time` → `created_at`，`SUM(amount)` → `SUM(ABS(delta))`，增加 `operation='USE'` 过滤
- revenue 指标：`pay_time` → `success_time`，`status='SUCCESS'` → `status=10`，增加 `deleted=false`
- BehaviorService 的 user_event 字段（user_id/event_type/page/target/create_time）与 v8__stats_schema.sql 一致，无需修改
- dau/mau（sys_user.last_login_time）和 messages（chat_message.create_time）字段确认正确

> **问题**：buildMetricSql() 中 tokens/revenue 的 SQL 字段名与实际表结构不符 → 根因：编码时未对照 v5/v8 迁移脚本 → 修正为实际列名和类型