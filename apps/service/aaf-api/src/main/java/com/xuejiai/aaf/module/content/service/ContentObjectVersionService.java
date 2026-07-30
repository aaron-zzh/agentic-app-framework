package com.xuejiai.aaf.module.content.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.content.ErrorCodeConstants.CONTENT_OBJECT_NOT_FOUND;
import static com.xuejiai.aaf.module.content.ErrorCodeConstants.CONTENT_OBJECT_VERSION_NOT_CANDIDATE;
import static com.xuejiai.aaf.module.content.ErrorCodeConstants.CONTENT_OBJECT_VERSION_NOT_FOUND;
import static com.xuejiai.aaf.module.content.ErrorCodeConstants.CONTENT_PROJECT_NOT_FOUND;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.content.ContentObjectStatusEnum;
import com.xuejiai.aaf.common.enums.content.ContentObjectVersionStatusEnum;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.content.domain.ContentObjectVersion;
import com.xuejiai.aaf.module.content.repository.ContentObjectVersionRepository;
import com.xuejiai.aaf.module.content.repository.ContentProjectObjectRepository;
import com.xuejiai.aaf.module.content.repository.ContentProjectRepository;
import com.xuejiai.aaf.module.content.vo.ContentObjectVersionPageDTO;
import com.xuejiai.aaf.module.content.vo.ContentObjectVersionVO;
import com.xuejiai.aaf.module.content.vo.ContentProjectObjectVO;

import lombok.RequiredArgsConstructor;

/**
 * 内容对象版本只读与采用服务。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentObjectVersionService
        extends BaseCrudService<
                ContentObjectVersion,
                ContentObjectVersionVO,
                Void,
                Void,
                ContentObjectVersionPageDTO> {

    private final ContentObjectVersionRepository repository;
    private final ContentProjectObjectRepository objectRepository;
    private final ContentProjectRepository projectRepository;
    private final ContentProjectObjectService objectService;
    private final OperatorContext operatorContext;

    @Override
    protected ContentObjectVersionRepository getRepository() {
        return repository;
    }

    @Override
    protected ContentObjectVersionVO toVO(ContentObjectVersion entity) {
        return new ContentObjectVersionVO(
                entity.getId(),
                entity.getVersion(),
                entity.getProjectId(),
                entity.getObjectId(),
                entity.getVersionNo(),
                entity.getStatus(),
                entity.getContentPayload(),
                entity.getAssetRefs(),
                entity.getExecutionRunId(),
                entity.getSummary(),
                entity.getAdoptedTime(),
                entity.getSupersededByVersionId(),
                entity.getCreateTime());
    }

    @Override
    protected ContentObjectVersion toEntity(Void dto) {
        throw new UnsupportedOperationException("对象版本只能由执行链路创建");
    }

    @Override
    protected void updateEntity(ContentObjectVersion entity, Void dto) {
        throw new UnsupportedOperationException("对象版本只能通过采用或否决端点变更");
    }

    public List<ContentObjectVersionVO> listByObject(Long objectId) {
        requireObject(objectId);
        return repository.findByObjectIdOrderByVersionNoDesc(objectId).stream()
                .map(this::toVO)
                .toList();
    }

    @Transactional
    public ContentProjectObjectVO adopt(Long objectId, Long versionId) {
        var object =
                objectRepository
                        .findLockedById(objectId)
                        .orElseThrow(() -> exception(CONTENT_OBJECT_NOT_FOUND));
        var version = requireVersion(objectId, versionId);
        if (ContentObjectVersionStatusEnum.ADOPTED.getCode().equals(version.getStatus())
                && String.valueOf(versionId).equals(object.getAdoptedVersionRef())) {
            return objectService.toView(object);
        }
        if (!ContentObjectVersionStatusEnum.CANDIDATE.getCode().equals(version.getStatus())) {
            throw exception(CONTENT_OBJECT_VERSION_NOT_CANDIDATE);
        }
        repository
                .findByObjectIdAndStatus(objectId, ContentObjectVersionStatusEnum.ADOPTED.getCode())
                .forEach(
                        adopted -> {
                            adopted.setStatus(ContentObjectVersionStatusEnum.SUPERSEDED.getCode());
                            adopted.setSupersededByVersionId(versionId);
                            repository.save(adopted);
                        });
        version.setStatus(ContentObjectVersionStatusEnum.ADOPTED.getCode());
        version.setAdoptedTime(LocalDateTime.now());
        version.setAdoptedBy(operatorContext.currentOwnerId().orElseThrow());
        repository.save(version);

        object.setAdoptedVersionRef(String.valueOf(versionId));
        object.setStatus(ContentObjectStatusEnum.ADOPTED.getCode());
        objectRepository.save(object);

        var project =
                projectRepository
                        .findLockedById(object.getProjectId())
                        .orElseThrow(() -> exception(CONTENT_PROJECT_NOT_FOUND));
        project.setGraphRevision(project.getGraphRevision() + 1);
        projectRepository.save(project);
        return objectService.toView(object);
    }

    @Transactional
    public ContentObjectVersionVO reject(Long objectId, Long versionId) {
        requireObject(objectId);
        var version = requireVersion(objectId, versionId);
        if (ContentObjectVersionStatusEnum.REJECTED.getCode().equals(version.getStatus())) {
            return toVO(version);
        }
        if (!ContentObjectVersionStatusEnum.CANDIDATE.getCode().equals(version.getStatus())) {
            throw exception(CONTENT_OBJECT_VERSION_NOT_CANDIDATE);
        }
        version.setStatus(ContentObjectVersionStatusEnum.REJECTED.getCode());
        return toVO(repository.save(version));
    }

    private void requireObject(Long objectId) {
        if (!objectRepository.existsById(objectId)) {
            throw exception(CONTENT_OBJECT_NOT_FOUND);
        }
    }

    private ContentObjectVersion requireVersion(Long objectId, Long versionId) {
        return repository
                .findById(versionId)
                .filter(version -> objectId.equals(version.getObjectId()))
                .orElseThrow(() -> exception(CONTENT_OBJECT_VERSION_NOT_FOUND));
    }

    @Override
    protected Specification<ContentObjectVersion> buildSpec(ContentObjectVersionPageDTO request) {
        return SpecificationBuilder.<ContentObjectVersion>builder()
                .eqIfPresent("projectId", request.getProjectId())
                .eqIfPresent("objectId", request.getObjectId())
                .eqIfPresent("status", request.getStatus())
                .build();
    }
}
