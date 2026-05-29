/**
 * AI 工作流节点集——LLM/Agent/知识检索/条件/HTTP/代码/控制流
 * @author AaronZZH & Kiro
 */

"use client"

import { createNodeRegistry, registerNodeType, setWorkflowRegistry } from "../../lib/registry"
import type { InspectorProps, NodeTypeDef, NodeTypeRegistry, PortDef } from "../../types"
import { BaseNode } from "../_base/base-node"

/** 通用端口 */
const inputOutput: PortDef[] = [
  { id: "in", direction: "input" },
  { id: "out", direction: "output" }
]

/** Agent 节点属性面板 */
function AgentInspector({ data, onChange }: InspectorProps) {
  return (
    <div className="space-y-3">
      <div>
        <label htmlFor="agentId" className="font-medium text-sm">
          Agent ID
        </label>
        <input
          id="agentId"
          className="mt-1 w-full rounded-md border border-input px-3 py-1.5 text-sm"
          value={(data.agentId as string) ?? ""}
          onChange={(e) => onChange({ ...data, agentId: e.target.value })}
          placeholder="智能体标识"
        />
      </div>
      <div>
        <label htmlFor="promptOverride" className="font-medium text-sm">
          Prompt 覆盖
        </label>
        <textarea
          id="promptOverride"
          className="mt-1 w-full rounded-md border border-input px-3 py-1.5 text-sm"
          rows={3}
          value={(data.promptOverride as string) ?? ""}
          onChange={(e) => onChange({ ...data, promptOverride: e.target.value })}
          placeholder="覆盖默认提示词（留空使用 Agent 默认）"
        />
      </div>
      <div>
        <label htmlFor="agent-modelId" className="font-medium text-sm">
          模型
        </label>
        <select
          id="agent-modelId"
          className="mt-1 w-full rounded-md border border-input px-3 py-1.5 text-sm"
          value={(data.modelId as string) ?? ""}
          onChange={(e) => onChange({ ...data, modelId: e.target.value })}
        >
          <option value="">默认模型</option>
          <option value="gpt-4o">GPT-4o</option>
          <option value="gpt-4o-mini">GPT-4o Mini</option>
          <option value="claude-sonnet-4-20250514">Claude Sonnet 4</option>
          <option value="deepseek-chat">DeepSeek Chat</option>
        </select>
      </div>
      <div>
        <label htmlFor="agent-temperature" className="font-medium text-sm">
          温度: {(data.temperature as number) ?? 0.7}
        </label>
        <input
          id="agent-temperature"
          type="range"
          min="0"
          max="2"
          step="0.1"
          className="mt-1 w-full"
          value={(data.temperature as number) ?? 0.7}
          onChange={(e) => onChange({ ...data, temperature: Number(e.target.value) })}
        />
      </div>
      <div>
        <label htmlFor="agent-tools" className="font-medium text-sm">
          工具绑定
        </label>
        <input
          id="agent-tools"
          className="mt-1 w-full rounded-md border border-input px-3 py-1.5 text-sm"
          value={(data.tools as string) ?? ""}
          onChange={(e) => onChange({ ...data, tools: e.target.value })}
          placeholder="工具名，逗号分隔"
        />
      </div>
      <div>
        <label htmlFor="agent-inputMapping" className="font-medium text-sm">
          输入映射 (JSON)
        </label>
        <textarea
          id="agent-inputMapping"
          className="mt-1 w-full rounded-md border border-input px-3 py-1.5 font-mono text-sm"
          rows={2}
          value={(data.inputMapping as string) ?? ""}
          onChange={(e) => onChange({ ...data, inputMapping: e.target.value })}
          placeholder='{"query": "${input}"}'
        />
      </div>
      <div>
        <label htmlFor="agent-outputMapping" className="font-medium text-sm">
          输出映射 (JSON)
        </label>
        <textarea
          id="agent-outputMapping"
          className="mt-1 w-full rounded-md border border-input px-3 py-1.5 font-mono text-sm"
          rows={2}
          value={(data.outputMapping as string) ?? ""}
          onChange={(e) => onChange({ ...data, outputMapping: e.target.value })}
          placeholder='{"result": "${output}"}'
        />
      </div>
    </div>
  )
}

