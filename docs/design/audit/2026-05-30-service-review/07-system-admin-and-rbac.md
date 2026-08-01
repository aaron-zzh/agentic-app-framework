# 07 系统管理与 RBAC

> 覆盖：用户、角色、权限点、行级数据权限、访问策略；以及方法级鉴权收口后的剩余角色与资源授权风险。

## 问题清单

| 编号 | 级别 | 位置 | 问题 | 修复建议 |
|------|------|------|------|---------|
| B9 | 🔴 | `system/user/controller/UserController`、`company/controller/CompanyController`、`stats/StatsController`、部分管理与跨用户端点 | 部分端点仍以过宽角色放行，SELF 资源归属与组织边界尚未清零 | 按“平台角色 / 组织角色 / SELF”细化授权；在 service 层补资源归属与 org 过滤；持续维护 10 区剩余矩阵直至逐项闭合 |
| B10 | 🔴 | `module/tool/ToolController#viewSource`、工具列表端点 | 身份或角色门控不能替代具体工具的资源级授权；源码查看与列表仍需按 owner/org/share scope 限定可见范围 | 统一调用工具资源授权服务，按所有者、组织与共享范围过滤列表并校验源码访问 |
| M17 | 🟠 | `permission/service/PermissionService#assignRolesToUser` vs `assignPermissionsToRole` | 语义不对称：分配权限给角色是“删后重建”，分配角色给用户是“只增不删”（取消勾选不生效） | 统一为“全量覆盖”或明确文档化差异；权限撤销语义确认前不直接修改 |
| 重复2 | 🟠 | `role/service/PermissionService` 与 `permission/service/PermissionService`（后者 `@Service("menuPermissionService")`）；`role/controller/PermissionController` 与 `permission/controller/PermissionController` | 两套 PermissionService/Controller 职责重叠（均涉及角色-权限），并行抽象 | 合并为单一权限服务，消除重叠 |
| m11 | 🟡 | `UserService#importUsers` | 未提供密码时使用可配置但可预测的默认值（代码默认 `web4.0`），且未见强制首登改密闭环 | 强制首登改密或生成一次性随机初始密码并通过安全渠道交付 |
| m12 | 🟡 | `DataAccessService#buildLeafPredicate` | 规则创建/更新时未校验 `field/operator`；非法字段在执行期由 `root.get` 抛出并被外层捕获为 deny-all，虽 fail-closed 但会造成配置错误静默拒绝全部数据 | 保存规则时按 entitySlug 对实体字段和操作符做白名单校验 |

## 良好实践

- `DataAccessService.buildSpecification` 无匹配规则时返回 `cb.disjunction()`（拒绝所有），行级权限 **fail-closed**，是正确范式（与 B1 租户过滤的 fail-open 形成对比，应以此为准）。
- `DataAccessService` 用 Criteria API 构建谓词，参数化、无 SQL 注入。
- `User` 充血模型（checkPassword/changePassword/isLocked/recordLoginFail/recordLoginSuccess）封装良好，密码仅以编码存储且已增加 `@JsonIgnore` 纵深防御。
- Company 的 plan/objective/key-result/task/metric 创建端点均改用受校验 DTO，service 只映射允许字段并设置状态、记录时间等系统字段，不再直接绑定 JPA 实体。
- Channel/Webhook 配置入口使用 SaveDTO、出参使用脱敏 VO；`appSecret`、`token`、`encodingAesKey`、`secret` 均增加 `@JsonIgnore` 纵深防御。
- `PermissionService.tree`/`buildTree` 递归构树清晰；版本化（permission 软删除 + 唯一 code 校验）到位。
- `UserController` 批量删除 >100 转异步任务；失败项现会累计用户 ID 并使现有异步任务进入 `FAILED`，不再静默报告成功。

## 对称性 / 一致性提示

- 认证 vs 鉴权（清单#8）：B9/B10 当前重点是角色范围、资源归属与组织边界，而非是否存在方法级鉴权注解。
- 创建 vs 删除（清单#2）：`assignRolesToUser` 只增不删（M17）。
