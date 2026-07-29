package com.xuejiai.aaf.module.content.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.content.domain.ContentBrandProfile;
import com.xuejiai.aaf.module.content.mapper.ContentBrandProfileConvert;
import com.xuejiai.aaf.module.content.repository.ContentBrandProfileRepository;
import com.xuejiai.aaf.module.content.vo.ContentBrandProfileCreateDTO;
import com.xuejiai.aaf.module.content.vo.ContentBrandProfilePageDTO;
import com.xuejiai.aaf.module.content.vo.ContentBrandProfileUpdateDTO;
import com.xuejiai.aaf.module.content.vo.ContentBrandProfileVO;

import lombok.RequiredArgsConstructor;

/**
 * 品牌/IP 资料 CRUD 服务。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentBrandProfileService
        extends BaseCrudService<
                ContentBrandProfile,
                ContentBrandProfileVO,
                ContentBrandProfileCreateDTO,
                ContentBrandProfileUpdateDTO,
                ContentBrandProfilePageDTO> {

    private final ContentBrandProfileRepository repository;
    private final OperatorContext operatorContext;

    @Override
    protected ContentBrandProfileRepository getRepository() {
        return repository;
    }

    @Override
    protected ContentBrandProfileVO toVO(ContentBrandProfile entity) {
        return new ContentBrandProfileVO(
                entity.getId(),
                entity.getVersion(),
                entity.getName(),
                entity.getKind(),
                entity.getIndustry(),
                entity.getLogoUrl(),
                entity.getPositioning(),
                entity.getAudience(),
                entity.getToneOfVoice(),
                entity.getVisualStyle(),
                entity.getDisclaimer(),
                entity.getForbiddenItems(),
                entity.getRules(),
                entity.getProfileAssets(),
                entity.getProfileVersion(),
                entity.getStatus(),
                entity.getCreateTime(),
                entity.getUpdateTime());
    }

    @Override
    protected ContentBrandProfile toEntity(ContentBrandProfileCreateDTO dto) {
        var entity = ContentBrandProfileConvert.INSTANCE.toEntity(dto);
        entity.setUserId(
                operatorContext
                        .currentOwnerId()
                        .orElseThrow(() -> exception(GlobalErrorCode.UNAUTHORIZED)));
        return entity;
    }

    @Override
    protected void updateEntity(ContentBrandProfile entity, ContentBrandProfileUpdateDTO dto) {
        entity.setVersion(
                ContentPatchSupport.requireVersion(entity.getVersion(), dto.expectedVersion()));
        ContentPatchSupport.required(dto.name(), "name", entity::setName);
        ContentPatchSupport.required(dto.kind(), "kind", entity::setKind);
        ContentPatchSupport.nullable(dto.industry(), entity::setIndustry);
        ContentPatchSupport.nullable(dto.logoUrl(), entity::setLogoUrl);
        ContentPatchSupport.nullable(dto.positioning(), entity::setPositioning);
        ContentPatchSupport.nullable(dto.audience(), entity::setAudience);
        ContentPatchSupport.nullable(dto.toneOfVoice(), entity::setToneOfVoice);
        ContentPatchSupport.nullable(dto.visualStyle(), entity::setVisualStyle);
        ContentPatchSupport.nullable(dto.disclaimer(), entity::setDisclaimer);
        ContentPatchSupport.nullable(dto.forbiddenItems(), entity::setForbiddenItems);
        ContentPatchSupport.nullable(dto.rules(), entity::setRules);
        ContentPatchSupport.nullable(dto.profileAssets(), entity::setProfileAssets);
        ContentPatchSupport.nullable(dto.profileVersion(), entity::setProfileVersion);
        ContentPatchSupport.required(dto.status(), "status", entity::setStatus);
    }

    @Override
    protected Specification<ContentBrandProfile> buildSpec(ContentBrandProfilePageDTO request) {
        return SpecificationBuilder.<ContentBrandProfile>builder()
                .eqIfPresent("kind", request.getKind())
                .eqIfPresent("status", request.getStatus())
                .build();
    }
}