/** LLM 节点属性面板 */
function LLMInspector({ data, onChange }: InspectorProps) {
  return (
    <div className="space-y-3">
      <div>
        <label htmlFor="llm-modelId" className="font-medium text-sm">
          模型
        </label>
        <select
          id="llm-modelId"
          className="mt-1 w-full rounded-md border border-input px-3 py-1.5 text-sm"
          value={(data.modelId as string) ?? ""}
          onChange={(e) => onChange({ ...data, modelId: e.target.value })}
        >
          <option value="">默认模型</option>
          <option value="gpt-4o">GPT-4o</option>
          <option value="gpt-4o-mini">GPT-4o Mini</option>
          <option value="claude-sonnet-4-20250514">Claude Sonnet 4</option>
          <option value="deepseek-chat">DeepSeek Chat</option>
        </select>
      </div>
      <div>
        <label htmlFor="prompt" className="font-medium text-sm">
          提示词
        </label>
        <textarea
          id="prompt"
          className="mt-1 w-full rounded-md border border-input px-3 py-1.5 text-sm"
          rows={3}
          value={(data.prompt as string) ?? ""}
          onChange={(e) => onChange({ ...data, prompt: e.target.value })}
          placeholder="输入系统提示词..."
        />
      </div>
      <div>
        <label htmlFor="llm-temperature" className="font-medium text-sm">
          温度: {(data.temperature as number) ?? 0.7}
        </label>
        <input
          id="llm-temperature"
          type="range"
          min="0"
          max="2"
          step="0.1"
          className="mt-1 w-full"
          value={(data.temperature as number) ?? 0.7}
          onChange={(e) => onChange({ ...data, temperature: Number(e.target.value) })}
        />
      </div>
      <div>
        <label htmlFor="llm-maxTokens" className="font-medium text-sm">
          最大 Tokens
        </label>
        <input
          id="llm-maxTokens"
          type="number"
          className="mt-1 w-full rounded-md border border-input px-3 py-1.5 text-sm"
          value={(data.maxTokens as number) ?? 2048}
          onChange={(e) => onChange({ ...data, maxTokens: Number(e.target.value) })}
          min={1}
          max={128000}
        />
      </div>
      <div>
        <label htmlFor="llm-outputSchema" className="font-medium text-sm">
          输出 Schema (JSON Schema)
        </label>
        <textarea
          id="llm-outputSchema"
          className="mt-1 w-full rounded-md border border-input px-3 py-1.5 font-mono text-sm"
          rows={4}
          value={(data.outputSchema as string) ?? ""}
          onChange={(e) => onChange({ ...data, outputSchema: e.target.value })}
          placeholder='{"type":"object","properties":{...}}'
        />
      </div>
    </div>
  )
}

/** 知识检索属性面板 */
function KnowledgeInspector({ data, onChange }: InspectorProps) {
  return (
    <div className="space-y-3">
      <div>
        <label htmlFor="kbId" className="font-medium text-sm">
          知识库 ID
        </label>
        <input
          id="kbId"
          className="mt-1 w-full rounded-md border border-input px-3 py-1.5 text-sm"
          value={(data.knowledgeBaseId as string) ?? ""}
          onChange={(e) => onChange({ ...data, knowledgeBaseId: e.target.value })}
          placeholder="知识库标识"
        />
      </div>
      <div>
        <label htmlFor="topK" className="font-medium text-sm">
          Top K
        </label>
        <input
          id="topK"
          type="number"
          className="mt-1 w-full rounded-md border border-input px-3 py-1.5 text-sm"
          value={(data.topK as number) ?? 5}
          onChange={(e) => onChange({ ...data, topK: Number(e.target.value) })}
        />
      </div>
      <div>
        <label htmlFor="similarityThreshold" className="font-medium text-sm">
          相似度阈值: {(data.similarityThreshold as number) ?? 0.7}
        </label>
        <input
          id="similarityThreshold"
          type="range"
          min="0"
          max="1"
          step="0.05"
          className="mt-1 w-full"
          value={(data.similarityThreshold as number) ?? 0.7}
          onChange={(e) => onChange({ ...data, similarityThreshold: Number(e.target.value) })}
        />
      </div>
    </div>
  )
}

