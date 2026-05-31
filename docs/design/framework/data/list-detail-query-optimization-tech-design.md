---
level: Practice
layer: Product
purpose: AAF 列表查询与详情快速切换优化技术方案
status: draft
version: 0.1.0
date: 2026-05-30
author: AaronZZH & Codex
---

# 列表查询与详情快速切换优化技术方案

> 本文定义 AAF 通用列表查询、详情快速切换、字段级懒加载与前端缓存协同方案。AAF 使用 Spring Data JPA + BaseCrudService + TanStack Query 落地。

## 背景

当前 AAF 已有基础分页能力：

- 后端 `PageParam` 提供 `pageNo/pageSize/sort`，最大 `pageSize=200`，支持 `pageSize=-1` 不分页。
- 后端 `PageResult<T>` 返回 `list + total`。
- 后端 `BaseCrudService.page()` 基于 Spring Data `findAll(Specification, Pageable)` 查询并转 VO。
- 后端 `BaseCrudController.GET /resource` 暴露分页查询，`GET /resource/{id}` 暴露详情。
- 前端 `useEntityList` 基于 TanStack Query 查询列表，`DataTable` 已支持服务端分页。
- 前端 `useEntityDetail` 查询单条详情，表单视图当前只知道 `recordId`，没有列表窗口上下文。

当前不足：

- 列表打开详情后，详情页没有当前查询窗口的 ID 列表，上一条/下一条需要重新依赖路由或返回列表。
- 列表行数据与详情数据没有统一的字段集协议，详情无法稳定“先用列表数据秒开，再补全详情字段”。
- `PageResult` 前端类型已假设有 `page/pageSize`，但后端 `PageResult` 当前只有 `list/total`，存在前后端契约不一致。
- 列表查询、详情读取、L3 记录规则和权限版本之间缺少明确的一致性标识。

## Odoo 借鉴点

- 后端 `search_fetch(domain, fields, offset, limit, order)`：把“查 ID + 拉字段”合并为最少 SQL 路径，避免列表查询后再逐条读字段。
- 后端 `search_read()`：基于 `search_fetch()` 返回前端需要的字段数据。
- 前端列表打开详情时传递当前页 `resIds`，表单页 pager 使用 `resIds.indexOf(currentId)` 做上一条/下一条。
- 前端记录缓存按 `id + 已加载字段` 判断是否需要补读；已有记录且字段齐全时不重复请求。

## 目标

- 列表查询返回当前窗口记录、当前窗口 ID 列表和查询上下文标识。
- 详情页可基于列表窗口快速切换上一条/下一条。
- 详情页先复用列表行缓存渲染，再异步补齐详情字段。
- 权限、L3 记录规则、组织上下文变化后，不复用旧列表窗口。
- 保持现有 `GET /resource` 和 `GET /resource/{id}` 可用，新增增强接口优先，不破坏已有调用。

## 非目标

- 不在本方案中引入虚拟滚动无限列表
- 不改变所有业务 Controller 的 URL 风格
- 不把前端服务端数据复制进 Zustand
- 不用前端隐藏结果替代后端权限过滤

## 核心模型

### 字段集

字段集用于区分列表轻量字段和详情完整字段。

| 字段集 | 用途 | 示例 |
|------|------|------|
| `list` | 列表行展示 | `id/name/status/updateTime` |
| `detail` | 表单详情展示 | 所有表单字段 |
| `picker` | 弹窗选择器 | `id/name/code` |
| `export` | 导出 | 导出列集合 |

字段集由实体元数据或 DTO 明确定义，不允许前端随意请求任意实体字段。字段级权限裁剪发生在服务端。

### 查询窗口

查询窗口表示一次列表查询的有序结果片段。

```json
{
  "list": [],
  "total": 128,
  "pageNo": 1,
  "pageSize": 20,
  "ids": [101, 98, 95],
  "queryToken": "opaque-token",
  "fieldSet": "list",
  "hasMore": true
}
```

`ids` 是当前页可见记录 ID，必须已经经过 L1/L2/L3/L4 中适用于查询的过滤。`queryToken` 是服务端生成的不透明查询上下文标识，用于详情切换和窗口校验。

### 查询 Token

`queryToken` 不保存业务数据本体，包含或可解析到以下信息：

```text
entitySlug
normalizedFilterHash
sortHash
fieldSet
pageNo/pageSize
operatorId/ownerId/orgId
permissionVersion
dataRuleVersion
schemaVersion
issuedAt
```

