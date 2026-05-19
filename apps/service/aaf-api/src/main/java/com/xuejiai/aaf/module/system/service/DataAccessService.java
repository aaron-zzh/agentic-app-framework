package com.xuejiai.aaf.module.system.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.system.domain.DataAccessRule;
import com.xuejiai.aaf.module.system.domain.UserRole;
import com.xuejiai.aaf.module.system.repository.DataAccessRuleRepository;
import com.xuejiai.aaf.module.system.repository.RoleRepository;
import com.xuejiai.aaf.module.system.repository.UserRoleRepository;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 行级数据权限服务。 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DataAccessService {

    private final DataAccessRuleRepository ruleRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final ObjectMapper objectMapper;

    /**
     * 构建行级数据权限 Specification。
     * 根据用户角色匹配规则，解析条件表达式生成 WHERE 子句。
     * 无匹配规则时返回 1=0（拒绝所有），实现无权限记录返回 404 效果。
     */
    public <T> Specification<T> buildSpecification(String entitySlug, Long userId) {
        // 获取用户角色编码
        var userRoleCodes = getUserRoleCodes(userId);

        // 查询该实体的所有规则
        var rules = ruleRepository.findByEntitySlugAndDeletedFalse(entitySlug);

        // 筛选匹配用户角色的规则
        var matchedRules = rules.stream()
                .filter(rule -> matchesRole(rule, userRoleCodes))
                .toList();

        if (matchedRules.isEmpty()) {
            // 无匹配规则，拒绝所有（返回 404 而非 403）
            return (root, query, cb) -> cb.disjunction();
        }

        // 构建用户上下文（用于 $user.xxx 表达式解析）
        var userContext = buildUserContext(userId);

        return (root, query, cb) -> {
            var allowPredicates = matchedRules.stream()
                    .filter(r -> "allow".equals(r.getEffect()))
                    .map(r -> buildPredicate(r, root, cb, userContext))
                    .filter(p -> p != null)
                    .toList();

            var denyPredicates = matchedRules.stream()
                    .filter(r -> "deny".equals(r.getEffect()))
                    .map(r -> buildPredicate(r, root, cb, userContext))
                    .filter(p -> p != null)
                    .toList();

            // allow 取并集，deny 取反后与 allow 交集
            Predicate result;
            if (allowPredicates.isEmpty()) {
                result = cb.disjunction();
            } else {
                result = cb.or(allowPredicates.toArray(new Predicate[0]));
            }

            for (Predicate deny : denyPredicates) {
                result = cb.and(result, cb.not(deny));
            }

            return result;
        };
    }

    private Set<String> getUserRoleCodes(Long userId) {
        var userRoles = userRoleRepository.findByUserIdAndDeletedFalse(userId);
        var roleIds = userRoles.stream().map(UserRole::getRoleId).toList();
        return roleRepository.findAllById(roleIds).stream()
                .map(r -> r.getCode())
                .collect(Collectors.toSet());
    }

    private boolean matchesRole(DataAccessRule rule, Set<String> userRoleCodes) {
        try {
            List<String> ruleRoles = objectMapper.readValue(rule.getRoles(), new TypeReference<>() {});
            return ruleRoles.stream().anyMatch(userRoleCodes::contains);
        } catch (Exception e) {
            log.warn("解析规则 roles 失败, ruleId={}: {}", rule.getId(), e.getMessage());
            return false;
        }
    }

    private Map<String, Object> buildUserContext(Long userId) {
        // 基础上下文：id 和 orgId（从 BaseEntity 继承）
        return Map.of("id", userId);
    }

    private Predicate buildPredicate(
            DataAccessRule rule,
            jakarta.persistence.criteria.Root<?> root,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            Map<String, Object> userContext) {
        try {
            var condition = objectMapper.readValue(rule.getCondition(), new TypeReference<Map<String, String>>() {});
            var field = condition.get("field");
            var operator = condition.get("operator");
            var value = resolveValue(condition.get("value"), userContext);

            if (field == null || operator == null || value == null) {
                return null;
            }

            return switch (operator) {
                case "eq" -> cb.equal(root.get(field), value);
                case "ne" -> cb.notEqual(root.get(field), value);
                case "gt" -> cb.greaterThan(root.get(field), value.toString());
                case "lt" -> cb.lessThan(root.get(field), value.toString());
                case "in" -> {
                    if (value instanceof List<?> list) {
                        yield root.get(field).in(list);
                    }
                    yield root.get(field).in(value);
                }
                default -> null;
            };
        } catch (Exception e) {
            log.warn("解析规则条件失败, ruleId={}: {}", rule.getId(), e.getMessage());
            return null;
        }
    }

    /** 解析 $user.xxx 表达式为实际值 */
    private Object resolveValue(String rawValue, Map<String, Object> userContext) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue.startsWith("$user.")) {
            var key = rawValue.substring("$user.".length());
            return userContext.get(key);
        }
        // 尝试解析为数字
        try {
            return Long.parseLong(rawValue);
        } catch (NumberFormatException e) {
            return rawValue;
        }
    }

    // ==================== CRUD ====================

    public List<DataAccessRule> list() {
        return ruleRepository.findAll();
    }

    public DataAccessRule getById(Long id) {
        return ruleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "数据权限规则不存在"));
    }

    @Transactional
    public DataAccessRule create(DataAccessRule rule) {
        return ruleRepository.save(rule);
    }

    @Transactional
    public DataAccessRule update(Long id, DataAccessRule updated) {
        var rule = getById(id);
        rule.setEntitySlug(updated.getEntitySlug());
        rule.setRoles(updated.getRoles());
        rule.setCondition(updated.getCondition());
        rule.setEffect(updated.getEffect());
        return ruleRepository.save(rule);
    }

    @Transactional
    public void delete(Long id) {
        ruleRepository.deleteById(id);
    }
}
