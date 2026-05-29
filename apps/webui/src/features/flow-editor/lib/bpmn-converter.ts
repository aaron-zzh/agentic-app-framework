/**
 * 画布 JSON ↔ BPMN 2.0 XML 转换
 * @author AaronZZH & Kiro
 */

import type { FlowDefinition, FlowEdge, FlowNode } from "../types"

/** BPMN 节点类型映射 */
const NODE_TYPE_TO_BPMN: Record<string, string> = {
  start: "startEvent",
  end: "endEvent",
  userTask: "userTask",
  serviceTask: "serviceTask",
  gateway: "exclusiveGateway",
  subProcess: "subProcess",
  llm: "serviceTask",
  knowledge: "serviceTask",
  condition: "exclusiveGateway",
  http: "serviceTask"
}

/** 转义 XML 特殊字符 */
function escapeXml(str: string): string {
  return str
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
}

/** 画布 JSON → BPMN 2.0 XML */
export function flowToBpmn(flow: FlowDefinition, processId = "process_1"): string {
  const lines: string[] = []
  lines.push(`<?xml version="1.0" encoding="UTF-8"?>`)
  lines.push(`<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"`)
  lines.push(`  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"`)
  lines.push(`  targetNamespace="http://aaf.xuejiai.com/bpmn">`)
  lines.push(`  <process id="${escapeXml(processId)}" isExecutable="true">`)

  // 节点
  for (const node of flow.nodes) {
    const bpmnType = NODE_TYPE_TO_BPMN[node.type] ?? "task"
    const name = (node.data.label as string) ?? node.id
    lines.push(`    <${bpmnType} id="${escapeXml(node.id)}" name="${escapeXml(name)}">`)
    // 用户任务附加属性
    if (node.type === "userTask" && node.data.assignee) {
      lines.push(
        `      <humanPerformer><resourceAssignmentExpression><formalExpression>${escapeXml(node.data.assignee as string)}</formalExpression></resourceAssignmentExpression></humanPerformer>`
      )
    }
    lines.push(`    </${bpmnType}>`)
  }

  // 边（序列流）
  for (const edge of flow.edges) {
    lines.push(
      `    <sequenceFlow id="${escapeXml(edge.id)}" sourceRef="${escapeXml(edge.source)}" targetRef="${escapeXml(edge.target)}"${edge.label ? ` name="${escapeXml(edge.label)}"` : ""}>`
    )
    if (edge.condition) {
      lines.push(
        `      <conditionExpression xsi:type="tFormalExpression">${escapeXml(edge.condition)}</conditionExpression>`
      )
    }
    lines.push(`    </sequenceFlow>`)
  }

  lines.push(`  </process>`)
  lines.push(`</definitions>`)
  return lines.join("\n")
}

/** BPMN XML → 画布 JSON（简化解析，基于正则） */
export function bpmnToFlow(xml: string): FlowDefinition {
  const nodes: FlowNode[] = []
  const edges: FlowEdge[] = []

  // 解析节点
  const bpmnToNodeType: Record<string, string> = {}
  for (const [nodeType, bpmnType] of Object.entries(NODE_TYPE_TO_BPMN)) {
    bpmnToNodeType[bpmnType] = nodeType
  }

  // 匹配所有 BPMN 元素
  const elementRegex =
    /<(startEvent|endEvent|userTask|serviceTask|exclusiveGateway|subProcess|task)\s+id="([^"]+)"(?:\s+name="([^"]*)")?/g
  let match: RegExpExecArray | null
  let index = 0
  for (match = elementRegex.exec(xml); match !== null; match = elementRegex.exec(xml)) {
    const bpmnType = match[1]
    const id = match[2]
    const name = match[3] ?? id
    const nodeType = bpmnToNodeType[bpmnType] ?? "serviceTask"
    nodes.push({
      id,
      type: nodeType,
      position: { x: 100 + (index % 4) * 200, y: 100 + Math.floor(index / 4) * 150 },
      data: { label: name }
    })
    index++
  }

  // 匹配序列流
  const flowRegex =
    /<sequenceFlow\s+id="([^"]+)"\s+sourceRef="([^"]+)"\s+targetRef="([^"]+)"(?:\s+name="([^"]*)")?/g
  for (match = flowRegex.exec(xml); match !== null; match = flowRegex.exec(xml)) {
    const edge: FlowEdge = {
      id: match[1],
      source: match[2],
      target: match[3],
      label: match[4] || undefined
    }
    // 检查条件表达式
    const condRegex = new RegExp(
      `<sequenceFlow[^>]*id="${match[1]}"[\\s\\S]*?<conditionExpression[^>]*>([\\s\\S]*?)</conditionExpression>`
    )
    const condMatch = condRegex.exec(xml)
    if (condMatch) {
      edge.condition = condMatch[1].trim()
    }
    edges.push(edge)
  }

  return { nodes, edges }
}
