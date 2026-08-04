---
level: Practice
layer: Product
purpose: Content Studio 产品工作台与统一 AIGC 工程领域设计目录入口
status: active
version: 1.7.1
date: 2026-08-05
author: AaronZZH & Kiro
changelog:
  - 2026-08-05 | v1.7.1 最终源码静态审查后标记统一 AIGC 工程设计为 active
  - 2026-08-04 | v1.7.0 明确八个 AIGC 核心领域子模块、ExecutionBinding 归属与专业能力适配器边界
  - 2026-08-04 | v1.6.1 明确三份核心文档的唯一职责、八个 AIGC 子模块与工程真理源边界
  - 2026-08-04 | v1.6.0 统一 Content Studio 产品名称与 AIGC 工程领域，补充概念映射和端到端生命周期导航
  - 2026-08-04 | v1.5.0 新增 Content Studio 与 AIGC 整合技术设计、目标表结构和文件生命周期方案
  - 2026-07-26 | v1.4.0 产品设计重构为项目类型优先入口与项目图谱工作台，并改用 Project 命名
  - 2026-07-25 | v1.3.0 竞品界面分析升级为完整竞品分析并重命名
  - 2026-07-24 | v1.2.0 新增竞品界面分析与截图证据索引
  - 2026-07-24 | v1.1.0 新增能力与概念地图
  - 2026-07-24 | v1.0.0 初版
scope:
  includes:
    - 企业广告与 OPC 内容创作产品设计
    - 产品能力、技术概念与领域概念地图
    - Content Studio 与 AIGC 整合技术设计
    - 参考产品竞品分析与界面证据
    - 跨 WebUI、Service 与智能层的产品方案
  excludes:
    - 单端通用技术规范
    - 具体任务实现记录
gains:
  - 能定位内容创作产品的统一设计方案
  - 能查找功能、技术与领域概念的映射
  - 能实施统一项目、媒体、资产、作品与文件生命周期模型
  - 能追溯竞品结论对应的官网与截图证据
---

# Content Studio 设计

> Content Studio 是面向用户的产品与创作工作台名称；AIGC 是内容生产能力唯一的工程领域。产品设计使用 Project、ProjectGraph、Media、Asset、Work 用户语义，工程实现统一映射为 `AigcProject`、`AigcProjectObject`、`AigcMedia`、`AigcAsset`、`AigcWork`，不建立平行 `content` 业务模块。AIGC 的业务内核由 `configuration`、`brand`、`project`、`media`、`execution`、`work`、`timeline`、`task` 八个核心领域子模块组成；`ExecutionBinding` 明确归 `execution`，Asset 归 `media`。`image`、`video`、`voice`、`model3d`、`copywriting` 等是面向模型、工具和供应商的专业能力适配器，不是平行业务内核。

## 文档列表

1. [Content Studio 产品设计](./content-studio-design.md) — 产品规则、对象语义、端到端生命周期、工作台交互与验收边界的唯一产品真理源。
2. [Content Studio 技术设计](./content-studio-tech.md) — 包、接口、命名矩阵、配置版本、BaseCrud、数据、文件生命周期与一次性迁移结果的唯一工程真理源。
3. [Content Studio 能力与概念地图](./content-studio-capability-concept-map.md) — 只导航核心概念、代码映射、功能、信息架构与场景，不复制产品细则或技术清单。
4. [Content Studio 竞品分析](./content-studio-competitor-analysis.md) — 分析 Flova、LibTV、Miora、ChatCut 与 WorkRally，并说明 AAF 的设计取舍。
5. [竞品截图证据](./tmp/) — 竞品分析使用的本地界面截图。
