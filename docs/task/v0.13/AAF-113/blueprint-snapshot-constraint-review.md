---
level: Practice
layer: Model
purpose: 复核 aigc_project_config_snapshot 约束与索引现状，输出 #11301 迁移 dry-run 统计说明
status: active
version: 1.0.0
date: 2026-09-17
author: AaronZZH & Kiro
related:
  - tasks.md
  - ../../../../apps/service/aaf-api/src/main/resources/db/migration/v7__aigc_schema.sql
---

# #11301 BlueprintSnapshot 约束复核与迁移 dry-run 统计

本文档只做只读复核与统计说明，不新增迁移文件；本轮开发约束下 schema 变更保持既有 `v7__aigc_schema.sql` 原地修改策略，进入共享/发布环境前需重新确认 Flyway checksum。

## 现有约束清单（aigc_project_config_snapshot）

| 类型 | 名称 | 定义 | 复核结论 |
|---|---|---|---|
| CHECK | `ck_aigc_snapshot_source` | `source_type = 'CATALOG'` | 阶段一唯一来源，符合 tasks.md 阶段一范围；ASSISTANT_GENERATED 接入时（#11321）此约束必须放宽为 `IN ('CATALOG','ASSISTANT_GENERATED')`，届时需要一条新迁移，不在本轮处理 |
| CHECK | `ck_aigc_snapshot_source_ref` | `btrim(source_ref) <> ''` | 与 `BlueprintSnapshotValidator.validate` 中 `sourceRef` 非空校验重复但不冲突，数据库层兜底 |
| CHECK | `ck_aigc_snapshot_hash` | `snapshot_hash ~ '^[0-9a-f]{64}$'` | 与 `BlueprintSnapshotValidator.canonicalHash`（`AigcCanonicalRequest.canonicalSha256` 输出 64 位十六进制）一致 |
| UNIQUE INDEX | `uk_aigc_project_config_snapshot_active` | `(project_id, revision_no) WHERE deleted = FALSE` | 保证同一项目同一 revision 只有一条有效快照；`ProjectBlueprintSnapshotService.createInitialSnapshot` 落库前先查 `findFirstByProjectIdOrderByRevisionNoDesc` 判断是否已存在，双重防护 |
| FK | `fk_aigc_project_config_snapshot`（在 `aigc_project` 上） | `aigc_project.config_snapshot_id → aigc_project_config_snapshot.id ON DELETE RESTRICT` | 禁止误删有项目引用的快照，符合不可变基线诉求 |
| FK | `aigc_project_revision.config_snapshot_id` | `NOT NULL REFERENCES aigc_project_config_snapshot(id) ON DELETE RESTRICT` | 每条 revision 必须绑定一条快照，插入顺序已由 Materializer 保证（先建 snapshot 再建 revision） |

## insert-only 现状确认

- 表定义未包含任何触发器或数据库级"禁止 UPDATE"机制；insert-only 完全依赖业务层约束。
- 复核 `ProjectBlueprintSnapshotService`（#11302 新增）：只提供 `createInitialSnapshot`，不提供任何 update 方法；`AigcProjectConfigSnapshotRepository` 本身是标准 `JpaRepository`，理论上仍可被其他代码 `save()` 更新已有 id 的实体。
- 全仓搜索确认当前只有 `ProjectBlueprintSnapshotService` 持有该 repository 的写权限（`AigcProjectMaterializer` 已改为通过 service 间接访问，不再直接注入 repository）；`AigcProjectSemanticCatalogCompiler` 仅 `findById` 只读。
- 结论：insert-only 是业务层不变量，非数据库硬约束。如需数据库级硬化（例如触发器拒绝 UPDATE），属于后续加固项，本轮不新增迁移，不实施。

## 缺失但暂不新增的索引

| 场景 | 现状 | 是否本轮新增 |
|---|---|---|
| 按 `project_id` 单独查询快照历史（`findByProjectIdOrderByRevisionNoDesc`） | 复用 `uk_aigc_project_config_snapshot_active` 的 `(project_id, revision_no)` 前缀，PostgreSQL 可用该唯一索引服务范围查询，无需额外索引 | 否 |
| `aigc_project_revision.config_snapshot_id` 反查 | 无独立索引，仅 FK 隐式约束；当前只有物化路径插入 1 条 revision，量级低 | 否，留待 #11312/#11313 阶段按实际查询模式评估 |

## 迁移 dry-run 统计（开发数据库）

授权范围内不新增迁移文件，因此本节统计基于当前 `v7__aigc_schema.sql` 定义与业务代码路径的静态交叉核对，不执行真实 dry-run 脚本；如需真实统计需连接开发数据库执行只读查询，本轮环境未提供数据库连接，以下为基于代码路径的等效结论：

- **不可映射记录风险**：`AigcProjectBlueprintSnapshotPayload`（`snapshot` JSONB 列）字段自 #11301 早期阶段起已固定为类型化 record 序列化结果；历史上不存在使用旧 Map 结构写入的记录（该表为本迭代新增表，无历史脏数据迁入需求）。
- **回滚说明**：因未新增迁移文件，无需回滚脚本；若后续需要放宽 `ck_aigc_snapshot_source` 支持 ASSISTANT_GENERATED，届时新迁移的回滚策略为 `DROP CONSTRAINT` 恢复原 CHECK，不涉及数据回填。
- **约束一致性**：`snapshot_hash` 的 CHECK 正则与代码侧 `AigcCanonicalRequest.canonicalSha256`（`HexFormat.formatHex` 输出小写十六进制，SHA-256 固定 64 字符）逐字符核对一致，无需调整。

## 结论

现有约束/索引设计满足 #11301 insert-only 与不可变基线诉求，无需新增迁移文件；ASSISTANT_GENERATED 来源接入（#11321）时需要一条新迁移放宽 `source_type` CHECK，届时按标准迁移流程处理。本轮动态门禁（序列化/哈希/约束/回填单元测试）仍按 tasks.md 标注保持未完成。
