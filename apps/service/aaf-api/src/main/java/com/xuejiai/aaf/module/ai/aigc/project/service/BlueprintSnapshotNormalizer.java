package com.xuejiai.aaf.module.ai.aigc.project.service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcBlueprintActionSpec;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcBlueprintRelationSpec;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcExecutionBindingVersionRef;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcProjectInitializationSpec;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcResolvedObjectSpec;
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcChannelSpec;
import com.xuejiai.aaf.module.ai.aigc.configuration.repository.AigcChannelSpecRepository;
import com.xuejiai.aaf.module.ai.aigc.configuration.repository.AigcProjectTypeRepository;
import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionBinding;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionBindingRepository;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcBlueprintSourceType;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectBlueprintSnapshotPayload;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectSpecification;
import com.xuejiai.aaf.module.ai.aigc.project.api.AssistantBlueprintCreationContext;
import com.xuejiai.aaf.module.ai.aigc.project.api.AssistantBlueprintProposalInput;

import tools.jackson.core.type.TypeReference;

/** BlueprintSnapshot 共享标准化器；所有数据库身份、发布版本、绑定和关系规范语义均在服务端补齐。 */
@Component
@Transactional(readOnly = true)
public class BlueprintSnapshotNormalizer {

    private static final String PUBLISHED = "published";

    private final AigcProjectTypeRepository projectTypes;
    private final AigcChannelSpecRepository channels;
    private final AigcExecutionBindingRepository bindings;

    public BlueprintSnapshotNormalizer(
            AigcProjectTypeRepository projectTypes,
            AigcChannelSpecRepository channels,
            AigcExecutionBindingRepository bindings) {
        this.projectTypes = Objects.requireNonNull(projectTypes, "projectTypes 不能为空");
        this.channels = Objects.requireNonNull(channels, "channels 不能为空");
        this.bindings = Objects.requireNonNull(bindings, "bindings 不能为空");
    }

