# AAF-112 开发记录

## #11201 冻结对象级生成规格

✅ 2026-09-05 — product / architect / designer

- 双视图共用 objectId 回调
- 项目生成统一走 ExecutionRun
- 参考图以 MediaVersion 为权威
- 本地上传与项目素材同入口
- 对话式创作拆为 AAF-113


## #11202 扩展项目媒体动作链

✅ 2026-09-05 — developer-service

- 动作参数冻结且重试恢复
- 对象类型与动作服务端校验
- 图片编辑与视频任务适配
- 上传图片物化媒体版本
- 增加幂等视频执行绑定

## #11203 实现项目参考素材选择

✅ 2026-09-05 — developer-webui

- 来源菜单区分上传与项目
- 素材网格与图谱共用选择器
- 仅采用图片版本可选择
- 附件保留顺序与业务身份
- 阻止重复媒体版本加入

## #11204 接入统一对象生成弹窗

✅ 2026-09-05 — developer-webui

- 项目页唯一持有生成弹窗
- 双视图共用对象生成回调
- 服务端动作决定入口可用性
- 项目模式仅走 ActionCommand
- 独立创作链保持原有行为

> 用户要求编译、测试、check 与验收均手工执行；#11205、#11206 保持待办。


## #11207 收敛动态槽位契约

✅ 2026-09-05 — developer-service

- 配置解析改为强类型槽位
- 固化预算质量与数量覆盖
- 实例身份与合同角色唯一
- 删除弱对象规格旧契约

## #11208 重写物化与对象合同

✅ 2026-09-05 — developer-service / developer-webui

- 动态对象按快照物化
- 实例号由服务端分配
- 对象增删与合同命令落地
- 视图状态按项目隔离恢复

## #11209 实现执行强幂等

✅ 2026-09-05 — developer-service

- Submission 持久化请求摘要
- reservation 使用 fencing CAS
- Run 绑定前禁止业务副作用
- 恢复扫描按组织上下文执行

## #11210 实现执行树与任务意图

✅ 2026-09-05 — developer-service

- Run 区分编排与活动类型
- 子 Run 固定冻结目标
- Task 先持久意图再派发
- 不安全恢复进入待对账态

## #11211 实现持久 Activity SSE

✅ 2026-09-05 — developer-service / developer-webui

- 单一 Activity wire envelope
- 按用户组织工作区隔离
- 游标重放与提交顺序固定
- 项目权威查询统一失效

## #11212 实现候选比较与采用 CAS

✅ 2026-09-05 — developer-service / developer-webui

- 文本与媒体候选可比较
- 媒体按版本身份查询
- 替换采用需确认与原因
- 空槽位条件采用使用 CAS

## #11213 实现内容包 manifest

✅ 2026-09-05 — developer-service / developer-webui

- REQUIRED 与 OPTIONAL 规范化
- reservation 与校验证据入门
- evidenceHash 冻结输入证据
- 同事务重算不可变 manifest

## #11214 实现审核退回与 stale

✅ 2026-09-05 — developer-service / developer-webui

- Review 固定精确 manifest
- 支持通过与退回意见
- 内容变化使审核失效
- 重送审保留历史版本

## #11215 重写作品发布完成契约

✅ 2026-09-05 — developer-service / developer-webui

- Work 固定已审 manifest
- Publication 支持取消重试
- 写命令携带项目版本 CAS
- 完成策略读取项目快照

## #11216 收敛生命周期与权限

✅ 2026-09-05 — developer-service / developer-webui

- 项目与执行状态正交
- 归档后全部写入口拒绝
- authority 单一来源投影前端
- owner/org/workspace 对称校验

## #11217 串联无对话工作台

✅ 2026-09-05 — developer-webui

- 创建到归档均有可视入口
- RunTree 使用完整根树接口
- TanStack Query 保持真理源
- AAF-113 阶段门继续生效

## #11218 补齐测试源码

✅ 2026-09-05 — developer / tester

- 后端新增闭环合同测试源码
- 前端新增关键接线测试源码
- 未执行任何自动验证命令
- #11205 与 #11206 待人工完成
