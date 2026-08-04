/**
 * /studio/assets/tags——AIGC Asset Tag 管理。
 * @author AaronZZH & Kiro
 */

import { EntityModuleRoute } from "@/sections/entity/view"

export default function StudioAssetTagsPage() {
  return <EntityModuleRoute kind="list" module="asset-tag" />
}
