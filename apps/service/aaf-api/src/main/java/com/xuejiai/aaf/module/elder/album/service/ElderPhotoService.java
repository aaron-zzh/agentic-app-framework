package com.xuejiai.aaf.module.elder.album.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.elder.album.ErrorCodeConstants.*;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.xuejiai.aaf.common.enums.elder.PhotoSourceEnum;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.crud.definition.Patch;
import com.xuejiai.aaf.module.elder.album.ElderPhotoResource;
import com.xuejiai.aaf.module.elder.album.api.AlbumQueryApi;
import com.xuejiai.aaf.module.elder.album.domain.ElderPhoto;
import com.xuejiai.aaf.module.elder.album.mapper.ElderPhotoConvert;
import com.xuejiai.aaf.module.elder.album.repository.ElderPhotoRepository;
import com.xuejiai.aaf.module.elder.album.vo.ElderPhotoCreateDTO;
import com.xuejiai.aaf.module.elder.album.vo.ElderPhotoPageDTO;
import com.xuejiai.aaf.module.elder.album.vo.ElderPhotoUpdateDTO;
import com.xuejiai.aaf.module.elder.album.vo.ElderPhotoVO;
import com.xuejiai.aaf.module.elder.profile.api.ElderQueryApi;
import com.xuejiai.aaf.module.elder.scenario.ElderDeviceScenario;
import com.xuejiai.aaf.module.system.file.api.FileRecordApi;
import com.xuejiai.aaf.module.system.file.api.FileReference;
import com.xuejiai.aaf.module.system.file.api.FileStoragePort;

