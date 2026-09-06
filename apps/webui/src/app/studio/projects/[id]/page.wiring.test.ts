/**
 * Content Studio 项目主路径静态接线测试。
 * @author AaronZZH & Kiro
 */

import { readFileSync } from "node:fs"
import { describe, expect, it } from "vitest"

const pageSource = readFileSync(new URL("./page.tsx", import.meta.url), "utf8")
const graphSource = readFileSync(
  new URL("../../../../features/studio/content/graph/ProjectGraphView.tsx", import.meta.url),
  "utf8"
)
const structureSource = readFileSync(
  new URL("../../../../features/studio/content/ProjectStructureView.tsx", import.meta.url),
  "utf8"
)
const detailSource = readFileSync(
  new URL("../../../../features/studio/content/ObjectDetailPanel.tsx", import.meta.url),
  "utf8"
)
const generationDialogSource = readFileSync(
  new URL("../../../../features/studio/content/ProjectObjectGenerationDialog.tsx", import.meta.url),
  "utf8"
)
const imageControllerSource = readFileSync(
  new URL("../../../../features/studio/media-generation/hooks/use-image-generation-controller.ts", import.meta.url),
  "utf8"
)
const videoControllerSource = readFileSync(
  new URL("../../../../features/studio/media-generation/hooks/use-video-generation-controller.ts", import.meta.url),
  "utf8"
)
const actionBarSource = readFileSync(
  new URL("../../../../features/studio/content/ContentActionBar.tsx", import.meta.url),
  "utf8"
)
const lifecycleSource = readFileSync(
  new URL("../../../../features/studio/content/ProjectLifecycleActions.tsx", import.meta.url),
  "utf8"
)
const timelineSource = readFileSync(
  new URL("../../../../features/studio/content/TimelinePanel.tsx", import.meta.url),
  "utf8"
)
const worksSource = readFileSync(
  new URL("../../../../features/studio/content/WorksLibrary.tsx", import.meta.url),
  "utf8"
)

function occurrences(source: string, token: string): number {
  return source.split(token).length - 1
}

describe("项目工作台主路径接线", () => {
  it("图谱与结构视图共享页面级唯一生成 Dialog", () => {
    expect(occurrences(pageSource, "<ProjectObjectGenerationDialog")).toBe(1)
    expect(occurrences(pageSource, "onGenerateObject={handleGenerateObject}")).toBe(2)
    expect(graphSource).not.toContain("ProjectObjectGenerationDialog")
    expect(structureSource).not.toContain("ProjectObjectGenerationDialog")
  })

  it("创建到生命周期主路径不依赖 Chatter", () => {
    expect(pageSource).toContain("ProjectGraphView")
    expect(pageSource).toContain("ProjectObjectGenerationDialog")
    expect(detailSource).toContain("CandidateComparisonDialog")
    expect(pageSource).toContain("DeliveryWorkflowPanel")
    expect(pageSource).toContain("ProjectLifecycleActions")
    expect(`${pageSource}\n${detailSource}`.toLowerCase()).not.toContain("chatter")
  })

  it("项目生成固定走 Action，控制器中没有独立 Task fallback", () => {
    expect(generationDialogSource).toContain("项目模式不会提交独立创作任务")
    expect(generationDialogSource).toContain("projectTargets={projectTargets}")
    for (const source of [imageControllerSource, videoControllerSource]) {
      expect(source).toContain("if (projectTarget)")
      expect(source).toContain("await runAction.mutateAsync")
      expect(source).toContain("return")
      expect(source.indexOf("await runAction.mutateAsync")).toBeLessThan(
        source.indexOf("await generate")
      )
    }
  })

  it("自定义 capability 与 CRUD 权限按后端 authority 分离且入口/handler 对称", () => {
    expect(pageSource).toContain(
      "hasEntityCapability(projectAccess, AIGC_AUTHORITY_CODES.PROJECT_ACTION)"
    )
    expect(pageSource).toContain(
      "hasEntityCapability(projectAccess, AIGC_AUTHORITY_CODES.OBJECT_VERSION_ADOPT)"
    )
    expect(pageSource).toContain(
      "hasEntityCapability(projectAccess, AIGC_AUTHORITY_CODES.PROJECT_REVIEW)"
    )
    expect(pageSource).toContain(
      "hasEntityCapability(projectAccess, AIGC_AUTHORITY_CODES.PROJECT_LIFECYCLE)"
    )
    expect(pageSource).toContain(
      "hasEntityCapability(workAccess, AIGC_AUTHORITY_CODES.WORK_COLLECT)"
    )
    expect(pageSource).toContain("AIGC_AUTHORITY_CODES.TIMELINE_READ")
    expect(pageSource).toContain("AIGC_AUTHORITY_CODES.TIMELINE_CREATE")
    expect(pageSource).toContain("AIGC_AUTHORITY_CODES.TIMELINE_UPDATE")
    expect(pageSource).toContain("AIGC_AUTHORITY_CODES.TIMELINE_DELETE")
    expect(pageSource).toContain("canTimelineRead={canTimelineRead}")
    expect(pageSource).toContain("canSubmitReview={canReview}")
    expect(pageSource).toContain("canDecideReview={canReview}")
    expect(pageSource).toContain("canLifecycleUpdate={canLifecycle}")
    expect(pageSource).toContain("canCollect={canCollect}")

    expect(actionBarSource).toContain("if (!writable || !selectedAction) return")
    expect(detailSource).toContain("if (!canAdoptVersion")
    expect(lifecycleSource).toContain("if (!canLifecycleUpdate")
    expect(timelineSource).toContain("if (!createEnabled) return")
    expect(timelineSource).toContain("if (!updateEnabled")
    expect(timelineSource).toContain("if (!deleteEnabled")
    expect(worksSource).toContain(
      "hasEntityCapability(workAccess, AIGC_AUTHORITY_CODES.WORK_PUBLISH)"
    )
    expect(worksSource).toContain(
      "hasEntityCapability(workAccess, AIGC_AUTHORITY_CODES.WORK_ARCHIVE)"
    )
    expect(occurrences(worksSource, "expectedProjectVersion:")).toBe(4)
    expect(worksSource).toContain("if (!canPublish || !action || projectVersion === null) return")
  })
})
