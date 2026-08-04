package com.xuejiai.aaf.module.ai.aigc.media.service;

import java.util.ArrayList;
import java.util.Objects;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.media.api.GeneratedMediaCommand;
import com.xuejiai.aaf.module.ai.aigc.media.api.MediaApi;
import com.xuejiai.aaf.module.ai.aigc.media.domain.Asset;
import com.xuejiai.aaf.module.ai.aigc.media.domain.Media;
import com.xuejiai.aaf.module.ai.aigc.media.domain.MediaVersion;
import com.xuejiai.aaf.module.ai.aigc.media.enums.MediaSourceType;
import com.xuejiai.aaf.module.ai.aigc.media.repository.AssetRepository;
import com.xuejiai.aaf.module.ai.aigc.media.repository.MediaRepository;
import com.xuejiai.aaf.module.ai.aigc.media.repository.MediaVersionRepository;
import com.xuejiai.aaf.module.ai.aigc.media.vo.MediaPageDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.MediaUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.MediaVO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.MediaVersionVO;
import com.xuejiai.aaf.module.system.file.api.FileRecordApi;
import com.xuejiai.aaf.module.system.file.api.FileReference;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

/** AIGC 媒体管理与生成结果持久化服务。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MediaService
        extends BaseCrudService<Media, MediaVO, Void, MediaUpdateDTO, MediaPageDTO>
        implements MediaApi {

    private static final String MEDIA_VERSION_REF_TYPE = "AIGC_MEDIA_VERSION";

    private final MediaRepository mediaRepository;
    private final MediaVersionRepository mediaVersionRepository;
    private final AssetRepository assetRepository;
    private final FileRecordApi fileRecordApi;
    private final OperatorContext operatorContext;

    @Override
    protected MediaRepository getRepository() {
        return mediaRepository;
    }

    @Override
    protected MediaVO toVO(Media media) {
        return toMediaVO(media, requireCurrentVersion(media));
    }

    @Override
    protected Media toEntity(Void ignored) {
        throw new UnsupportedOperationException("Media 不支持标准创建，请使用 createFromGeneratedFile");
    }

    @Override
    protected void updateEntity(Media media, MediaUpdateDTO request) {
        media.setName(request.name());
    }

    @Override
    protected void beforeDelete(Media media) {
        if (assetRepository.findByMediaId(media.getId()).isPresent()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "媒体已保存为资产，请先删除对应资产");
        }
    }

    @Override
    protected Specification<Media> buildSpec(MediaPageDTO request) {
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

    /** 按版本 ID 查询任务输出媒体。 */
    @Override
    public MediaVO getByVersionId(Long mediaVersionId, Long userId) {
        var version =
                mediaVersionRepository
                        .findOwnedVersion(mediaVersionId, userId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND,
                                                "媒体版本不存在或无权访问"));
        return toMediaVO(requireOwnedMedia(version.getMediaId(), userId), version);
    }

    /**
     * 将已由 FileUploadService 持久化的生成结果创建为 Media + MediaVersion。
     *
     * <p>调用成功后文件引用由 MediaVersion 持有；任一步失败时文件进入待清理队列。
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MediaVO createFromGeneratedFile(GeneratedMediaCommand command) {
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
            var media = new Media();
            media.setName(
                    command.name() == null || command.name().isBlank()
                            ? "AI 生成素材"
                            : command.name());
            media.setMediaType(command.mediaType());
            media.setSourceType(MediaSourceType.GENERATION);
            media.setSourceExecutionRunId(command.sourceExecutionRunId());
            media.setSourceTaskId(command.sourceTaskId());
            media.setOriginalProjectId(command.originalProjectId());
            media.setUserId(command.userId());
            media.setOwnerId(command.userId());
            media = mediaRepository.saveAndFlush(media);

            var version = new MediaVersion();
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
            version = mediaVersionRepository.saveAndFlush(version);

            fileRecordApi.retain(
                    version.getFileId(),
                    new FileReference(
                            MEDIA_VERSION_REF_TYPE, version.getId(), "FILE", "PRIMARY"));
            if (version.getThumbnailFileId() != null) {
                fileRecordApi.retain(
                        version.getThumbnailFileId(),
                        new FileReference(
                                MEDIA_VERSION_REF_TYPE,
                                version.getId(),
                                "THUMBNAIL",
                                "PREVIEW"));
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

    Media requireOwnedMedia(Long mediaId, Long userId) {
        return mediaRepository
                .findByIdAndUserId(mediaId, userId)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        GlobalErrorCode.NOT_FOUND,
                                        "媒体不存在或无权访问"));
    }

    private MediaVersion requireCurrentVersion(Media media) {
        if (media.getCurrentVersionId() == null) {
            throw new BusinessException(GlobalErrorCode.INTERNAL_SERVER_ERROR, "媒体缺少当前版本");
        }
        return mediaVersionRepository
                .findByIdAndMediaId(media.getCurrentVersionId(), media.getId())
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        GlobalErrorCode.INTERNAL_SERVER_ERROR,
                                        "媒体当前版本不存在"));
    }

    private MediaVO toMediaVO(Media media, MediaVersion version) {
        var assetId = assetRepository.findByMediaId(media.getId()).map(Asset::getId).orElse(null);
        return new MediaVO(
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

    private MediaVersionVO toVersionVO(MediaVersion version) {
        return new MediaVersionVO(
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
