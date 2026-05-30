/**
 * 对话式界面搭建组件
 * 用户通过对话描述需求 → AI 追问细节 → 实时预览 → 迭代修改
 * @author AaronZZH & Kiro
 */

"use client"

import { useCallback, useReducer } from "react"

import type { EntityDef } from "@/lib/types/entity"

import { ComponentGenerator, type GenerationResult } from "./ComponentGenerator"

/** 对话消息 */
export interface BuilderMessage {
  id: string
  role: "user" | "assistant"
  content: string
  timestamp: number
  /** 关联的生成结果（assistant 消息可能附带预览） */
  generationResult?: GenerationResult
}

/** 追问问题 */
interface ClarificationQuestion {
  field: string
  question: string
  options?: string[]
}

/** Builder 状态 */
interface BuilderState {
  messages: BuilderMessage[]
  currentResult: GenerationResult | null
  isProcessing: boolean
  pendingQuestions: ClarificationQuestion[]
}

type BuilderAction =
  | { type: "ADD_MESSAGE"; message: BuilderMessage }
  | { type: "SET_RESULT"; result: GenerationResult | null }
  | { type: "SET_PROCESSING"; value: boolean }
  | { type: "SET_QUESTIONS"; questions: ClarificationQuestion[] }
  | { type: "CLEAR_QUESTIONS" }

function builderReducer(state: BuilderState, action: BuilderAction): BuilderState {
  switch (action.type) {
    case "ADD_MESSAGE":
      return { ...state, messages: [...state.messages, action.message] }
    case "SET_RESULT":
      return { ...state, currentResult: action.result }
    case "SET_PROCESSING":
      return { ...state, isProcessing: action.value }
    case "SET_QUESTIONS":
      return { ...state, pendingQuestions: action.questions }
    case "CLEAR_QUESTIONS":
      return { ...state, pendingQuestions: [] }
  }
}

/** 判断是否需要追问 */
function detectClarifications(text: string): ClarificationQuestion[] {
  const questions: ClarificationQuestion[] = []
  const normalized = text.toLowerCase()

  // 未指定字段
  if (!normalized.includes("字段") && !normalized.includes("field")) {
    questions.push({
      field: "fields",
      question: "需要包含哪些字段？",
      options: ["标题+状态+创建时间", "自定义字段列表", "从已有实体继承"]
    })
  }

  // 未指定操作
  if (
    !normalized.includes("操作") &&
    !normalized.includes("action") &&
    !normalized.includes("按钮")
  ) {
    questions.push({
      field: "actions",
      question: "需要哪些操作按钮？",
      options: ["新建+删除", "新建+编辑+删除+导出", "仅查看"]
    })
  }

  return questions
}

/** 生成 assistant 回复 */
function generateReply(result: GenerationResult, questions: ClarificationQuestion[]): string {
  const parts: string[] = []

  if (result.config.slug) {
    parts.push(
      `已为您生成「${result.config.label ?? result.config.slug}」的${result.intent.type === "generate-view" ? "列表" : "表单"}视图配置。`
    )
  }

  if (result.intent.features.length > 0) {
    parts.push(`包含功能：${result.intent.features.join("、")}。`)
  }

  if (questions.length > 0) {
    parts.push("\n还需要确认以下细节：")
    for (const q of questions) {
      parts.push(`- ${q.question}${q.options ? `（${q.options.join(" / ")}）` : ""}`)
    }
  } else {
    parts.push("您可以继续描述修改需求，如\u201C把表格改成卡片布局\u201D。")
  }

  return parts.join("\n")
}

/** 对话式界面搭建 Hook */
export function useConversationalBuilder(entityDef?: EntityDef) {
  const [state, dispatch] = useReducer(builderReducer, {
    messages: [],
    currentResult: null,
    isProcessing: false,
    pendingQuestions: []
  })

  /** 发送用户消息 */
  const sendMessage = useCallback(
    (text: string) => {
      // 添加用户消息
      const userMsg: BuilderMessage = {
        id: `msg_${Date.now()}`,
        role: "user",
        content: text,
        timestamp: Date.now()
      }
      dispatch({ type: "ADD_MESSAGE", message: userMsg })
      dispatch({ type: "SET_PROCESSING", value: true })

      // 生成或增量更新
      const result = state.currentResult
        ? ComponentGenerator.update(state.currentResult, text)
        : ComponentGenerator.generate(text, entityDef)

      dispatch({ type: "SET_RESULT", result })

      // 检测是否需要追问
      const questions = detectClarifications(text)
      dispatch({ type: "SET_QUESTIONS", questions })

      // 生成 assistant 回复
      const reply = generateReply(result, questions)
      const assistantMsg: BuilderMessage = {
        id: `msg_${Date.now() + 1}`,
        role: "assistant",
        content: reply,
        timestamp: Date.now(),
        generationResult: result
      }
      dispatch({ type: "ADD_MESSAGE", message: assistantMsg })
      dispatch({ type: "SET_PROCESSING", value: false })
    },
    [state.currentResult, entityDef]
  )

  /** 回答追问 */
  const answerQuestion = useCallback(
    (_field: string, answer: string) => {
      dispatch({ type: "CLEAR_QUESTIONS" })
      sendMessage(answer)
    },
    [sendMessage]
  )

  /** 重置对话 */
  const reset = useCallback(() => {
    dispatch({ type: "SET_RESULT", result: null })
    dispatch({ type: "SET_QUESTIONS", questions: [] })
  }, [])

  return {
    messages: state.messages,
    currentResult: state.currentResult,
    isProcessing: state.isProcessing,
    pendingQuestions: state.pendingQuestions,
    sendMessage,
    answerQuestion,
    reset
  }
}
