---
level: Practice
layer: Product
purpose: AAF-086 Beta 发布的技术任务清单
status: pending
version: 1.0.0
date: 2026-05-19
author: AaronZZH
---

# Beta 发布（AAF-086）

> 负责人：architect + product | 创建：05-19

## 任务列表

### 发布准备

1. [x] #8601 发布说明
   - 版本号确定（语义化版本 0.x.0-beta.1）
   - 功能清单（新增功能列表、改进项、已知限制）
   - 破坏性变更说明（API 变更、配置变更、迁移步骤）
   - 致谢与贡献者列表
   - verify: 发布说明覆盖所有变更，破坏性变更有迁移指南

2. [x] #8602 迁移指南
   - 从零开始安装指南（全新部署步骤）
   - 数据库迁移脚本汇总（Flyway 迁移顺序验证）
   - 配置迁移（旧配置→新配置映射表、自动迁移脚本）
   - 兼容性矩阵（支持的浏览器/Node/Java 版本）
   - verify: 按迁移指南可从空环境成功部署

3. [x] #8603 已知问题列表
   - 已知 Bug 列表（暂不修复的问题、临时 Workaround）
   - 功能限制说明（Beta 阶段不支持的功能、计划支持时间）
   - 性能限制（已知性能瓶颈、建议使用规模）
   - 安全注意事项（Beta 阶段安全建议、不建议生产使用的场景）
   - verify: 已知问题列表完整，Workaround 可操作

### 发布执行

4. [x] #8604 反馈渠道建立
   - GitHub Issues 模板（Bug 报告/功能请求/问题咨询模板）
   - 社区讨论区（GitHub Discussions 分类配置）
   - 反馈收集表单（用户体验问卷、功能优先级投票）
   - 反馈处理流程（收集→分类→评估→排期→回复）
   - verify: 反馈渠道可用，提交 Issue 后流程正确触发

5. [ ] #8605 正式发布
   - 发布 Checklist 执行（交付清单全部通过）
   - Git Tag 创建（v0.x.0-beta.1）+ GitHub Release
   - Docker 镜像发布（Docker Hub / 阿里云容器镜像）
   - 公告发布（README 更新、博客文章、社交媒体）
   - verify: Release 创建成功，Docker 镜像可拉取并运行