/** 代码执行节点属性面板 */
function CodeInspector({ data, onChange }: InspectorProps) {
  return (
    <div className="space-y-3">
      <div>
        <label htmlFor="code-language" className="font-medium text-sm">
          语言
        </label>
        <select
          id="code-language"
          className="mt-1 w-full rounded-md border border-input px-3 py-1.5 text-sm"
          value={(data.language as string) ?? "js"}
          onChange={(e) => onChange({ ...data, language: e.target.value })}
        >
          <option value="js">JavaScript</option>
          <option value="python">Python</option>
        </select>
      </div>
      <div>
        <label htmlFor="code-content" className="font-medium text-sm">
          代码
        </label>
        <textarea
          id="code-content"
          className="mt-1 w-full rounded-md border border-input px-3 py-1.5 font-mono text-sm"
          rows={6}
          value={(data.code as string) ?? ""}
          onChange={(e) => onChange({ ...data, code: e.target.value })}
          placeholder="// 输入代码..."
        />
      </div>
    </div>
  )
}

/** 循环节点属性面板 */
function IterationInspector({ data, onChange }: InspectorProps) {
  return (
    <div className="space-y-3">
      <div>
        <label htmlFor="iter-items" className="font-medium text-sm">
          列表变量
        </label>
        <input
          id="iter-items"
          className="mt-1 w-full rounded-md border border-input px-3 py-1.5 text-sm"
          value={(data.items as string) ?? ""}
          onChange={(e) => onChange({ ...data, items: e.target.value })}
          placeholder="变量名（如 ${itemList}）"
        />
      </div>
      <div>
        <label htmlFor="iter-max" className="font-medium text-sm">
          最大迭代数
        </label>
        <input
          id="iter-max"
          type="number"
          className="mt-1 w-full rounded-md border border-input px-3 py-1.5 text-sm"
          value={(data.maxIterations as number) ?? 100}
          onChange={(e) => onChange({ ...data, maxIterations: Number(e.target.value) })}
          min={1}
          max={10000}
        />
      </div>
    </div>
  )
}

/** 等待节点属性面板 */
function WaitInspector({ data, onChange }: InspectorProps) {
  return (
    <div className="space-y-3">
      <div>
        <label htmlFor="wait-type" className="font-medium text-sm">
          等待类型
        </label>
        <select
          id="wait-type"
          className="mt-1 w-full rounded-md border border-input px-3 py-1.5 text-sm"
          value={(data.waitType as string) ?? "signal"}
          onChange={(e) => onChange({ ...data, waitType: e.target.value })}
        >
          <option value="signal">外部信号</option>
          <option value="timer">定时器</option>
          <option value="human">人工审批</option>
        </select>
      </div>
      <div>
        <label htmlFor="wait-key" className="font-medium text-sm">
          等待标识
        </label>
        <input
          id="wait-key"
          className="mt-1 w-full rounded-md border border-input px-3 py-1.5 text-sm"
          value={(data.waitKey as string) ?? ""}
          onChange={(e) => onChange({ ...data, waitKey: e.target.value })}
          placeholder="唯一标识（用于恢复信号匹配）"
        />
      </div>
    </div>
  )
}

/** 空属性面板 */
function EmptyInspector() {
  return <p className="text-muted-foreground text-sm">该节点无可配置属性</p>
}

