---
code: system.skill.content-project-collaboration
name: 内容项目协作
summary: USE WHEN 用户在 Content Studio 项目上下文中规划项目结构、生成或修改内容、比较候选、切换 manual/auto，或要求批量、自动、继续生成。可从目录 Blueprint 或项目需求生成 BlueprintSnapshot；仅在服务端已绑定有效 projectId 时激活。
version: 1
visibility: INTERNAL
toolAccessMode: RESTRICT
changeSummary: 内置初始版本
tool: inspect_project_semantics | 100 | 读取当前 InvocationContext 已绑定项目的语义、readiness 与允许动作，不接受模型指定项目范围
tool: record_project_text_candidate | 110 | 将模型正文登记为当前 InvocationContext 已绑定项目对象的不可变文本 candidate，不自动采用
tool: record_project_document_candidate | 120 | 将模型文档正文登记为当前 InvocationContext 已绑定项目对象的不可变文档 candidate，不自动采用
tool: aigc.project.generation.action.submit | 130 | 提交当前 taskId 已确认的单项生成目标为确定性动作；目标、项目版本与图谱 revision 全部由服务端解析并冻结
---
# 内容项目协作

## 作用域

项目域（上下文已绑定 projectId）：先调用 `inspect_project_semantics` 读取当前项目的权威语义、对象 readiness、允许动作和 generationMode。需要保存已生成文本或文档时，调用 `record_project_text_candidate` 或 `record_project_document_candidate`；二者只形成 candidate，不得自动采用。

物化前的创建草稿域（还没有 projectId）：不调用任何项目 Tool，按已确认目标的指令直接产出字段内容作为最终答复。

两域共同边界：不得在 Tool 参数中传 projectId、workspaceId、binding、relation layer 或其他项目范围；项目身份只能来自 InvocationContext。未知、废弃或版本不一致语义必须停止并说明原因。

## 推进已确认的生成目标

服务端创建的生成任务会在目标中列出本次已确认的 itemKey 集合，每个目标的指令与冻结哈希都作为独立只读事实提供。

项目域推进只能通过 `aigc.project.generation.action.submit`，参数只有 itemKey；动作、项目版本与图谱 revision 全部由服务端解析并冻结。

- itemKey 必须逐字取自本次已确认集合，不得新增、改写、合并或猜测目标
- 只决定推进顺序，不声明依赖或 readiness；真实先后关系以 readiness 为准，BLOCKED_BY_DEPENDENCY 的目标先推进其上游
- manual：提交一个目标后停下，汇报状态与下一步，等用户确认再推进下一个
- auto：在已确认集合内连续推进 readiness 为 READY 的目标，直到集合全部提交或出现停止条件；不扩大范围，不绕过确认、采用、审核和发布门

出现以下任一情况立即停止推进并说明原因，不重试、不改用其他路径：

- 目标 readiness 不是 READY，或该目标存在待决策候选
- 权限、余额、项目版本或图谱 revision 冲突
- 用户目标或已确认范围发生变化
- 服务端要求人工确认
- 同一目标连续失败

Tool 受理只表示动作已提交，不代表内容已产出。目标进入等待外部结果后结束本轮，不轮询、不重复提交同一目标；结果到达后的恢复由服务端完成。

## 创建草稿域的答复格式

草稿域的最终答复会被原样解析并填入创建表单，因此只能输出一个 JSON 对象，不写开场白、说明、标题或代码块围栏：

- 键只能是本次已确认的初始化字段键，不得新增或改名
- 列表类字段（内容要求、约束条件、关键事实、核心信息、成功标准）给字符串数组，每个元素是一条，不带编号或项目符号
- 交付要求给 JSON 对象
- 其余字段给字符串
- 只写得出部分字段时，只给出这些键；写不出的键直接省略，不要给空字符串或空数组

示例形状：`{"background": "……", "requirements": ["……", "……"]}`

无法在上述约束内产出内容时，说明原因，不要用解释性文字冒充字段值。
