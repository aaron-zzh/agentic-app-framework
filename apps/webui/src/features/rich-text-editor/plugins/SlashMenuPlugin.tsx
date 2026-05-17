/**
 * SlashMenuPlugin——/ 斜杠命令菜单
 * @author AaronZZH & Kiro
 *
 * 输入 / 弹出命令菜单，支持：标题/列表/代码块/引用/分割线
 */

"use client"

import { $createCodeNode } from "@lexical/code"
import { INSERT_ORDERED_LIST_COMMAND, INSERT_UNORDERED_LIST_COMMAND } from "@lexical/list"
import { useLexicalComposerContext } from "@lexical/react/LexicalComposerContext"
import {
  LexicalTypeaheadMenuPlugin,
  MenuOption,
  useBasicTypeaheadTriggerMatch
} from "@lexical/react/LexicalTypeaheadMenuPlugin"
import { $createHeadingNode, $createQuoteNode } from "@lexical/rich-text"
import { $setBlocksType } from "@lexical/selection"
import {
  $createParagraphNode,
  $getSelection,
  $isRangeSelection,
  COMMAND_PRIORITY_LOW,
  type TextNode
} from "lexical"
import { useCallback, useMemo, useState } from "react"
import { cn } from "@/lib/utils/cn"
import { OPEN_AI_WRITE_COMMAND } from "./AIWritePlugin"

/** 命令定义 */
interface SlashCommand {
  key: string
  label: string
  description: string
  icon: string
  keywords?: string[]
  execute: (editor: ReturnType<typeof useLexicalComposerContext>[0]) => void
}

const COMMANDS: SlashCommand[] = [
  {
    key: "h1",
    label: "标题 1",
    description: "大标题",
    icon: "H1",
    keywords: ["heading", "h1", "标题"],
    execute: (editor) =>
      editor.update(() => {
        const selection = $getSelection()
        if ($isRangeSelection(selection)) $setBlocksType(selection, () => $createHeadingNode("h1"))
      })
  },
  {
    key: "h2",
    label: "标题 2",
    description: "中标题",
    icon: "H2",
    keywords: ["heading", "h2"],
    execute: (editor) =>
      editor.update(() => {
        const selection = $getSelection()
        if ($isRangeSelection(selection)) $setBlocksType(selection, () => $createHeadingNode("h2"))
      })
  },
  {
    key: "h3",
    label: "标题 3",
    description: "小标题",
    icon: "H3",
    keywords: ["heading", "h3"],
    execute: (editor) =>
      editor.update(() => {
        const selection = $getSelection()
        if ($isRangeSelection(selection)) $setBlocksType(selection, () => $createHeadingNode("h3"))
      })
  },
  {
    key: "ul",
    label: "无序列表",
    description: "项目符号列表",
    icon: "•",
    keywords: ["list", "bullet", "ul", "列表"],
    execute: (editor) => editor.dispatchCommand(INSERT_UNORDERED_LIST_COMMAND, undefined)
  },
  {
    key: "ol",
    label: "有序列表",
    description: "编号列表",
    icon: "1.",
    keywords: ["list", "numbered", "ol"],
    execute: (editor) => editor.dispatchCommand(INSERT_ORDERED_LIST_COMMAND, undefined)
  },
  {
    key: "quote",
    label: "引用",
    description: "引用块",
    icon: "❝",
    keywords: ["quote", "blockquote", "引用"],
    execute: (editor) =>
      editor.update(() => {
        const selection = $getSelection()
        if ($isRangeSelection(selection)) $setBlocksType(selection, () => $createQuoteNode())
      })
  },
  {
    key: "code",
    label: "代码块",
    description: "代码片段",
    icon: "</>",
    keywords: ["code", "代码"],
    execute: (editor) =>
      editor.update(() => {
        const selection = $getSelection()
        if ($isRangeSelection(selection)) $setBlocksType(selection, () => $createCodeNode())
      })
  },
  {
    key: "paragraph",
    label: "正文",
    description: "普通段落",
    icon: "¶",
    keywords: ["paragraph", "text", "正文"],
    execute: (editor) =>
      editor.update(() => {
        const selection = $getSelection()
        if ($isRangeSelection(selection)) $setBlocksType(selection, () => $createParagraphNode())
      })
  },
  {
    key: "ai",
    label: "AI 写作",
    description: "用 AI 生成内容",
    icon: "✨",
    keywords: ["ai", "生成", "写作", "gpt"],
    execute: (editor) => editor.dispatchCommand(OPEN_AI_WRITE_COMMAND, undefined)
  }
]

class SlashMenuOption extends MenuOption {
  command: SlashCommand

  constructor(command: SlashCommand) {
    super(command.key)
    this.command = command
  }
}

/** 斜杠命令菜单插件 */
export function SlashMenuPlugin() {
  const [editor] = useLexicalComposerContext()
  const [queryString, setQueryString] = useState<string | null>(null)

  // 使用官方 hook 检测 / 触发，允许空查询（直接输入 / 就触发）
  const checkForSlashTriggerMatch = useBasicTypeaheadTriggerMatch("/", {
    minLength: 0,
    maxLength: 20,
    allowWhitespace: false
  })

  const options = useMemo(() => {
    const q = queryString?.toLowerCase() ?? ""
    return COMMANDS.filter(
      (cmd) =>
        !q ||
        cmd.label.toLowerCase().includes(q) ||
        cmd.key.includes(q) ||
        cmd.keywords?.some((k) => k.includes(q))
    ).map((cmd) => new SlashMenuOption(cmd))
  }, [queryString])

  const onSelectOption = useCallback(
    (option: SlashMenuOption, textNodeWithQuery: TextNode | null, closeMenu: () => void) => {
      editor.update(() => {
        // 删除 /query 文本
        textNodeWithQuery?.remove()
      })
      option.command.execute(editor)
      closeMenu()
    },
    [editor]
  )

  return (
    <LexicalTypeaheadMenuPlugin<SlashMenuOption>
      onQueryChange={setQueryString}
      onSelectOption={onSelectOption}
      triggerFn={checkForSlashTriggerMatch}
      options={options}
      commandPriority={COMMAND_PRIORITY_LOW}
      menuRenderFn={(
        anchorElementRef,
        { selectedIndex, selectOptionAndCleanUp, setHighlightedIndex }
      ) => {
        if (!anchorElementRef.current || !options.length) return null
        const rect = anchorElementRef.current.getBoundingClientRect()
        return (
          <div
            className="fixed z-50 min-w-48 rounded-md border bg-popover p-1 shadow-md"
            style={{ top: rect.bottom + 4, left: rect.left }}
          >
            {options.map((opt, i) => (
              <button
                key={opt.key}
                type="button"
                className={cn(
                  "flex w-full items-center gap-3 rounded px-2 py-1.5 text-left text-sm",
                  selectedIndex === i ? "bg-accent text-accent-foreground" : "hover:bg-accent"
                )}
                onMouseEnter={() => setHighlightedIndex(i)}
                onClick={() => selectOptionAndCleanUp(opt)}
              >
                <span className="flex h-7 w-7 items-center justify-center rounded border bg-background font-mono text-xs">
                  {opt.command.icon}
                </span>
                <div>
                  <div className="font-medium">{opt.command.label}</div>
                  <div className="text-muted-foreground text-xs">{opt.command.description}</div>
                </div>
              </button>
            ))}
          </div>
        )
      }}
    />
  )
}
