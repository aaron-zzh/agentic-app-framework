package com.xuejiai.aaf.module.ai.aigc.media.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcGeneratedMediaCommand;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaApi;
import com.xuejiai.aaf.module.ai.aigc.media.domain.AigcAsset;
import com.xuejiai.aaf.module.ai.aigc.media.domain.AigcMedia;
import com.xuejiai.aaf.module.ai.aigc.media.domain.AigcMediaVersion;
import com.xuejiai.aaf.module.ai.aigc.media.enums.AigcMediaSourceType;
import com.xuejiai.aaf.module.ai.aigc.media.repository.AigcAssetRepository;
import com.xuejiai.aaf.module.ai.aigc.media.repository.AigcMediaRepository;
import com.xuejiai.aaf.module.ai.aigc.media.repository.AigcMediaVersionRepository;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcMediaPageDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcMediaUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcMediaVO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcMediaVersionVO;
import com.xuejiai.aaf.module.system.file.api.FileRecordApi;
import com.xuejiai.aaf.module.system.file.api.FileReference;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

/** AIGC 媒体管理与生成结果持久化服务。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AigcMediaService
        extends BaseCrudService<AigcMedia, AigcMediaVO, Void, AigcMediaUpdateDTO, AigcMediaPageDTO>
        implements AigcMediaApi {

    private static final String MEDIA_VERSION_REF_TYPE = "AIGC_MEDIA_VERSION";
    private static final List<AigcMediaSourceType> PROJECT_GENERATED_SOURCE_TYPES =
            List.of(
                    AigcMediaSourceType.GENERATION,
                    AigcMediaSourceType.DERIVED,
                    AigcMediaSourceType.EXPORT);

    private final AigcMediaRepository mediaRepository;
    private final AigcMediaVersionRepository mediaVersionRepository;
    private final AigcAssetRepository assetRepository;
    private final FileRecordApi fileRecordApi;
    private final OperatorContext operatorContext;
    private final EntityManager entityManager;

    @Override
    protected AigcMediaRepository getRepository() {
        return mediaRepository;
    }

    @Override
    protected AigcMediaVO toVO(AigcMedia media) {
        return toMediaVO(media, requireCurrentVersion(media));
    }

    @Override
    protected AigcMedia toEntity(Void ignored) {
        throw new UnsupportedOperationException("媒体不支持标准创建，请使用生成结果持久化入口");
    }

    @Override
    protected void updateEntity(AigcMedia media, AigcMediaUpdateDTO request) {
        media.setName(request.name());
    }

    @Override
    protected void beforeDelete(AigcMedia media) {
        if (assetRepository.findByMediaId(media.getId()).isPresent()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "媒体已保存为资产，请先删除对应资产");
        }
    }

    @Override
    protected Specification<AigcMedia> buildSpec(AigcMediaPageDTO request) {
        return (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            if (request.getMediaType() != null) {
                predicates.add(builder.equal(root.get("mediaType"), request.getMediaType()));
            }
            if (request.getSourceType() != null) {
                predicates.add(builder.equal(root.get("sourceType"), request.getSourceType()));
            }
            if (request.getProjectId() != null) {
                predicates.add(
                        builder.equal(root.get("originalProjectId"), request.getProjectId()));
            }
            if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
                predicates.add(
                        builder.like(
                                builder.lower(root.get("name")),
                                "%" + request.getKeyword().toLowerCase() + "%"));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    public AigcMediaVO getByVersionId(Long mediaVersionId) {
        var userId =
                operatorContext
                        .currentOwnerId()
                        .orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED));
        return getByVersionId(mediaVersionId, userId);
    }

    @Override
    public AigcMediaVO getByVersionId(Long mediaVersionId, Long userId) {
        lockMediaVersionShared(mediaVersionId);
        var version =
                mediaVersionRepository
                        .findOwnedVersion(mediaVersionId, userId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "媒体版本不存在或无权访问"));
        return toMediaVO(requireOwnedMedia(version.getMediaId(), userId), version);
    }

    @Override
    public void lockMediaForReference(Long mediaId, Long userId) {
        var versionIds =
                mediaVersionRepository.findByMediaIdOrderByIdAsc(mediaId).stream()
                        .map(AigcMediaVersion::getId)
                        .toList();
        versionIds.forEach(this::lockMediaVersionShared);
        requireOwnedMedia(mediaId, userId);
    }

    @Override
    @Transactional
    public void deleteExclusiveGeneratedByProject(Long projectId) {
        entityManager.flush();
        var candidates =
                mediaRepository.findByOriginalProjectIdAndSourceTypeInOrderByIdAsc(
                        projectId, PROJECT_GENERATED_SOURCE_TYPES);
        candidates.forEach(
                media ->
                        mediaVersionRepository.findByMediaIdOrderByIdAsc(media.getId()).stream()
                                .map(AigcMediaVersion::getId)
                                .forEach(this::lockMediaVersionExclusive));
        lockMediaReferenceTables();
        for (var media : candidates) {
            var versions = mediaVersionRepository.findByMediaIdOrderByIdAsc(media.getId());
            var shared =
                    versions.stream()
                            .anyMatch(
                                    version ->
                                            hasActiveExternalReference(
                                                    media.getId(), version.getId(), projectId));
            if (shared) {
                continue;
            }
            versions.forEach(this::releaseFiles);
            mediaVersionRepository.deleteAll(versions);
            mediaRepository.delete(media);
        }
    }

    @Override
    @Transactional
    public AigcMediaVO createFromUploadedFile(
            com.xuejiai.aaf.module.ai.aigc.media.api.AigcUploadedMediaCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        Objects.requireNonNull(command.userId(), "userId 不能为空");
        Objects.requireNonNull(command.mediaType(), "mediaType 不能为空");
        Objects.requireNonNull(command.fileId(), "fileId 不能为空");
        var file = fileRecordApi.requireCurrentOwner(command.fileId());
        requireGeneratedFileOwner(command.userId(), file.uploaderId(), "上传文件");
        if (command.mediaType() != com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaType.IMAGE
                || file.mimeType() == null
                || !file.mimeType().startsWith("image/")) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "项目封面必须是图片文件");
        }

        var media = new AigcMedia();
        media.setName(
                command.name() == null || command.name().isBlank()
                        ? file.originalName()
                        : command.name());
        media.setMediaType(command.mediaType());
        media.setSourceType(AigcMediaSourceType.UPLOAD);
        media.setOriginalProjectId(command.originalProjectId());
        media.setUserId(command.userId());
        media.setOwnerId(command.userId());
        media.setOrgId(com.xuejiai.aaf.framework.org.OrgContext.getCurrentOrgId());
        media.setWorkspaceId(com.xuejiai.aaf.framework.org.OrgContext.getCurrentWorkspaceId());
        media = mediaRepository.saveAndFlush(media);

        var version = new AigcMediaVersion();
        version.setMediaId(media.getId());
        version.setVersionNo(1);
        version.setFileId(file.fileId());
        version.setMimeType(file.mimeType());
        version.setSize(file.size());
        version.setChecksum(file.contentHash());
        version.setOwnerId(command.userId());
        version.setOrgId(media.getOrgId());
        version.setWorkspaceId(media.getWorkspaceId());
        version = mediaVersionRepository.saveAndFlush(version);
        fileRecordApi.retain(
                version.getFileId(),
                new FileReference(MEDIA_VERSION_REF_TYPE, version.getId(), "FILE", "PRIMARY"));

        media.setCurrentVersionId(version.getId());
        mediaRepository.save(media);
        return toVO(media);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AigcMediaVO createFromGeneratedFile(AigcGeneratedMediaCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        Objects.requireNonNull(command.userId(), "userId 不能为空");
        Objects.requireNonNull(command.mediaType(), "mediaType 不能为空");
        Objects.requireNonNull(command.file(), "file 不能为空");
        requireGeneratedFileOwner(command.userId(), command.file().uploaderId(), "主文件");
        if (command.thumbnailFile() != null) {
            requireGeneratedFileOwner(
                    command.userId(), command.thumbnailFile().uploaderId(), "缩略图");
        }

        try {
            var media = new AigcMedia();
            media.setName(
                    command.name() == null || command.name().isBlank()
                            ? "AI 生成素材"
                            : command.name());
            media.setMediaType(command.mediaType());
            media.setSourceType(AigcMediaSourceType.GENERATION);
            media.setSourceExecutionRunId(command.sourceExecutionRunId());
            media.setSourceTaskId(command.sourceTaskId());
            media.setOriginalProjectId(command.originalProjectId());
            media.setUserId(command.userId());
            media.setOwnerId(command.userId());
            media = mediaRepository.saveAndFlush(media);

            var version = new AigcMediaVersion();
            version.setMediaId(media.getId());
            version.setVersionNo(1);
            version.setFileId(command.file().fileId());
            version.setThumbnailFileId(
                    command.thumbnailFile() != null ? command.thumbnailFile().fileId() : null);
            version.setMimeType(command.file().mimeType());
            version.setSize(command.file().size());
            version.setWidth(command.width());
            version.setHeight(command.height());
            version.setDuration(command.duration());
            version.setFrameRate(command.frameRate());
            version.setGenerationInfo(command.generationInfo());
            version.setChecksum(command.file().contentHash());
            version.setOwnerId(command.userId());
            version.setOrgId(media.getOrgId());
            version.setWorkspaceId(media.getWorkspaceId());
            version = mediaVersionRepository.saveAndFlush(version);

            fileRecordApi.retain(
                    version.getFileId(),
                    new FileReference(MEDIA_VERSION_REF_TYPE, version.getId(), "FILE", "PRIMARY"));
            if (version.getThumbnailFileId() != null) {
                fileRecordApi.retain(
                        version.getThumbnailFileId(),
                        new FileReference(
                                MEDIA_VERSION_REF_TYPE, version.getId(), "THUMBNAIL", "PREVIEW"));
            }

            media.setCurrentVersionId(version.getId());
            mediaRepository.save(media);
            return toVO(media);
        } catch (RuntimeException failure) {
            requestCleanup(command.file().fileId(), failure);
            if (command.thumbnailFile() != null) {
                requestCleanup(command.thumbnailFile().fileId(), failure);
            }
            throw failure;
        }
    }

    AigcMedia requireOwnedMedia(Long mediaId, Long userId) {
        return mediaRepository
                .findByIdAndUserId(mediaId, userId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "媒体不存在或无权访问"));
    }

    private AigcMediaVersion requireCurrentVersion(AigcMedia media) {
        if (media.getCurrentVersionId() == null) {
            throw new BusinessException(GlobalErrorCode.INTERNAL_SERVER_ERROR, "媒体缺少当前版本");
        }
        return mediaVersionRepository
                .findByIdAndMediaId(media.getCurrentVersionId(), media.getId())
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        GlobalErrorCode.INTERNAL_SERVER_ERROR, "媒体当前版本不存在"));
    }

    private AigcMediaVO toMediaVO(AigcMedia media, AigcMediaVersion version) {
        var assetId =
                assetRepository.findByMediaId(media.getId()).map(AigcAsset::getId).orElse(null);
        return new AigcMediaVO(
                media.getId(),
                media.getName(),
                media.getMediaType(),
                media.getSourceType(),
                media.getSourceExecutionRunId(),
                media.getSourceTaskId(),
                media.getOriginalProjectId(),
                assetId,
                toVersionVO(version),
                media.getCreateTime(),
                media.getUpdateTime());
    }

    private AigcMediaVersionVO toVersionVO(AigcMediaVersion version) {
        return new AigcMediaVersionVO(
                version.getId(),
                version.getVersionNo(),
                version.getFileId(),
                fileRecordApi.getAccessibleUrl(version.getFileId()),
                version.getThumbnailFileId(),
                version.getThumbnailFileId() != null
                        ? fileRecordApi.getAccessibleUrl(version.getThumbnailFileId())
                        : null,
                version.getMimeType(),
                version.getSize(),
                version.getWidth(),
                version.getHeight(),
                version.getDuration(),
                version.getFrameRate(),
                version.getGenerationInfo(),
                version.getChecksum(),
                version.getCreateTime());
    }

    private void lockMediaVersionShared(Long mediaVersionId) {
        entityManager
                .createNativeQuery("SELECT pg_advisory_xact_lock_shared(:lockKey)")
                .setParameter("lockKey", mediaVersionLockKey(mediaVersionId))
                .getSingleResult();
    }

    private void lockMediaVersionExclusive(Long mediaVersionId) {
        entityManager
                .createNativeQuery("SELECT pg_advisory_xact_lock(:lockKey)")
                .setParameter("lockKey", mediaVersionLockKey(mediaVersionId))
                .getSingleResult();
    }

    private long mediaVersionLockKey(Long mediaVersionId) {
        return 0x4D45444900000000L ^ mediaVersionId;
    }

    private void lockMediaReferenceTables() {
        entityManager
                .createNativeQuery(
                        """
                        LOCK TABLE
                            ai_cloned_voice,
                            ai_digital_avatar,
                            aigc_asset,
                            aigc_asset_relation,
                            aigc_brand_profile_media_ref,
                            aigc_execution_run,
                            aigc_media,
                            aigc_media_version,
                            aigc_project,
                            aigc_project_media_ref,
                            aigc_snippet,
                            aigc_storyboard_export,
                            aigc_task,
                            aigc_timeline_clip,
                            aigc_work,
                            generation_history,
                            user_workflow_template,
                            video_template
                        IN SHARE ROW EXCLUSIVE MODE
                        """)
                .executeUpdate();
    }

    private boolean hasActiveExternalReference(
            Long mediaId, Long mediaVersionId, Long deletingProjectId) {
        var query =
                entityManager.createNativeQuery(
                        """
                        SELECT 1
                        WHERE EXISTS (
                            SELECT 1 FROM aigc_asset a
                            WHERE a.media_id = :mediaId AND a.deleted = false
                        ) OR EXISTS (
                            SELECT 1 FROM aigc_asset_relation r
                            WHERE (r.source_media_id = :mediaId OR r.target_media_id = :mediaId)
                              AND r.deleted = false
                        ) OR EXISTS (
                            SELECT 1
                            FROM aigc_execution_run r,
                                 jsonb_array_elements_text(
                                     COALESCE(r.attachment_refs, '[]'::jsonb)
                                 ) AS refs(media_version_id)
                            WHERE refs.media_version_id = CAST(:mediaVersionId AS text)
                              AND r.deleted = false
                        ) OR EXISTS (
                            SELECT 1 FROM aigc_project_media_ref r
                            WHERE r.media_version_id = :mediaVersionId AND r.deleted = false
                        ) OR EXISTS (
                            SELECT 1 FROM aigc_project p
                            WHERE p.cover_media_version_id = :mediaVersionId
                              AND p.id <> :deletingProjectId AND p.deleted = false
                        ) OR EXISTS (
                            SELECT 1 FROM aigc_brand_profile_media_ref r
                            WHERE r.media_version_id = :mediaVersionId AND r.deleted = false
                        ) OR EXISTS (
                            SELECT 1 FROM aigc_work w
                            WHERE w.cover_media_version_id = :mediaVersionId AND w.deleted = false
                        ) OR EXISTS (
                            SELECT 1 FROM aigc_timeline_clip c
                            WHERE c.media_version_id = :mediaVersionId AND c.deleted = false
                        ) OR EXISTS (
                            SELECT 1 FROM aigc_storyboard_export e
                            WHERE e.export_media_version_id = :mediaVersionId AND e.deleted = false
                        ) OR EXISTS (
                            SELECT 1 FROM aigc_task t
                            WHERE t.output_media_version_id = :mediaVersionId AND t.deleted = false
                        ) OR EXISTS (
                            SELECT 1 FROM generation_history h
                            WHERE h.media_version_id = :mediaVersionId AND h.deleted = false
                        ) OR EXISTS (
                            SELECT 1 FROM video_template t
                            WHERE (t.preview_media_version_id = :mediaVersionId
                                OR t.thumbnail_media_version_id = :mediaVersionId)
                              AND t.deleted = false
                        ) OR EXISTS (
                            SELECT 1 FROM ai_cloned_voice v
                            WHERE (v.source_media_version_id = :mediaVersionId
                                OR v.sample_audio_media_version_id = :mediaVersionId)
                              AND v.deleted = false
                        ) OR EXISTS (
                            SELECT 1 FROM ai_digital_avatar a
                            WHERE (a.image_media_version_id = :mediaVersionId
                                OR a.source_media_version_id = :mediaVersionId)
                              AND a.deleted = false
                        ) OR EXISTS (
                            SELECT 1 FROM user_workflow_template t
                            WHERE t.cover_media_version_id = :mediaVersionId AND t.deleted = false
                        ) OR EXISTS (
                            SELECT 1
                            FROM aigc_snippet s,
                                 jsonb_array_elements_text(s.reference_media_version_ids)
                                     AS refs(media_version_id)
                            WHERE refs.media_version_id = CAST(:mediaVersionId AS text)
                              AND s.deleted = false
                        )
                        """);
        query.setParameter("mediaId", mediaId);
        query.setParameter("mediaVersionId", mediaVersionId);
        query.setParameter("deletingProjectId", deletingProjectId);
        return !query.getResultList().isEmpty();
    }

    private void releaseFiles(AigcMediaVersion version) {
        fileRecordApi.release(
                version.getFileId(),
                new FileReference(MEDIA_VERSION_REF_TYPE, version.getId(), "FILE", "PRIMARY"));
        if (version.getThumbnailFileId() != null) {
            fileRecordApi.release(
                    version.getThumbnailFileId(),
                    new FileReference(
                            MEDIA_VERSION_REF_TYPE, version.getId(), "THUMBNAIL", "PREVIEW"));
        }
    }

    private void requireGeneratedFileOwner(Long commandUserId, Long uploaderId, String label) {
        var currentUserId =
                operatorContext
                        .currentOwnerId()
                        .orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED));
        if (!Objects.equals(commandUserId, currentUserId)
                || !Objects.equals(commandUserId, uploaderId)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, label + "不属于当前用户");
        }
    }

    private void requestCleanup(Long fileId, RuntimeException originalFailure) {
        try {
            fileRecordApi.requestDelete(fileId);
        } catch (RuntimeException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
        }
    }
}
