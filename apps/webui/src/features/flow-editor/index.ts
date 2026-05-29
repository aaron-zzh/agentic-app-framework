/**
 * 统一流程图编辑器——公开 API
 * @author AaronZZH & Kiro
 *
 * @example
 * ```tsx
 * import { FlowEditor, approvalNodeRegistry } from "@/features/flow-editor"
 *
 * <FlowEditor
 *   mode="approval"
 *   nodeRegistry={approvalNodeRegistry}
 *   initialData={flowDef}
 *   onChange={setFlowDef}
 * />
 * ```
 *
 * @example 竖向审批设计器
 * ```tsx
 * import { VerticalDesigner } from "@/features/flow-editor"
 *
 * <VerticalDesigner value={flowData} onChange={setFlowData} formFields={fields} />
 * ```
 */

export { ExecutionPanel } from "./components/execution-panel"
// 主组件
export { FlowEditor } from "./components/flow-editor"
export { PublishDialog } from "./components/publish-dialog"
export { TemplateDialog } from "./components/template-dialog"
export type {
  ApprovalFlowBranch,
  ApprovalFlowNode,
  ApprovalNodeType,
  CcTiming,
  ConditionExpression,
  ConditionGroup,
  FormFieldDef,
  FormFieldType,
  FormTemplate
} from "./components/vertical-designer"

// 竖向审批设计器
export {
  ApproverNodeConfig,
  CcNodeConfig,
  ConditionEditor,
  FormTemplateEditor,
  VerticalDesigner
} from "./components/vertical-designer"
export { WorkflowChat } from "./components/workflow-chat"
export type { NodeExecutionLog } from "./hooks/use-execution-state"
export { useExecutionState } from "./hooks/use-execution-state"
export {
  useCreateFromTemplate,
  useFlowDelete,
  useFlowDeploy,
  useFlowDetail,
  useFlowList,
  useFlowSave,
  useFlowTemplates,
  useSaveAsTemplate
} from "./hooks/use-flow-query"
// Hooks
export { useFlowState } from "./hooks/use-flow-state"
export { useFlowValidation } from "./hooks/use-flow-validation"
export {
  useCreateFormTemplate,
  useFormTemplate,
  useFormTemplates,
  useUpdateFormTemplate
} from "./hooks/use-form-template"
export type { WorkflowMessage, WorkflowRunStatus } from "./hooks/use-workflow-runtime"
export { useWorkflowRuntime } from "./hooks/use-workflow-runtime"
// 工具
export { bpmnToFlow, flowToBpmn } from "./lib/bpmn-converter"
export { getRegistryForMode } from "./lib/registry"
export { builtinTemplates } from "./lib/templates"
// 节点注册表
export { approvalNodeRegistry } from "./nodes/approval"
export { workflowNodeRegistry } from "./nodes/workflow"
// 类型
export type {
  ExecutionState,
  FlowDefinition,
  FlowEdge,
  FlowEditorProps,
  FlowMode,
  FlowNode,
  FlowTemplate,
  InspectorProps,
  NodeCategory,
  NodeTypeDef,
  NodeTypeRegistry,
  PortDef,
  VariableDef
} from "./types"
