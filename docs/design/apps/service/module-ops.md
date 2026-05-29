# 企业智能运营模块（company）设计文档

## 背景

融合两大方法论：
- **The Founder's Playbook**（AI 原生创业）：AI 驱动的四阶段创业（Idea→MVP→Launch→Scale），强调工作流自动化、Agent 编排
- **The Minimalist Entrepreneur**（精益创业）：社区优先、手动验证、流程化再产品化、盈利优先、可持续增长

company 模块为 AAF 提供内置的企业智能运营能力，覆盖从创业验证到规模化运营的全链路。

## 模块定位

- 层次：Layer 4（服务层）
- 包路径：`com.xuejiai.aaf.module.company`
- 依赖：aaf-framework（工作流引擎、AI 能力）

## 子包结构

```
com.xuejiai.aaf.module.company/
├── planning/          → 企业战略规划（含创业验证、MVP 规划）
├── okr/               → OKR 目标管理
├── erp/               → 轻量 ERP 资源管理（预算、人力、盈利追踪）
├── ops/               → 运营任务自动化（报告、同步、监控）
└── automation/        → AI 自动化编排（事件驱动规则）
```

## AI 角色体系

基于 tmp/skills 的精益创业方法论 + Founder's Playbook 的 AI 原生理念，设计以下角色：

| 角色 ID | 名称 | 职责 | 对应阶段 |
|---------|------|------|---------|
| company-strategist | 企业战略规划师 | 战略规划、市场调研、竞争分析 | Scale |
| company-validator | 创业验证顾问 | 想法验证、问题定义、社区发现 | Idea |
| company-mvp-coach | MVP 教练 | 手动验证→流程化→产品化指导 | MVP |
| company-sales-advisor | 销售增长顾问 | 首批客户获取、定价策略、营销计划 | Launch |
| company-okr-coach | OKR 教练 | OKR 制定、对齐、复盘 | 全阶段 |
| company-ops-manager | 运营自动化管理员 | 运营任务、指标监控、报告生成 | Launch/Scale |
| company-erp-analyst | 资源与盈利分析师 | 预算追踪、盈利分析、可持续增长评估 | 全阶段 |
| company-automation-engineer | 自动化编排工程师 | 事件驱动规则、Agent 联动 | Scale |
| company-culture-advisor | 企业文化顾问 | 价值观定义、团队文化、远程协作 | Scale |
| company-decision-reviewer | 精益决策审查员 | 用精益原则审查任何商业决策 | 全阶段 |

## AI 技能体系

### Idea 阶段（验证优先）

| 技能 ID | 名称 | 来源 | 核心能力 |
|---------|------|------|---------|
| company-validate-idea | 想法验证 | validate-idea | 用精益框架验证商业想法是否值得构建 |
| company-find-community | 社区发现 | find-community | 识别和评估可服务的目标社区 |
| company-market-research | 市场调研 | Playbook | TAM/SAM/SOM、竞品分析、趋势分析 |

### MVP 阶段（构建最小化）

| 技能 ID | 名称 | 来源 | 核心能力 |
|---------|------|------|---------|
| company-processize | 流程化设计 | processize | 将想法转化为可手动交付的流程 |
| company-mvp-build | MVP 构建指导 | mvp | 最小可行产品范围定义和构建策略 |
| company-pricing | 定价策略 | pricing | 成本/价值定价、分层设计、盈利计算 |

### Launch 阶段（销售与增长）

| 技能 ID | 名称 | 来源 | 核心能力 |
|---------|------|------|---------|
| company-first-customers | 首批客户获取 | first-customers | 同心圆销售策略、冷启动 |
| company-marketing-plan | 营销计划 | marketing-plan | 内容营销、社交媒体、邮件列表 |
| company-ops-report | 运营报告生成 | Playbook | 汇总指标、生成周报/月报 |
| company-ops-monitor | 运营指标监控 | Playbook | 异常检测、预警 |

### Scale 阶段（可持续规模化）

| 技能 ID | 名称 | 来源 | 核心能力 |
|---------|------|------|---------|
| company-grow-sustainably | 可持续增长评估 | grow-sustainably | 盈利优先、花费决策、招聘时机 |
| company-values | 企业价值观定义 | company-values | 文化建设、价值观落地 |
| company-planning | 战略规划生成 | Playbook | 结构化战略规划文档 |
| company-decision-review | 精益决策审查 | minimalist-review | 用 8 条原则审查商业决策 |
| company-okr-align | OKR 对齐建议 | Playbook | 层级对齐、冲突识别 |
| company-okr-review | OKR 周期复盘 | Playbook | 达成率分析、改进建议 |
| company-resource-track | 资源使用追踪 | Playbook | 消耗预测、预警 |
| company-automation-design | 自动化规则设计 | Playbook | 事件驱动规则设计 |

## 数据模型

（同前版设计，此处省略重复）

### planning - CompanyPlan

planType 扩展为：`STRATEGY/PRODUCT/GROWTH/FINANCE/VALIDATION/MVP`

### 新增字段考虑

- CompanyPlan 增加 `stage` 字段（IDEA/MVP/LAUNCH/SCALE）标记企业所处阶段
- OpsMetric 增加 `category` 字段区分指标类别（REVENUE/COST/GROWTH/ENGAGEMENT）

## 接口设计

```
# 规划（含创业验证）
GET/POST   /api/company/plans

# OKR
GET/POST   /api/company/okr/objectives
GET/POST   /api/company/okr/objectives/{id}/key-results

# ERP / 资源
GET/POST   /api/company/resources

# 运营任务
GET/POST   /api/company/ops/tasks
POST       /api/company/ops/tasks/{id}/execute
GET        /api/company/ops/metrics

# 自动化
GET/POST   /api/company/automation/rules

# 仪表盘
GET        /api/company/dashboard
```

## 核心设计原则（来自 tmp/skills）

- **社区优先**：先找到社区和问题，再构建产品
- **手动→流程→产品**：三阶段渐进自动化
- **盈利优先**：每个决策都评估对盈利的影响
- **最小化构建**：能周末发布的才是 MVP
- **花时间而非花钱**：免费渠道优先
- **客户速度增长**：不追求虚荣指标
- **可逆决策**：避免不可逆的大投入

## 与其他模块的关系

- **livechat/customerservice**：保持独立，客服数据作为运营指标数据源
- **ai/agent**：company 角色通过 agentId 关联 AI Agent 系统
- **billing**：ERP 资源追踪可关联 billing 模块的收入数据
