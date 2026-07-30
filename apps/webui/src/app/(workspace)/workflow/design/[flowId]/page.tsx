"use client"

import { useParams } from "next/navigation"
import { FlowEditorView } from "@/sections/workflow/view/flow-editor-view"

export default function WorkflowDesignEditorPage() {
  const params = useParams<{ flowId: string }>()
  return <FlowEditorView flowId={params.flowId} />
}
