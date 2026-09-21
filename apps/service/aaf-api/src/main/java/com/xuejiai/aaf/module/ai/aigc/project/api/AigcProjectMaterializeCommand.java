package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.util.List;
import java.util.Map;

import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcSlotCountOverride;

/** 项目物化命令判别联合；两种来源在服务端解析后进入同一快照与项目持久化循环。 */
public sealed interface AigcProjectMaterializeCommand
        permits AigcProjectMaterializeCommand.Catalog,
                AigcProjectMaterializeCommand.AssistantProposal {

    AigcBlueprintSourceType sourceType();

    /** 已发布目录 Blueprint 的确定性物化输入；sourceRef 与执行绑定由服务端计算。 */
    record Catalog(
            Long workspaceId,
            String name,
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
            String additionalContext,
            String projectTypeCode,
            Long blueprintVersionId,
            List<Long> brandProfileVersionIds,
            List<Long> channelSpecVersionIds,
            List<AigcProjectDocumentInput> documents,
            String productionMode,
            List<AigcSlotCountOverride> slotOverrides,
            AigcProjectCoverMode coverMode,
            Long coverFileId,
            String coverPrompt,
            String coverIdempotencyKey,
            CreationProposalRef approvedCreationProposal)
            implements AigcProjectMaterializeCommand {

        public Catalog {
            requirements = requirements == null ? List.of() : List.copyOf(requirements);
            constraints = constraints == null ? List.of() : List.copyOf(constraints);
            keyFacts = keyFacts == null ? List.of() : List.copyOf(keyFacts);
            keyMessages = keyMessages == null ? List.of() : List.copyOf(keyMessages);
            successCriteria = successCriteria == null ? List.of() : List.copyOf(successCriteria);
            deliveryRequirements =
                    deliveryRequirements == null ? Map.of() : Map.copyOf(deliveryRequirements);
            brandProfileVersionIds =
                    brandProfileVersionIds == null
                            ? List.of()
                            : List.copyOf(brandProfileVersionIds);
            channelSpecVersionIds =
                    channelSpecVersionIds == null ? List.of() : List.copyOf(channelSpecVersionIds);
            documents = documents == null ? List.of() : List.copyOf(documents);
            slotOverrides = slotOverrides == null ? List.of() : List.copyOf(slotOverrides);
        }

        @Override
        public AigcBlueprintSourceType sourceType() {
            return AigcBlueprintSourceType.CATALOG;
        }
    }

    /** Assistant 最终物化凭据；拒绝 Snapshot JSON、sourceRef、binding 或其他配置字段。 */
    record AssistantProposal(String proposalId, Integer proposalVersion, String proposalHash)
            implements AigcProjectMaterializeCommand {

        @Override
        public AigcBlueprintSourceType sourceType() {
            return AigcBlueprintSourceType.ASSISTANT_GENERATED;
        }
    }

    /** CATALOG 初始化字段生成 Proposal 的确认凭据；与 Assistant Blueprint Proposal 无关。 */
    record CreationProposalRef(
            String draftId, String proposalId, Integer proposalVersion, String proposalHash) {}
}
