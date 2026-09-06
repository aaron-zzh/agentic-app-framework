/**
 * 交付工作流 capability 对称性组件测试。
 * @author AaronZZH & Kiro
 */

import { fireEvent, render, screen } from "@testing-library/react"
import { beforeEach, describe, expect, it, vi } from "vitest"
import type { AigcProject, AigcProjectObject, AigcReview } from "@/lib/api/rest/ai/aigc"

const state = vi.hoisted(() => ({
  reviews: [] as AigcReview[],
  works: [] as Array<{ deliverableSetObjectId: number; manifestObjectVersionId: number }>,
  evaluate: { mutate: vi.fn(), isPending: false },
  freeze: { mutate: vi.fn(), isPending: false },
  submit: { mutate: vi.fn(), isPending: false },
  approve: { mutate: vi.fn(), isPending: false },
  returnReview: { mutate: vi.fn(), isPending: false },
  collect: { mutate: vi.fn(), isPending: false }
}))

vi.mock("@/lib/api/rest/ai/aigc", () => ({
  useAigcReviews: () => ({ data: state.reviews }),
  useAigcCompletionEvidence: () => ({ data: undefined }),
  useAigcWorks: () => ({ data: { list: state.works } }),
  useEvaluateAigcDeliverableSet: () => state.evaluate,
  useFreezeAigcDeliverableSet: () => state.freeze,
  useSubmitAigcReview: () => state.submit,
  useApproveAigcProjectReview: () => state.approve,
  useReturnAigcProjectReview: () => state.returnReview,
  useCollectAigcWork: () => state.collect
}))
vi.mock("@/lib/notification", () => ({
  notify: { success: vi.fn(), error: vi.fn() }
}))

import { DeliveryWorkflowPanel } from "./DeliveryWorkflowPanel"

const deliverableSet = {
  id: 12,
  objectType: "deliverable_set",
  adoptedVersionId: 31,
  stableKey: "deliverable-set"
} as AigcProjectObject

function project(status: AigcProject["status"]): AigcProject {
  return { id: 7, status, graphRevision: 4, version: 2 } as AigcProject
}

const denied = {
  canEvaluate: false,
  canFreeze: false,
  canSubmitReview: false,
  canDecideReview: false,
  canCollect: false
}

describe("DeliveryWorkflowPanel capability gates", () => {
  beforeEach(() => {
    state.reviews = []
    state.works = []
    for (const mutation of [
      state.evaluate,
      state.freeze,
      state.submit,
      state.approve,
      state.returnReview,
      state.collect
    ]) {
      mutation.mutate.mockReset()
    }
  })

  it("无 capability 时不渲染可变更入口", () => {
    render(<DeliveryWorkflowPanel project={project("CREATING")} objects={[deliverableSet]} {...denied} />)
    expect(screen.queryByRole("button", { name: /评估证据/ })).not.toBeInTheDocument()
    expect(screen.queryByRole("button", { name: /冻结 Manifest/ })).not.toBeInTheDocument()
    expect(screen.queryByRole("button", { name: /提交 Review/ })).not.toBeInTheDocument()
  })

  it("evaluate 与 freeze 分别由 read/update capability 控制并调用对应 handler", () => {
    const evaluation = {
      complete: true,
      graphRevision: 4,
      evidenceHash: "hash-4",
      blockers: [],
      includedProjectObjectIds: [12]
    }
    state.evaluate.mutate.mockImplementation(
      (_input: unknown, options?: { onSuccess?: (result: typeof evaluation) => void }) =>
        options?.onSuccess?.(evaluation)
    )
    render(
      <DeliveryWorkflowPanel
        project={project("CREATING")}
        objects={[deliverableSet]}
        {...denied}
        canEvaluate
        canFreeze
      />
    )

    fireEvent.click(screen.getByRole("button", { name: /评估证据/ }))
    expect(state.evaluate.mutate).toHaveBeenCalledOnce()
    fireEvent.click(screen.getByRole("button", { name: /冻结 Manifest/ }))
    expect(state.freeze.mutate).toHaveBeenCalledOnce()
  })

  it("submit 与 decision 使用不同 capability 且 handler 同步门控", () => {
    const { rerender } = render(
      <DeliveryWorkflowPanel
        project={project("CREATING")}
        objects={[deliverableSet]}
        {...denied}
        canSubmitReview
      />
    )
    fireEvent.click(screen.getByRole("button", { name: /提交 Review/ }))
    expect(state.submit.mutate).toHaveBeenCalledOnce()

    state.reviews = [{
      reviewObjectId: 51,
      reviewVersion: 1,
      subjectObjectId: 12,
      subjectObjectVersionId: 31,
      evidenceHash: "hash-4",
      status: "PENDING"
    } as AigcReview]
    rerender(
      <DeliveryWorkflowPanel
        project={project("REVIEWING")}
        objects={[deliverableSet]}
        {...denied}
        canDecideReview
      />
    )
    fireEvent.click(screen.getByRole("button", { name: /通过/ }))
    expect(state.approve.mutate).toHaveBeenCalledOnce()
  })

  it("collect capability 仅在交付状态开放收录 handler", () => {
    state.reviews = [{
      reviewObjectId: 51,
      reviewVersion: 1,
      subjectObjectId: 12,
      subjectObjectVersionId: 31,
      evidenceHash: "hash-4",
      status: "APPROVED"
    } as AigcReview]
    render(
      <DeliveryWorkflowPanel
        project={project("DELIVERING")}
        objects={[deliverableSet]}
        {...denied}
        canCollect
      />
    )
    fireEvent.click(screen.getByRole("button", { name: /收录为 Work/ }))
    expect(state.collect.mutate).toHaveBeenCalledOnce()
  })
})
