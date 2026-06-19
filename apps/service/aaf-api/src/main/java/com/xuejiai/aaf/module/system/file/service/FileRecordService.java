package com.xuejiai.aaf.module.system.file.service;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.QuotaExceededException;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
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

    private final FileRecordRepository fileRecordRepository;
    private final EntitlementQuotaRepository entitlementQuotaRepository;

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
        long quotaBytes = quota.get().getRemain() * GB;
        long used = fileRecordRepository.sumSizeByUploaderId(userId);
        if (used + newFileSize > quotaBytes) {
            throw new QuotaExceededException("storage", 1, 0);
        }
    }

    /** 分页查询文件记录。 */
    public PageResult<FileRecordVO> page(FileRecordPageDTO req) {
        var pageable = req.toPageable(Sort.by("id").descending());
        Specification<FileRecord> spec =
                SpecificationBuilder.<FileRecord>builder()
                        .likeIfPresent("originalName", req.getOriginalName())
                        .eqIfPresent("mimeType", req.getMimeType())
                        .build();
        var page = fileRecordRepository.findAll(spec, pageable);
        return new PageResult<>(
                page.getContent().stream().map(this::toVO).toList(), page.getTotalElements());
    }

    private FileRecordVO toVO(FileRecord entity) {
        return new FileRecordVO(
                entity.getId(),
                entity.getKey(),
                entity.getOriginalName(),
                entity.getMimeType(),
                entity.getSize(),
                entity.getUploaderId(),
                entity.getCreateTime());
    }
}
