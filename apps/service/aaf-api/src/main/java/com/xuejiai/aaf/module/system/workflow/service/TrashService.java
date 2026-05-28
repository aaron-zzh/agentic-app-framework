package com.xuejiai.aaf.module.system.workflow.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.module.system.workflow.vo.TrashItemVO;
import com.xuejiai.aaf.module.system.workflow.vo.TrashPageDTO;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 回收站服务：查询已删除记录、恢复、彻底删除、定时清理。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrashService {

    private final EntityManager entityManager;

    @Value("${aaf.trash.retention-days:30}")
    private int retentionDays;

    /** 支持回收站查询的表及其标题字段映射。 */
    private static final List<TableMapping> TABLE_MAPPINGS =
            List.of(
                    new TableMapping("sys_user", "user", "nickname"),
                    new TableMapping("sys_todo", "todo", "title"),
                    new TableMapping("sys_notification", "notification", "title"));

    /** 分页查询已删除记录。 */
    public PageResult<TrashItemVO> page(TrashPageDTO request) {
        var filteredTables =
                TABLE_MAPPINGS.stream()
                        .filter(
                                t ->
                                        request.getEntityType() == null
                                                || request.getEntityType().equals(t.entityType))
                        .toList();

        if (filteredTables.isEmpty()) {
            return PageResult.empty();
        }

        // 构建 UNION ALL 查询
        var unions =
                filteredTables.stream()
                        .map(
                                t ->
                                        "SELECT id, '%s' AS entity_type, %s AS title, update_by AS deleted_by, delete_time AS deleted_at FROM %s WHERE deleted = true"
                                                .formatted(
                                                        t.entityType, t.titleColumn, t.tableName))
                        .toList();
        var unionSql = String.join(" UNION ALL ", unions);

        // 查总数
        var countSql = "SELECT COUNT(*) FROM (%s) t".formatted(unionSql);
        var total =
                ((Number) entityManager.createNativeQuery(countSql).getSingleResult()).longValue();

        if (total == 0) {
            return PageResult.empty();
        }

        // 分页查询
        int offset = (request.getPageNo() - 1) * request.getPageSize();
        var dataSql =
                "%s ORDER BY deleted_at DESC LIMIT %d OFFSET %d"
                        .formatted(unionSql, request.getPageSize(), offset);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(dataSql).getResultList();

        var list =
                rows.stream()
                        .map(
                                row ->
                                        new TrashItemVO(
                                                ((Number) row[0]).longValue(),
                                                (String) row[1],
                                                (String) row[2],
                                                row[3] != null
                                                        ? ((Number) row[3]).longValue()
                                                        : null,
                                                row[4] != null
                                                        ? ((java.sql.Timestamp) row[4])
                                                                .toLocalDateTime()
                                                        : null))
                        .toList();

        return new PageResult<>(list, total);
    }

    /** 恢复已删除记录。 */
    @Transactional
    public void restore(String entityType, List<Long> ids) {
        var table = findTable(entityType);
        entityManager
                .createNativeQuery(
                        "UPDATE %s SET deleted = false, delete_time = NULL WHERE id IN (:ids) AND deleted = true"
                                .formatted(table.tableName))
                .setParameter("ids", ids)
                .executeUpdate();
    }

    /** 彻底删除记录。 */
    @Transactional
    public void purge(String entityType, List<Long> ids) {
        var table = findTable(entityType);
        entityManager
                .createNativeQuery(
                        "DELETE FROM %s WHERE id IN (:ids) AND deleted = true"
                                .formatted(table.tableName))
                .setParameter("ids", ids)
                .executeUpdate();
    }

    /** 定时清理：每天凌晨 3 点删除超过保留天数的记录。 */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void scheduledPurge() {
        var deadline = LocalDateTime.now().minusDays(retentionDays);
        for (var table : TABLE_MAPPINGS) {
            int deleted =
                    entityManager
                            .createNativeQuery(
                                    "DELETE FROM %s WHERE deleted = true AND delete_time < :deadline"
                                            .formatted(table.tableName))
                            .setParameter("deadline", deadline)
                            .executeUpdate();
            if (deleted > 0) {
                log.info("定时清理：从 {} 中物理删除 {} 条过期记录", table.tableName, deleted);
            }
        }
    }

    private TableMapping findTable(String entityType) {
        return TABLE_MAPPINGS.stream()
                .filter(t -> t.entityType.equals(entityType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的实体类型: " + entityType));
    }

    private record TableMapping(String tableName, String entityType, String titleColumn) {}
}
