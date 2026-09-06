/**
 * DeliverableSet 证据、冻结 Manifest、Review 与 Work 收录闭环。
 * OPTIONAL 选择仅保存在当前组件，不进入 Zustand 或 ProjectObject。
 * @author AaronZZH & Kiro
 */

"use client"

import { CheckCheck, ClipboardCheck, FileLock2, Library, RotateCcw, Send } from "lucide-react"
import { useMemo, useState } from "react"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty"
import { Textarea } from "@/components/ui/textarea"
import type {
  AigcDeliverableSetCompletion,
  AigcProject,
  AigcProjectObject,
  AigcReview
} from "@/lib/api/rest/ai/aigc"
import {
  useAigcCompletionEvidence,
  useAigcReviews,
  useAigcWorks,
  useApproveAigcProjectReview,
  useCollectAigcWork,
  useEvaluateAigcDeliverableSet,
  useFreezeAigcDeliverableSet,
  useReturnAigcProjectReview,
  useSubmitAigcReview
} from "@/lib/api/rest/ai/aigc"
import { notify } from "@/lib/notification"

const REVIEW_LABEL: Record<AigcReview["status"], string> = {
  PENDING: "待审核",
  APPROVED: "已通过",
  RETURNED: "已退回",
  STALE: "证据已过期"
}
const REVIEW_VARIANT = {
  PENDING: "default",
  APPROVED: "secondary",
  RETURNED: "destructive",
  STALE: "outline"
} as const

