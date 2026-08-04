/**
 * /studio/assets/collections——AIGC Asset Collection 管理。
 * @author AaronZZH & Kiro
 */

import { EntityModuleRoute } from "@/sections/entity/view"

export default function StudioAssetCollectionsPage() {
  return <EntityModuleRoute kind="list" module="asset-collection" />
}
