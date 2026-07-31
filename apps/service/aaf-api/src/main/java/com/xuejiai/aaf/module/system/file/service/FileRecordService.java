package com.xuejiai.aaf.module.system.file.service;

import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.exception.QuotaExceededException;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.storage.StorageService;
import com.xuejiai.aaf.module.billing.repository.EntitlementQuotaRepository;
import com.xuejiai.aaf.module.system.file.domain.FileRecord;
import com.xuejiai.aaf.module.system.file.repository.FileRecordRepository;
import com.xuejiai.aaf.module.system.file.vo.FileRecordPageDTO;
import com.xuejiai.aaf.module.system.file.vo.FileRecordVO;

import lombok.RequiredArgsConstructor;

/**
 * 文件记录业务逻辑——sys_file 唯一写入收口。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FileRecordService {

    private static final long GB = 1024L * 1024 * 1024;
    private static final Set<String> SORTABLE_FIELDS =
            Set.of("id", "originalName", "mimeType", "size", "createTime");

    private final FileRecordRepository fileRecordRepository;
    private final EntitlementQuotaRepository entitlementQuotaRepository;
    private final StorageService storageService;
    private final OperatorContext operatorContext;

    /** 按存储 key 删除文件记录（同时归还存储配额，预留扩展点）。 */
    @Transactional
    public void deleteByKey(String key) {
        if (key == null || key.isBlank()) return;
        fileRecordRepository.deleteByKey(key);
    }

    /** 校验并返回当前用户拥有的文件记录。 */
    public FileRecord requireOwnedByKey(String key) {
        return fileRecordRepository
                .findByKeyAndUploaderId(key, requireCurrentOwnerId())
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        GlobalErrorCode.NOT_FOUND, "文件不存在或无权访问"));
    }

    /** 删除当前用户拥有的文件记录。 */
    @Transactional
    public void deleteOwnedByKey(String key) {
        var record = requireOwnedByKey(key);
        fileRecordRepository.delete(record);
    }

    /** 客户端直传 key 必须位于当前用户命名空间。 */
    public void requireCurrentOwnerNamespace(String key) {
        var prefix = "users/" + requireCurrentOwnerId() + "/";
        if (key == null || !key.startsWith(prefix) || key.contains("..")) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "文件 key 不属于当前用户命名空间");
        }
    }

    /** HTTP 上传完成后按当前身份保存记录。 */
    @Transactional
    public FileRecord saveForCurrentOwner(
            String key, String originalName, String mimeType, long size) {
        var uploaderId = requireCurrentOwnerId();
        return save(key, originalName, mimeType, size, uploaderId);
    }

    /** 保存文件记录（统一收口）。 上传成功后调此方法落库，同时校验存储配额。 */
    @Transactional
    public FileRecord save(
            String key, String originalName, String mimeType, long size, Long uploaderId) {
        if (uploaderId != null) {
            checkStorageQuota(uploaderId, size);
        }
        var record = new FileRecord();
        record.setKey(key);
        record.setOriginalName(originalName);
        record.setMimeType(mimeType);
        record.setSize(size);
        record.setUploaderId(uploaderId);
        return fileRecordRepository.save(record);
    }

    /** 检查存储配额：已用 + 本次 <= quota（GB） */
    private void checkStorageQuota(Long userId, long newFileSize) {
        var quota = entitlementQuotaRepository.findByUserIdAndEntCode(userId, "storage");
        if (quota.isEmpty()) return; // 未配置配额则不限制

        long remainGB = quota.get().getRemain();
        if (remainGB == -1) return; // -1 表示不限制

        long quotaBytes = remainGB * GB;
        long used = fileRecordRepository.sumSizeByUploaderId(userId);
        if (used + newFileSize > quotaBytes) {
            throw new QuotaExceededException("storage", 1, 0);
        }
    }

    /** 分页查询文件记录。 */
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

    private Long requireCurrentOwnerId() {
        return operatorContext
                .currentOwnerId()
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED));
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
