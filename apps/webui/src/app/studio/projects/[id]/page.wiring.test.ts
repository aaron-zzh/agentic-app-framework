/**
 * Content Studio 项目主路径静态接线测试。
 * @author AaronZZH & Kiro
 */

import { readFileSync } from "node:fs"
import { pathToFileURL } from "node:url"
import { describe, expect, it } from "vitest"

const moduleUrl = import.meta.url.startsWith("file:")
  ? import.meta.url
  : pathToFileURL(import.meta.url).href

function readSource(relativePath: string): string {
  return readFileSync(new URL(relativePath, moduleUrl), "utf8")
}

const pageSource = readSource("./page.tsx")
const graphSource = readSource("../../../../features/studio/content/graph/ProjectGraphView.tsx")
const structureSource = readSource("../../../../features/studio/content/ProjectStructureView.tsx")
const detailSource = readSource("../../../../features/studio/content/ObjectDetailPanel.tsx")
const generationDialogSource = readSource(
  "../../../../features/studio/content/ProjectObjectGenerationDialog.tsx"
)
const imageControllerSource = readSource(
  "../../../../features/studio/media-generation/hooks/use-image-generation-controller.ts"
)
const videoControllerSource = readSource(
  "../../../../features/studio/media-generation/hooks/use-video-generation-controller.ts"
)
const actionBarSource = readSource("../../../../features/studio/content/ContentActionBar.tsx")
const lifecycleSource = readSource(
  "../../../../features/studio/content/ProjectLifecycleActions.tsx"
)
const timelineSource = readSource("../../../../features/studio/content/TimelinePanel.tsx")
const worksSource = readSource("../../../../features/studio/content/WorksLibrary.tsx")

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

  it("创建到生命周期主路径不自建或回退默认 Assistant 对话", () => {
    expect(pageSource).toContain("ProjectGraphView")
    expect(pageSource).toContain("ProjectObjectGenerationDialog")
    expect(detailSource).toContain("CandidateComparisonDialog")
    expect(pageSource).toContain("DeliveryWorkflowPanel")
    expect(pageSource).toContain("ProjectLifecycleActions")
    expect(`${pageSource}\n${detailSource}`).not.toContain("<Chatter")
    expect(`${pageSource}\n${detailSource}`).not.toContain("DEFAULT_CHATTER_ASSISTANT_ID")
  })

  it("项目生成固定走 Action，控制器中没有独立 Task fallback", () => {
    expect(generationDialogSource).toContain("创建新的 ExecutionRun 和候选版本")
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

    expect(actionBarSource).toContain("if (!writable || !selectedAction || !focusedObject) return")
    expect(detailSource).toContain(
      "if (!canAdoptCandidate || !object || adoptId === null || (replacing && !reason.trim())) return"
    )
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
