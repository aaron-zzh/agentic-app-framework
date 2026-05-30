# 11a framework 结算引擎 · 存储服务（优先级 1）

> 覆盖：`framework/engine/settlement/`（结算引擎 + 渠道适配）与 `framework/storage/`（S3/本地存储、FileService、ImageProcessor）。
> 承接 [11 执行计划](11-followup-review-plan.md) 优先级 1，对应已知模式 B2/B3/B7/B11。
> 审查人 AI/architect · 2026-05-30 · 依据 [代码审查规范](../../../reference/dev/code-review-standard.md)。

## 问题清单

| 编号 | 级别 | 位置 | 问题 | 修复建议 |
|------|------|------|------|---------|
| B12 | 🔴 | `settlement/MockPayChannelAdapter`（类级 `@Component`） | Mock 渠道**无生产隔离**（无 `@Profile`/`@ConditionalOnProperty`），生产环境照常注册；`charge` 同步置成功并记账、`withdraw`/`refund` 均直接返回成功→是 B2"任意铸积分/假提现"的框架层根因 | `@Profile("!prod")` 或 `@ConditionalOnProperty("aaf.pay.mock.enabled")` 默认关闭；生产缺省不注册 Mock |
| B13 | 🔴 | `storage/FileService#upload/uploadImage` + `S3StorageService#upload` + `LocalStorageService#upload` | 上传**无类型/大小校验**：任意 `contentType`、任意大小直接落盘；`StorageProperties` 无 allowedTypes/maxSize。可上传 `.html` 经 `getUrl` 同源访问触发存储型 XSS、可上传超大文件/zip 炸弹 | `StorageProperties` 增 `allowedContentTypes`+`maxSize`；`FileService` 上传前校验 MIME 白名单与大小 |
| B14 | 🔴 | `storage/LocalStorageService#download/delete`（`Path.of(basePath, key)`） | 本地存储按调用方 `key` 拼路径，无规范化/根目录包含校验。`key=../../etc/passwd`（Win `..\\`）逃逸 basePath→任意文件读/删；经 `FileController` `/{key}/download`、`DELETE /{key}` 客户端可达（B11/M20 框架层根因） | 解析后 `normalize()` 并断言 `resolvedPath.startsWith(basePath)`，否则拒绝 |
| M25 | 🟠 | `settlement/channel/WxPayChannelAdapter#refund` | `amount.setTotal((int) request.amount())` 把**原单总额误设为退款额**（注释自承"简化：退款金额=原单金额时"）。微信 V3 要求 total=原单总额；部分退款/退款≠原额时被拒或比例算错。`RefundRequest` 不含原单金额，无法正确 | `RefundRequest` 增 `originalAmount`，total 用原单总额，refund 用退款额 |
| M26 | 🟠 | `settlement/SettlementEngine#refund` + 各适配器 | 引擎层**无退款上限/幂等**：不校验累计退款 ≤ 已支付额，仅依赖渠道 `outRefundNo` 去重；引擎对 `refundNo` 无防重 | 退款前在引擎/服务层校验累计退款额上限 + 按 `refundNo` 幂等（与 02 区待确认项合并） |
| M27 | 🟠 | `WxPayChannelAdapter#downloadBill`、`AlipayChannelAdapter#downloadBill` | 真实渠道账单下载为**占位空实现**：log 成功但 `return List.of()` 未解析 CSV。`ReconcileService` 拿到零条渠道记录→对账静默失真（全标差异或假"无差异"），同 M13 假实现 | 实现 CSV 解析，或未实现前 `throw UnsupportedOperationException`，禁止静默返回空 |
| M28 | 🟠 | `PayChannelAdapter`/`SettlementEngine` 接口 vs `Wx/Alipay` 具体类 | 验签解析方法（`parseOrderNotify`/`verifyNotify`）**仅存在于具体适配器**，未上提到接口；引擎不暴露统一验签入口。回调方需感知具体类型才能验签，故 B3 中 api 回调直接信 `dto.success()`（`PayNotifyDTO` 连签名字段都没有）能绕过——此为 B3 框架层根因 | 将 `verifyAndParseNotify` 上提到 `PayChannelAdapter`/`SettlementEngine`，回调强制经引擎验签 |
| M29 | 🟠 | `storage/StorageService#getPresignedUploadUrl(key, …)` + `S3StorageService` | 框架按调用方**任意 key** 生成预签名 PUT，无命名空间/归属约束。`FileController#getPresignedUrl(@RequestParam key)` 直透→B11 框架层根因 | 接口改为按 `(ownerScope, filename)` 由服务端生成 key，或提供 `generateKey` 命名空间助手，禁止裸 key 直传 |
| M30 | 🟠 | `S3StorageService#upload` | `RequestBody.fromInputStream(input, input.available())` 用 `available()` 当 contentLength——非流长度，多数流返回 0/部分→上传截断或为空 | 用 `file.getSize()` 传入真实长度，或先缓冲全量字节 |
| m18 | 🟡 | `settlement/DefaultSettlementEngine#queryStatus` | 未知单号时**遍历所有渠道**远程查询（wx/alipay/mock 各一次 API）；且 `WxPayChannelAdapter#queryStatus` 异常与"未找到"都返回 `null`，瞬时故障被当 `UNPAID` 掩盖真实状态 | 按订单存储的 channelCode 路由查询，不盲遍历；区分"未找到"与"查询异常" |
| m19 | 🟡 | `settlement/*ChannelAdapter#charge` / `ChargeRequest` | 引擎/适配器边界未校验 `amount > 0`（负/零金额不拦） | charge 入口校验金额为正（金额可信性主体在 api 层 B2） |
| m20 | 🟡 | `S3StorageService#getPresignedUploadUrl` | 预签名 PUT 未固定 `contentType`/`content-length-range`，即便 key 命名空间化，客户端仍可向签名 key 上传任意内容/超大文件 | presign 时约束 contentType 与大小范围 |