/** 长者相册 CRUD、设备上传与最近照片查询服务。 */
@Service
public class ElderPhotoService
        extends BaseCrudService<
                ElderPhoto,
                ElderPhotoVO,
                ElderPhotoCreateDTO,
                ElderPhotoUpdateDTO,
                ElderPhotoPageDTO>
        implements AlbumQueryApi {

    public static final String BASE_PATH = ElderPhotoResource.BASE_PATH;

    private static final long MAX_PHOTO_SIZE = 10L * 1024 * 1024;
    private static final int MAX_RECENT_LIMIT = 50;
    private static final String FILE_REFERENCE_TYPE = "ELDER_PHOTO";
    private static final String FILE_REFERENCE_FIELD = "FILE";
    private static final String FILE_REFERENCE_PURPOSE = "MASTER";

    private final ElderPhotoRepository repository;
    private final ElderQueryApi elderQueryApi;
    private final FileStoragePort fileStoragePort;
    private final FileRecordApi fileRecordApi;
    private final TransactionTemplate transactionTemplate;

    public ElderPhotoService(
            ElderPhotoRepository repository,
            ElderQueryApi elderQueryApi,
            FileStoragePort fileStoragePort,
            FileRecordApi fileRecordApi,
            TransactionTemplate transactionTemplate) {
        this.repository = repository;
        this.elderQueryApi = elderQueryApi;
        this.fileStoragePort = fileStoragePort;
        this.fileRecordApi = fileRecordApi;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    protected ElderPhotoRepository getRepository() {
        return repository;
    }

    @Override
    protected ElderPhotoVO toVO(ElderPhoto entity) {
        var contentUrl = contentUrl(entity.getId());
        return new ElderPhotoVO(
                entity.getId(),
                entity.getVersion(),
                entity.getElderId(),
                entity.getDeviceId(),
                entity.getClientPhotoId(),
                entity.getFileId(),
                entity.getSource(),
                entity.getCapturedAt(),
                entity.getOriginalName(),
                entity.getMimeType(),
                entity.getFileSize(),
                entity.getContentHash(),
                entity.getDescription(),
                contentUrl,
                contentUrl,
                entity.getCreateTime(),
                entity.getUpdateTime(),
                null,
                null,
                null);
    }

    @Override
    protected ElderPhoto toEntity(ElderPhotoCreateDTO request) {
        elderQueryApi.requireOverview(request.elderId());
        var entity = ElderPhotoConvert.INSTANCE.toEntity(request);
        validate(entity);
        return entity;
    }

    @Override
    protected void afterCreate(ElderPhoto entity, ElderPhotoCreateDTO request) {
        retainFile(entity);
    }

    @Override
    protected void updateEntity(ElderPhoto entity, ElderPhotoUpdateDTO request) {
        requireExpectedVersion(entity, request.expectedVersion());
        applyNullable(request.description(), entity::setDescription);
        validateDescription(entity.getDescription());
        entity.setVersion(currentVersion(entity) + 1);
    }

    @Override
    protected void afterDelete(ElderPhoto entity) {
        fileRecordApi.release(entity.getFileId(), fileReference(entity));
    }

    @Override
    protected Specification<ElderPhoto> buildSpec(ElderPhotoPageDTO request) {
        return SpecificationBuilder.<ElderPhoto>builder()
                .eqIfPresent("elderId", request.getElderId())
                .eqIfPresent("deviceId", request.getDeviceId())
                .eqIfPresent("source", request.getSource())
                .build();
    }

    @Override
    protected List<String> optionSearchFields() {
        return List.of("description", "originalName");
    }

    /** 设备上传只信任认证主体中的设备、长者、所有者和租户信息。 */
    public ElderPhotoVO uploadFromDevice(
            Long deviceId,
            String scenarioKey,
            Long elderId,
            Long ownerUserId,
            Long orgId,
            Long workspaceId,
            String clientPhotoId,
            String source,
            LocalDateTime capturedAt,
            MultipartFile file) {
        requireDeviceContext(deviceId, scenarioKey, elderId, ownerUserId);
        requireClientPhotoId(clientPhotoId);
        requireSource(source);
        if (capturedAt == null) {
            throw exception(PHOTO_INVALID);
        }

        var photo = readAndValidate(file);
        var existing = repository.findByDeviceIdAndClientPhotoId(deviceId, clientPhotoId);
        if (existing.isPresent()) {
            return requireSameContent(existing.get(), photo.contentHash());
        }

        var storagePath =
                "elder/album/%d/%s.%s"
                        .formatted(elderId, UUID.randomUUID(), photo.format().extension());
        var stored =
                fileStoragePort.uploadFromBytes(
                        photo.bytes(), storagePath, photo.format().mimeType(), ownerUserId);
        try {
            var committed =
                    transactionTemplate.execute(
                            status ->
                                    persistUploadedPhoto(
                                            deviceId,
                                            elderId,
                                            ownerUserId,
                                            orgId,
                                            workspaceId,
                                            clientPhotoId,
                                            source,
                                            capturedAt,
                                            displayName(file, clientPhotoId, photo.format()),
                                            photo,
                                            stored.fileId()));
            if (committed == null) {
                throw exception(PHOTO_INVALID);
            }
            if (!committed.created()) {
                requestCleanup(stored.fileId(), null);
                return requireSameContent(committed.photo(), photo.contentHash());
            }
            return toVO(committed.photo());
        } catch (DataIntegrityViolationException conflict) {
            requestCleanup(stored.fileId(), conflict);
            var duplicate =
                    repository
                            .findByDeviceIdAndClientPhotoId(deviceId, clientPhotoId)
                            .orElseThrow(() -> conflict);
            return requireSameContent(duplicate, photo.contentHash());
        } catch (RuntimeException failure) {
            requestCleanup(stored.fileId(), failure);
            throw failure;
        }
    }

    /** 先走 BaseCrud 读取鉴权，再打开文件正文。 */
    public PhotoContent openContent(Long photoId) {
        var photo = getById(photoId, null, "detail");
        var stored = fileRecordApi.get(photo.fileId());
        return new PhotoContent(
                fileRecordApi.openByKey(stored.key()),
                photo.originalName(),
                photo.mimeType(),
                photo.fileSize());
    }

    @Override
    public List<RecentPhoto> listRecentPhotos(Long elderId, int limit) {
        if (elderId == null || elderId <= 0 || limit <= 0) {
            throw exception(PHOTO_INVALID);
        }
        var request = new ElderPhotoPageDTO();
        request.setElderId(elderId);
        request.setPageNo(1);
        request.setPageSize(Math.min(limit, MAX_RECENT_LIMIT));
        return queryWindow(request, "agent", List.of()).list().stream()
                .map(
                        photo ->
                                new RecentPhoto(
                                        photo.id(),
                                        photo.elderId(),
                                        photo.source(),
                                        photo.capturedAt(),
                                        photo.description(),
                                        photo.thumbnailUrl(),
                                        photo.originalUrl()))
                .toList();
    }

    private UploadCommit persistUploadedPhoto(
            Long deviceId,
            Long elderId,
            Long ownerUserId,
            Long orgId,
            Long workspaceId,
            String clientPhotoId,
            String source,
            LocalDateTime capturedAt,
            String originalName,
            ValidatedPhoto photo,
            Long fileId) {
        var existing = repository.findByDeviceIdAndClientPhotoId(deviceId, clientPhotoId);
        if (existing.isPresent()) {
            return new UploadCommit(existing.get(), false);
        }
        var request =
                new ElderPhotoCreateDTO(
                        elderId,
                        deviceId,
                        clientPhotoId,
                        fileId,
                        source,
                        capturedAt,
                        originalName,
                        photo.format().mimeType(),
                        (long) photo.bytes().length,
                        photo.contentHash(),
                        null);
        var entity = ElderPhotoConvert.INSTANCE.toEntity(request);
        entity.setOwnerId(ownerUserId);
        entity.setOrgId(orgId);
        entity.setWorkspaceId(workspaceId);
        validate(entity);
        entity = repository.saveAndFlush(entity);
        retainFile(entity);
        return new UploadCommit(entity, true);
    }

    private ValidatedPhoto readAndValidate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw exception(PHOTO_INVALID);
        }
        if (file.getSize() > MAX_PHOTO_SIZE) {
            throw exception(PHOTO_TOO_LARGE);
        }
        try (var input = file.getInputStream()) {
            var bytes = input.readNBytes((int) MAX_PHOTO_SIZE + 1);
            if (bytes.length > MAX_PHOTO_SIZE) {
                throw exception(PHOTO_TOO_LARGE);
            }
            var format = detectFormat(bytes);
            return new ValidatedPhoto(bytes, format, sha256(bytes));
        } catch (IOException failure) {
            throw exception(PHOTO_INVALID);
        }
    }

    private PhotoFormat detectFormat(byte[] bytes) {
        if (isJpeg(bytes)) {
            return new PhotoFormat("image/jpeg", "jpg");
        }
        if (isPng(bytes)) {
            return new PhotoFormat("image/png", "png");
        }
        if (isWebp(bytes)) {
            return new PhotoFormat("image/webp", "webp");
        }
        throw exception(PHOTO_FORMAT_UNSUPPORTED);
    }

    private boolean isJpeg(byte[] bytes) {
        return bytes.length >= 3
                && bytes[0] == (byte) 0xFF
                && bytes[1] == (byte) 0xD8
                && bytes[2] == (byte) 0xFF;
    }

    private boolean isPng(byte[] bytes) {
        byte[] signature = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        return bytes.length >= signature.length
                && MessageDigest.isEqual(Arrays.copyOf(bytes, signature.length), signature);
    }

    private boolean isWebp(byte[] bytes) {
        return bytes.length >= 12
                && bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P';
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("运行环境不支持 SHA-256", failure);
        }
    }

    private String displayName(MultipartFile file, String clientPhotoId, PhotoFormat format) {
        var originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            return clientPhotoId + "." + format.extension();
        }
        var normalized = originalName.replace('\\', '/');
        var leaf = normalized.substring(normalized.lastIndexOf('/') + 1).strip();
        if (leaf.isEmpty()) {
            return clientPhotoId + "." + format.extension();
        }
        return leaf.length() <= 500 ? leaf : leaf.substring(leaf.length() - 500);
    }

    private ElderPhotoVO requireSameContent(ElderPhoto existing, String contentHash) {
        if (!contentHash.equals(existing.getContentHash())) {
            throw exception(PHOTO_CLIENT_ID_CONFLICT);
        }
        return toVO(existing);
    }

    private void validate(ElderPhoto entity) {
        requireEntityIdentity(entity.getDeviceId(), entity.getElderId());
        requireClientPhotoId(entity.getClientPhotoId());
        requireSource(entity.getSource());
        if (entity.getFileId() == null
                || entity.getFileId() <= 0
                || entity.getCapturedAt() == null
                || entity.getOriginalName() == null
                || entity.getOriginalName().isBlank()
                || entity.getMimeType() == null
                || entity.getMimeType().isBlank()
                || entity.getFileSize() == null
                || entity.getFileSize() <= 0
                || entity.getContentHash() == null
                || entity.getContentHash().length() != 64) {
            throw exception(PHOTO_INVALID);
        }
        validateDescription(entity.getDescription());
    }

    private void requireDeviceContext(
            Long deviceId, String scenarioKey, Long elderId, Long ownerUserId) {
        if (deviceId == null
                || deviceId <= 0
                || !ElderDeviceScenario.KEY.equals(scenarioKey)
                || elderId == null
                || elderId <= 0
                || ownerUserId == null
                || ownerUserId <= 0) {
            throw exception(PHOTO_INVALID);
        }
    }

    private void requireEntityIdentity(Long deviceId, Long elderId) {
        if (deviceId == null || deviceId <= 0 || elderId == null || elderId <= 0) {
            throw exception(PHOTO_INVALID);
        }
    }

    private void requireClientPhotoId(String clientPhotoId) {
        if (clientPhotoId == null || clientPhotoId.isBlank() || clientPhotoId.length() > 100) {
            throw exception(PHOTO_INVALID);
        }
    }

    private void requireSource(String source) {
        if (source == null || Arrays.stream(PhotoSourceEnum.ARRAYS).noneMatch(source::equals)) {
            throw exception(PHOTO_INVALID);
        }
    }

    private void validateDescription(String description) {
        if (description != null && description.length() > 500) {
            throw exception(PHOTO_INVALID);
        }
    }

    private void retainFile(ElderPhoto entity) {
        fileRecordApi.retain(entity.getFileId(), fileReference(entity));
    }

    private FileReference fileReference(ElderPhoto entity) {
        return new FileReference(
                FILE_REFERENCE_TYPE, entity.getId(), FILE_REFERENCE_FIELD, FILE_REFERENCE_PURPOSE);
    }

    private void requestCleanup(Long fileId, RuntimeException originalFailure) {
        try {
            fileRecordApi.requestDelete(fileId);
        } catch (RuntimeException cleanupFailure) {
            if (originalFailure == null) {
                throw cleanupFailure;
            }
            originalFailure.addSuppressed(cleanupFailure);
        }
    }

    private void requireExpectedVersion(ElderPhoto entity, Integer expectedVersion) {
        if (expectedVersion == null || expectedVersion != currentVersion(entity)) {
            throw exception(PHOTO_VERSION_CONFLICT);
        }
    }

    private int currentVersion(ElderPhoto entity) {
        return entity.getVersion() == null ? 0 : entity.getVersion();
    }

    private <T> void applyNullable(Patch<T> patch, Consumer<T> setter) {
        if (!patch.isAbsent()) {
            setter.accept(patch.valueOrNull());
        }
    }

    private String contentUrl(Long photoId) {
        return "%s/%d/content".formatted(BASE_PATH, photoId);
    }

    private record PhotoFormat(String mimeType, String extension) {}

    private record ValidatedPhoto(byte[] bytes, PhotoFormat format, String contentHash) {}

    private record UploadCommit(ElderPhoto photo, boolean created) {}

    /** 已鉴权的照片正文。 */
    public record PhotoContent(InputStream input, String filename, String mimeType, long size) {}
}
