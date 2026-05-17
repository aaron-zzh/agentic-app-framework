"use client"

import { useState } from "react"
import { ComponentLayout } from "@/components/common/ComponentLayout"
import { RichTextEditor } from "@/features/rich-text-editor"

export default function EditorPage() {
  const [richHtml, setRichHtml] = useState("")
  const [chatterHtml, setChatterHtml] = useState("")
  const [minimalHtml, setMinimalHtml] = useState("")

  return (
    <ComponentLayout
      heading="编辑器"
      description="基于 Lexical 的富文本编辑器，支持多种 preset。"
      links={[
        { name: "Lexical", href: "https://lexical.dev/" },
        { name: "Meta Open Source", href: "https://github.com/facebook/lexical" }
      ]}
      sectionData={[
        {
          name: "richField",
          description: "表单字段，完整工具栏",
          component: (
            <div className="w-full">
              <RichTextEditor value={richHtml} onChange={setRichHtml}
                placeholder="输入富文本内容..." preset="richField" />
              {richHtml && <p className="mt-1 text-muted-foreground text-xs">HTML 长度：{richHtml.length}</p>}
            </div>
          )
        },
        {
          name: "chatter",
          description: "评论输入，支持 @mention",
          component: (
            <div className="w-full">
              <RichTextEditor value={chatterHtml} onChange={setChatterHtml}
                placeholder="输入评论，@ 提及用户..." preset="chatter" />
            </div>
          )
        },
        {
          name: "minimal",
          description: "简单格式，粗体/斜体",
          component: (
            <div className="w-full">
              <RichTextEditor value={minimalHtml} onChange={setMinimalHtml}
                placeholder="简单文本..." preset="minimal" minHeight={80} />
            </div>
          )
        },
        {
          name: "document",
          description: "文档编辑，含图片上传",
          component: (
            <div className="w-full">
              <RichTextEditor value="" onChange={() => {}}
                placeholder="支持图片上传（工具栏 🖼 或粘贴/拖拽）..."
                preset="document" uploadEndpoint="/api/upload" />
            </div>
          )
        },
        {
          name: "disabled",
          description: "只读状态",
          component: (
            <div className="w-full">
              <RichTextEditor
                value="<p>这是<strong>只读</strong>内容，<em>无法编辑</em>。</p>"
                preset="richField" disabled />
            </div>
          )
        }
      ]}
    />
  )
}
