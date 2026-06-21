package com.xuejiai.aaf.module.system.log.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.log.domain.RecordVersion;

/**
 * 版本快照数据访问层。
 *
 * @author AaronZZH & Kiro
 */
public interface RecordVersionRepository extends JpaRepository<RecordVersion, Long> {

    /** 按实体查询版本列表（版本号降序） */
    List<RecordVersion> findByEntityTypeAndEntityIdOrderByVerNumberDesc(
            String entityType, Long entityId);

    /** 查询指定版本 */
    Optional<RecordVersion> findByEntityTypeAndEntityIdAndVerNumber(
            String entityType, Long entityId, Integer verNumber);

    /** 查询当前最大版本号 */
    Optional<RecordVersion> findTopByEntityTypeAndEntityIdOrderByVerNumberDesc(
            String entityType, Long entityId);

    /** 统计实体的版本数量 */
    long countByEntityTypeAndEntityId(String entityType, Long entityId);

    /** 删除最早的版本（用于超出 maxPerRecord 时清理） */
    void deleteByEntityTypeAndEntityIdAndVerNumberLessThanEqual(
            String entityType, Long entityId, Integer verNumber);
}