实现可选：

- 无状态：服务端签名 token，内容加签防篡改。
- 有状态：Redis 存 `query_window:{token}`，TTL 10-30 分钟。

v0.1 推荐有状态 Redis 起步，便于权限版本、窗口 ID、调试信息和主动失效。

## 后端接口设计

### 保留现有分页接口

现有接口继续可用：

```http
GET /api/{resource}?pageNo=1&pageSize=20&sort=-updateTime
```

响应继续兼容：

```json
{
  "list": [],
  "total": 128
}
```

该接口不承担详情快速切换能力，仅作为基础分页。

### 新增增强列表接口

为通用实体引擎新增增强查询语义。具体 URL 可由 `BaseCrudController` 增加可选参数或新增子路径，推荐新增子路径避免改变旧契约：

```http
GET /api/{resource}/_query?pageNo=1&pageSize=20&sort=-updateTime&fieldSet=list
```

响应：

```java
public record PageResult<T>(
        List<T> list,
        long total,
        Integer pageNo,
        Integer pageSize,
        List<Long> ids,
        String queryToken,
        String fieldSet,
        Boolean hasMore) {}
```

兼容策略：

- `PageResult<T>` 统一承载基础分页与查询窗口元信息。
- 基础分页可只返回 `list/total/pageNo/pageSize/hasMore`，增强查询额外返回 `ids/queryToken/fieldSet`。
- 前端只维护 `PageResult<T>` 一个分页响应类型，是否具备查询窗口能力由 `queryToken` 是否存在判断。

### 新增窗口详情接口

详情读取保持原接口：

```http
GET /api/{resource}/{id}
```

新增窗口上下文读取：

```http
GET /api/{resource}/{id}?queryToken=xxx&fieldSet=detail
```

响应可继续是详情 VO。服务端行为：

- 校验 `queryToken` 的 `entitySlug/operator/org/permissionVersion/dataRuleVersion`。
- 若 token 有效且 `id` 在窗口或窗口上下文允许范围内，读取详情。
- 若 token 失效，回退为普通详情权限检查。
- 若记录因权限变化不可见，返回 404，不暴露存在性。

### 可选批量详情预取接口

用于详情页预取上一条/下一条：

```http
POST /api/{resource}/_batch-read
{
  "ids": [98, 101, 105],
  "fieldSet": "detail",
  "queryToken": "xxx"
}
```

服务端必须逐条套用详情权限与 L3 过滤，不可因来自同一 token 就跳过权限。

## 后端实现设计

### BaseCrudService 扩展

新增方法，不替换原 `page()`：

```java
public PageResult<V> queryWindow(P request, FieldSet fieldSet) {
    Specification<E> spec = buildSpec(request).and(recordRuleSpec());
    Page<E> page = getSpecExecutor().findAll(spec, request.toPageable(defaultSort()));
    List<Long> ids = page.getContent().stream().map(BaseEntity::getId).toList();
    List<V> list = page.getContent().stream().map(e -> toVO(e, fieldSet)).toList();
    String token = queryWindowService.issue(...);
    return new PageResult<>(list, page.getTotalElements(), request.getPageNo(), request.getPageSize(), ids, token, fieldSet.name(), page.hasNext());
}
```

实现要求：

- `buildSpec(request)` 与 L3 `recordRuleSpec()` 必须进入同一查询。
- `ids` 来自已经过滤后的 page content。
- `toVO(entity, fieldSet)` 可先默认复用现有 `toVO(entity)`，后续逐实体优化字段集。
- 对大表可后续优化为“两段式”：先查 ID，再按 ID 批量 fetch list 字段，避免复杂 join + count 的成本。

### QueryWindowService

职责：

- 生成 `queryToken`。
- 保存当前窗口 `ids`、查询 hash、权限版本、记录规则版本。
- 校验 token 是否属于当前 operator / owner / org。
- 在权限版本变化时让旧 token 失效。

Redis key：

```text
query_window:{token}
```

TTL：默认 15 分钟。切换组织、登出、权限版本变化时主动失效相关 token。

### 权限一致性

增强列表与详情必须遵守：

- 列表 ID 窗口只包含当前用户可见记录。
- 详情读取仍执行 L1/L2/L3 检查。
- `queryToken` 只能减少重复计算，不能作为绕过权限的凭证。
- L3 规则变化后，`dataRuleVersion` 变化，旧 token 失效。
- 用户角色、权限码、关系权限变化后，`permissionVersion/schemaVersion` 变化，旧 token 失效。

