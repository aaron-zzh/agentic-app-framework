package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.util.List;
import java.util.Objects;

import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcBlueprintActionSpec;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcBlueprintProcessPolicy;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcBlueprintRelationSpec;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcBlueprintSlotTemplateSpec;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcDeliverableSetSpec;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcProjectInitializationSpec;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcResolvedObjectSpec;

/** 项目创建时冻结的最终 BlueprintSnapshot v2 载荷。 */
public record AigcProjectBlueprintSnapshotPayload(
        AigcBlueprintSourceType sourceType,
        String sourceRef,
        AigcProjectSpecification projectSpecification,
        AigcProjectInitializationSpec projectInitialization,
        List<AigcBlueprintSlotTemplateSpec> slotTemplates,
        List<AigcResolvedObjectSpec> objectSpecs,
        List<AigcBlueprintRelationSpec> relationSpecs,
        List<OutputContractSpec> outputContracts,
        List<AigcBlueprintActionSpec> actions,
        List<AigcDeliverableSetSpec> deliverableSets,
        AigcBlueprintProcessPolicy processPolicy,
        int snapshotVersion) {

    public AigcProjectBlueprintSnapshotPayload {
        projectSpecification =
                Objects.requireNonNull(projectSpecification, "projectSpecification 不能为空");
        projectInitialization =
                projectInitialization == null
                        ? AigcProjectInitializationSpec.empty()
                        : projectInitialization;
        slotTemplates = slotTemplates == null ? List.of() : List.copyOf(slotTemplates);
        objectSpecs = objectSpecs == null ? List.of() : List.copyOf(objectSpecs);
        relationSpecs = relationSpecs == null ? List.of() : List.copyOf(relationSpecs);
        outputContracts = outputContracts == null ? List.of() : List.copyOf(outputContracts);
        actions = actions == null ? List.of() : List.copyOf(actions);
        deliverableSets = deliverableSets == null ? List.of() : List.copyOf(deliverableSets);
    }

    /** 基于当前 ProjectGraph 创建新的不可变快照载荷，不修改历史 revision。 */
    public AigcProjectBlueprintSnapshotPayload withGraph(
            List<AigcResolvedObjectSpec> resolvedObjects,
            List<AigcBlueprintRelationSpec> resolvedRelations) {
        var immutableObjects = List.copyOf(resolvedObjects);
        return new AigcProjectBlueprintSnapshotPayload(
                sourceType,
                sourceRef,
                projectSpecification,
                projectInitialization,
                slotTemplates,
                immutableObjects,
                resolvedRelations,
                immutableObjects.stream()
                        .map(
                                object ->
                                        new OutputContractSpec(
                                                object.stableKey(), object.outputContract()))
                        .toList(),
                actions,
                deliverableSets,
                processPolicy,
                snapshotVersion);
    }

    public record OutputContractSpec(String localKey, String outputContract) {}
}
