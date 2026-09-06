# AAF-114 开发记录

## #11401 产品/UI/技术契约

✅ 2026-09-05 — product / architect / designer / qa

- 完成 Gherkin 验收标准
- 冻结单一状态源边界
- 明确附件真实发送协议
- 计划开关不强制规划
- 思考开关不展示原始 CoT

## 验证约束

- 用户明确要求跳过 lint、测试和 check。
- 本轮仅执行源码与 diff 静态复核。
- 未经自动化验证，不满足常规完工门禁；最终汇报必须披露风险。


## #11402 助理与角色标题选择

✅ 2026-09-05 — developer-webui

- 按 Assistant 分组 Role
- 原子绑定 Assistant 与 Role
- 清除未校验 Skill
- 移除虚构 Role fallback
- resolved Role 不回写请求

## #11403 Composer 模型、附件和模式控制

✅ 2026-09-05 — developer-webui

- AUTO 精简为图标“自动”
- 启用附件按钮与 Dropzone
- 组合图片与文本 adapter
- 新增计划思考 Tooltip Toggle
- 保留语音输入与窄宽布局

## #11404 AG-UI 附件与偏好接线

✅ 2026-09-05 — developer-webui / developer-service

- showThinking 贯通摄取与渲染
- showPlan 仅隐藏 TaskBoard
- 图片 key 进入既有附件请求
- 校验 owner/MIME 并统一文件错误码
- 补齐 ExecutorPlan 事件类型

## #11405 静态复核与任务记录

✅ 2026-09-05 — developer-webui / architect / qa

- 两轮独立静态审阅
- 修复角色双真理源风险
- 修复跨用户文件访问风险
- LSP 改动文件无诊断
- 自动化门禁按用户要求跳过
