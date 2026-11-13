/**
 * 平台控制类（系统内置）工具名与中文名常量
 * @author AaronZZH & Kiro
 *
 * 覆盖任务控制、执行上报、澄清、上下文加载、知识检索、UI 渲染与脚本执行。
 * 与后端 BuiltinControlToolProfile 的控制工具及框架内置通用工具保持一致，后端增删时同步修改。
 * 系统工具由框架自动提供：技能只声明业务工具，协作画布将其收拢为"系统工具"节点。
 * 中文名只用于界面展示，正文引用与模型调用仍使用工具名。
 *
 * 用法：
 * ```ts
 * const businessTools = toolOptions.filter((tool) => !SYSTEM_TOOL_NAMES.has(tool.name))
 * const label = SYSTEM_TOOL_LABELS[toolName] ?? toolName
 * ```
 */
export const SYSTEM_TOOL_LABELS: Readonly<Record<string, string>> = {
  inspect_tasks: "查看任务",
  promote_to_task: "升级为任务",
  amend_task: "修改任务",
  pause_task: "暂停任务",
  resume_task: "恢复任务",
  take_over_task: "接管任务",
  hand_back_task: "交还任务",
  cancel_task: "取消任务",
  activate_role: "切换角色",
  "context.load": "加载技能上下文",
  plan_steps: "规划步骤",
  report_step: "上报步骤",
  request_clarification: "请求澄清",
  delegate_agents: "委派执行者",
  knowledge_search: "知识库检索",
  render_ui_block: "渲染界面块",
  "script.execute.javascript": "执行脚本"
}

export const SYSTEM_TOOL_NAMES: ReadonlySet<string> = new Set(Object.keys(SYSTEM_TOOL_LABELS))
