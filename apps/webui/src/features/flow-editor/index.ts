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

// 主组件
export { FlowEditor } from "./components/flow-editor"
export { TemplateDialog } from "./components/template-dialog"
export { WorkflowChat } from "./components/workflow-chat"
export { ExecutionPanel } from "./components/execution-panel"
export { PublishDialog } from "./components/publish-dialog"

// 竖向审批设计器
export {
  VerticalDesigner,
  ConditionEditor,
  FormTemplateEditor,
  CcNodeConfig,
  ApproverNodeConfig
} from "./components/vertical-designer"

// 节点注册表
export { approvalNodeRegistry } from "./nodes/approval"
export { workflowNodeRegistry } from "./nodes/workflow"

// Hooks
export { useFlowState } from "./hooks/use-flow-state"
export { useFlowValidation } from "./hooks/use-flow-validation"
export { useExecutionState } from "./hooks/use-execution-state"
export type { NodeExecutionLog } from "./hooks/use-execution-state"
export { useWorkflowRuntime } from "./hooks/use-workflow-runtime"
export type { WorkflowMessage, WorkflowRunStatus } from "./hooks/use-workflow-runtime"
export {
  useFlowList,
  useFlowDetail,
  useFlowSave,
  useFlowDelete,
  useFlowDeploy,
  useFlowTemplates,
  useCreateFromTemplate,
  useSaveAsTemplate
} from "./hooks/use-flow-query"
export {
  useFormTemplates,
  useFormTemplate,
  useCreateFormTemplate,
  useUpdateFormTemplate
} from "./hooks/use-form-template"

// 工具
export { flowToBpmn, bpmnToFlow } from "./lib/bpmn-converter"
export { getRegistryForMode } from "./lib/registry"
export { builtinTemplates } from "./lib/templates"

// 类型
export type {
  FlowEditorProps,
  FlowDefinition,
  FlowNode,
  FlowEdge,
  FlowMode,
  FlowTemplate,
  NodeTypeRegistry,
  NodeTypeDef,
  NodeCategory,
  ExecutionState,
  VariableDef,
  InspectorProps,
  PortDef
} from "./types"

export type {
  ApprovalFlowNode,
  ApprovalFlowBranch,
  ApprovalNodeType,
  ConditionGroup,
  ConditionExpression,
  CcTiming,
  FormFieldDef,
  FormFieldType,
  FormTemplate
} from "./components/vertical-designer"
