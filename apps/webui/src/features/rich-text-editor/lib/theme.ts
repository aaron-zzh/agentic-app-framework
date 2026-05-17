/**
 * Lexical 编辑器主题——节点 CSS 类名映射
 * @author AaronZZH & Kiro
 */

import type { EditorThemeClasses } from "lexical"

export const editorTheme: EditorThemeClasses = {
  paragraph: "mb-1 last:mb-0",
  heading: {
    h1: "text-2xl font-bold mb-2",
    h2: "text-xl font-semibold mb-2",
    h3: "text-lg font-medium mb-1"
  },
  text: {
    bold: "font-bold",
    italic: "italic",
    underline: "underline",
    strikethrough: "line-through",
    code: "font-mono bg-muted px-1 py-0.5 rounded text-sm"
  },
  list: {
    ul: "list-disc list-inside mb-1",
    ol: "list-decimal list-inside mb-1",
    listitem: "mb-0.5"
  },
  quote: "border-l-4 border-border pl-3 text-muted-foreground italic my-1",
  code: "block font-mono bg-muted p-3 rounded text-sm my-1 overflow-x-auto",
  link: "text-primary underline cursor-pointer"
}
