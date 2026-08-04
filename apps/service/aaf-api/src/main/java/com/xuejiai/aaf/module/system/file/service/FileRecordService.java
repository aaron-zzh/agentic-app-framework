package com.xuejiai.aaf.module.system.file.service;

import java.time.LocalDateTime;
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
import com.xuejiai.aaf.framework.storage.StorageService;
import com.xuejiai.aaf.module.billing.repository.EntitlementQuotaRepository;
import com.xuejiai.aaf.module.system.file.api.FileRecordApi;
import com.xuejiai.aaf.module.system.file.api.FileReference;
import com.xuejiai.aaf.module.system.file.api.StoredFile;
import com.xuejiai.aaf.module.system.file.domain.FileRecord;
import com.xuejiai.aaf.module.system.file.domain.FileReferenceRecord;
import com.xuejiai.aaf.module.system.file.repository.FileConfigRepository;
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
    private final FileConfigRepository fileConfigRepository;
    private final EntitlementQuotaRepository entitlementQuotaRepository;
    private final StorageService storageService;
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

    @Override
    @Transactional
    public StoredFile registerCurrent(
            String key, String originalName, String mimeType, long size, String contentHash) {
        return register(key, originalName, mimeType, size, contentHash, requireCurrentOwnerId());
    }

    @Override
    @Transactional
    public StoredFile register(
            String key,
            String originalName,
            String mimeType,
            long size,
            String contentHash,
            Long uploaderId) {
        if (uploaderId != null) {
            checkStorageQuota(uploaderId, size);
        }
        var record = new FileRecord();
        record.setStorageConfigId(
                fileConfigRepository.findByMasterTrue().map(config -> config.getId()).orElse(null));
        record.setKey(key);
        record.setOriginalName(originalName != null ? originalName : key);
        record.setMimeType(mimeType);
        record.setSize(size);
        record.setContentHash(contentHash);
        record.setStorageStatus("ACTIVE");
        record.setUploaderId(uploaderId);
        return toStoredFile(fileRecordRepository.save(record));
    }

    @Override
    public StoredFile get(Long fileId) {
        return toStoredFile(requireFile(fileId));
    }

    @Override
    public String getAccessibleUrl(Long fileId) {
        return storageService.getUrl(requireFile(fileId).getKey());
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

        if (!"ACTIVE".equals(file.getStorageStatus())) {
            file.setStorageStatus("ACTIVE");
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
            file.setStorageStatus("PENDING_DELETE");
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
        file.setStorageStatus("PENDING_DELETE");
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

    private FileRecord requireFile(Long fileId) {
        var file =
                fileRecordRepository
                        .findById(fileId)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "文件不存在"));
        if ("DELETED".equals(file.getStorageStatus())) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "文件已删除");
        }
        return file;
    }

    private Long requireCurrentOwnerId() {
        return operatorContext
                .currentOwnerId()
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED));
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
                storageService.getUrl(file.getKey()),
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
                storageService.getUrl(entity.getKey()),
                entity.getOriginalName(),
                entity.getMimeType(),
                entity.getSize(),
                entity.getUploaderId(),
                entity.getCreateTime());
    }
}
