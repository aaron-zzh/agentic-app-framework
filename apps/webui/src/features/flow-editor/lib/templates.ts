/**
 * 内置流程模板数据
 * @author AaronZZH & Kiro
 */

import type { FlowTemplate } from "../types"

/** 请假审批模板 */
const leaveApproval: FlowTemplate = {
  id: "tpl_leave",
  name: "请假审批",
  description: "员工提交请假申请 → 直属主管审批 → 人事确认",
  mode: "approval",
  definition: {
    nodes: [
      { id: "start_1", type: "start", position: { x: 250, y: 50 }, data: { label: "开始" } },
      { id: "task_1", type: "userTask", position: { x: 200, y: 150 }, data: { label: "提交申请", icon: "👤", ports: [{ id: "in", direction: "input" }, { id: "out", direction: "output" }], assignee: "${applicant}" } },
      { id: "task_2", type: "userTask", position: { x: 200, y: 270 }, data: { label: "主管审批", icon: "👤", ports: [{ id: "in", direction: "input" }, { id: "out", direction: "output" }], assignee: "${manager}" } },
      { id: "gw_1", type: "gateway", position: { x: 240, y: 380 }, data: { label: "网关", condition: "" } },
      { id: "task_3", type: "userTask", position: { x: 100, y: 480 }, data: { label: "人事确认", icon: "👤", ports: [{ id: "in", direction: "input" }, { id: "out", direction: "output" }], assignee: "hr" } },
      { id: "end_1", type: "end", position: { x: 250, y: 580 }, data: { label: "结束" } }
    ],
    edges: [
      { id: "e1", source: "start_1", target: "task_1", sourceHandle: "out" },
      { id: "e2", source: "task_1", target: "task_2", sourceHandle: "out" },
      { id: "e3", source: "task_2", target: "gw_1", sourceHandle: "out" },
      { id: "e4", source: "gw_1", target: "task_3", sourceHandle: "out-yes", label: "通过", condition: "${approved}" },
      { id: "e5", source: "gw_1", target: "end_1", sourceHandle: "out-no", label: "驳回" },
      { id: "e6", source: "task_3", target: "end_1", sourceHandle: "out" }
    ],
    variables: [
      { name: "applicant", type: "string", description: "申请人" },
      { name: "manager", type: "string", description: "直属主管" },
      { name: "approved", type: "boolean", description: "是否通过" }
    ]
  }
}

/** 报销审批模板 */
const expenseApproval: FlowTemplate = {
  id: "tpl_expense",
  name: "报销审批",
  description: "员工提交报销 → 金额判断 → 主管/总监审批 → 财务打款",
  mode: "approval",
  definition: {
    nodes: [
      { id: "start_1", type: "start", position: { x: 250, y: 50 }, data: { label: "开始" } },
      { id: "task_1", type: "userTask", position: { x: 200, y: 150 }, data: { label: "提交报销", icon: "👤", ports: [{ id: "in", direction: "input" }, { id: "out", direction: "output" }], assignee: "${applicant}" } },
      { id: "gw_1", type: "gateway", position: { x: 240, y: 260 }, data: { label: "金额判断", condition: "" } },
      { id: "task_2", type: "userTask", position: { x: 80, y: 370 }, data: { label: "主管审批", icon: "👤", ports: [{ id: "in", direction: "input" }, { id: "out", direction: "output" }], assignee: "${manager}" } },
      { id: "task_3", type: "userTask", position: { x: 350, y: 370 }, data: { label: "总监审批", icon: "👤", ports: [{ id: "in", direction: "input" }, { id: "out", direction: "output" }], assignee: "${director}" } },
      { id: "task_4", type: "serviceTask", position: { x: 200, y: 480 }, data: { label: "财务打款", icon: "⚙️", ports: [{ id: "in", direction: "input" }, { id: "out", direction: "output" }], serviceClass: "com.xuejiai.aaf.module.finance.PaymentService" } },
      { id: "end_1", type: "end", position: { x: 250, y: 580 }, data: { label: "结束" } }
    ],
    edges: [
      { id: "e1", source: "start_1", target: "task_1", sourceHandle: "out" },
      { id: "e2", source: "task_1", target: "gw_1", sourceHandle: "out" },
      { id: "e3", source: "gw_1", target: "task_2", sourceHandle: "out-yes", label: "≤5000", condition: "${amount <= 5000}" },
      { id: "e4", source: "gw_1", target: "task_3", sourceHandle: "out-no", label: ">5000", condition: "${amount > 5000}" },
      { id: "e5", source: "task_2", target: "task_4", sourceHandle: "out" },
      { id: "e6", source: "task_3", target: "task_4", sourceHandle: "out" },
      { id: "e7", source: "task_4", target: "end_1", sourceHandle: "out" }
    ],
    variables: [
      { name: "applicant", type: "string", description: "申请人" },
      { name: "manager", type: "string", description: "主管" },
      { name: "director", type: "string", description: "总监" },
      { name: "amount", type: "number", description: "报销金额" }
    ]
  }
}

/** 所有内置模板 */
export const builtinTemplates: FlowTemplate[] = [leaveApproval, expenseApproval]
