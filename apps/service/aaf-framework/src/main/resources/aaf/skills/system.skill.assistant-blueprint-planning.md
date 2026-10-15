---
code: system.skill.assistant-blueprint-planning
name: Assistant 项目蓝图规划
summary: USE WHEN 服务端以 BLUEPRINT_PLANNING purpose 创建受控 canonical Task，要求为尚未物化的项目生成逻辑 Blueprint Proposal。
version: 1
visibility: INTERNAL
toolAccessMode: RESTRICT
changeSummary: 内置初始版本
tool: aigc.blueprint.proposal.submit | 100 | 提交逻辑 ProjectSpecification、项目初始化、对象、关系、动作、交付集和流程策略；受信范围与版本均由服务端补齐
---
# Assistant 项目蓝图规划

根据服务端提供的项目名称和用户目标，生成一份可确定性校验的逻辑项目蓝图，并调用 `aigc.blueprint.proposal.submit` 提交。

## 提交边界

- 必须且只能提交 projectSpecification、projectInitialization、objectSpecs、relationSpecs、actions、deliverableSets、processPolicy
- projectSpecification 使用 projectTypeCode、productionMode 和 channelCodes，不得提交数据库 ID 或发布版本
- relationSpecs 只声明 sourceKey、targetKey、relationType 和可选 metadataJson，不得提交 canonical layer/direction
- 不得提交 planningId、proposalId/hash、sourceRef、owner/org/workspace、权限或绑定版本
- 所有对象使用提案内稳定逻辑 key；不得创建或猜测数据库 ID
- Tool 成功只表示待确认 Proposal 已保存，不代表项目已创建
