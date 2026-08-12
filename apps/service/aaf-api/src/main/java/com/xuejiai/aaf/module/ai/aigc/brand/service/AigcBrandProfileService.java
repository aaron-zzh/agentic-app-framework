package com.xuejiai.aaf.module.ai.aigc.brand.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.ai.aigc.brand.AigcBrandErrorCode.PROFILE_HAS_VERSIONS;
import static com.xuejiai.aaf.module.ai.aigc.brand.AigcBrandErrorCode.PROFILE_NOT_FOUND;
import static com.xuejiai.aaf.module.ai.aigc.brand.AigcBrandErrorCode.VERSION_IMMUTABLE;
import static com.xuejiai.aaf.module.ai.aigc.brand.AigcBrandErrorCode.VERSION_NOT_FOUND;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.brand.api.AigcBrandApi;
import com.xuejiai.aaf.module.ai.aigc.brand.api.AigcBrandProfileVersionView;
import com.xuejiai.aaf.module.ai.aigc.brand.domain.AigcBrandProfile;
import com.xuejiai.aaf.module.ai.aigc.brand.domain.AigcBrandProfileDocumentRef;
import com.xuejiai.aaf.module.ai.aigc.brand.domain.AigcBrandProfileMediaRef;
import com.xuejiai.aaf.module.ai.aigc.brand.domain.AigcBrandProfileVersion;
import com.xuejiai.aaf.module.ai.aigc.brand.enums.AigcBrandProfileVersionStatus;
import com.xuejiai.aaf.module.ai.aigc.brand.repository.AigcBrandProfileDocumentRefRepository;
import com.xuejiai.aaf.module.ai.aigc.brand.repository.AigcBrandProfileMediaRefRepository;
import com.xuejiai.aaf.module.ai.aigc.brand.repository.AigcBrandProfileRepository;
import com.xuejiai.aaf.module.ai.aigc.brand.repository.AigcBrandProfileVersionRepository;
import com.xuejiai.aaf.module.ai.aigc.brand.vo.AigcBrandProfileCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.brand.vo.AigcBrandProfilePageDTO;
import com.xuejiai.aaf.module.ai.aigc.brand.vo.AigcBrandProfileUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.brand.vo.AigcBrandProfileVO;
import com.xuejiai.aaf.module.ai.aigc.brand.vo.AigcBrandProfileVersionCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.brand.vo.AigcBrandProfileVersionPublishDTO;
import com.xuejiai.aaf.module.ai.aigc.brand.vo.AigcBrandProfileVersionVO;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaApi;

import lombok.RequiredArgsConstructor;