    /** 规范化并冻结创建表单；任何服务端签发身份都不进入该上下文。 */
    public AssistantBlueprintCreationContext normalizeCreationContext(
            AssistantBlueprintCreationContext context) {
        Objects.requireNonNull(context, "creationContext 不能为空");
        var specification = normalizeLogical(context.projectSpecification());
        var documents = context.documents().stream().map(this::normalizeCreationDocument).toList();
        var coverMode = Objects.requireNonNull(context.coverMode(), "coverMode 不能为空");
        var coverFileId = positiveId(context.coverFileId(), "coverFileId");
        var coverPrompt = trimmed(context.coverPrompt());
        var coverIdempotencyKey = trimmed(context.coverIdempotencyKey());
        switch (coverMode) {
            case NONE -> {
                if (coverFileId != null || coverPrompt != null || coverIdempotencyKey != null) {
                    throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "封面模式与创建上下文参数不匹配");
                }
            }
            case UPLOAD -> {
                if (coverFileId == null || coverPrompt != null || coverIdempotencyKey != null) {
                    throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "封面模式与创建上下文参数不匹配");
                }
            }
            case AI_GENERATE -> {
                if (coverFileId != null || coverIdempotencyKey == null) {
                    throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "封面模式与创建上下文参数不匹配");
                }
                if (coverIdempotencyKey.length() > 100) {
                    throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "封面幂等键长度不能超过 100");
                }
            }
        }
        return new AssistantBlueprintCreationContext(
                text(context.name(), "name"),
                specification,
                normalizedPositiveIds(context.brandProfileVersionIds(), "brandProfileVersionIds"),
                documents,
                coverMode,
                coverFileId,
                coverPrompt,
                coverIdempotencyKey);
    }

    /** 将 Assistant 逻辑提案解析为最终类型化快照；模型不能改写任何冻结的用户创建事实。 */
    public AigcProjectBlueprintSnapshotPayload normalizeAssistant(
            AssistantBlueprintProposalInput input,
            String sourceRef,
            AssistantBlueprintCreationContext creationContext) {
        Objects.requireNonNull(input, "Assistant Blueprint Proposal 不能为空");
        var frozen = normalizeCreationContext(creationContext);
        var logical = normalizeLogical(input.projectSpecification());
        if (!logical.equals(frozen.projectSpecification())) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "Tool ProjectSpecification 与冻结创建上下文不一致");
        }
        var projectTypeCode = frozen.projectSpecification().projectTypeCode();
        var productionMode = frozen.projectSpecification().productionMode();
        var projectType =
                projectTypes
                        .findFirstByCodeAndStatusAndDeletedFalseOrderByIdDesc(
                                projectTypeCode, PUBLISHED)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "已发布项目类型不存在"));
        var channelCodes = frozen.projectSpecification().channelCodes();
        if (channelCodes.isEmpty()) {
            channelCodes =
                    normalizedOrderedTexts(projectType.getDefaultChannels(), "defaultChannels");
        }
        var channelVersions = requireChannels(channelCodes);
        var actions = normalizeActions(input.actions());
        var effectiveChannelCodes = channelCodes;
        var executionBindings =
                actions.stream()
                        .map(
                                action ->
                                        new AigcExecutionBindingVersionRef(
                                                action.actionKey(),
                                                requireBinding(
                                                                action.actionKey(),
                                                                projectTypeCode,
                                                                productionMode,
                                                                effectiveChannelCodes)
                                                        .getId()))
                        .toList();
        var frozenSpecification = frozen.projectSpecification();
        var specification =
                new AigcProjectSpecification(
                        projectTypeCode,
                        projectType.getDefinitionVersion(),
                        productionMode,
                        channelVersions,
                        executionBindings,
                        frozenSpecification.description(),
                        frozenSpecification.background(),
                        frozenSpecification.targetAudience(),
                        frozenSpecification.requirements(),
                        frozenSpecification.constraints(),
                        frozenSpecification.keyFacts(),
                        frozenSpecification.keyMessages(),
                        frozenSpecification.styleGuidance(),
                        frozenSpecification.successCriteria(),
                        frozenSpecification.deliveryRequirements(),
                        frozenSpecification.locale(),
                        frozenSpecification.additionalContext(),
                        List.of());
        var objects =
                input.objectSpecs().stream()
                        .map(this::normalizeAssistantObject)
                        .sorted(Comparator.comparing(AigcResolvedObjectSpec::stableKey))
                        .toList();
        var relations =
                input.relationSpecs().stream()
                        .map(
                                relation ->
                                        new AigcBlueprintRelationSpec(
                                                text(relation.sourceKey(), "relation.sourceKey"),
                                                text(relation.targetKey(), "relation.targetKey"),
                                                text(
                                                        relation.relationType(),
                                                        "relation.relationType"),
                                                normalizeJson(
                                                        relation.metadataJson(),
                                                        "relation.metadataJson")))
                        .sorted(
                                Comparator.comparing(AigcBlueprintRelationSpec::sourceKey)
                                        .thenComparing(AigcBlueprintRelationSpec::targetKey)
                                        .thenComparing(AigcBlueprintRelationSpec::relationType))
                        .toList();
        var payload =
                new AigcProjectBlueprintSnapshotPayload(
                        AigcBlueprintSourceType.ASSISTANT_GENERATED,
                        text(sourceRef, "sourceRef"),
                        specification,
                        normalizeInitialization(input.projectInitialization()),
                        List.of(),
                        objects,
                        relations,
                        objects.stream()
                                .map(
                                        object ->
                                                new AigcProjectBlueprintSnapshotPayload
                                                        .OutputContractSpec(
                                                        object.stableKey(),
                                                        object.outputContract()))
                                .toList(),
                        actions,
                        input.deliverableSets(),
                        input.processPolicy(),
                        2);
        return normalize(payload);
    }

    private AssistantBlueprintProposalInput.ProjectSpecification normalizeLogical(
            AssistantBlueprintProposalInput.ProjectSpecification logical) {
        Objects.requireNonNull(logical, "projectSpecification 不能为空");
        return new AssistantBlueprintProposalInput.ProjectSpecification(
                text(logical.projectTypeCode(), "projectTypeCode"),
                text(logical.productionMode(), "productionMode"),
                normalizedOrderedTexts(logical.channelCodes(), "channelCodes"),
                trimmed(logical.description()),
                trimmed(logical.background()),
                trimmed(logical.targetAudience()),
                normalizedOrderedTexts(logical.requirements(), "requirements"),
                normalizedOrderedTexts(logical.constraints(), "constraints"),
                normalizedOrderedTexts(logical.keyFacts(), "keyFacts"),
                normalizedOrderedTexts(logical.keyMessages(), "keyMessages"),
                trimmed(logical.styleGuidance()),
                normalizedOrderedTexts(logical.successCriteria(), "successCriteria"),
                logical.deliveryRequirements(),
                trimmed(logical.locale()),
                trimmed(logical.additionalContext()));
    }

    private AssistantBlueprintCreationContext.Document normalizeCreationDocument(
            AssistantBlueprintCreationContext.Document document) {
        Objects.requireNonNull(document, "creationContext.documents 不能包含 null");
        var documentId = positiveId(document.documentId(), "documents.documentId");
        var documentVersionId =
                positiveId(document.documentVersionId(), "documents.documentVersionId");
        if ((documentId == null) == (documentVersionId == null)) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "创建上下文文档必须且只能提供 documentId 或 documentVersionId");
        }
        return new AssistantBlueprintCreationContext.Document(
                text(document.fieldKey(), "documents.fieldKey"),
                documentId,
                documentVersionId,
                trimmed(document.contextNote()));
    }

    private static List<Long> normalizedPositiveIds(List<Long> values, String field) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        var result = values.stream().map(value -> positiveId(value, field)).toList();
        if (result.contains(null)) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, field + " 不能包含 null");
        }
        if (result.stream().distinct().count() != result.size()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, field + " 包含重复值");
        }
        return result;
    }

    private static Long positiveId(Long value, String field) {
        if (value == null) {
            return null;
        }
        if (value < 1) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, field + " 必须为正整数");
        }
        return value;
    }

    /** 对两种来源执行相同的排序、空值与 JSON 规范化。 */
    public AigcProjectBlueprintSnapshotPayload normalize(
            AigcProjectBlueprintSnapshotPayload payload) {
        Objects.requireNonNull(payload, "BlueprintSnapshot 不能为空");
        var specification = payload.projectSpecification();
        var normalizedSpecification =
                new AigcProjectSpecification(
                        text(specification.projectTypeCode(), "projectTypeCode"),
                        text(specification.projectTypeVersion(), "projectTypeVersion"),
                        text(specification.productionMode(), "productionMode"),
                        specification.channelSpecVersionIds().stream().distinct().toList(),
                        specification.executionBindings().stream()
                                .sorted(
                                        Comparator.comparing(
                                                AigcExecutionBindingVersionRef::actionKey))
                                .toList(),
                        trimmed(specification.description()),
                        trimmed(specification.background()),
                        trimmed(specification.targetAudience()),
                        normalizedOrderedTexts(specification.requirements(), "requirements"),
                        normalizedOrderedTexts(specification.constraints(), "constraints"),
                        normalizedOrderedTexts(specification.keyFacts(), "keyFacts"),
                        normalizedOrderedTexts(specification.keyMessages(), "keyMessages"),
                        trimmed(specification.styleGuidance()),
                        normalizedOrderedTexts(specification.successCriteria(), "successCriteria"),
                        specification.deliveryRequirements(),
                        trimmed(specification.locale()),
                        trimmed(specification.additionalContext()),
                        specification.slotOverrides());
        var objects =
                payload.objectSpecs().stream()
                        .sorted(Comparator.comparing(AigcResolvedObjectSpec::stableKey))
                        .toList();
        var relations =
                payload.relationSpecs().stream()
                        .sorted(
                                Comparator.comparing(AigcBlueprintRelationSpec::sourceKey)
                                        .thenComparing(AigcBlueprintRelationSpec::targetKey)
                                        .thenComparing(AigcBlueprintRelationSpec::relationType))
                        .toList();
        return new AigcProjectBlueprintSnapshotPayload(
                payload.sourceType(),
                text(payload.sourceRef(), "sourceRef"),
                normalizedSpecification,
                normalizeInitialization(payload.projectInitialization()),
                payload.slotTemplates(),
                objects,
                relations,
                objects.stream()
                        .map(
                                object ->
                                        new AigcProjectBlueprintSnapshotPayload.OutputContractSpec(
                                                object.stableKey(), object.outputContract()))
                        .toList(),
                normalizeActions(payload.actions()),
                payload.deliverableSets().stream()
                        .sorted(Comparator.comparing(value -> value.setTemplateKey()))
                        .toList(),
                payload.processPolicy(),
                payload.snapshotVersion());
    }

    /** 从服务端绑定版本重算本提案的最大预估成本。 */
    public BigDecimal estimatedCredits(AigcProjectBlueprintSnapshotPayload payload) {
        var bindingById =
                bindings
                        .findAllById(
                                payload.projectSpecification().executionBindings().stream()
                                        .map(
                                                AigcExecutionBindingVersionRef
                                                        ::executionBindingVersionId)
                                        .toList())
                        .stream()
                        .collect(
                                Collectors.toMap(AigcExecutionBinding::getId, Function.identity()));
        return payload.projectSpecification().executionBindings().stream()
                .map(AigcExecutionBindingVersionRef::executionBindingVersionId)
                .distinct()
                .map(bindingById::get)
                .filter(Objects::nonNull)
                .map(AigcExecutionBinding::getEstimatedCredits)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<Long> requireChannels(List<String> channelCodes) {
        if (channelCodes.isEmpty()) {
            return List.of();
        }
        var byCode =
                channels.findByCodeInAndStatusAndDeletedFalse(channelCodes, PUBLISHED).stream()
                        .collect(
                                Collectors.toMap(
                                        AigcChannelSpec::getCode,
                                        Function.identity(),
                                        (left, right) ->
                                                left.getId() > right.getId() ? left : right));
        if (byCode.size() != channelCodes.size()) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "存在未发布渠道规格");
        }
        return channelCodes.stream().map(code -> byCode.get(code).getId()).toList();
    }

    private AigcExecutionBinding requireBinding(
            String actionKey,
            String projectTypeCode,
            String productionMode,
            List<String> channelCodes) {
        return bindings.findByActionKeyAndStatusOrderByPriorityDesc(actionKey, PUBLISHED).stream()
                .filter(binding -> matches(binding.getProjectTypeCode(), projectTypeCode))
                .filter(binding -> matches(binding.getProductionMode(), productionMode))
                .filter(
                        binding ->
                                binding.getChannelCode() == null
                                        || channelCodes.contains(binding.getChannelCode()))
                .findFirst()
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        GlobalErrorCode.BAD_REQUEST,
                                        "动作缺少适用的已发布执行绑定: " + actionKey));
    }

    private AigcResolvedObjectSpec normalizeAssistantObject(
            AssistantBlueprintProposalInput.ObjectSpec object) {
        Objects.requireNonNull(object, "objectSpec 不能为空");
        var stableKey = text(object.stableKey(), "object.stableKey");
        return new AigcResolvedObjectSpec(
                stableKey,
                stableKey,
                1,
                text(object.objectType(), "object.objectType"),
                text(object.displayName(), "object.displayName"),
                trimmed(object.parentKey()),
                object.orderNo() == null ? 0 : object.orderNo(),
                text(object.contractRole(), "object.contractRole").toUpperCase(),
                text(object.semanticRole(), "object.semanticRole").toUpperCase(),
                text(object.outputContract(), "object.outputContract").toUpperCase(),
                text(object.adoptionPolicy(), "object.adoptionPolicy").toUpperCase(),
                trimmed(object.defaultActionKey()),
                normalizeJson(object.userInstructionJson(), "object.userInstructionJson"),
                normalizeJson(object.initialPayloadJson(), "object.initialPayloadJson"),
                normalizeJson(object.payloadSchemaJson(), "object.payloadSchemaJson"));
    }

    private List<AigcBlueprintActionSpec> normalizeActions(List<AigcBlueprintActionSpec> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(
                        action -> {
                            Objects.requireNonNull(action, "action 不能为空");
                            return new AigcBlueprintActionSpec(
                                    text(action.actionKey(), "action.actionKey"),
                                    text(action.targetTemplateKey(), "action.targetTemplateKey"),
                                    normalizedTexts(
                                            action.dependencyTemplateKeys(),
                                            "action.dependencyTemplateKeys"),
                                    normalizedTexts(
                                            action.requiredContextKeys(),
                                            "action.requiredContextKeys"),
                                    text(action.confirmationPolicy(), "action.confirmationPolicy")
                                            .toUpperCase(),
                                    action.isDefault(),
                                    normalizeJson(
                                            action.inputPresetJson(), "action.inputPresetJson"));
                        })
                .sorted(Comparator.comparing(AigcBlueprintActionSpec::actionKey))
                .toList();
    }

    private AigcProjectInitializationSpec normalizeInitialization(
            AigcProjectInitializationSpec value) {
        if (value == null) {
            return AigcProjectInitializationSpec.empty();
        }
        return new AigcProjectInitializationSpec(
                value.fields().stream()
                        .sorted(Comparator.comparing(AigcProjectInitializationSpec.FieldSpec::key))
                        .toList(),
                value.documents().stream()
                        .sorted(
                                Comparator.comparing(
                                        AigcProjectInitializationSpec.DocumentSpec::key))
                        .toList());
    }

    private static boolean matches(String expected, String actual) {
        return expected == null || expected.equals(actual);
    }

    private static List<String> normalizedOrderedTexts(List<String> values, String field) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        var normalized = values.stream().map(value -> text(value, field)).toList();
        if (new java.util.LinkedHashSet<>(normalized).size() != normalized.size()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, field + " 包含重复值");
        }
        return normalized;
    }

    private static List<String> normalizedTexts(List<String> values, String field) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        var normalized =
                values.stream().map(value -> text(value, field)).distinct().sorted().toList();
        if (normalized.size() != values.size()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, field + " 包含重复值");
        }
        return normalized;
    }

    private static String normalizeJson(String json, String field) {
        if (json == null || json.isBlank()) {
            return "{}";
        }
        try {
            var value = JsonUtils.parseObject(json, new TypeReference<Map<String, Object>>() {});
            return JsonUtils.toJsonString(canonicalValue(value));
        } catch (RuntimeException failure) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, field + " 必须是 JSON 对象");
        }
    }

    private static Object canonicalValue(Object value) {
        return switch (value) {
            case Map<?, ?> map -> {
                var sorted = new TreeMap<String, Object>();
                map.forEach((key, item) -> sorted.put(String.valueOf(key), canonicalValue(item)));
                yield sorted;
            }
            case List<?> list ->
                    list.stream().map(BlueprintSnapshotNormalizer::canonicalValue).toList();
            default -> value;
        };
    }

    private static String text(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, field + " 不能为空");
        }
        return value.trim();
    }

    private static String trimmed(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
