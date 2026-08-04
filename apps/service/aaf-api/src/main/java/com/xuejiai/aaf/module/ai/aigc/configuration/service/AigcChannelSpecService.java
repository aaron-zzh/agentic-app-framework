package com.xuejiai.aaf.module.ai.aigc.configuration.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.ai.aigc.configuration.AigcConfigurationErrorCode.PUBLISH_STATE_INVALID;
import static com.xuejiai.aaf.module.ai.aigc.configuration.AigcConfigurationErrorCode.VERSION_IMMUTABLE;

import java.util.Set;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcChannelSpecApi;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcChannelSpecVersionView;
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcChannelSpec;
import com.xuejiai.aaf.module.ai.aigc.configuration.enums.AigcConfigStatus;
import com.xuejiai.aaf.module.ai.aigc.configuration.mapper.AigcChannelSpecConvert;
import com.xuejiai.aaf.module.ai.aigc.configuration.repository.AigcChannelSpecRepository;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcChannelSpecCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcChannelSpecPageDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcChannelSpecUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcChannelSpecVO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcConfigurationPublishDTO;

import lombok.RequiredArgsConstructor;

/** AIGC 渠道规格 CRUD 与发布服务。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AigcChannelSpecService
        extends BaseCrudService<
                AigcChannelSpec,
                AigcChannelSpecVO,
                AigcChannelSpecCreateDTO,
                AigcChannelSpecUpdateDTO,
                AigcChannelSpecPageDTO>
        implements AigcChannelSpecApi {

    private final AigcChannelSpecRepository repository;

    @Override
    protected AigcChannelSpecRepository getRepository() {
        return repository;
    }

    @Override
    protected AigcChannelSpecVO toVO(AigcChannelSpec entity) {
        return new AigcChannelSpecVO(
                entity.getId(),
                entity.getVersion(),
                entity.getCode(),
                entity.getName(),
                entity.getSpecVersion(),
                entity.getAspectRatio(),
                entity.getWidth(),
                entity.getHeight(),
                entity.getMaxDurationSeconds(),
                entity.getCopyStructure(),
                entity.getRequiredDisclaimers(),
                entity.getExportFormat(),
                entity.getSortOrder(),
                entity.getStatus());
    }

    @Override
    protected AigcChannelSpec toEntity(AigcChannelSpecCreateDTO request) {
        var entity = AigcChannelSpecConvert.INSTANCE.toEntity(request);
        entity.setStatus(AigcConfigStatus.DRAFT.getCode());
        return entity;
    }

    @Override
    protected void beforeUpdate(AigcChannelSpec entity, AigcChannelSpecUpdateDTO request) {
        requireDraft(entity.getStatus());
    }

    @Override
    protected void beforeDelete(AigcChannelSpec entity) {
        requireDraft(entity.getStatus());
    }

    @Override
    protected void updateEntity(AigcChannelSpec entity, AigcChannelSpecUpdateDTO request) {
        entity.setVersion(
                AigcConfigurationPatchSupport.requireVersion(
                        entity.getVersion(), request.expectedVersion()));
        AigcConfigurationPatchSupport.required(request.code(), "code", entity::setCode);
        AigcConfigurationPatchSupport.required(request.name(), "name", entity::setName);
        AigcConfigurationPatchSupport.required(
                request.specVersion(), "specVersion", entity::setSpecVersion);
        AigcConfigurationPatchSupport.nullable(request.aspectRatio(), entity::setAspectRatio);
        AigcConfigurationPatchSupport.nullable(request.width(), entity::setWidth);
        AigcConfigurationPatchSupport.nullable(request.height(), entity::setHeight);
        AigcConfigurationPatchSupport.nullable(
                request.maxDurationSeconds(), entity::setMaxDurationSeconds);
        AigcConfigurationPatchSupport.nullable(request.copyStructure(), entity::setCopyStructure);
        AigcConfigurationPatchSupport.nullable(
                request.requiredDisclaimers(), entity::setRequiredDisclaimers);
        AigcConfigurationPatchSupport.nullable(request.exportFormat(), entity::setExportFormat);
        AigcConfigurationPatchSupport.required(
                request.sortOrder(), "sortOrder", entity::setSortOrder);
    }

    @Transactional
    public AigcChannelSpecVO publish(Long id, AigcConfigurationPublishDTO command) {
        return executeCustomUpdateCommand(
                id,
                command,
                new CustomUpdatePlan<>(
                        "PUBLISH",
                        Set.of("status"),
                        (entity, request) -> {
                            requireDraftForPublish(entity.getStatus());
                            AigcConfigurationPatchSupport.requireVersion(
                                    entity.getVersion(), request.expectedVersion());
                        },
                        (entity, request) -> entity.setStatus(AigcConfigStatus.PUBLISHED.getCode()),
                        (entity, request) -> null,
                        true,
                        (entity, request, ignored) -> {},
                        (entity, request, ignored) -> toVO(entity)));
    }

    @Override
    public AigcChannelSpecVersionView requirePublishedVersion(Long channelSpecVersionId) {
        var entity =
                repository
                        .findById(channelSpecVersionId)
                        .filter(
                                candidate ->
                                        AigcConfigStatus.PUBLISHED
                                                .getCode()
                                                .equals(candidate.getStatus()))
                        .orElseThrow(() -> new BusinessException(404, "已发布渠道规格版本不存在"));
        return new AigcChannelSpecVersionView(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getSpecVersion(),
                entity.getExportFormat(),
                entity.getMaxDurationSeconds());
    }

    @Override
    protected Specification<AigcChannelSpec> buildSpec(AigcChannelSpecPageDTO request) {
        return SpecificationBuilder.<AigcChannelSpec>builder()
                .eqIfPresent("status", request.getStatus())
                .build();
    }

    private void requireDraft(String status) {
        if (!AigcConfigStatus.DRAFT.getCode().equals(status)) throw exception(VERSION_IMMUTABLE);
    }

    private void requireDraftForPublish(String status) {
        if (!AigcConfigStatus.DRAFT.getCode().equals(status))
            throw exception(PUBLISH_STATE_INVALID);
    }
}