/** AIGC 品牌根、不可变版本与跨模块读取服务。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AigcBrandProfileService
        extends BaseCrudService<
                AigcBrandProfile,
                AigcBrandProfileVO,
                AigcBrandProfileCreateDTO,
                AigcBrandProfileUpdateDTO,
                AigcBrandProfilePageDTO>
        implements AigcBrandApi {

    private static final String DEFAULT_REFERENCE_ROLE = "REFERENCE";

    private final AigcBrandProfileRepository profileRepository;
    private final AigcBrandProfileVersionRepository versionRepository;
    private final AigcBrandProfileMediaRefRepository mediaRefRepository;
    private final AigcBrandProfileDocumentRefRepository documentRefRepository;
    private final AigcMediaApi mediaApi;
    private final OperatorContext operatorContext;

    @Override
    protected AigcBrandProfileRepository getRepository() {
        return profileRepository;
    }

    @Override
    protected AigcBrandProfileVO toVO(AigcBrandProfile entity) {
        return new AigcBrandProfileVO(
                entity.getId(),
                entity.getVersion(),
                entity.getName(),
                entity.getKind(),
                entity.getIndustry(),
                entity.getCurrentVersionId(),
                entity.getStatus(),
                entity.getCreateTime(),
                entity.getUpdateTime());
    }

    @Override
    protected AigcBrandProfile toEntity(AigcBrandProfileCreateDTO request) {
        var userId =
                operatorContext
                        .currentOwnerId()
                        .orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED));
        var entity = new AigcBrandProfile();
        entity.setName(request.name());
        entity.setKind(request.kind());
        entity.setIndustry(request.industry());
        entity.setStatus(request.status());
        entity.setUserId(userId);
        return entity;
    }

    @Override
    protected void beforeUpdate(AigcBrandProfile entity, AigcBrandProfileUpdateDTO request) {
        if (!request.currentVersionId().isAbsent()) {
            throw new BusinessException(400, "currentVersionId 只能通过版本发布命令修改");
        }
    }

    @Override
    protected void beforeDelete(AigcBrandProfile entity) {
        if (versionRepository.existsByBrandProfileIdAndDeletedFalse(entity.getId())) {
            throw exception(PROFILE_HAS_VERSIONS);
        }
    }

    @Override
    protected void updateEntity(AigcBrandProfile entity, AigcBrandProfileUpdateDTO request) {
        entity.setVersion(
                AigcBrandPatchSupport.requireVersion(
                        entity.getVersion(), request.expectedVersion()));
        AigcBrandPatchSupport.required(request.name(), "name", entity::setName);
        AigcBrandPatchSupport.required(request.kind(), "kind", entity::setKind);
        AigcBrandPatchSupport.nullable(request.industry(), entity::setIndustry);
        AigcBrandPatchSupport.required(request.status(), "status", entity::setStatus);
    }

    @Transactional
    public AigcBrandProfileVersionVO createVersion(
            Long profileId, AigcBrandProfileVersionCreateDTO command) {
        return executeCustomUpdateCommand(
                profileId,
                command,
                new CustomUpdatePlan<>(
                        "CREATE_VERSION",
                        Set.of("currentVersionId"),
                        (profile, request) ->
                                AigcBrandPatchSupport.requireVersion(
                                        profile.getVersion(), request.expectedProfileVersion()),
                        (profile, request) -> {},
                        (profile, request) -> createDraftVersion(profile, request),
                        false,
                        (profile, request, version) -> {},
                        (profile, request, version) -> toVersionVO(version)));
    }

    @Transactional
    public AigcBrandProfileVersionVO publishVersion(
            Long profileId, Long versionId, AigcBrandProfileVersionPublishDTO command) {
        return executeCustomUpdateCommand(
                profileId,
                command,
                new CustomUpdatePlan<>(
                        "PUBLISH_VERSION",
                        Set.of("currentVersionId"),
                        (profile, request) -> {
                            AigcBrandPatchSupport.requireVersion(
                                    profile.getVersion(), request.expectedProfileVersion());
                            requireDraftVersion(profileId, versionId, request.expectedVersion());
                        },
                        (profile, request) -> {
                            profile.setCurrentVersionId(versionId);
                            profile.setVersion(profile.getVersion() + 1);
                        },
                        (profile, request) ->
                                publishDraftVersion(
                                        profileId, versionId, request.expectedVersion()),
                        true,
                        (profile, request, version) -> {},
                        (profile, request, version) -> toVersionVO(version)));
    }

    public List<AigcBrandProfileVersionVO> listVersions(Long profileId) {
        requireProfile(profileId);
        return versionRepository
                .findByBrandProfileIdAndDeletedFalseOrderByVersionNoDesc(profileId)
                .stream()
                .map(this::toVersionVO)
                .toList();
    }

    @Override
    public AigcBrandProfileVersionView requireVersion(
            Long brandProfileVersionId, Long workspaceId) {
        var version =
                versionRepository
                        .findById(brandProfileVersionId)
                        .filter(candidate -> !Boolean.TRUE.equals(candidate.getDeleted()))
                        .filter(
                                candidate ->
                                        AigcBrandProfileVersionStatus.PUBLISHED
                                                .code()
                                                .equals(candidate.getStatus()))
                        .orElseThrow(() -> exception(VERSION_NOT_FOUND));
        var profile = requireEntity(version.getBrandProfileId());
        if (profile.getWorkspaceId() != null
                && !Objects.equals(profile.getWorkspaceId(), workspaceId)) {
            throw exception(PROFILE_NOT_FOUND);
        }
        return new AigcBrandProfileVersionView(
                profile.getId(),
                version.getId(),
                version.getVersionNo(),
                profile.getKind(),
                JsonUtils.toJsonString(version.getRules()),
                mediaVersionIds(version.getId()),
                documentVersionIds(version.getId()));
    }

    @Override
    public List<AigcBrandProfileVersionView> requireVersions(
            Collection<Long> versionIds, Long workspaceId) {
        if (versionIds == null || versionIds.isEmpty()) return List.of();
        return versionIds.stream().distinct().map(id -> requireVersion(id, workspaceId)).toList();
    }

    @Override
    protected Specification<AigcBrandProfile> buildSpec(AigcBrandProfilePageDTO request) {
        return SpecificationBuilder.<AigcBrandProfile>builder()
                .eqIfPresent("kind", request.getKind())
                .eqIfPresent("status", request.getStatus())
                .build();
    }

    private AigcBrandProfileVersion createDraftVersion(
            AigcBrandProfile profile, AigcBrandProfileVersionCreateDTO command) {
        var version = new AigcBrandProfileVersion();
        version.setBrandProfileId(profile.getId());
        version.setVersionNo(versionRepository.findMaxVersionNo(profile.getId()) + 1);
        version.setPositioning(command.positioning());
        version.setAudience(command.audience());
        version.setToneOfVoice(command.toneOfVoice());
        version.setVisualStyle(command.visualStyle());
        version.setDisclaimer(command.disclaimer());
        version.setForbiddenItems(command.forbiddenItems());
        version.setRules(command.rules());
        version.setStatus(AigcBrandProfileVersionStatus.DRAFT.code());
        copyScope(profile, version);
        var saved = versionRepository.saveAndFlush(version);
        saveMediaRefs(saved.getId(), command.mediaVersionIds());
        saveDocumentRefs(saved.getId(), command.documentVersionIds());
        return saved;
    }

    private AigcBrandProfileVersion publishDraftVersion(
            Long profileId, Long versionId, Integer expectedVersion) {
        var version = requireDraftVersion(profileId, versionId, expectedVersion);
        version.setVersion(version.getVersion() + 1);
        version.setStatus(AigcBrandProfileVersionStatus.PUBLISHED.code());
        return versionRepository.save(version);
    }

    private AigcBrandProfileVersion requireDraftVersion(
            Long profileId, Long versionId, Integer expectedVersion) {
        var version =
                versionRepository
                        .findByIdAndBrandProfileIdAndDeletedFalse(versionId, profileId)
                        .orElseThrow(() -> exception(VERSION_NOT_FOUND));
        if (!AigcBrandProfileVersionStatus.DRAFT.code().equals(version.getStatus())) {
            throw exception(VERSION_IMMUTABLE);
        }
        AigcBrandPatchSupport.requireVersion(version.getVersion(), expectedVersion);
        return version;
    }

    private AigcBrandProfile requireProfile(Long profileId) {
        return requireEntity(profileId);
    }

    private void copyScope(AigcBrandProfile profile, AigcBrandProfileVersion version) {
        version.setOrgId(profile.getOrgId());
        version.setWorkspaceId(profile.getWorkspaceId());
        version.setOwnerId(profile.getOwnerId());
    }

    private void saveMediaRefs(Long versionId, List<Long> mediaVersionIds) {
        if (mediaVersionIds == null) return;
        var userId = operatorContext.currentOwnerId().orElseThrow();
        for (var index = 0; index < mediaVersionIds.size(); index++) {
            var mediaVersionId = mediaVersionIds.get(index);
            mediaApi.getByVersionId(mediaVersionId, userId);
            var ref = new AigcBrandProfileMediaRef();
            ref.setBrandProfileVersionId(versionId);
            ref.setMediaVersionId(mediaVersionId);
            ref.setRole(DEFAULT_REFERENCE_ROLE);
            ref.setSortOrder(index);
            mediaRefRepository.save(ref);
        }
    }

    private void saveDocumentRefs(Long versionId, List<Long> documentVersionIds) {
        if (documentVersionIds == null) return;
        for (var index = 0; index < documentVersionIds.size(); index++) {
            var ref = new AigcBrandProfileDocumentRef();
            ref.setBrandProfileVersionId(versionId);
            ref.setDocumentVersionId(documentVersionIds.get(index));
            ref.setRole(DEFAULT_REFERENCE_ROLE);
            ref.setSortOrder(index);
            documentRefRepository.save(ref);
        }
    }

    private AigcBrandProfileVersionVO toVersionVO(AigcBrandProfileVersion version) {
        return new AigcBrandProfileVersionVO(
                version.getId(),
                version.getVersion(),
                version.getBrandProfileId(),
                version.getVersionNo(),
                version.getPositioning(),
                version.getAudience(),
                version.getToneOfVoice(),
                version.getVisualStyle(),
                version.getDisclaimer(),
                version.getForbiddenItems(),
                version.getRules(),
                version.getStatus(),
                mediaVersionIds(version.getId()),
                documentVersionIds(version.getId()),
                version.getCreateTime());
    }

    private List<Long> mediaVersionIds(Long versionId) {
        return mediaRefRepository
                .findByBrandProfileVersionIdAndDeletedFalseOrderBySortOrder(versionId)
                .stream()
                .map(AigcBrandProfileMediaRef::getMediaVersionId)
                .toList();
    }

    private List<Long> documentVersionIds(Long versionId) {
        return documentRefRepository
                .findByBrandProfileVersionIdAndDeletedFalseOrderBySortOrder(versionId)
                .stream()
                .map(AigcBrandProfileDocumentRef::getDocumentVersionId)
                .toList();
    }
}