## 前端实现设计

### Query Key

列表增强查询：

```ts
[
  entity.slug,
  "queryWindow",
  {
    workspaceId,
    orgId,
    pageNo,
    pageSize,
    sort,
    filters,
    fieldSet: "list"
  }
]
```

详情查询：

```ts
[entity.slug, "detail", { id, fieldSet: "detail", queryToken }]
```

TanStack Query 负责服务端状态缓存；Zustand 仅保存 UI 状态，如当前打开的视图、面板展开、选中行，不保存列表数据副本。

### 列表打开详情

列表行点击时：

```text
rowId + ids + queryToken → form route state / URL query
```

推荐路由参数：

```text
?view=form&id=101&qw={queryToken}
```

`ids` 不建议完整塞 URL，可存在 TanStack Query 缓存或 Redis token。页面刷新后依赖 `queryToken` 恢复窗口。

### 详情秒开

详情页加载顺序：

- 先从列表 `queryWindow` 缓存中找当前 `id` 的 list 字段数据，作为 initialData。
- 立即发起 `fieldSet=detail` 的详情请求补齐字段。
- 成功后写入详情 Query Cache。
- 预取上一条/下一条详情，提升 pager 切换速度。

### 上一条/下一条

详情页通过 `queryToken` 恢复当前窗口 `ids`：

- 当前 `id` 在 `ids` 中，上一条/下一条直接切换。
- 到窗口边界时，可调用增强列表接口加载相邻页。
- 若某条记录详情返回 404，说明权限或数据变化，前端从窗口中移除该 ID 并跳到下一条可见记录。

## 对已有接口的影响

结论：可以做到**无破坏性影响**。

| 接口/类型 | 当前状态 | 影响 |
|------|------|------|
| `GET /api/{resource}` | 返回 `PageResult<T>` | 保留 |
| `GET /api/{resource}/{id}` | 返回详情 VO | 保留，可增加可选 `queryToken/fieldSet` 参数 |
| `PageParam` | `pageNo/pageSize/sort` | 保留，可后续增加 `fieldSet` 子类字段 |
| `PageResult<T>` | `list/total/pageNo/pageSize/ids/queryToken/fieldSet/hasMore` | 统一基础分页与查询窗口 |
| `BaseCrudService.page()` | 基础分页 | 保留 |
| `BaseCrudController.page()` | 基础分页端点 | 保留 |

新增能力通过新方法承载：

- `QueryWindowService`
- `BaseCrudService.queryWindow(...)`
- `GET /api/{resource}/_query`
- 可选 `POST /api/{resource}/_batch-read`

前端可渐进迁移：

- 普通列表继续用 `useEntityList`。
- 需要快速切换详情的页面新增 `useEntityQueryWindow`。
- `useEntityDetail` 增加可选 `queryToken/initialData/fieldSet`。

## 与访问控制的关系

本方案依赖访问控制设计中的版本化缓存：

- `permissionVersion`：角色、权限码、角色权限关系、用户角色关系变化。
- `dataRuleVersion`：`sys_data_access_rule` 或用户组织/团队归属变化。
- `schemaVersion`：ReBAC 权限 Schema 或关系结构变化。

`queryToken` 必须包含这些版本，保证列表窗口不会在权限变化后继续提供旧 ID 导航。

## 落地步骤

- 扩展 `PageResult<T>` 并新增 `QueryWindowService`。
- 在 `BaseCrudService` 增加 `queryWindow()`，默认复用 `toVO()`。
- 在 `BaseCrudController` 增加 `GET /_query`。
- 前端新增 `useEntityQueryWindow`，保留 `useEntityList`。
- `ConnectedListView` 在启用快速详情时使用 `useEntityQueryWindow`。
- `ConnectedFormView` 支持 `queryToken`，从列表缓存 initialData 秒开详情。
- 增加上一条/下一条 pager UI 与相邻详情预取。
- 接入 L3 记录规则版本与权限版本，旧 token 失效。

## 风险与约束

- `queryToken` 不是授权凭证，只是查询上下文；详情仍要重新鉴权。
- `ids` 窗口可能因删除或权限变化失效，前端必须处理 404 并刷新窗口。
- 大页码 offset 查询在大表上仍有性能问题；后续可增加 seek/cursor 模式，但不影响本方案接口形态。
- 字段集需要白名单，避免前端请求敏感字段。
- 批量详情预取要限制数量，默认最多 3-5 条，避免放大流量。
