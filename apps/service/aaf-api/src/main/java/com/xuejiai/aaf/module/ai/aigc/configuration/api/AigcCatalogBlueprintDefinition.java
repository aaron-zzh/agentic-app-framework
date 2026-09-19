package com.xuejiai.aaf.module.ai.aigc.configuration.api;

import java.util.List;

/**
 * CATALOG Blueprint 发布边界的类型化聚合。
 *
 * <p>{@link com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcProjectBlueprint} 的五组 JSONB（
 * slotTemplateSpec/relationSpec/actionSpec/deliverableSpec/processPolicy）加 projectInitialization
 * 仅作为存储载体；发布校验与运行解析统一通过本聚合读取，不再各自散读宽松 Map。
 */
public record AigcCatalogBlueprintDefinition(
        String projectTypeCode,
        String blueprintVersion,
        String productionMode,
        AigcProjectInitializationSpec projectInitialization,
        List<AigcBlueprintSlotTemplateSpec> slotTemplates,
        List<AigcBlueprintRelationSpecDraft> relations,
        List<AigcBlueprintActionSpec> actions,
        List<AigcDeliverableSetSpec> deliverableSets,
        AigcBlueprintProcessPolicy processPolicy) {

    public AigcCatalogBlueprintDefinition {
        slotTemplates = slotTemplates == null ? List.of() : List.copyOf(slotTemplates);
        relations = relations == null ? List.of() : List.copyOf(relations);
        actions = actions == null ? List.of() : List.copyOf(actions);
        deliverableSets = deliverableSets == null ? List.of() : List.copyOf(deliverableSets);
    }

    /** 蓝图 relationSpec 中按 templateKey 表达的关系草稿，未展开为具体对象实例。 */
    public record AigcBlueprintRelationSpecDraft(
            String sourceTemplateKey, String targetTemplateKey, String relationType) {}
}
