# 开发记录：AAF-100 Studio 首页四行改版

执行者：AI/developer-service + AI/developer-webui + AI/Kiro

## 实现文件

| 文件 | 说明 |
|------|------|
| `apps/service/aaf-api/src/main/resources/db/migration/v7__aigc_schema.sql` | 基线新增蓝图封面列 |
| `apps/service/aaf-api/src/main/resources/db/seed/view/v102__aigc_entity_def.sql` | 管理字段新增封面 URL |
| `apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/aigc/configuration/` | Entity、DTO、VO、Service 贯通 `coverUrl` |
| `apps/webui/src/lib/api/rest/ai/aigc/configuration.ts` | 前端蓝图契约增加 `coverUrl` |
| `apps/webui/src/features/studio/home/HomeBlueprintSection.tsx` | 蓝图 Tabs、卡片、封面回退与建项 Dialog |
| `apps/webui/src/app/studio/page.tsx` | 首页收敛为四区并增加快速创作入口 |
| `apps/webui/src/app/studio/create/page.tsx` | 增加数字人未开放占位模式 |

## 实现决策

- ✅ #10001 蓝图封面全链路 — 直接更新未部署 `v7`/`v102` 基线，未新增迁移版本。（2026-08-10）
- ✅ #10002 蓝图展示 — published 类型 Tabs、深色卡片及按类型稳定渐变回退。（2026-08-10）
- ✅ #10003 蓝图建项 — 复用现有 Query/物化链路，失败保值并用同步锁防重复。（2026-08-10）
- ✅ #10004 首页四区 — 保留独立创作页，数字人仅提供不可提交占位。（2026-08-10）
- ✅ 静态审查 — 修正默认名称为 `MM-DD`，封面状态按蓝图 ID 与 URL 重置。（2026-08-10）

- ✅ 全局配置可见性 — 六类定义模型统一为 `GLOBAL` + `@OrgIgnore`，修复内置类型被组织过滤。（2026-08-10）
- ✅ 全局配置权限 — `member`/`org_admin` 只读全局定义，写操作仅 `admin`/`super_admin`。（2026-08-10）
- ✅ 首页顺序调整 — 最近项目移动到蓝图区之前。（2026-08-10）
- ✅ 创作入口依赖修复 — 使用现有 `Card` 替代不存在的 `Alert` 组件。（2026-08-10）


## 注意事项

- 用户明确要求本轮不运行测试、`check:affected`、build、格式化或迁移命令。
- 仅执行 LSP diagnostics、文件回读、路由存在性与源码语义核对；关键文件均无 LSP 诊断。
- `#10005`–`#10008` 及正式质量门禁未执行，不生成 `review.md` 或 `test-report.md`。
- 部署前需执行完整验证并使用更新后的 Flyway 基线重建环境。