export function DeliveryWorkflowPanel({
  project,
  objects,
  canEvaluate,
  canFreeze,
  canSubmitReview,
  canDecideReview,
  canCollect
}: {
  project: AigcProject
  objects: AigcProjectObject[]
  canEvaluate: boolean
  canFreeze: boolean
  canSubmitReview: boolean
  canDecideReview: boolean
  canCollect: boolean
}) {
  const [optionalIds, setOptionalIds] = useState<number[]>([])
  const [evaluation, setEvaluation] = useState<AigcDeliverableSetCompletion | null>(null)
  const [comment, setComment] = useState("")
  const setObject = objects.find((object) => object.objectType === "deliverable_set")
  const optionalObjects = useMemo(
    () =>
      objects.filter(
        (object) => object.parentId === setObject?.id && object.contractRole === "OPTIONAL"
      ),
    [objects, setObject?.id]
  )
  const { data: reviews = [] } = useAigcReviews(project.id)
  const { data: completion } = useAigcCompletionEvidence(project.id)
  const { data: workPage } = useAigcWorks({ projectId: project.id })
  const evaluate = useEvaluateAigcDeliverableSet()
  const freeze = useFreezeAigcDeliverableSet()
  const submit = useSubmitAigcReview()
  const approve = useApproveAigcProjectReview()
  const returnReview = useReturnAigcProjectReview()
  const collect = useCollectAigcWork()
  const manifestId = setObject?.adoptedVersionId ?? null
  const review =
    manifestId === null
      ? undefined
      : reviews.find(
          (item) =>
            item.subjectObjectId === setObject?.id && item.subjectObjectVersionId === manifestId
        )
  const work =
    manifestId === null
      ? undefined
      : workPage?.list.find(
          (item) =>
            item.deliverableSetObjectId === setObject?.id &&
            item.manifestObjectVersionId === manifestId
        )
  const evaluateEnabled =
    canEvaluate && ["MATERIALIZED", "CREATING", "EXECUTING", "ADOPTING"].includes(project.status)
  const freezeEnabled =
    canFreeze && ["MATERIALIZED", "CREATING", "EXECUTING", "ADOPTING"].includes(project.status)
  const submitReviewEnabled = canSubmitReview && ["CREATING", "ADOPTING"].includes(project.status)
  const decideReviewEnabled = canDecideReview && project.status === "REVIEWING"
  const collectWorkEnabled = canCollect && project.status === "DELIVERING"

  if (!setObject) {
    return (
      <Empty className="min-h-44">
        <EmptyHeader>
          <EmptyTitle>蓝图没有 DeliverableSet</EmptyTitle>
          <EmptyDescription>当前项目无法建立交付 Manifest，请检查固化的配置快照。</EmptyDescription>
        </EmptyHeader>
      </Empty>
    )
  }
  const setObjectId = setObject.id

  function runEvaluation() {
    if (!evaluateEnabled) return
    evaluate.mutate(
      {
        projectId: project.id,
        setObjectId,
        includedOptionalObjectIds: optionalIds,
        expectedGraphRevision: project.graphRevision
      },
      {
        onSuccess: (result) => {
          setEvaluation(result)
          notify.success(result.complete ? "交付证据已满足" : "已评估交付证据")
        }
      }
    )
  }

  function runFreeze() {
    if (!freezeEnabled || !evaluation) return
    freeze.mutate(
      {
        projectId: project.id,
        setObjectId,
        includedOptionalObjectIds: optionalIds,
        expectedGraphRevision: evaluation.graphRevision,
        expectedEvidenceHash: evaluation.evidenceHash,
        expectedProjectVersion: project.version,
        idempotencyKey: crypto.randomUUID()
      },
      {
        onSuccess: () => {
          setEvaluation(null)
          notify.success("Manifest 已冻结；后续证据变化会使旧 Review stale")
        },
        onError: (error) =>
          notify.error(
            error instanceof Error
              ? `${error.message}；权威项目证据已刷新`
              : "冻结失败；权威项目证据已刷新"
          )
      }
    )
  }

  function decide(kind: "approve" | "return") {
    if (
      !decideReviewEnabled ||
      !review ||
      manifestId === null ||
      (kind === "return" && !comment.trim())
    )
      return
    const mutation = kind === "approve" ? approve : returnReview
    mutation.mutate(
      {
        projectId: project.id,
        reviewObjectId: review.reviewObjectId,
        expectedManifestObjectVersionId: manifestId,
        expectedProjectVersion: project.version,
        expectedReviewVersion: review.reviewVersion,
        comment: comment.trim() || undefined,
        idempotencyKey: crypto.randomUUID()
      },
      {
        onSuccess: () => {
          setComment("")
          notify.success(kind === "approve" ? "Manifest 已审核通过" : "Manifest 已退回")
        },
        onError: (error) =>
          notify.error(
            error instanceof Error
              ? `${error.message}；权威 Review 已刷新`
              : "审核失败；权威 Review 已刷新"
          )
      }
    )
  }

  return (
    <section className="flex flex-col gap-4 rounded-xl border p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="font-medium">交付、审核与作品</h2>
          <p className="text-muted-foreground text-sm">
            DeliverableSet #{setObject.id} · graph r{project.graphRevision}
          </p>
        </div>
        {manifestId ? (
          <Badge variant="secondary">Manifest ObjectVersion #{manifestId}</Badge>
        ) : (
          <Badge variant="outline">尚未冻结</Badge>
        )}
      </div>

      {optionalObjects.length > 0 ? (
        <div className="flex flex-col gap-2 rounded-lg border p-3">
          <p className="font-medium text-sm">本次纳入的 OPTIONAL 槽位</p>
          {optionalObjects.map((object) => (
            <label
              key={object.id}
              htmlFor={`optional-object-${object.id}`}
              className="flex items-center gap-2 text-sm"
            >
              <Checkbox
                id={`optional-object-${object.id}`}
                checked={optionalIds.includes(object.id)}
                disabled={!evaluateEnabled}
                onCheckedChange={(checked) => {
                  setEvaluation(null)
                  setOptionalIds((current) =>
                    checked === true
                      ? [...current, object.id]
                      : current.filter((id) => id !== object.id)
                  )
                }}
              />
              {object.title || object.stableKey}{" "}
              {object.adoptedVersionId ? `(采用版 #${object.adoptedVersionId})` : "（未采用）"}
            </label>
          ))}
          <p className="text-muted-foreground text-xs">
            选择只存在于本次 UI 会话；服务端 evaluate 会规范化 REQUIRED/OPTIONAL/EXCLUDED。
          </p>
        </div>
      ) : null}

      {evaluateEnabled || freezeEnabled ? (
        <div className="flex flex-wrap gap-2">
          {evaluateEnabled ? (
            <Button
              type="button"
              variant="outline"
              disabled={evaluate.isPending}
              onClick={runEvaluation}
            >
              <ClipboardCheck />
              评估证据
            </Button>
          ) : null}
          {freezeEnabled ? (
            <Button
              type="button"
              disabled={!evaluation?.complete || freeze.isPending}
              onClick={runFreeze}
            >
              <FileLock2 />
              冻结 Manifest
            </Button>
          ) : null}
        </div>
      ) : null}

      {evaluation ? (
        <Alert variant={evaluation.complete ? "default" : "destructive"}>
          <AlertTitle>{evaluation.complete ? "证据完整，可冻结" : "存在交付 blocker"}</AlertTitle>
          <AlertDescription className="flex flex-col gap-2">
            <span>evidenceHash: {evaluation.evidenceHash}</span>
            {evaluation.blockers.map((blocker) => (
              <span key={blocker}>• {blocker}</span>
            ))}
            <span>{evaluation.includedProjectObjectIds.length} 个对象将写入 Manifest</span>
          </AlertDescription>
        </Alert>
      ) : null}

      {manifestId && !review && submitReviewEnabled ? (
        <Button
          type="button"
          className="w-fit"
          disabled={submit.isPending}
          onClick={() => {
            if (!submitReviewEnabled) return
            submit.mutate(
              {
                projectId: project.id,
                setObjectId,
                manifestObjectVersionId: manifestId,
                expectedProjectVersion: project.version,
                idempotencyKey: crypto.randomUUID()
              },
              { onSuccess: () => notify.success("Manifest 已提交审核") }
            )
          }}
        >
          <Send />
          提交 Review
        </Button>
      ) : null}

      {review ? (
        <div className="flex flex-col gap-3 rounded-lg border p-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div>
              <p className="font-medium text-sm">Review #{review.reviewObjectId}</p>
              <p className="text-muted-foreground text-xs">
                固定 Manifest #{review.subjectObjectVersionId} · evidence {review.evidenceHash}
              </p>
            </div>
            <Badge variant={REVIEW_VARIANT[review.status]}>{REVIEW_LABEL[review.status]}</Badge>
          </div>
          {review.comment ? <p className="text-sm">意见：{review.comment}</p> : null}
          {review.staleReason ? (
            <Alert variant="destructive">
              <AlertTitle>Review 已 stale</AlertTitle>
              <AlertDescription>
                {review.staleReason}；请重新 evaluate、freeze 并提交新 Review。
              </AlertDescription>
            </Alert>
          ) : null}
          {review.status === "PENDING" && decideReviewEnabled ? (
            <>
              <Textarea
                value={comment}
                onChange={(event) => setComment(event.target.value)}
                placeholder="审核意见；退回时必填"
              />
              <div className="flex gap-2">
                <Button
                  type="button"
                  variant="outline"
                  disabled={approve.isPending || returnReview.isPending || !comment.trim()}
                  onClick={() => decide("return")}
                >
                  <RotateCcw />
                  退回
                </Button>
                <Button
                  type="button"
                  disabled={approve.isPending || returnReview.isPending}
                  onClick={() => decide("approve")}
                >
                  <CheckCheck />
                  通过
                </Button>
              </div>
            </>
          ) : null}
        </div>
      ) : null}

      {project.status === "DELIVERING" && completion ? (
        <Alert variant={completion.satisfied ? "default" : "destructive"}>
          <AlertTitle>
            完成策略：{completion.publicationPolicy} · {completion.satisfied ? "已满足" : "未满足"}
          </AlertTitle>
          <AlertDescription>
            <span className="block">
              active Work {completion.activeWorkCount} · 成功渠道{" "}
              {completion.succeededChannelSpecVersionIds.length}/
              {completion.requiredChannelSpecVersionIds.length}
            </span>
            {completion.blockers.map((blocker) => (
              <span key={blocker} className="block">
                • {blocker}
              </span>
            ))}
          </AlertDescription>
        </Alert>
      ) : null}

      {review?.status === "APPROVED" && project.status === "DELIVERING" ? (
        work ? (
          <Alert>
            <Library />
            <AlertTitle>已收录 Work #{work.id}</AlertTitle>
            <AlertDescription>
              精确固定 DeliverableSet #{work.deliverableSetObjectId} / Manifest #
              {work.manifestObjectVersionId}。
            </AlertDescription>
          </Alert>
        ) : collectWorkEnabled ? (
          <Button
            type="button"
            className="w-fit"
            disabled={collect.isPending}
            onClick={() => {
              if (!collectWorkEnabled) return
              collect.mutate(
                {
                  projectId: project.id,
                  deliverableSetObjectId: setObject.id,
                  manifestObjectVersionId: review.subjectObjectVersionId,
                  expectedProjectVersion: project.version,
                  visibility: "PRIVATE",
                  idempotencyKey: crypto.randomUUID()
                },
                { onSuccess: () => notify.success("已按 approved non-stale Manifest 收录 Work") }
              )
            }}
          >
            <Library />
            收录为 Work
          </Button>
        ) : null
      ) : null}
    </section>
  )
}
