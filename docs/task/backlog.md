# 用户故事看板

所有用户故事的唯一来源。条目由 product agent 细化后登记，协调者维护编号和状态。

编号规则：`AAF-{三位序号}`，全局递增，不按版本重置。当前最大编号：**AAF-026**，新一级用户故事从 AAF-027 开始。添加新条目后必须同步更新此行。

## 当前迭代（v0.1.0）

- [ ] AAF-018 (创建: 05-03) 开源框架授权控制（启动时 JWT 校验 + 分散式权限耦合，零运行时开销）需求规格：[requirement.md](v0.1.0/AAF-018/requirement.md) (依赖: AAF-023)
- [ ] AAF-019 (创建: 05-03) 文档管理系统（块状存储 + Neo4j 关系图谱 + 全文检索 + 本地双向同步）需求规格：[requirement.md](v0.1.0/AAF-019/requirement.md)
- [ ] AAF-020 (创建: 05-03) 聊天协作界面（流式对话 + AI Tool 修改文档 + SSE 推送 + Lexical 编辑器）需求规格：[requirement.md](v0.1.0/AAF-020/requirement.md)
- [ ] AAF-021 (创建: 05-03) Auto Dev 平台（AI 协作开发监控与管理）需求规格：[requirement.md](v0.1.0/AAF-021/requirement.md)
- [ ] AAF-022 (创建: 05-03) 用户与访问控制模块（JWT 认证 + RBAC + Actor 统一抽象 + 组织隔离）需求规格：[requirement.md](v0.1.0/AAF-022/requirement.md) (依赖: AAF-023)
- [ ] AAF-023 (创建: 05-03) 项目基础框架搭建（后端 Maven 多模块 + Flyway 初始化 + 前端 Next.js 脚手架）(依赖: 无，其他所有 Epic 依赖此项)
- [ ] AAF-024 (创建: 05-05) 协作基础设施优化（真理源归一 + Agent Resources 精确配置 + 流程产出物分级 + ADR 目录）依据：[开发流程及协作规范分析 2026-05-05](../task/v0.1.0/AAF-023/dev-log.md)
- [ ] AAF-025 (创建: 05-06) 在线源码查看系统（目录浏览 + 语法高亮 + 文件搜索 + Git 历史 + Agent 代码引用跳转）(依赖: AAF-023, AAF-021)

## 待排期

（暂无）

## 已完成

- [x] AAF-026 (创建: 05-06, 完成: 05-07) 对外文档站点（Fumadocs + 读取项目 docs/ 目录 + 静态部署）
