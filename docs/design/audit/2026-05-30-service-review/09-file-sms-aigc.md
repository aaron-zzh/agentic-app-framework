# 09 文件 · 短信 · AIGC 媒体

> 覆盖：文件上传/下载、短信模板与发送、AIGC 图像/视频/媒资生成。

## 问题清单（2026-08-01 复核）

| 编号 | 级别 | 状态 | 位置 | 结论 |
|------|------|------|------|------|
| M21 | 🟠 | FIXED | `system/sms/SmsController#testSend` | 测试发送可调用生产短信通道，无环境隔离与号码白名单。修复：`SmsProperties.testSend`（enabled + phoneWhitelist），生产默认整体禁用 |
| M22 | 🟠 | FIXED | `SmsController` | 直接注入 `SmsTemplateRepository` 并操作，模板 CRUD 返回实体。修复：新建 `SmsTemplateService`（`SmsTemplateVO`/`SmsTemplateCreateDTO`/`SmsTemplateUpdateDTO` 迁到 `vo` 包），与 `MessageTemplateService` 同一模式；新增 `SMS_TEMPLATE_NOT_FOUND` 错误码（`1_016_000`） |
| M23 | 🟠 | FIXED | `ai/aigc/image/ImageController#imageToImage/editImage` | 核实：`AiServiceRegistry` 返回的实例已被 `ImageGenServiceDecorator` 包裹，结算本已自动发生；真正缺口是 precheck。修复：两端点补 `estimateCost` + `creditGuard.precheck`，`userId` 改 `orElseThrow` |
| m16 | 🟡 | OPEN | `ImageController#getById`（`/{id}`）、`queryTask/queryTasks` | 按 id/taskId 查询无归属校验→可查他人图像（prompt+URL）；底层透传 Midjourney 任务查询 | 查询加归属过滤，未在本轮处理 |
| m17 | 🟡 | OPEN | `SmsController#callback/{aliyun,tencent}` | 回调为空实现（占位，连日志都未记），且不在白名单 | 实现或移除；明确占位状态，未在本轮处理 |

## 良好实践

- `ImageController` 写操作均用 `operatorContext.currentUserId()`；`AiImageService.action/delete` 校验 `image.getUserId().equals(userId)`，对象级授权到位（应推广到 getById/queryTask/queryTasks）。
- `AiImageService` 异步生成用虚拟线程 + 定时同步任务（syncMidjourney/syncWanx）+ 配额不足友好提示，结构清晰。
- `validateCustomId` 对回调 customId 做白名单校验，防止越权操作按钮。
- `CapabilityRouter` 统一模型路由，AIGC 平台可插拔。

## 对称性 / 一致性提示

- 认证 vs 鉴权（清单#8）：图像查询仍缺对象归属校验（m16）。
- 状态变更 vs 通知（清单#7）：SMS 回调仍为空实现（m17），状态同步链尚未闭合。
- 计费一致性：统一任务主链已有 `precheck`，但 image-to-image/edit 旁路仍需收口（M23）。
