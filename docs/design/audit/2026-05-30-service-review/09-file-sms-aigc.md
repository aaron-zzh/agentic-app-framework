# 09 文件 · 短信 · AIGC 媒体

> 覆盖：文件上传/下载、短信模板与发送、AIGC 图像/视频/媒资生成。

## 问题清单

| 编号 | 级别 | 位置 | 问题 | 修复建议 |
|------|------|------|------|---------|
| B11 | 🔴 | `system/file/FileController#getPresignedUrl` | 为**任意 key** 生成预签名**上传** URL，无归属校验→可获取覆盖他人对象（含他人文件）的写 URL | 预签名 key 必须由服务端按当前用户命名空间生成，禁止客户端指定任意 key |
| M20 | 🟠 | `FileController#download`/`delete`（`/{key}`） | 按 key 下载/删除任意文件，无归属校验→IDOR（读/删他人文件）；`CONTENT_DISPOSITION` 直接拼 key，存在响应头注入 | 校验文件归属；文件名做 RFC5987 编码/白名单 |
| M21 | 🟠 | `system/sms/SmsController#testSend` | "实际调用厂商 API，产生真实费用"，无鉴权、无频率限制→任意登录用户向任意号码发真实短信（费用滥用/短信轰炸） | 限管理员 + 强频率限制 + 号码白名单 |
| M22 | 🟠 | `SmsController` | 控制器直接注入 `SmsTemplateRepository`/`SmsLogRepository` 并操作→违反"controller→service→repository"分层；模板 CRUD 返回实体、无鉴权 | 经 service 层访问；出参 VO；加鉴权 |
| M23 | 🟠 | `ai/aigc/image/ImageController`（imagine/draw/action/image-to-image/edit） | 计费型 AIGC 生成端点未见积分/权益门控（无 `@Entitlement`/credit 扣减）→可无限发起付费生成 | 生成入口接入权益/积分门控（参考 `EntitlementAspect`） |
| M24 | 🟠 | `ImageController#notify`（Midjourney 回调） | 回调无签名校验，可伪造完成事件注入任意 imageUrl 到用户记录 | 校验回调来源/签名；与 B3/M5 webhook 验签统一 |
| m16 | 🟡 | `ImageController#getById`（`/{id}`）、`queryTask/queryTasks` | 按 id/taskId 查询无归属校验→可查他人图像（prompt+URL）；底层透传 Midjourney 任务查询 | 查询加归属过滤 |
| m17 | 🟡 | `SmsController#callback/{aliyun,tencent}` | 回调为空实现（占位，连日志都未记），且不在白名单 | 实现或移除；明确占位状态 |
| 重复3 | 🟠 | `ai/assistant/AssistantManagementService` 与 `ai/assistant/service/AssistantManagementService`；`AssistantController` ×2；`AssistantCreateDTO/VO/UpdateDTO` ×2 | 又一组同名类/DTO 重复（同 PermissionService/PermissionCacheService 模式） | 删除旧的一套，保留 service/ 子包版本 |

## 良好实践

- `ImageController` 写操作均用 `operatorContext.currentUserId()`；`AiImageService.action/delete` 校验 `image.getUserId().equals(userId)`，对象级授权到位（应推广到 download/getById）。
- `AiImageService` 异步生成用虚拟线程 + 定时同步任务（syncMidjourney/syncWanx）+ 配额不足友好提示，结构清晰。
- `validateCustomId` 对回调 customId 做白名单校验，防止越权操作按钮。
- `CapabilityRouter` 统一模型路由，AIGC 平台可插拔。

## 对称性 / 一致性提示

- 认证 vs 鉴权（清单#8）：file/sms 接口无鉴权（B9 续）、file/image 对象级 IDOR。
- 状态变更 vs 通知（清单#7）：Midjourney/SMS 回调无验签（M24，与 B3/M5 同类）。
- 已有模式 vs 新建（清单#13）：AssistantManagementService 等又一组重复实现（重复3）。
- 计费一致性：AIGC 与积分/权益体系未打通（M23），与 02 区资金一致性问题呼应。
