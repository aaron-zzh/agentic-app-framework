/**
 * HTML ↔ EditorState 序列化工具
 * @author AaronZZH & Kiro
 */

import { $generateHtmlFromNodes, $generateNodesFromDOM } from "@lexical/html"
import { $getRoot, $insertNodes, type LexicalEditor } from "lexical"

/** EditorState → HTML 字符串 */
export function editorStateToHtml(editor: LexicalEditor): string {
  return editor.getEditorState().read(() => $generateHtmlFromNodes(editor))
}

/** HTML 字符串 → 写入编辑器 */
export function htmlToEditorState(editor: LexicalEditor, html: string): void {
  editor.update(() => {
    const parser = new DOMParser()
    const dom = parser.parseFromString(html, "text/html")
    const nodes = $generateNodesFromDOM(editor, dom)
    $getRoot().clear()
    $getRoot().select()
    $insertNodes(nodes)
  })
}

/** EditorState → 纯文本 */
export function editorStateToPlainText(editor: LexicalEditor): string {
  return editor.getEditorState().read(() => $getRoot().getTextContent())
}
