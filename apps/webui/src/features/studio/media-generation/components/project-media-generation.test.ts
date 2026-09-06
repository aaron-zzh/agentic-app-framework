/**
 * 项目媒体生成边界逻辑单元测试。
 * @author AaronZZH & Kiro
 */

import { describe, expect, it } from "vitest"
import { buildProjectMediaTargets } from "@/features/studio/content/ProjectObjectGenerationDialog"
import { filterEligibleProjectMediaRefs } from "@/features/studio/media-generation/components/ProjectReferenceImagePicker"
import type {
  AigcActionOption,
  AigcProjectMediaRef,
  AigcProjectObject
} from "@/lib/api/rest/ai/aigc"

const imageObject: AigcProjectObject = {
  id: 12,
  version: 1,
  projectId: 7,
  objectType: "image_deliverable",
  stableKey: "hero-image",
  sortOrder: 1,
  status: "adopted",
  source: "blueprint",
  adoptedVersionId: 31
}

function action(actionKey: string, applicableObjectTypes: string[]): AigcActionOption {
  return {
    actionKey,
    label: actionKey,
    targetType: "TOOL",
    confirmationRequired: false,
    applicableObjectTypes
  }
}

describe("buildProjectMediaTargets", () => {
  it("仅映射服务端允许且适用于当前对象的图像和视频动作", () => {
    const targets = buildProjectMediaTargets(
      [
        action("copy.generate", ["copy_deliverable"]),
        action("image.edit", ["image_deliverable"]),
        action("image.generate", ["image_deliverable"]),
        action("video.generate", ["video_deliverable"])
      ],
      7,
      imageObject
    )

    expect(targets).toEqual({
      image: { projectId: 7, objectId: 12, actionKey: "image.generate" }
    })
  })
})

describe("filterEligibleProjectMediaRefs", () => {
  it("仅保留已采用且指向对象当前采用版本的媒体引用", () => {
    const refs: AigcProjectMediaRef[] = [
      {
        id: 1,
        objectId: 12,
        objectVersionId: 31,
        mediaVersionId: 101,
        role: "result",
        sortOrder: 0,
        adoptionStatus: "adopted"
      },
      {
        id: 2,
        objectId: 12,
        objectVersionId: 30,
        mediaVersionId: 102,
        role: "result",
        sortOrder: 1,
        adoptionStatus: "adopted"
      },
      {
        id: 3,
        objectId: 12,
        objectVersionId: 31,
        mediaVersionId: 103,
        role: "result",
        sortOrder: 2,
        adoptionStatus: "candidate"
      }
    ]

    expect(filterEligibleProjectMediaRefs(refs, [imageObject])).toEqual([refs[0]])
  })
})
