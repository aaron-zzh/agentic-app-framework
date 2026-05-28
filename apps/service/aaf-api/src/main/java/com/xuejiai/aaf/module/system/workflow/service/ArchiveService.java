package com.xuejiai.aaf.module.system.workflow.service;

import java.util.List;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.system.workflow.domain.ArchiveRule;
import com.xuejiai.aaf.module.system.workflow.repository.ArchiveRuleRepository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 数据归档服务：手动归档/恢复 + 定时自动归档。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArchiveService {

    private final ArchiveRuleRepository ruleRepository;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    /** 实体标识 → 表名映射 */
    private static final Map<String, String> ENTITY_TABLE_MAP =
            Map.ofEntries(
                    Map.entry("user", "sys_user"),
                    Map.entry("todo", "sys_todo"),
                    Map.entry("notification", "sys_notification"),
                    Map.entry("comment", "sys_comment"),
                    Map.entry("subscription", "sys_subscription"),
                    Map.entry("role", "sys_role"),
                    Map.entry("document", "doc_document"));

    /** 手动归档。 */
    @Transactional
    public void archive(String entitySlug, Long id) {
        var table = resolveTable(entitySlug);
        int updated =
                entityManager
                        .createNativeQuery(
                                "UPDATE %s SET archived_at = CURRENT_TIMESTAMP WHERE id = :id AND archived_at IS NULL AND deleted = false"
                                        .formatted(table))
                        .setParameter("id", id)
                        .executeUpdate();
        if (updated == 0) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "记录不存在或已归档");
        }
    }

    /** 恢复归档。 */
    @Transactional
    public void unarchive(String entitySlug, Long id) {
        var table = resolveTable(entitySlug);
        int updated =
                entityManager
                        .createNativeQuery(
                                "UPDATE %s SET archived_at = NULL WHERE id = :id AND archived_at IS NOT NULL AND deleted = false"
                                        .formatted(table))
                        .setParameter("id", id)
                        .executeUpdate();
        if (updated == 0) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "记录不存在或未归档");
        }
    }

    /** 定时自动归档：每天凌晨 2 点扫描规则执行。 */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void scheduledArchive() {
        var rules = ruleRepository.findByEnabledTrueAndDeletedFalse();
        for (var rule : rules) {
            try {
                executeRule(rule);
            } catch (Exception e) {
                log.error(
                        "执行归档规则失败, ruleId={}, entity={}: {}",
                        rule.getId(),
                        rule.getEntitySlug(),
                        e.getMessage());
            }
        }
    }

    private void executeRule(ArchiveRule rule) {
        var table = ENTITY_TABLE_MAP.get(rule.getEntitySlug());
        if (table == null) {
            log.warn("归档规则引用了未知实体: {}", rule.getEntitySlug());
            return;
        }

        // 解析条件
        String whereClause = buildWhereClause(rule);
        if (whereClause == null) {
            return;
        }

        // 执行归档：满足条件 + update_time 超过 afterDays 天 + 未归档 + 未删除
        var sql =
                "UPDATE %s SET archived_at = CURRENT_TIMESTAMP WHERE %s AND update_time < CURRENT_TIMESTAMP - INTERVAL '%d days' AND archived_at IS NULL AND deleted = false"
                        .formatted(table, whereClause, rule.getAfterDays());

        int archived = entityManager.createNativeQuery(sql).executeUpdate();
        if (archived > 0) {
            log.info("自动归档：规则 {} 归档 {} 中 {} 条记录", rule.getId(), table, archived);
        }
    }

    /** 解析 JSONB condition 为 SQL WHERE 片段。 */
    private String buildWhereClause(ArchiveRule rule) {
        try {
            var condition =
                    objectMapper.readValue(
                            rule.getCondition(), new TypeReference<Map<String, String>>() {});
            var field = condition.get("field");
            var operator = condition.get("operator");
            var value = condition.get("value");
            if (field == null || operator == null || value == null) {
                return null;
            }
            return switch (operator) {
                case "eq" -> "%s = '%s'".formatted(field, value);
                case "ne" -> "%s != '%s'".formatted(field, value);
                case "gt" -> "%s > '%s'".formatted(field, value);
                case "lt" -> "%s < '%s'".formatted(field, value);
                case "is_null" -> "%s IS NULL".formatted(field);
                case "is_not_null" -> "%s IS NOT NULL".formatted(field);
                default -> null;
            };
        } catch (Exception e) {
            log.warn("解析归档规则条件失败, ruleId={}: {}", rule.getId(), e.getMessage());
            return null;
        }
    }

    private String resolveTable(String entitySlug) {
        var table = ENTITY_TABLE_MAP.get(entitySlug);
        if (table == null) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "不支持的实体类型: " + entitySlug);
        }
        return table;
    }

    // ==================== 规则 CRUD ====================

    public List<ArchiveRule> listRules() {
        return ruleRepository.findAll();
    }

    public ArchiveRule getRuleById(Long id) {
        return ruleRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "归档规则不存在"));
    }

    @Transactional
    public ArchiveRule createRule(ArchiveRule rule) {
        return ruleRepository.save(rule);
    }

    @Transactional
    public ArchiveRule updateRule(Long id, ArchiveRule updated) {
        var rule = getRuleById(id);
        rule.setEntitySlug(updated.getEntitySlug());
        rule.setCondition(updated.getCondition());
        rule.setAfterDays(updated.getAfterDays());
        rule.setEnabled(updated.getEnabled());
        return ruleRepository.save(rule);
    }

    @Transactional
    public void deleteRule(Long id) {
        ruleRepository.deleteById(id);
    }
}
