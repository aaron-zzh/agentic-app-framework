package com.xuejiai.aaf.module.system.file.service;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.module.system.file.domain.FileRecord;
import com.xuejiai.aaf.module.system.file.repository.FileRecordRepository;
import com.xuejiai.aaf.module.system.file.vo.FileRecordPageDTO;
import com.xuejiai.aaf.module.system.file.vo.FileRecordVO;

import lombok.RequiredArgsConstructor;

/**
 * 文件记录业务逻辑。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class FileRecordService {

    private final FileRecordRepository fileRecordRepository;

    /**
     * 分页查询文件记录。
     *
     * @param req 分页查询参数
     * @return 分页结果
     */
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
