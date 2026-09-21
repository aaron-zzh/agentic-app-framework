package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcBlueprintActionSpec;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcBlueprintProcessPolicy;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcDeliverableSetSpec;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcProjectInitializationSpec;

/** Assistant Tool 可提交的逻辑 Blueprint；不包含数据库身份、服务端版本、关系规范语义或哈希。 */
public record AssistantBlueprintProposalInput(
        ProjectSpecification projectSpecification,
        AigcProjectInitializationSpec projectInitialization,
        List<ObjectSpec> objectSpecs,
        List<RelationSpec> relationSpecs,
        List<AigcBlueprintActionSpec> actions,
        List<AigcDeliverableSetSpec> deliverableSets,
        AigcBlueprintProcessPolicy processPolicy) {

    public AssistantBlueprintProposalInput {
        objectSpecs = objectSpecs == null ? List.of() : List.copyOf(objectSpecs);
        relationSpecs = relationSpecs == null ? List.of() : List.copyOf(relationSpecs);
        actions = actions == null ? List.of() : List.copyOf(actions);
        deliverableSets = deliverableSets == null ? List.of() : List.copyOf(deliverableSets);
    }

    /** 项目逻辑规格；渠道使用稳定 code，所有数据库 ID 与发布版本由服务端解析。 */
    public record ProjectSpecification(
            String projectTypeCode,
            String productionMode,
            List<String> channelCodes,
            String description,
            String background,
            String targetAudience,
            List<String> requirements,
            List<String> constraints,
            List<String> keyFacts,
            List<String> keyMessages,
            String styleGuidance,
            List<String> successCriteria,
            Map<String, Object> deliveryRequirements,
            String locale,
            String additionalContext) {

        public ProjectSpecification {
            channelCodes = channelCodes == null ? List.of() : List.copyOf(channelCodes);
            requirements = requirements == null ? List.of() : List.copyOf(requirements);
            constraints = constraints == null ? List.of() : List.copyOf(constraints);
            keyFacts = keyFacts == null ? List.of() : List.copyOf(keyFacts);
            keyMessages = keyMessages == null ? List.of() : List.copyOf(keyMessages);
            successCriteria = successCriteria == null ? List.of() : List.copyOf(successCriteria);
            deliveryRequirements =
                    deliveryRequirements == null ? Map.of() : Map.copyOf(deliveryRequirements);
        }

        @JsonAnySetter
        public void rejectUnknownField(String field, Object ignored) {
            throw new IllegalArgumentException("projectSpecification 不接受字段: " + field);
        }
    }

    /** 具体对象逻辑规格；稳定键是提案内局部身份，不是数据库 ID。 */
    public record ObjectSpec(
            String stableKey,
            String objectType,
            String displayName,
            String parentKey,
            Integer orderNo,
            String contractRole,
            String semanticRole,
            String outputContract,
            String adoptionPolicy,
            String defaultActionKey,
            String userInstructionJson,
            String initialPayloadJson,
            String payloadSchemaJson) {}

    /** 关系只声明端点与 relationType，canonical layer/direction 等语义由服务端目录计算。 */
    public record RelationSpec(
            String sourceKey, String targetKey, String relationType, String metadataJson) {}
}
