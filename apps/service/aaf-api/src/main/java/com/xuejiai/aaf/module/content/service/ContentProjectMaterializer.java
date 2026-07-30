package com.xuejiai.aaf.module.content.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.content.ErrorCodeConstants.CONTENT_BLUEPRINT_INCOMPATIBLE;
import static com.xuejiai.aaf.module.content.ErrorCodeConstants.CONTENT_BLUEPRINT_NOT_FOUND;
import static com.xuejiai.aaf.module.content.ErrorCodeConstants.CONTENT_CHANNEL_SPEC_NOT_FOUND;
import static com.xuejiai.aaf.module.content.ErrorCodeConstants.CONTENT_DOMAIN_EXTENSION_NOT_FOUND;
import static com.xuejiai.aaf.module.content.ErrorCodeConstants.CONTENT_DOMAIN_EXTENSION_NOT_PUBLISHED;
import static com.xuejiai.aaf.module.content.ErrorCodeConstants.CONTENT_HARD_RULE_VIOLATION;
import static com.xuejiai.aaf.module.content.ErrorCodeConstants.CONTENT_PROJECT_TYPE_NOT_FOUND;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.xuejiai.aaf.common.enums.content.ContentConfigStatusEnum;
import com.xuejiai.aaf.common.enums.content.ContentExecutionStatusEnum;
import com.xuejiai.aaf.common.enums.content.ContentGenerationModeEnum;
import com.xuejiai.aaf.common.enums.content.ContentObjectSourceEnum;
import com.xuejiai.aaf.common.enums.content.ContentObjectStatusEnum;
import com.xuejiai.aaf.common.enums.content.ContentObjectTypeEnum;
import com.xuejiai.aaf.common.enums.content.ContentProfileRefScopeEnum;
import com.xuejiai.aaf.common.enums.content.ContentProjectStatusEnum;
import com.xuejiai.aaf.common.enums.content.ContentProjectTypeEnum;
import com.xuejiai.aaf.common.enums.content.ContentRelationLayerEnum;
import com.xuejiai.aaf.common.enums.content.ContentRelationTypeEnum;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.content.domain.ContentChannelSpec;
import com.xuejiai.aaf.module.content.domain.ContentDomainExtension;
import com.xuejiai.aaf.module.content.domain.ContentProject;
import com.xuejiai.aaf.module.content.domain.ContentProjectBlueprint;
import com.xuejiai.aaf.module.content.domain.ContentProjectObject;
import com.xuejiai.aaf.module.content.domain.ContentProjectProfileRef;
import com.xuejiai.aaf.module.content.domain.ContentProjectRelation;
import com.xuejiai.aaf.module.content.domain.ContentProjectType;
import com.xuejiai.aaf.module.content.repository.ContentChannelSpecRepository;
import com.xuejiai.aaf.module.content.repository.ContentDomainExtensionRepository;
import com.xuejiai.aaf.module.content.repository.ContentExecutionRunRepository;
import com.xuejiai.aaf.module.content.repository.ContentProjectBlueprintRepository;
import com.xuejiai.aaf.module.content.repository.ContentProjectObjectRepository;
import com.xuejiai.aaf.module.content.repository.ContentProjectProfileRefRepository;
import com.xuejiai.aaf.module.content.repository.ContentProjectRelationRepository;
import com.xuejiai.aaf.module.content.repository.ContentProjectRepository;
import com.xuejiai.aaf.module.content.repository.ContentProjectTypeRepository;
import com.xuejiai.aaf.module.content.vo.ContentProjectGraphVO;
import com.xuejiai.aaf.module.content.vo.ContentProjectMaterializeDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectSummaryVO;
import com.xuejiai.aaf.module.content.vo.ContentProjectVO;

import lombok.RequiredArgsConstructor;

