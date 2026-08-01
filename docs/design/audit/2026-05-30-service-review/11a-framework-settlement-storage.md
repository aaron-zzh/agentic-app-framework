# 11a framework 结算引擎 · 存储服务（优先级 1）

> 覆盖：`framework/engine/settlement/`（结算引擎 + 渠道适配）与 `framework/storage/`（S3/本地存储、FileService、ImageProcessor）。
> 2026-05-30 分区复审：结算正确性、回调抽象与存储边界。
> 审查人 AI/architect · 2026-05-30 · 依据 [代码审查规范](../../../reference/dev/code-review-standard.md)。

## 问题清单

| 编号 | 级别 | 位置 | 问题 | 修复建议 |
|------|------|------|------|---------|
| B13 | 🔴 | `storage/FileService` 的 URL/`byte[]`/Base64 入口 + `StorageService#upload` 实现 | 这些入口及底层 `StorageService` 直调仍可绕过统一校验；SVG/HTML 等主动内容也未统一拒绝或净化，可能形成超大对象写入或存储型 XSS | 将类型、大小及主动内容策略下沉为所有入口和存储实现共享的强制校验；拒绝或净化 SVG/HTML，并为 URL/bytes/Base64 设置读取上限 |
| M25 | 🟠 | `settlement/channel/WxPayChannelAdapter#refund` | `amount.setTotal((int) request.amount())` 把**原单总额误设为退款额**（注释自承"简化：退款金额=原单金额时"）。微信 V3 要求 total=原单总额；部分退款/退款≠原额时被拒或比例算错。`RefundRequest` 不含原单金额，无法正确 | `RefundRequest` 增 `originalAmount`，total 用原单总额，refund 用退款额 |
| M26 | 🟠 | `settlement/SettlementEngine#refund` + 退款服务 | 累计额的“读取后判断再写入”在并发请求下仍可能竞态超额；客户端也没有稳定幂等键，超时重试若生成新 `refundNo` 仍会形成重复退款请求 | 用行锁、原子条件更新或串行化事务保证累计退款不超额；增加客户端幂等键并建立唯一约束，服务端重试复用同一退款记录 |
| M27 | 🟠 | `WxPayChannelAdapter#downloadBill`、`AlipayChannelAdapter#downloadBill` | 真实渠道账单下载为**占位空实现**：log 成功但 `return List.of()` 未解析 CSV。`ReconcileService` 拿到零条渠道记录→对账静默失真（全标差异或假"无差异"） | 实现 CSV 解析，或未实现前 `throw UnsupportedOperationException`，禁止静默返回空 |
| M28 | 🟠 | `PayChannelAdapter`/`SettlementEngine` 接口 vs `Wx/Alipay` 具体类 | 验签/解析仍未上提为统一适配器契约；微信回调所需的请求头、签名与原始报文上下文无法由现有接口兼容表达，新增调用方仍需感知具体适配器 | 定义包含原始报文、请求头和渠道上下文的回调信封，将 `verifyAndParseNotify` 上提到 `PayChannelAdapter`/`SettlementEngine`；统一 Wx/Alipay 的验签结果模型 |
| M29 | 🟠 | `storage/StorageService#getPresignedUploadUrl(key, …)` + `S3StorageService` | 框架接口仍接受调用方提供的**裸 key**，没有在类型或契约层绑定 owner；新增调用方可绕过上层约束而误签其他命名空间 | 接口改为按 `(ownerScope, filename)` 由服务端生成 key，或要求强类型 `OwnedStorageKey`，避免通用接口接受裸 key |
| m18 | 🟡 | `settlement/DefaultSettlementEngine#queryStatus` | 未知单号时**遍历所有渠道**远程查询（wx/alipay/mock 各一次 API）；且 `WxPayChannelAdapter#queryStatus` 异常与"未找到"都返回 `null`，瞬时故障被当 `UNPAID` 掩盖真实状态 | 按订单存储的 channelCode 路由查询，不盲遍历；区分"未找到"与"查询异常" |
| m19 | 🟡 | `settlement/*ChannelAdapter#charge` / `ChargeRequest` | 引擎/适配器边界未校验 `amount > 0`（负/零金额不拦） | charge 入口校验金额为正（金额可信性主体在 api 层下单入口，已收敛为服务端货架定价） |
| m20 | 🟡 | `S3StorageService#getPresignedUploadUrl` | 预签名 PUT 未固定 `contentType`/`content-length-range`，即便 key 命名空间化，客户端仍可向签名 key 上传任意内容/超大文件 | presign 时约束 contentType 与大小范围 |

## 良好实践

- 真实渠道（Wx/Alipay）`charge` 正确返回预支付信息而非同步成功，且类级 `@ConditionalOnProperty(enabled=true)` 默认不启用。
- Wx/Alipay 回调入口已调用规范验签能力（`parseOrderNotifyV3Result` / `AlipaySignature.rsaCheckV1`）；剩余问题是统一抽象不完整（M28）。
- `MultipartFile` 上传入口已执行 MIME 与大小校验；写入侧 key 由“日期分区 + UUID + 原扩展名”服务端生成，无用户可控路径、无碰撞。
- `StorageAutoConfiguration` 按 `aaf.storage.type` 条件装配后端，切换清晰；密钥（apiV3Key/privateKey/secretKey）置于 Properties，未见日志输出。

## 对称性 / 一致性提示

- 抽象 vs 实现（清单#13）：具体支付入口已验签，但统一渠道接口不能完整表达微信回调上下文（M28），新接入点仍容易形成渠道特判。
- 创建 vs 删除（清单#2）：`FileService.uploadImage` 仅成功时传缩略图、`delete` 吞错删缩略图，主-缩略图大致对称（可接受）。
- 已有模式 vs 新建（清单#13）：API 已实施 owner 命名空间规则，但框架预签名接口仍暴露裸 key（M29），约束未在层间对称下沉。
- 资源申请 vs 释放：`S3Client`/`S3Presigner` 在 Service 生命周期内持有，随容器销毁（可接受）。

## 待确认

- 并发退款压测是否能突破累计退款上限，以及客户端超时重试是否会生成不同 `refundNo`（M26）。
- `ReconcileService`：拿到空账单（M27）后的差异判定逻辑——是误报全差异还是静默通过。
- URL、bytes、Base64 与底层存储直调是否均经过统一校验，以及对象访问域是否允许浏览器执行 SVG/HTML 主动内容（B13）。
