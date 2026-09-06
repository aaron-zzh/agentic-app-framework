/**
 * Work 与 Publication 双层乐观锁请求测试。
 * @author AaronZZH & Kiro
 */

import { beforeEach, describe, expect, it, vi } from "vitest"

const { post } = vi.hoisted(() => ({ post: vi.fn() }))
vi.mock("../../backend-client", () => ({ backendApi: { get: vi.fn(), post } }))

import { aigcWorkApi } from "./work"

describe("aigcWorkApi project optimistic lock", () => {
  beforeEach(() => post.mockReset())

  it("publish 传 project 与 work version", async () => {
    await aigcWorkApi.publish({
      workId: 11,
      expectedProjectVersion: 3,
      expectedWorkVersion: 5,
      channelSpecVersionId: 17,
      idempotencyKey: "publish-key"
    })
    expect(post).toHaveBeenCalledWith("/aigc/works/11/publications", {
      expectedProjectVersion: 3,
      expectedWorkVersion: 5,
      channelSpecVersionId: 17,
      scheduledAt: undefined,
      idempotencyKey: "publish-key"
    })
  })

  it("cancel 传 project、work 与 publication version", async () => {
    await aigcWorkApi.cancelPublication({
      workId: 11,
      publicationId: 19,
      expectedProjectVersion: 3,
      expectedWorkVersion: 5,
      expectedPublicationVersion: 7,
      reason: "cancel",
      idempotencyKey: "cancel-key"
    })
    expect(post).toHaveBeenCalledWith("/aigc/works/11/publications/19/_cancel", {
      expectedProjectVersion: 3,
      expectedWorkVersion: 5,
      expectedPublicationVersion: 7,
      reason: "cancel",
      idempotencyKey: "cancel-key"
    })
  })

  it("retry 传 project、work 与 publication version", async () => {
    await aigcWorkApi.retryPublication({
      workId: 11,
      publicationId: 19,
      expectedProjectVersion: 3,
      expectedWorkVersion: 5,
      expectedPublicationVersion: 7,
      idempotencyKey: "retry-key"
    })
    expect(post).toHaveBeenCalledWith("/aigc/works/11/publications/19/_retry", {
      expectedProjectVersion: 3,
      expectedWorkVersion: 5,
      expectedPublicationVersion: 7,
      scheduledAt: undefined,
      idempotencyKey: "retry-key"
    })
  })

  it("archive 传 project 与 work version", async () => {
    await aigcWorkApi.archive({
      workId: 11,
      expectedProjectVersion: 3,
      expectedWorkVersion: 5,
      reason: "archive",
      idempotencyKey: "archive-key"
    })
    expect(post).toHaveBeenCalledWith("/aigc/works/11/_archive", {
      expectedProjectVersion: 3,
      expectedWorkVersion: 5,
      idempotencyKey: "archive-key",
      reason: "archive"
    })
  })
})
