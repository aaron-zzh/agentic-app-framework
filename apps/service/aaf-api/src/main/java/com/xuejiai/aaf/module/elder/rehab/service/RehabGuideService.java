package com.xuejiai.aaf.module.elder.rehab.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.elder.rehab.ErrorCodeConstants.REHAB_GUIDE_DOCUMENT_NOT_FOUND;
import static com.xuejiai.aaf.module.elder.rehab.ErrorCodeConstants.REHAB_GUIDE_INVALID;
import static com.xuejiai.aaf.module.elder.rehab.ErrorCodeConstants.REHAB_GUIDE_VERSION_CONFLICT;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.elder.RehabGuideCategoryEnum;
import com.xuejiai.aaf.common.enums.elder.RehabGuideStatusEnum;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.crud.definition.Patch;
import com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO;
import com.xuejiai.aaf.module.elder.rehab.RehabGuideResource;
import com.xuejiai.aaf.module.elder.rehab.domain.RehabGuide;
import com.xuejiai.aaf.module.elder.rehab.mapper.RehabGuideConvert;
import com.xuejiai.aaf.module.elder.rehab.repository.RehabGuideRepository;
import com.xuejiai.aaf.module.elder.rehab.vo.RehabGuideActionDTO;
import com.xuejiai.aaf.module.elder.rehab.vo.RehabGuideCreateDTO;
import com.xuejiai.aaf.module.elder.rehab.vo.RehabGuidePageDTO;
import com.xuejiai.aaf.module.elder.rehab.vo.RehabGuideUpdateDTO;
import com.xuejiai.aaf.module.elder.rehab.vo.RehabGuideVO;
import com.xuejiai.aaf.module.knowledge.api.KnowledgeSearchApi;

import lombok.RequiredArgsConstructor;

/** 康复指导 CRUD 服务。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RehabGuideService
        extends BaseCrudService<
                RehabGuide,
                RehabGuideVO,
                RehabGuideCreateDTO,
                RehabGuideUpdateDTO,
                RehabGuidePageDTO> {

    private final RehabGuideRepository repository;
    private final KnowledgeSearchApi knowledgeSearchApi;

    @Override
    protected RehabGuideRepository getRepository() {
        return repository;
    }

    @Override
    protected RehabGuideVO toVO(RehabGuide entity) {
        var document =
                knowledgeSearchApi
                        .findDocument(entity.getKnowledgeDocumentId())
                        .map(
                                ref ->
                                        new ResourceRefDTO(
                                                "knowledge.knowledge-document",
                                                ref.id(),
                                                ref.title(),
                                                null))
                        .orElse(null);
        return new RehabGuideVO(
                entity.getId(),
                entity.getVersion(),
                entity.getTitle(),
                entity.getCategory(),
                entity.getSummary(),
                entity.getApplicableAudience(),
                entity.getKnowledgeDocumentId(),
                entity.getStatus(),
                entity.getCreateTime(),
                entity.getUpdateTime(),
                document,
                null,
                null);
    }

    @Override
    protected RehabGuide toEntity(RehabGuideCreateDTO request) {
        var entity = RehabGuideConvert.INSTANCE.toEntity(request);
        entity.setStatus(RehabGuideStatusEnum.DRAFT.getCode());
        validate(entity);
        return entity;
    }

    @Override
    protected void updateEntity(RehabGuide entity, RehabGuideUpdateDTO request) {
        requireExpectedVersion(entity, request.expectedVersion());
        applyRequired(request.title(), entity::setTitle);
        applyRequired(request.category(), entity::setCategory);
        applyRequired(request.summary(), entity::setSummary);
        applyRequired(request.applicableAudience(), entity::setApplicableAudience);
        applyRequired(request.knowledgeDocumentId(), entity::setKnowledgeDocumentId);
        validate(entity);
        bumpVersion(entity);
    }

    @Override
    protected Specification<RehabGuide> buildSpec(RehabGuidePageDTO request) {
        return SpecificationBuilder.<RehabGuide>builder()
                .likeIfPresent("title", request.getTitle())
                .eqIfPresent("category", request.getCategory())
                .eqIfPresent("status", request.getStatus())
                .eqIfPresent("knowledgeDocumentId", request.getKnowledgeDocumentId())
                .build();
    }

    @Override
    protected List<String> optionSearchFields() {
        return List.of("title");
    }

    /** 发布后该指导关联的文档才进入康复检索范围。 */
    @Transactional
    public RehabGuideVO publish(RehabGuideActionDTO command) {
        return changeStatus(
                command,
                RehabGuideResource.COMMAND_PUBLISH,
                RehabGuideStatusEnum.PUBLISHED.getCode());
    }

    /** 下线回到草稿状态，关联文档立即退出康复检索范围。 */
    @Transactional
    public RehabGuideVO offline(RehabGuideActionDTO command) {
        return changeStatus(
                command, RehabGuideResource.COMMAND_OFFLINE, RehabGuideStatusEnum.DRAFT.getCode());
    }

    private RehabGuideVO changeStatus(
            RehabGuideActionDTO command, String commandType, String targetStatus) {
        var plan =
                new CustomUpdatePlan<RehabGuide, RehabGuideActionDTO, Void, RehabGuideVO>(
                        commandType,
                        Set.of("status"),
                        (guide, ignored) -> {
                            if (RehabGuideStatusEnum.PUBLISHED.getCode().equals(targetStatus)) {
                                requireDocument(guide.getKnowledgeDocumentId());
                            }
                        },
                        (guide, ignored) -> {
                            if (!Objects.equals(guide.getStatus(), targetStatus)) {
                                guide.setStatus(targetStatus);
                                bumpVersion(guide);
                            }
                        },
                        (guide, ignored) -> null,
                        true,
                        (guide, ignored, result) -> {},
                        (guide, ignored, result) -> toVO(guide));
        return executeCustomUpdateCommand(command.id(), command, plan);
    }

    private void validate(RehabGuide entity) {
        requireText(entity.getTitle(), 200);
        requireText(entity.getSummary(), 1000);
        requireText(entity.getApplicableAudience(), 1000);
        if (entity.getCategory() == null
                || Arrays.stream(RehabGuideCategoryEnum.ARRAYS)
                        .noneMatch(entity.getCategory()::equals)) {
            throw exception(REHAB_GUIDE_INVALID);
        }
        requireDocument(entity.getKnowledgeDocumentId());
    }

    private void requireText(String value, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw exception(REHAB_GUIDE_INVALID);
        }
    }

    private void requireDocument(Long documentId) {
        if (documentId == null
                || documentId <= 0
                || knowledgeSearchApi.findDocument(documentId).isEmpty()) {
            throw exception(REHAB_GUIDE_DOCUMENT_NOT_FOUND);
        }
    }

    private void requireExpectedVersion(RehabGuide entity, Integer expectedVersion) {
        if (expectedVersion == null || expectedVersion != currentVersion(entity)) {
            throw exception(REHAB_GUIDE_VERSION_CONFLICT);
        }
    }

    private void bumpVersion(RehabGuide entity) {
        entity.setVersion(currentVersion(entity) + 1);
    }

    private int currentVersion(RehabGuide entity) {
        return entity.getVersion() == null ? 0 : entity.getVersion();
    }

    private <T> void applyRequired(Patch<T> patch, Consumer<T> setter) {
        if (patch.isAbsent()) {
            return;
        }
        if (patch.isNullValue()) {
            throw exception(REHAB_GUIDE_INVALID);
        }
        setter.accept(patch.valueOrNull());
    }
}
