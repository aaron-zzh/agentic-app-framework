package com.xuejiai.aaf.module.system.log.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.system.log.domain.RecordVersion;
import com.xuejiai.aaf.module.system.log.repository.RecordVersionRepository;

import lombok.RequiredArgsConstructor;

/**
 * 版本快照业务逻辑。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class RecordVersionService {

    private final RecordVersionRepository recordVersionRepository;

    /** 每个实体最大保留版本数 */
    @Value("${aaf.version.max-per-record:50}")
    private int maxPerRecord;

    /** 创建版本快照 */
    @Transactional
    public RecordVersion createSnapshot(String entityType, Long entityId, String data) {
        int nextVerNumber =
                recordVersionRepository
                        .findTopByEntityTypeAndEntityIdOrderByVerNumberDesc(entityType, entityId)
                        .map(v -> v.getVerNumber() + 1)
                        .orElse(1);

        var version = new RecordVersion();
        version.setEntityType(entityType);
        version.setEntityId(entityId);
        version.setVerNumber(nextVerNumber);
        version.setData(data);

        var saved = recordVersionRepository.save(version);

        long count = recordVersionRepository.countByEntityTypeAndEntityId(entityType, entityId);
        if (count > maxPerRecord) {
            var all =
                    recordVersionRepository.findByEntityTypeAndEntityIdOrderByVerNumberDesc(
                            entityType, entityId);
            int cutoff = all.get(maxPerRecord - 1).getVerNumber();
            recordVersionRepository.deleteByEntityTypeAndEntityIdAndVerNumberLessThanEqual(
                    entityType, entityId, cutoff - 1);
        }

        return saved;
    }

    /** 获取版本列表 */
    @Transactional(readOnly = true)
    public List<RecordVersion> listVersions(String entityType, Long entityId) {
        return recordVersionRepository.findByEntityTypeAndEntityIdOrderByVerNumberDesc(
                entityType, entityId);
    }

    /** 获取指定版本的快照数据 */
    @Transactional(readOnly = true)
    public String getVersionData(String entityType, Long entityId, Integer verNumber) {
        return recordVersionRepository
                .findByEntityTypeAndEntityIdAndVerNumber(entityType, entityId, verNumber)
                .map(RecordVersion::getData)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "版本不存在"));
    }
}