## 良好实践

- 真实渠道（Wx/Alipay）`charge` 正确返回预支付信息而非同步成功（与 Mock 形成对比），且类级 `@ConditionalOnProperty(enabled=true)` 默认不启用。
- Wx/Alipay 已封装规范验签（`parseOrderNotifyV3Result` / `AlipaySignature.rsaCheckV1`）——能力到位，缺的是上提接口 + 回调强制调用（M28）。
- 上传**写入侧** key 由"日期分区 + UUID + 原扩展名"服务端生成，无用户可控路径、无碰撞（写侧安全，问题集中在 download/delete/presigned 的**读侧**信任 key）。
- `StorageAutoConfiguration` 按 `aaf.storage.type` 条件装配后端，切换清晰；密钥（apiV3Key/privateKey/secretKey）置于 Properties，未见日志输出。

## 对称性 / 一致性提示

- 状态变更 vs 通知（清单#7）：回调验签能力存在但未接入（M28，B3 同源）；Mock 同步记账与真实异步回调可重复入账（02 区 M3 呼应）。
- 创建 vs 删除（清单#2）：`FileService.uploadImage` 仅成功时传缩略图、`delete` 吞错删缩略图，主-缩略图大致对称（可接受）。
- 已有模式 vs 新建（清单#13）：`generateKey` 命名空间助手已存在于 `S3StorageService` 内部，却未复用到预签名/下载校验（M29/B14）。
- 资源申请 vs 释放：`S3Client`/`S3Presigner` 在 Service 生命周期内持有，随容器销毁（可接受）。

## 待确认

- `module/pay PayRefundService`：退款上限、累计退款、`refundNo` 幂等是否在服务层补足（决定 M26 是否降级）。
- `ReconcileService`：拿到空账单（M27）后的差异判定逻辑——是误报全差异还是静默通过。
- 生产环境 `aaf.storage.type` 与 `aaf.pay.*` 实际配置：确认 prod 是否真的禁用了 Mock、本地存储是否暴露于公网路径前缀。
