# 开发记录：AAF-073 支付系统真实渠道适配器、退款、对账

执行者：AI/developer-service

## #7301 微信支付真实适配器

✅ 05-29 — developer-service

- aaf-dependencies 引入 weixin-java-pay:4.7.4.B
- WxPayChannelAdapter 支持 wx_pub/wx_lite/wx_app/wx_native 四渠道
- 按 TradeTypeEnum 映射区分下单方式（JSAPI/APP/NATIVE）
- @ConditionalOnProperty 控制，无配置时不注册
- WxPayProperties 集中配置（appId/mchId/apiV3Key/证书路径）

> **决策**：PayChannelAdapter 接口新增 `supportedChannelCodes()` default 方法，DefaultSettlementEngine 路由改为遍历 supportedChannelCodes 注册。这是接口签名变更（新增 default 方法，不破坏已有实现）。

## #7302 支付宝真实适配器

✅ 05-29 — developer-service

- aaf-dependencies 引入 alipay-sdk-java:4.39.218.ALL
- AlipayChannelAdapter 支持 alipay_pc/alipay_wap/alipay_app/alipay_qr
- 按 channelCode switch 分发到 pagePay/wapPay/appPay/precreate
- RSA2 签名验签，verifyNotify 方法供回调使用
- @ConditionalOnProperty 控制，默认 Mock

## #7304 退款

✅ 05-29 — developer-service

- RefundOrder 实体 + refund_order 表（追加到 v5 迁移脚本）
- PayRefundService：申请→调 SettlementEngine.refund→状态追踪
- 退款回调处理（handleRefundNotify）
- 失败重试（retryFailedRefunds，供定时任务调用）
- RefundController 暴露 REST API

## #7305 对账

✅ 05-29 — developer-service

- ReconcileRecord 实体 + reconcile_record 表（追加到 v5）
- PayChannelAdapter 新增 downloadBill(date) default 方法
- MockPayChannelAdapter 记录已支付订单，downloadBill 返回模拟账单
- ReconcileService：下载账单→比对本地→差异标记→保存记录
- 财务统计汇总（收入/退款/手续费）
- ReconcileController 暴露 REST API
- 新增枚举：ReconcileStatusEnum、ReconcileDiffTypeEnum

## 实现文件

| 文件 | 说明 |
|------|------|
| `aaf-dependencies/pom.xml` | 新增 weixin-java-pay + alipay-sdk-java 版本 |
| `aaf-framework/pom.xml` | 引入支付 SDK（optional） |
| `aaf-framework/.../settlement/PayChannelAdapter.java` | 新增 supportedChannelCodes() + downloadBill() |
| `aaf-framework/.../settlement/DefaultSettlementEngine.java` | 路由改为遍历 supportedChannelCodes |
| `aaf-framework/.../settlement/MockPayChannelAdapter.java` | 新增 paidOrders 记录 + downloadBill |
| `aaf-framework/.../settlement/channel/WxPayChannelAdapter.java` | 新增 |
| `aaf-framework/.../settlement/channel/WxPayProperties.java` | 新增 |
| `aaf-framework/.../settlement/channel/AlipayChannelAdapter.java` | 新增 |
| `aaf-framework/.../settlement/channel/AlipayProperties.java` | 新增 |
| `aaf-common/.../enums/pay/ReconcileStatusEnum.java` | 新增 |
| `aaf-common/.../enums/pay/ReconcileDiffTypeEnum.java` | 新增 |
| `aaf-api/.../resources/db/migration/v5__order_schema.sql` | 追加 refund_order + reconcile_record 表 |
| `aaf-api/.../module/pay/domain/RefundOrder.java` | 新增 |
| `aaf-api/.../module/pay/domain/ReconcileRecord.java` | 新增 |
| `aaf-api/.../module/pay/repository/RefundOrderRepository.java` | 新增 |
| `aaf-api/.../module/pay/repository/ReconcileRecordRepository.java` | 新增 |
| `aaf-api/.../module/pay/service/PayRefundService.java` | 新增 |
| `aaf-api/.../module/pay/service/ReconcileService.java` | 新增 |
| `aaf-api/.../module/pay/vo/RefundApplyDTO.java` | 新增 |
| `aaf-api/.../module/pay/vo/RefundOrderVO.java` | 新增 |
| `aaf-api/.../module/pay/vo/ReconcileRecordVO.java` | 新增 |
| `aaf-api/.../module/pay/vo/FinanceSummaryVO.java` | 新增 |
| `aaf-api/.../module/pay/controller/RefundController.java` | 新增 |
| `aaf-api/.../module/pay/controller/ReconcileController.java` | 新增 |

## 实现决策

- **DefaultSettlementEngine 路由改造**：PayChannelAdapter 新增 `default List<String> supportedChannelCodes()` 方法（返回 channelCode() 单元素列表），DefaultSettlementEngine 构造时遍历每个 adapter 的 supportedChannelCodes() 注册到 Map。这是非破坏性接口变更——已有 MockPayChannelAdapter 无需修改即可工作。
- **微信多渠道处理**：一个 WxPayChannelAdapter Bean 注册 4 个 channelCode，内部按 TradeTypeEnum 映射区分下单方式。
- **Mock/真实切换机制**：真实适配器用 `@ConditionalOnProperty(prefix="aaf.pay.wx/alipay", name="enabled", havingValue="true")` 控制。无配置时只有 MockPayChannelAdapter 生效（始终 @Component 注册）。
- **downloadBill**：新增 default 方法，默认返回空列表。Mock 适配器覆盖返回内存中记录的已支付订单。真实适配器调用渠道 API 下载账单（当前为骨架实现，CSV 解析待后续完善）。

## 注意事项

- 微信支付 V3 需要商户证书文件（privateKeyPath/privateCertPath），部署时需配置文件路径
- 支付宝 SDK 使用 RSA2 签名，需配置应用私钥和支付宝公钥
- 退款重试（retryFailedRefunds）需外部定时任务触发，当前未配置 @Scheduled
- 对账的 downloadBill 真实实现需解析渠道返回的 CSV 格式，当前为骨架
