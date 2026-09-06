/**
 * Project 完成与归档动作。
 * Review 决策由 Manifest 交付面板负责，不混入 lifecycle mutation。
 * @author AaronZZH & Kiro
 */

"use client"

import { Archive, CircleCheckBig } from "lucide-react"
import { useState } from "react"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Textarea } from "@/components/ui/textarea"
import type { AigcProject, AigcProjectObject } from "@/lib/api/rest/ai/aigc"
import { useAigcCompletionEvidence, useAigcProjectLifecycle } from "@/lib/api/rest/ai/aigc"
import { notify } from "@/lib/notification"

type LifecycleAction = "complete" | "archive"

export function ProjectLifecycleActions({ project, objects: _objects, canLifecycleUpdate }: { project: AigcProject; objects: AigcProjectObject[]; canLifecycleUpdate: boolean }) {
  const [action, setAction] = useState<LifecycleAction | null>(null)
  const [reason, setReason] = useState("")
  const lifecycle = useAigcProjectLifecycle()
  const { data: evidence } = useAigcCompletionEvidence(project.id)
  const completionReady = evidence?.satisfied === true

  function execute() {
    if (!canLifecycleUpdate || !action || !reason.trim()) return
    if (action === "complete" && (project.status !== "DELIVERING" || !completionReady)) return
    if (action === "archive" && project.status === "ARCHIVED") return
    lifecycle.mutate({ projectId: project.id, action, expectedProjectVersion: project.version, reason: reason.trim(), idempotencyKey: crypto.randomUUID() }, {
      onSuccess: () => { notify.success(action === "complete" ? "项目已完成并进入全局只读" : "项目已归档并保留全部历史"); setAction(null); setReason("") }
    })
  }

  if (!canLifecycleUpdate) return null

  return (
    <div className="flex flex-wrap items-center gap-2">
      {project.status === "DELIVERING" ? <Button type="button" size="sm" disabled={lifecycle.isPending || !completionReady} title={completionReady ? undefined : evidence?.blockers.join("；") || "正在读取完成证据"} onClick={() => setAction("complete")}><CircleCheckBig />完成项目</Button> : null}
      {project.status !== "ARCHIVED" ? <Button type="button" variant="outline" size="sm" disabled={lifecycle.isPending} onClick={() => setAction("archive")}><Archive />{project.status === "COMPLETED" ? "完成后归档" : "放弃并归档"}</Button> : null}

      <Dialog open={action !== null} onOpenChange={(open) => { if (!open) { setAction(null); setReason("") } }}>
        <DialogContent><DialogHeader><DialogTitle>{action === "complete" ? "完成项目" : "归档项目"}</DialogTitle><DialogDescription>{action === "complete" ? "完成前服务端会重新验证 Work、Publication 和 processPolicy，成功后项目全局只读。" : "归档不会物理删除图谱、版本、Run、Review、Work 或 Publication 历史。"}</DialogDescription></DialogHeader>
          {action === "complete" && evidence && !evidence.satisfied ? <Alert variant="destructive"><AlertTitle>完成策略尚未满足：{evidence.publicationPolicy}</AlertTitle><AlertDescription>{evidence.blockers.map((blocker) => <span key={blocker} className="block">• {blocker}</span>)}</AlertDescription></Alert> : null}
          <Textarea value={reason} onChange={(event) => setReason(event.target.value)} placeholder={action === "complete" ? "填写完成说明（必填）" : "填写归档/放弃原因（必填）"} />
          <DialogFooter><Button type="button" variant="outline" onClick={() => setAction(null)}>取消</Button><Button type="button" variant={action === "archive" ? "destructive" : "default"} disabled={!reason.trim() || lifecycle.isPending || (action === "complete" && !completionReady)} onClick={execute}>确认</Button></DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
