/**
 * Timeline optimistic-lock API tests.
 * @author AaronZZH & Kiro
 */

import { beforeEach, describe, expect, it, vi } from "vitest"

const { post, put } = vi.hoisted(() => ({ post: vi.fn(), put: vi.fn() }))
vi.mock("../../backend-client", () => ({ backendApi: { get: vi.fn(), post, put } }))

import { ApiError } from "@/lib/api/errors"
import { aigcTimelineApi, invalidateAigcTimelineMutation } from "./timeline"

describe("aigcTimelineApi optimistic lock", () => {
  beforeEach(() => {
    post.mockReset()
    put.mockReset()
  })

  it("create 传 expectedProjectVersion", async () => {
    await aigcTimelineApi.create({ projectId: 7, expectedProjectVersion: 3, title: "Timeline" })
    expect(post).toHaveBeenCalledWith(
      "/aigc/timelines/_create",
      expect.objectContaining({ projectId: 7, expectedProjectVersion: 3 })
    )
  })

  it("replace 传 project 与 composition 两级 version", async () => {
    await aigcTimelineApi.replace({
      timelineId: 9,
      projectId: 7,
      expectedProjectVersion: 3,
      expectedVersion: 5,
      tracks: []
    })
    expect(put).toHaveBeenCalledWith(
      "/aigc/timelines/9/composition",
      expect.objectContaining({ projectId: 7, expectedProjectVersion: 3, expectedVersion: 5 })
    )
  })

  it("delete 传 project 与 composition 两级 version", async () => {
    await aigcTimelineApi.delete({
      timelineId: 9,
      projectId: 7,
      expectedProjectVersion: 3,
      expectedVersion: 5
    })
    expect(post).toHaveBeenCalledWith(
      "/aigc/timelines/9/_delete",
      expect.objectContaining({ projectId: 7, expectedProjectVersion: 3, expectedVersion: 5 })
    )
  })

  it("409 同时失效 timeline 与 project 权威查询", () => {
    const queryClient = { invalidateQueries: vi.fn() }
    invalidateAigcTimelineMutation(queryClient as never, 7, new ApiError(409, "conflict"))
    const keys = queryClient.invalidateQueries.mock.calls.map(
      (call) => (call[0] as { queryKey: readonly unknown[] }).queryKey
    )
    expect(keys).toContainEqual(["aigc.timeline"])
    expect(keys).toContainEqual(["aigc.project", "detail", 7])
  })
})
