/** 图像生成路由。 */

import { MediaGenerationWorkspace } from "@/features/studio/media-generation/MediaGenerationWorkspace"

export default function StudioCreateImagePage() {
  return <MediaGenerationWorkspace initialMode="image" />
}