/**
 * Content Studio 项目蓝图物化器。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class ContentProjectMaterializer {

    private static final String PUBLISHED = ContentConfigStatusEnum.PUBLISHED.getCode();
    private static final Set<String> VIDEO_CHANNELS =
            Set.of("douyin", "wechat_channels", "bilibili");

    private final ContentProjectTypeRepository projectTypeRepository;
    private final ContentProjectBlueprintRepository blueprintRepository;
    private final ContentDomainExtensionRepository domainExtensionRepository;
    private final ContentChannelSpecRepository channelSpecRepository;
    private final ContentProjectRepository projectRepository;
    private final ContentProjectProfileRefRepository profileRefRepository;
    private final ContentProjectObjectRepository objectRepository;
    private final ContentProjectRelationRepository relationRepository;
    private final ContentExecutionRunRepository executionRunRepository;
    private final ContentProjectService projectService;
    private final ContentBrandProfileService brandProfileService;
    private final ContentProjectObjectService objectService;
    private final ContentProjectRelationService relationService;
    private final OperatorContext operatorContext;

    /** 按类型、蓝图、领域扩展和渠道配置创建项目图谱 revision 1。 */
    @org.springframework.security.access.prepost.PreAuthorize(
            "hasPermission(null, 'content:project:create')")
    @Transactional
    public ContentProjectVO materialize(ContentProjectMaterializeDTO dto) {
        var projectType = requireProjectType(dto.projectTypeCode());
        var productionMode =
                StringUtils.hasText(dto.productionMode())
                        ? dto.productionMode()
                        : projectType.getDefaultProductionMode();
        var blueprint = requireBlueprint(dto, productionMode);
        var domainExtension = requireDomainExtension(dto.domainExtensionCode());
        var channels = resolveChannels(dto.channels(), projectType.getDefaultChannels());
        var channelSpecs = requireChannelSpecs(channels);
        var primaryProfile = requireProfile(dto.primaryBrandProfileId());
        var auxiliaryProfiles = requireProfiles(dto.auxiliaryBrandProfileIds());
        var snapshot =
                configurationSnapshot(
                        projectType, blueprint, domainExtension, channelSpecs, productionMode);

        var ownerId =
                operatorContext
                        .currentOwnerId()
                        .orElseThrow(() -> exception(GlobalErrorCode.UNAUTHORIZED));
        var project = new ContentProject();
        project.setOrgId(com.xuejiai.aaf.framework.org.OrgContext.getCurrentOrgId());
        project.setWorkspaceId(com.xuejiai.aaf.framework.org.OrgContext.getCurrentWorkspaceId());
        project.setOwnerId(ownerId);
        project.setUserId(ownerId);
        project.setName(dto.name());
        project.setProjectTypeCode(projectType.getCode());
        project.setBlueprintCode(blueprint.getCode());
        project.setBlueprintVersion(blueprint.getBlueprintVersion());
        project.setDomainExtensionCode(domainExtension == null ? null : domainExtension.getCode());
        project.setDomainExtensionVersion(
                domainExtension == null ? null : domainExtension.getExtensionVersion());
        project.setProductionMode(productionMode);
        project.setGenerationMode(ContentGenerationModeEnum.MANUAL.getCode());
        project.setStatus(ContentProjectStatusEnum.DRAFT.getCode());
        project.setBrief(dto.brief());
        project.setChannels(channels);
        project.setConfigSnapshot(snapshot);
        project.setGraphRevision(1);
        project.setPrimaryBrandProfileId(primaryProfile == null ? null : primaryProfile.id());
        project.setCostUsed(BigDecimal.ZERO);
        project.setLastActiveTime(LocalDateTime.now());
        projectRepository.save(project);

        materializeProfileRefs(project, primaryProfile, auxiliaryProfiles);
        if (blueprint.getObjectSpec() == null) {
            materializeDefaultGraph(project, channels);
        } else {
            materializeBlueprintGraph(project, blueprint);
        }
        return projectService.toVO(project);
    }

    /** 返回结构视图与图谱视图共享的唯一对象关系源。 */
    @Transactional(readOnly = true)
    public ContentProjectGraphVO graph(Long projectId) {
        var project = requireAccessibleProject(projectId);
        var objects =
                objectRepository.findByProjectIdOrderBySortOrderAscIdAsc(projectId).stream()
                        .map(objectService::toVO)
                        .toList();
        var relations =
                relationRepository.findByProjectIdOrderByIdAsc(projectId).stream()
                        .map(relationService::toVO)
                        .toList();
        return new ContentProjectGraphVO(project.id(), project.graphRevision(), objects, relations);
    }

    /** 汇总项目对象、确认点、阻断和执行成本。 */
    @Transactional(readOnly = true)
    public ContentProjectSummaryVO summary(Long projectId) {
        var project = requireAccessibleProject(projectId);
        var objects = objectRepository.findByProjectIdOrderBySortOrderAscIdAsc(projectId);
        var deliverableCount =
                objects.stream()
                        .filter(object -> object.getObjectType().endsWith("_deliverable"))
                        .count();
        var pendingConfirmCount =
                objects.stream()
                        .filter(
                                object ->
                                        ContentObjectStatusEnum.PENDING_CONFIRM
                                                .getCode()
                                                .equals(object.getStatus()))
                        .count();
        var blockedCount =
                objects.stream()
                        .filter(
                                object ->
                                        ContentObjectStatusEnum.BLOCKED
                                                .getCode()
                                                .equals(object.getStatus()))
                        .count();
        var runningCount =
                executionRunRepository.countByProjectIdAndStatus(
                        projectId, ContentExecutionStatusEnum.RUNNING.getCode());
        return new ContentProjectSummaryVO(
                projectId,
                objects.size(),
                deliverableCount,
                pendingConfirmCount,
                blockedCount,
                runningCount,
                project.costUsed());
    }

    private ContentProjectVO requireAccessibleProject(Long projectId) {
        return projectService.getById(projectId, null, "detail");
    }

    private ContentProjectType requireProjectType(String code) {
        return projectTypeRepository
                .findFirstByCodeOrderByIdDesc(code)
                .orElseThrow(() -> exception(CONTENT_PROJECT_TYPE_NOT_FOUND, code));
    }

    private ContentProjectBlueprint requireBlueprint(
            ContentProjectMaterializeDTO dto, String productionMode) {
        var blueprint =
                StringUtils.hasText(dto.blueprintCode())
                        ? blueprintRepository
                                .findFirstByCodeAndStatusOrderByIdDesc(
                                        dto.blueprintCode(), PUBLISHED)
                                .orElseThrow(() -> exception(CONTENT_BLUEPRINT_NOT_FOUND))
                        : blueprintRepository
                                .findFirstByProjectTypeCodeAndProductionModeAndStatusOrderByIdDesc(
                                        dto.projectTypeCode(), productionMode, PUBLISHED)
                                .orElseThrow(() -> exception(CONTENT_BLUEPRINT_NOT_FOUND));
        if (!dto.projectTypeCode().equals(blueprint.getProjectTypeCode())
                || !productionMode.equals(blueprint.getProductionMode())) {
            throw exception(CONTENT_BLUEPRINT_INCOMPATIBLE, productionMode);
        }
        return blueprint;
    }

    private ContentDomainExtension requireDomainExtension(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        var extension =
                domainExtensionRepository
                        .findFirstByCodeOrderByIdDesc(code)
                        .orElseThrow(() -> exception(CONTENT_DOMAIN_EXTENSION_NOT_FOUND));
        if (!PUBLISHED.equals(extension.getStatus())) {
            throw exception(CONTENT_DOMAIN_EXTENSION_NOT_PUBLISHED);
        }
        return extension;
    }

    private List<String> resolveChannels(List<String> requested, List<String> defaults) {
        var source = requested == null || requested.isEmpty() ? defaults : requested;
        if (source == null) {
            return List.of();
        }
        return List.copyOf(new LinkedHashSet<>(source));
    }

    private List<ContentChannelSpec> requireChannelSpecs(List<String> channels) {
        if (channels.isEmpty()) {
            return List.of();
        }
        var specs = channelSpecRepository.findByCodeInAndStatus(channels, PUBLISHED);
        var foundCodes =
                specs.stream()
                        .map(ContentChannelSpec::getCode)
                        .collect(java.util.stream.Collectors.toSet());
        var missing = channels.stream().filter(code -> !foundCodes.contains(code)).findFirst();
        if (missing.isPresent()) {
            throw exception(CONTENT_CHANNEL_SPEC_NOT_FOUND, missing.get());
        }
        var order = new LinkedHashMap<String, ContentChannelSpec>();
        specs.forEach(spec -> order.put(spec.getCode(), spec));
        return channels.stream().map(order::get).toList();
    }

    private com.xuejiai.aaf.module.content.vo.ContentBrandProfileVO requireProfile(Long id) {
        return id == null ? null : brandProfileService.getById(id, null, "detail");
    }

    private List<com.xuejiai.aaf.module.content.vo.ContentBrandProfileVO> requireProfiles(
            Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream().filter(Objects::nonNull).distinct().map(this::requireProfile).toList();
    }

    private Map<String, Object> configurationSnapshot(
            ContentProjectType projectType,
            ContentProjectBlueprint blueprint,
            ContentDomainExtension domainExtension,
            List<ContentChannelSpec> channelSpecs,
            String productionMode) {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put(
                "projectType",
                Map.of("code", projectType.getCode(), "name", projectType.getName()));
        snapshot.put(
                "blueprint",
                Map.of("code", blueprint.getCode(), "version", blueprint.getBlueprintVersion()));
        snapshot.put("productionMode", productionMode);
        snapshot.put(
                "channels",
                channelSpecs.stream()
                        .map(
                                spec ->
                                        Map.of(
                                                "code",
                                                spec.getCode(),
                                                "version",
                                                spec.getSpecVersion()))
                        .toList());
        if (domainExtension != null) {
            snapshot.put(
                    "domainExtension",
                    Map.of(
                            "code",
                            domainExtension.getCode(),
                            "version",
                            domainExtension.getExtensionVersion()));
        }
        return snapshot;
    }

    private void materializeProfileRefs(
            ContentProject project,
            com.xuejiai.aaf.module.content.vo.ContentBrandProfileVO primary,
            List<com.xuejiai.aaf.module.content.vo.ContentBrandProfileVO> auxiliaries) {
        if (primary != null) {
            profileRefRepository.save(
                    profileRef(
                            project,
                            primary.id(),
                            primary.profileVersion(),
                            ContentProfileRefScopeEnum.PRIMARY.getCode()));
        }
        auxiliaries.stream()
                .filter(profile -> primary == null || !profile.id().equals(primary.id()))
                .map(
                        profile ->
                                profileRef(
                                        project,
                                        profile.id(),
                                        profile.profileVersion(),
                                        ContentProfileRefScopeEnum.AUXILIARY.getCode()))
                .forEach(profileRefRepository::save);
    }

    private ContentProjectProfileRef profileRef(
            ContentProject project, Long profileId, String profileVersion, String scope) {
        var ref = new ContentProjectProfileRef();
        copyScope(project, ref);
        ref.setProjectId(project.getId());
        ref.setBrandProfileId(profileId);
        ref.setProfileVersion(profileVersion);
        ref.setRefScope(scope);
        return ref;
    }

    private void materializeDefaultGraph(ContentProject project, List<String> channels) {
        var objects = new LinkedHashMap<String, ContentProjectObject>();
        objects.put(
                "brief",
                saveObject(
                        project,
                        "brief",
                        ContentObjectTypeEnum.BRIEF.getCode(),
                        "项目简报",
                        ContentObjectStatusEnum.PENDING_CONFIRM.getCode(),
                        10,
                        null));
        objects.put(
                "creative-concept-1",
                saveObject(
                        project,
                        "creative-concept-1",
                        ContentObjectTypeEnum.CREATIVE_CONCEPT.getCode(),
                        "创意方向 A",
                        ContentObjectStatusEnum.DRAFT.getCode(),
                        20,
                        null));
        objects.put(
                "creative-concept-2",
                saveObject(
                        project,
                        "creative-concept-2",
                        ContentObjectTypeEnum.CREATIVE_CONCEPT.getCode(),
                        "创意方向 B",
                        ContentObjectStatusEnum.DRAFT.getCode(),
                        30,
                        null));
        var deliverableSet =
                saveObject(
                        project,
                        "deliverable-set",
                        ContentObjectTypeEnum.DELIVERABLE_SET.getCode(),
                        "内容包",
                        ContentObjectStatusEnum.EMPTY.getCode(),
                        40,
                        null);
        objects.put("deliverable-set", deliverableSet);

        var sort = 50;
        for (var channel : channels) {
            var payload = Map.<String, Object>of("channel", channel);
            objects.put(
                    "image-" + channel,
                    saveObject(
                            project,
                            "image-" + channel,
                            ContentObjectTypeEnum.IMAGE_DELIVERABLE.getCode(),
                            channel + " 图片",
                            ContentObjectStatusEnum.EMPTY.getCode(),
                            sort++,
                            payload));
            objects.put(
                    "copy-" + channel,
                    saveObject(
                            project,
                            "copy-" + channel,
                            ContentObjectTypeEnum.COPY_DELIVERABLE.getCode(),
                            channel + " 文案",
                            ContentObjectStatusEnum.EMPTY.getCode(),
                            sort++,
                            payload));
        }
        if (ContentProjectTypeEnum.NARRATIVE_SERIES.getCode().equals(project.getProjectTypeCode())
                || channels.stream().anyMatch(VIDEO_CHANNELS::contains)) {
            objects.put(
                    "video-deliverable",
                    saveObject(
                            project,
                            "video-deliverable",
                            ContentObjectTypeEnum.VIDEO_DELIVERABLE.getCode(),
                            "视频交付物",
                            ContentObjectStatusEnum.EMPTY.getCode(),
                            sort++,
                            null));
        }
        objects.put(
                "review",
                saveObject(
                        project,
                        "review",
                        ContentObjectTypeEnum.REVIEW.getCode(),
                        "审核",
                        ContentObjectStatusEnum.EMPTY.getCode(),
                        sort,
                        null));

        relate(
                project,
                objects.get("brief"),
                objects.get("creative-concept-1"),
                ContentRelationTypeEnum.DERIVES.getCode(),
                null);
        relate(
                project,
                objects.get("brief"),
                objects.get("creative-concept-2"),
                ContentRelationTypeEnum.DERIVES.getCode(),
                null);
        objects.values().stream()
                .filter(object -> object.getObjectType().endsWith("_deliverable"))
                .forEach(
                        deliverable -> {
                            relate(
                                    project,
                                    objects.get("creative-concept-1"),
                                    deliverable,
                                    ContentRelationTypeEnum.DERIVES.getCode(),
                                    null);
                            relate(
                                    project,
                                    objects.get("creative-concept-2"),
                                    deliverable,
                                    ContentRelationTypeEnum.DERIVES.getCode(),
                                    null);
                            relate(
                                    project,
                                    deliverableSet,
                                    deliverable,
                                    ContentRelationTypeEnum.CONTAINS.getCode(),
                                    null);
                        });
    }

    private void materializeBlueprintGraph(
            ContentProject project, ContentProjectBlueprint blueprint) {
        var objectSpecs = mapList(blueprint.getObjectSpec().get("objects"), "objectSpec.objects");
        var objects = new LinkedHashMap<String, ContentProjectObject>();
        var parentKeys = new LinkedHashMap<String, String>();
        for (var spec : objectSpecs) {
            var key = requiredText(spec, "key");
            var object =
                    saveObject(
                            project,
                            key,
                            requiredText(spec, "type"),
                            optionalText(spec, "title"),
                            optionalText(spec, "status", ContentObjectStatusEnum.EMPTY.getCode()),
                            optionalInteger(spec, "sortOrder", objects.size()),
                            optionalMap(spec, "payload"));
            objects.put(key, object);
            var parentKey = optionalText(spec, "parentKey");
            if (parentKey != null) {
                parentKeys.put(key, parentKey);
            }
        }
        parentKeys.forEach(
                (key, parentKey) -> {
                    var parent = objects.get(parentKey);
                    if (parent == null) {
                        throw exception(CONTENT_HARD_RULE_VIOLATION, "蓝图父对象不存在: " + parentKey);
                    }
                    var object = objects.get(key);
                    object.setParentId(parent.getId());
                    objectRepository.save(object);
                });
        if (blueprint.getRelationSpec() == null) {
            return;
        }
        var relationSpecs =
                mapList(blueprint.getRelationSpec().get("relations"), "relationSpec.relations");
        for (var spec : relationSpecs) {
            var source = objects.get(requiredText(spec, "sourceKey"));
            var target = objects.get(requiredText(spec, "targetKey"));
            if (source == null || target == null) {
                throw exception(CONTENT_HARD_RULE_VIOLATION, "蓝图关系引用了不存在的对象");
            }
            relate(project, source, target, requiredText(spec, "type"), optionalMap(spec, "meta"));
        }
    }

    private ContentProjectObject saveObject(
            ContentProject project,
            String key,
            String type,
            String title,
            String status,
            int sortOrder,
            Map<String, Object> payload) {
        var object = new ContentProjectObject();
        copyScope(project, object);
        object.setProjectId(project.getId());
        object.setObjectKey(key);
        object.setBlueprintNodeKey(key);
        object.setObjectType(type);
        object.setTitle(title);
        object.setStatus(status);
        object.setSource(ContentObjectSourceEnum.BLUEPRINT.getCode());
        object.setSchemaVersion("1.0.0");
        object.setSortOrder(sortOrder);
        object.setPayload(payload);
        return objectRepository.save(object);
    }

    private void relate(
            ContentProject project,
            ContentProjectObject source,
            ContentProjectObject target,
            String type,
            Map<String, Object> meta) {
        var relation = new ContentProjectRelation();
        copyScope(project, relation);
        relation.setProjectId(project.getId());
        relation.setRelationType(type);
        relation.setLayer(ContentRelationLayerEnum.DOMAIN.getCode());
        relation.setSourceObjectId(source.getId());
        relation.setTargetObjectId(target.getId());
        relation.setRelationMeta(meta);
        relationRepository.save(relation);
    }

    private void copyScope(ContentProject project, BaseEntity target) {
        target.setOrgId(project.getOrgId());
        target.setWorkspaceId(project.getWorkspaceId());
        target.setOwnerId(
                project.getOwnerId() == null
                        ? operatorContext
                                .currentOwnerId()
                                .orElseThrow(() -> exception(GlobalErrorCode.UNAUTHORIZED))
                        : project.getOwnerId());
    }

    private List<Map<String, Object>> mapList(Object value, String field) {
        if (!(value instanceof List<?> values)) {
            throw exception(CONTENT_HARD_RULE_VIOLATION, field + " 必须是数组");
        }
        var result = new ArrayList<Map<String, Object>>();
        for (var item : values) {
            if (!(item instanceof Map<?, ?> source)) {
                throw exception(CONTENT_HARD_RULE_VIOLATION, field + " 元素必须是对象");
            }
            var mapped = new LinkedHashMap<String, Object>();
            source.forEach((key, element) -> mapped.put(String.valueOf(key), element));
            result.add(mapped);
        }
        return result;
    }

    private String requiredText(Map<String, Object> source, String field) {
        var value = optionalText(source, field);
        if (!StringUtils.hasText(value)) {
            throw exception(CONTENT_HARD_RULE_VIOLATION, field + " 不能为空");
        }
        return value;
    }

    private String optionalText(Map<String, Object> source, String field) {
        var value = source.get(field);
        return value == null ? null : String.valueOf(value);
    }

    private String optionalText(Map<String, Object> source, String field, String defaultValue) {
        var value = optionalText(source, field);
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private int optionalInteger(Map<String, Object> source, String field, int defaultValue) {
        var value = source.get(field);
        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    private Map<String, Object> optionalMap(Map<String, Object> source, String field) {
        var value = source.get(field);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Map<?, ?> values)) {
            throw exception(CONTENT_HARD_RULE_VIOLATION, field + " 必须是对象");
        }
        var result = new LinkedHashMap<String, Object>();
        values.forEach((key, element) -> result.put(String.valueOf(key), element));
        return result;
    }
}
