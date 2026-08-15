package com.xuejiai.aaf.module.system.file.service;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.exception.QuotaExceededException;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.storage.UploadPolicy;
import com.xuejiai.aaf.module.billing.repository.EntitlementQuotaRepository;
import com.xuejiai.aaf.module.system.file.api.FileRecordApi;
import com.xuejiai.aaf.module.system.file.api.FileReference;
import com.xuejiai.aaf.module.system.file.api.StoredFile;
import com.xuejiai.aaf.module.system.file.config.FileStorageProperties;
import com.xuejiai.aaf.module.system.file.domain.FileRecord;
import com.xuejiai.aaf.module.system.file.domain.FileReferenceRecord;
import com.xuejiai.aaf.module.system.file.enums.FileStorageStatus;
import com.xuejiai.aaf.module.system.file.repository.FileRecordRepository;
import com.xuejiai.aaf.module.system.file.repository.FileReferenceRepository;
import com.xuejiai.aaf.module.system.file.vo.FileRecordPageDTO;
import com.xuejiai.aaf.module.system.file.vo.FileRecordVO;

import lombok.RequiredArgsConstructor;

/** 文件记录业务逻辑——sys_file 与 sys_file_reference 的唯一写入收口。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FileRecordService implements FileRecordApi {

    private static final long GB = 1024L * 1024 * 1024;
    private static final long DELETE_GRACE_DAYS = 7L;
    private static final Set<String> SORTABLE_FIELDS =
            Set.of("id", "originalName", "mimeType", "size", "createTime");

    private final FileRecordRepository fileRecordRepository;
    private final FileReferenceRepository fileReferenceRepository;
    private final EntitlementQuotaRepository entitlementQuotaRepository;
    private final FileStorageReferenceService storageReferenceService;
    private final FileAccessService fileAccessService;
    private final FileStorageProperties storageProperties;
    private final OperatorContext operatorContext;

    /** 按存储 key 删除文件记录。 */
    @Transactional
    public void deleteByKey(String key) {
        if (key == null || key.isBlank()) return;
        fileRecordRepository.deleteByKey(key);
    }

    /** 校验并返回当前用户拥有的文件记录。 */
    public FileRecord requireOwnedByKey(String key) {
        return fileRecordRepository
                .findByKeyAndUploaderId(key, requireCurrentOwnerId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "文件不存在或无权访问"));
    }

    /** 删除当前用户拥有的文件记录。 */
    @Transactional
    public void deleteOwnedByKey(String key) {
        fileRecordRepository.delete(requireOwnedByKey(key));
    }

    /** 按文件创建时绑定的存储配置下载当前用户拥有的文件。 */
    public InputStream downloadOwnedByKey(String key) {
        var file = requireOwnedByKey(key);
        return storageReferenceService.resolve(file).download(file.getKey());
    }

    /** 客户端直传 key 必须位于当前用户命名空间。 */
    public void requireCurrentOwnerNamespace(String key) {
        var prefix = currentOwnerNamespace() + "/";
        if (key == null || !key.startsWith(prefix) || key.contains("..")) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "文件 key 不属于当前用户命名空间");
        }
    }

    /** 当前用户的存储命名空间前缀。 */
    public String currentOwnerNamespace() {
        return "users/" + requireCurrentOwnerId();
    }

    /** 当前用户按存储配置隔离的直传命名空间。 */
    public String currentOwnerStorageNamespace(Long storageConfigId) {
        if (storageConfigId == null) {
            throw new IllegalArgumentException("storageConfigId 不能为空");
        }
        return currentOwnerNamespace() + "/storage/" + storageConfigId;
    }

    /** 从服务端生成的直传 key 提取创建时绑定的存储配置 ID。 */
    private Long storageConfigIdFromCurrentOwnerKey(String key) {
        var prefix = currentOwnerNamespace() + "/storage/";
        if (key == null || !key.startsWith(prefix) || key.contains("..")) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "文件 key 不属于当前用户直传命名空间");
        }
        var suffix = key.substring(prefix.length());
        var slash = suffix.indexOf('/');
        if (slash <= 0) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "直传文件 key 格式无效");
        }
        var configId = suffix.substring(0, slash);
        try {
            return Long.valueOf(configId);
        } catch (NumberFormatException e) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "直传文件存储配置无效");
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StoredFile registerCurrent(
            String key, String originalName, String mimeType, long size, String contentHash) {
        return registerCurrentForStorage(
                key, originalName, mimeType, size, contentHash, currentStorageConfigId());
    }

    /** 校验浏览器直传的实际对象后，以签发时绑定的存储配置登记。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StoredFile confirmCurrentUpload(
            String key, String originalName, String mimeType, long declaredSize) {
        var storageConfigId = storageConfigIdFromCurrentOwnerKey(key);
        var policy = new UploadPolicy(storageProperties.uploadLimits());
        policy.validate(originalName, mimeType, declaredSize);
        long actualSize = countUploadedObject(key, storageConfigId, policy.maxSizeBytes());
        if (actualSize != declaredSize) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "直传文件大小与实际对象不一致");
        }
        return registerCurrentForStorage(
                key, originalName, mimeType, actualSize, null, storageConfigId);
    }

    private long countUploadedObject(String key, Long storageConfigId, long maxSizeBytes) {
        try (var input = storageReferenceService.resolveByConfigId(storageConfigId).download(key)) {
            long total = 0;
            var buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxSizeBytes) {
                    throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "直传文件超过大小限制");
                }
            }
            return total;
        } catch (java.io.IOException e) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "直传文件不存在或无法读取");
        }
    }

    /** 使用上传时已选定的存储配置登记当前用户文件，避免主配置切换造成错绑。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StoredFile registerCurrentForStorage(
            String key,
            String originalName,
            String mimeType,
            long size,
            String contentHash,
            Long storageConfigId) {
        return registerForStorage(
                key,
                originalName,
                mimeType,
                size,
                contentHash,
                requireCurrentOwnerId(),
                storageConfigId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StoredFile register(
            String key,
            String originalName,
            String mimeType,
            long size,
            String contentHash,
            Long uploaderId) {
        return registerForStorage(
                key,
                originalName,
                mimeType,
                size,
                contentHash,
                uploaderId,
                currentStorageConfigId());
    }

    /** 使用上传时已选定的存储配置登记任意归属的文件。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StoredFile registerForStorage(
            String key,
            String originalName,
            String mimeType,
            long size,
            String contentHash,
            Long uploaderId,
            Long storageConfigId) {
        if (storageConfigId == null) {
            throw new IllegalArgumentException("storageConfigId 不能为空");
        }
        if (uploaderId != null) {
            checkStorageQuota(uploaderId, size);
        }
        var record = new FileRecord();
        record.setStorageConfigId(storageConfigId);
        record.setKey(key);
        record.setOriginalName(originalName != null ? originalName : key);
        record.setMimeType(mimeType);
        record.setSize(size);
        record.setContentHash(contentHash);
        record.setStorageStatus(FileStorageStatus.ACTIVE.name());
        record.setUploaderId(uploaderId);
        return toStoredFile(fileRecordRepository.save(record));
    }

    @Override
    public StoredFile get(Long fileId) {
        return toStoredFile(requireFile(fileId));
    }

    @Override
    public StoredFile getByKey(String key) {
        return toStoredFile(requireFileByKey(key));
    }

    @Override
    public StoredFile requireCurrentOwner(Long fileId) {
        var file = requireFile(fileId);
        if (!requireCurrentOwnerId().equals(file.getUploaderId())) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "文件不存在或无权访问");
        }
        return toStoredFile(file);
    }

    @Override
    public List<String> prepareCurrentOwnerImageInputs(List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        return fileIds.stream()
                .map(
                        fileId -> {
                            var file = requireFile(fileId);
                            if (!requireCurrentOwnerId().equals(file.getUploaderId())) {
                                throw new BusinessException(
                                        GlobalErrorCode.NOT_FOUND, "文件不存在或无权访问");
                            }
                            return storageReferenceService.prepareImageInput(file);
                        })
                .toList();
    }

    @Override
    public String getAccessibleUrl(Long fileId) {
        requireFile(fileId);
        return fileAccessService.accessUrl(fileId);
    }

    @Override
    public InputStream openByKey(String key) {
        return fileAccessService.open(requireFileByKey(key));
    }

    @Override
    public String prepareExternalAccessByKey(String key, java.time.Duration expiry) {
        return storageReferenceService.prepareExternalAccess(requireFileByKey(key), expiry);
    }

    @Override
    @Transactional
    public void requestDeleteByKey(String key) {
        requestDelete(requireFileByKey(key).getId());
    }

    @Override
    @Transactional
    public void retain(Long fileId, FileReference reference) {
        var file = requireFile(fileId);
        var existing =
                fileReferenceRepository.findByFileIdAndRefTypeAndRefIdAndRefField(
                        fileId, reference.refType(), reference.refId(), reference.refField());
        if (existing.isPresent()) return;

        var record = new FileReferenceRecord();
        record.setFileId(fileId);
        record.setRefType(reference.refType());
        record.setRefId(reference.refId());
        record.setRefField(reference.refField());
        record.setPurpose(reference.purpose());
        operatorContext.currentOwnerId().ifPresent(record::setOwnerId);
        fileReferenceRepository.save(record);

        if (!FileStorageStatus.ACTIVE.name().equals(file.getStorageStatus())) {
            file.setStorageStatus(FileStorageStatus.ACTIVE.name());
            file.setDeleteAfter(null);
            fileRecordRepository.save(file);
        }
    }

    @Override
    @Transactional
    public void release(Long fileId, FileReference reference) {
        var file = requireFile(fileId);
        fileReferenceRepository
                .findByFileIdAndRefTypeAndRefIdAndRefField(
                        fileId, reference.refType(), reference.refId(), reference.refField())
                .ifPresent(fileReferenceRepository::delete);
        fileReferenceRepository.flush();
        if (fileReferenceRepository.countByFileId(fileId) == 0) {
            file.setStorageStatus(FileStorageStatus.PENDING_DELETE.name());
            file.setDeleteAfter(LocalDateTime.now().plusDays(DELETE_GRACE_DAYS));
            fileRecordRepository.save(file);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requestDelete(Long fileId) {
        var file = requireFile(fileId);
        if (fileReferenceRepository.countByFileId(fileId) > 0) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "文件仍被业务对象引用，不能进入删除队列");
        }
        file.setStorageStatus(FileStorageStatus.PENDING_DELETE.name());
        file.setDeleteAfter(LocalDateTime.now());
        fileRecordRepository.save(file);
    }

    /** 分页查询当前用户的文件记录。 */
    public PageResult<FileRecordVO> page(FileRecordPageDTO req) {
        var pageable = req.toPageable(Sort.by("id").descending(), SORTABLE_FIELDS);
        Specification<FileRecord> spec =
                SpecificationBuilder.<FileRecord>builder()
                        .likeIfPresent("originalName", req.getOriginalName())
                        .eqIfPresent("mimeType", req.getMimeType())
                        .eqIfPresent("uploaderId", requireCurrentOwnerId())
                        .build();
        var page = fileRecordRepository.findAll(spec, pageable);
        return new PageResult<>(
                page.getContent().stream().map(this::toVO).toList(), page.getTotalElements());
    }

    private FileRecord requireFileByKey(String key) {
        var file =
                fileRecordRepository
                        .findByKey(key)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "文件不存在"));
        if (FileStorageStatus.DELETED.name().equals(file.getStorageStatus())) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "文件已删除");
        }
        return file;
    }

    private FileRecord requireFile(Long fileId) {
        var file =
                fileRecordRepository
                        .findById(fileId)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "文件不存在"));
        if (FileStorageStatus.DELETED.name().equals(file.getStorageStatus())) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "文件已删除");
        }
        return file;
    }

    private Long requireCurrentOwnerId() {
        return operatorContext
                .currentOwnerId()
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED));
    }

    private Long currentStorageConfigId() {
        return storageReferenceService.resolveCurrentMaster().storageConfigId();
    }

    private void checkStorageQuota(Long userId, long newFileSize) {
        var quota = entitlementQuotaRepository.findByUserIdAndEntCode(userId, "storage");
        if (quota.isEmpty() || quota.get().getRemain() == -1) return;
        var quotaBytes = quota.get().getRemain() * GB;
        var used = fileRecordRepository.sumSizeByUploaderId(userId);
        if (used + newFileSize > quotaBytes) {
            throw new QuotaExceededException("storage", 1, 0);
        }
    }

    private StoredFile toStoredFile(FileRecord file) {
        return new StoredFile(
                file.getId(),
                file.getKey(),
                fileAccessService.accessUrl(file.getId()),
                file.getOriginalName(),
                file.getMimeType(),
                file.getSize(),
                file.getContentHash(),
                file.getUploaderId());
    }

    private FileRecordVO toVO(FileRecord entity) {
        return new FileRecordVO(
                entity.getId(),
                entity.getKey(),
                fileAccessService.accessUrl(entity.getId()),
                entity.getOriginalName(),
                entity.getMimeType(),
                entity.getSize(),
                entity.getUploaderId(),
                entity.getCreateTime());
    }
}
