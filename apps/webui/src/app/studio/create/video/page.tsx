/** 视频生成路由。 */

import { MediaGenerationWorkspace } from "@/features/studio/media-generation/MediaGenerationWorkspace"

export default function StudioCreateVideoPage() {
  return <MediaGenerationWorkspace initialMode="video" />
}