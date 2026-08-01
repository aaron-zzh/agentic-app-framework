# 09 文件 · 短信 · AIGC 媒体

> 覆盖：文件上传/下载、短信模板与发送、AIGC 图像/视频/媒资生成。

## 问题清单

| 编号 | 级别 | 位置 | 问题 | 修复建议 |
|------|------|------|------|---------|
| M21 | 🟠 | `system/sms/SmsController#testSend` | 测试发送可调用生产短信通道，但生产环境缺少测试号码白名单与环境隔离，仍可能误发真实短信并产生费用 | 生产环境禁用测试发送，或仅允许独立测试通道与号码白名单 |
| M22 | 🟠 | `SmsController` | 控制器直接注入 `SmsTemplateRepository`/`SmsLogRepository` 并操作，违反“controller→service→repository”分层；模板 CRUD 仍返回实体 | 经 service 层访问；统一改为 VO 出参 |
| M23 | 🟠 | `ai/aigc/image/ImageController#imageToImage/editImage` | image-to-image/edit 仍旁路统一任务主链，可绕过同一计费前置检查 | 将 image-to-image/edit 接入统一任务主链，复用 `precheck`、扣减与失败补偿 |
| m16 | 🟡 | `ImageController#getById`（`/{id}`）、`queryTask/queryTasks` | 按 id/taskId 查询无归属校验→可查他人图像（prompt+URL）；底层透传 Midjourney 任务查询 | 查询加归属过滤 |
| m17 | 🟡 | `SmsController#callback/{aliyun,tencent}` | 回调为空实现（占位，连日志都未记），且不在白名单 | 实现或移除；明确占位状态 |

## 良好实践

- `ImageController` 写操作均用 `operatorContext.currentUserId()`；`AiImageService.action/delete` 校验 `image.getUserId().equals(userId)`，对象级授权到位（应推广到 getById/queryTask/queryTasks）。
- `AiImageService` 异步生成用虚拟线程 + 定时同步任务（syncMidjourney/syncWanx）+ 配额不足友好提示，结构清晰。
- `validateCustomId` 对回调 customId 做白名单校验，防止越权操作按钮。
- `CapabilityRouter` 统一模型路由，AIGC 平台可插拔。

## 对称性 / 一致性提示

- 认证 vs 鉴权（清单#8）：图像查询仍缺对象归属校验（m16）。
- 状态变更 vs 通知（清单#7）：SMS 回调仍为空实现（m17），状态同步链尚未闭合。
- 计费一致性：统一任务主链已有 `precheck`，但 image-to-image/edit 旁路仍需收口（M23）。
