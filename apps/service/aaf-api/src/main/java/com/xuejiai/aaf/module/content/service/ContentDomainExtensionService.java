package com.xuejiai.aaf.module.content.service;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.content.domain.ContentDomainExtension;
import com.xuejiai.aaf.module.content.mapper.ContentDomainExtensionConvert;
import com.xuejiai.aaf.module.content.repository.ContentDomainExtensionRepository;
import com.xuejiai.aaf.module.content.vo.ContentDomainExtensionCreateDTO;
import com.xuejiai.aaf.module.content.vo.ContentDomainExtensionPageDTO;
import com.xuejiai.aaf.module.content.vo.ContentDomainExtensionUpdateDTO;
import com.xuejiai.aaf.module.content.vo.ContentDomainExtensionVO;

import lombok.RequiredArgsConstructor;

/**
 * 行业扩展 CRUD 服务。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentDomainExtensionService
        extends BaseCrudService<
                ContentDomainExtension,
                ContentDomainExtensionVO,
                ContentDomainExtensionCreateDTO,
                ContentDomainExtensionUpdateDTO,
                ContentDomainExtensionPageDTO> {

    private final ContentDomainExtensionRepository repository;

    @Override
    protected ContentDomainExtensionRepository getRepository() {
        return repository;
    }

    @Override
    protected ContentDomainExtensionVO toVO(ContentDomainExtension entity) {
        return new ContentDomainExtensionVO(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getExtensionVersion(),
                entity.getIndustry(),
                entity.getRegion(),
                entity.getLanguage(),
                entity.getStatus());
    }

    @Override
    protected ContentDomainExtension toEntity(ContentDomainExtensionCreateDTO dto) {
        var entity = ContentDomainExtensionConvert.INSTANCE.toEntity(dto);
        return entity;
    }

    @Override
    protected void updateEntity(
            ContentDomainExtension entity, ContentDomainExtensionUpdateDTO dto) {
        entity.setVersion(
                ContentPatchSupport.requireVersion(entity.getVersion(), dto.expectedVersion()));
        ContentPatchSupport.required(dto.code(), "code", entity::setCode);
        ContentPatchSupport.required(dto.name(), "name", entity::setName);
        ContentPatchSupport.required(
                dto.extensionVersion(), "extensionVersion", entity::setExtensionVersion);
        ContentPatchSupport.nullable(dto.industry(), entity::setIndustry);
        ContentPatchSupport.nullable(dto.region(), entity::setRegion);
        ContentPatchSupport.nullable(dto.language(), entity::setLanguage);
        ContentPatchSupport.required(dto.status(), "status", entity::setStatus);
        ContentPatchSupport.nullable(dto.profileSchemaExt(), entity::setProfileSchemaExt);
        ContentPatchSupport.nullable(dto.objectDefinitions(), entity::setObjectDefinitions);
        ContentPatchSupport.nullable(dto.knowledgeRequirements(), entity::setKnowledgeRequirements);
        ContentPatchSupport.nullable(dto.ruleSets(), entity::setRuleSets);
        ContentPatchSupport.nullable(dto.validators(), entity::setValidators);
        ContentPatchSupport.nullable(dto.roleRecommendations(), entity::setRoleRecommendations);
        ContentPatchSupport.nullable(dto.actionConstraints(), entity::setActionConstraints);
        ContentPatchSupport.nullable(dto.channelOverrides(), entity::setChannelOverrides);
    }

    @Override
    protected Specification<ContentDomainExtension> buildSpec(
            ContentDomainExtensionPageDTO request) {
        return SpecificationBuilder.<ContentDomainExtension>builder()
                .eqIfPresent("status", request.getStatus())
                .build();
    }
}