/** 构建 AI 工作流节点注册表 */
function buildWorkflowRegistry(): NodeTypeRegistry {
  let registry = createNodeRegistry()

  registry = registerNodeType(registry, "start", {
    component: BaseNode as NodeTypeDef["component"],
    inspector: EmptyInspector,
    icon: "▶",
    label: "开始",
    category: "trigger",
    ports: [{ id: "out", direction: "output" }],
    defaultData: { label: "开始", icon: "▶", ports: [{ id: "out", direction: "output" }] }
  })

  registry = registerNodeType(registry, "end", {
    component: BaseNode as NodeTypeDef["component"],
    inspector: EmptyInspector,
    icon: "⏹",
    label: "结束",
    category: "output",
    ports: [{ id: "in", direction: "input" }],
    defaultData: { label: "结束", icon: "⏹", ports: [{ id: "in", direction: "input" }] }
  })

  registry = registerNodeType(registry, "llm", {
    component: BaseNode as NodeTypeDef["component"],
    inspector: LLMInspector,
    icon: "🤖",
    label: "LLM",
    category: "ai",
    ports: inputOutput,
    defaultData: {
      label: "LLM",
      icon: "🤖",
      ports: inputOutput,
      modelId: "",
      prompt: "",
      temperature: 0.7,
      maxTokens: 2048,
      outputSchema: ""
    }
  })

  registry = registerNodeType(registry, "agent", {
    component: BaseNode as NodeTypeDef["component"],
    inspector: AgentInspector,
    icon: "🧠",
    label: "Agent",
    category: "ai",
    ports: inputOutput,
    defaultData: {
      label: "Agent",
      icon: "🧠",
      ports: inputOutput,
      agentId: "",
      promptOverride: "",
      modelId: "",
      temperature: 0.7,
      tools: "",
      inputMapping: "",
      outputMapping: ""
    }
  })

  registry = registerNodeType(registry, "knowledge", {
    component: BaseNode as NodeTypeDef["component"],
    inspector: KnowledgeInspector,
    icon: "📚",
    label: "知识检索",
    category: "data",
    ports: inputOutput,
    defaultData: {
      label: "知识检索",
      icon: "📚",
      ports: inputOutput,
      knowledgeBaseId: "",
      topK: 5,
      similarityThreshold: 0.7
    }
  })

  registry = registerNodeType(registry, "code", {
    component: BaseNode as NodeTypeDef["component"],
    inspector: CodeInspector,
    icon: "💻",
    label: "代码执行",
    category: "tool",
    ports: inputOutput,
    defaultData: {
      label: "代码执行",
      icon: "💻",
      ports: inputOutput,
      language: "js",
      code: ""
    }
  })

  registry = registerNodeType(registry, "condition", {
    component: BaseNode as NodeTypeDef["component"],
    inspector: EmptyInspector,
    icon: "🔀",
    label: "条件分支",
    category: "logic",
    ports: [
      { id: "in", direction: "input" },
      { id: "out-true", direction: "output" },
      { id: "out-false", direction: "output" }
    ],
    defaultData: {
      label: "条件分支",
      icon: "🔀",
      ports: [
        { id: "in", direction: "input" },
        { id: "out-true", direction: "output" },
        { id: "out-false", direction: "output" }
      ]
    }
  })

  registry = registerNodeType(registry, "iteration", {
    component: BaseNode as NodeTypeDef["component"],
    inspector: IterationInspector,
    icon: "🔁",
    label: "循环",
    category: "logic",
    ports: [
      { id: "in", direction: "input" },
      { id: "loop-out", direction: "output", label: "循环体" },
      { id: "out", direction: "output", label: "完成" }
    ],
    defaultData: {
      label: "循环",
      icon: "🔁",
      ports: [
        { id: "in", direction: "input" },
        { id: "loop-out", direction: "output", label: "循环体" },
        { id: "out", direction: "output", label: "完成" }
      ],
      items: "",
      maxIterations: 100
    }
  })

  registry = registerNodeType(registry, "wait", {
    component: BaseNode as NodeTypeDef["component"],
    inspector: WaitInspector,
    icon: "⏸",
    label: "等待",
    category: "logic",
    ports: inputOutput,
    defaultData: {
      label: "等待",
      icon: "⏸",
      ports: inputOutput,
      waitType: "signal",
      waitKey: ""
    }
  })

  registry = registerNodeType(registry, "parallel", {
    component: BaseNode as NodeTypeDef["component"],
    inspector: EmptyInspector,
    icon: "⚡",
    label: "并行",
    category: "logic",
    ports: [
      { id: "in", direction: "input" },
      { id: "out-1", direction: "output", label: "分支 1" },
      { id: "out-2", direction: "output", label: "分支 2" },
      { id: "out-3", direction: "output", label: "分支 3" }
    ],
    defaultData: {
      label: "并行",
      icon: "⚡",
      ports: [
        { id: "in", direction: "input" },
        { id: "out-1", direction: "output", label: "分支 1" },
        { id: "out-2", direction: "output", label: "分支 2" },
        { id: "out-3", direction: "output", label: "分支 3" }
      ]
    }
  })

  registry = registerNodeType(registry, "http", {
    component: BaseNode as NodeTypeDef["component"],
    inspector: EmptyInspector,
    icon: "🌐",
    label: "HTTP 请求",
    category: "tool",
    ports: inputOutput,
    defaultData: { label: "HTTP 请求", icon: "🌐", ports: inputOutput }
  })

  return registry
}

export const workflowNodeRegistry = buildWorkflowRegistry()
setWorkflowRegistry(workflowNodeRegistry)
