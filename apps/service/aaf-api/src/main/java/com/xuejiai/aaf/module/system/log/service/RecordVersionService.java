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
        // 获取当前最大版本号
        int nextVersion =
                recordVersionRepository
                        .findTopByEntityTypeAndEntityIdOrderByVersionDesc(entityType, entityId)
                        .map(v -> v.getVersion() + 1)
                        .orElse(1);

        var version = new RecordVersion();
        version.setEntityType(entityType);
        version.setEntityId(entityId);
        version.setVersion(nextVersion);
        version.setData(data);
        // createBy 由 JPA Auditing 自动填充

        var saved = recordVersionRepository.save(version);

        // 超出上限时清理最早的版本
        long count = recordVersionRepository.countByEntityTypeAndEntityId(entityType, entityId);
        if (count > maxPerRecord) {
            var all =
                    recordVersionRepository.findByEntityTypeAndEntityIdOrderByVersionDesc(
                            entityType, entityId);
            int cutoffVersion = all.get(maxPerRecord - 1).getVersion();
            recordVersionRepository.deleteByEntityTypeAndEntityIdAndVersionLessThanEqual(
                    entityType, entityId, cutoffVersion - 1);
        }

        return saved;
    }

    /** 获取版本列表 */
    @Transactional(readOnly = true)
    public List<RecordVersion> listVersions(String entityType, Long entityId) {
        return recordVersionRepository.findByEntityTypeAndEntityIdOrderByVersionDesc(
                entityType, entityId);
    }

    /** 获取指定版本的快照数据 */
    @Transactional(readOnly = true)
    public String getVersionData(String entityType, Long entityId, Integer version) {
        return recordVersionRepository
                .findByEntityTypeAndEntityIdAndVersion(entityType, entityId, version)
                .map(RecordVersion::getData)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "版本不存在"));
    }
}
