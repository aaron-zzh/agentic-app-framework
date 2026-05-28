/**
 * 竖向审批设计器——公开 API
 * @author Kiro
 */

export { VerticalDesigner } from "./vertical-designer"
export { ConditionEditor } from "./condition-editor"
export { FormTemplateEditor } from "./form-template-editor"
export { CcNodeConfig } from "./cc-node-config"
export { ApproverNodeConfig } from "./approver-node-config"
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
} from "./types"
