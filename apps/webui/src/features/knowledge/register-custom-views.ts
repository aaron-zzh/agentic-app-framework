/**
 * 知识库实体的自定义视图注册——EntityDef.customViews 的 component 名称在此解析。
 * 由 sections/entity/view/entity-module-route.tsx 引入，保证注册先于渲染。
 * @author AaronZZH & Kiro
 */

import { registerCustomView } from "@/features/entity-engine/lib/component-registry"
import { KnowledgeOpsView } from "./components/KnowledgeOpsView"

registerCustomView("knowledge-ops", KnowledgeOpsView)
