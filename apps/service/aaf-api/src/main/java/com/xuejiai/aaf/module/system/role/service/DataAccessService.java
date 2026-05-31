package com.xuejiai.aaf.module.system.role.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.security.access.PermissionVersionService;
import com.xuejiai.aaf.framework.security.access.RecordRuleSupport;
import com.xuejiai.aaf.module.system.role.domain.DataAccessRule;
import com.xuejiai.aaf.module.system.role.domain.UserRole;
import com.xuejiai.aaf.module.system.role.repository.DataAccessRuleRepository;
import com.xuejiai.aaf.module.system.role.repository.RoleRepository;
import com.xuejiai.aaf.module.system.role.repository.UserRoleRepository;
import com.xuejiai.aaf.module.system.role.vo.DataAccessRuleCreateDTO;
import com.xuejiai.aaf.module.system.role.vo.DataAccessRulePageParam;
import com.xuejiai.aaf.module.system.role.vo.DataAccessRuleVO;
import com.xuejiai.aaf.module.system.user.repository.UserRepository;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 行级数据权限服务。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DataAccessService
        extends BaseCrudService<
                DataAccessRule,
                DataAccessRuleVO,
                DataAccessRuleCreateDTO,
                DataAccessRuleCreateDTO,
                DataAccessRulePageParam>
        implements RecordRuleSupport {

    private final DataAccessRuleRepository ruleRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final DataAccessRuleCache ruleCache;
    private final PermissionVersionService versionService;

    @Override
    protected JpaRepository<DataAccessRule, Long> getRepository() {
        return ruleRepository;
    }

    @Override
    protected JpaSpecificationExecutor<DataAccessRule> getSpecExecutor() {
        return ruleRepository;
    }

    @Override
    protected DataAccessRuleVO toVO(DataAccessRule rule) {
        return new DataAccessRuleVO(
                rule.getId(),
                rule.getEntitySlug(),
                rule.getRoles(),
                rule.getCondition(),
                rule.getEffect(),
                rule.getCreateTime());
    }

    @Override
    protected DataAccessRule toEntity(DataAccessRuleCreateDTO dto) {
        var rule = new DataAccessRule();
        applyDTO(rule, dto);
        return rule;
    }

    @Override
    protected void updateEntity(DataAccessRule rule, DataAccessRuleCreateDTO dto) {
        applyDTO(rule, dto);
    }

    @Override
    protected Specification<DataAccessRule> buildSpec(DataAccessRulePageParam request) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (request.getEntitySlug() != null && !request.getEntitySlug().isBlank()) {
                predicates.add(cb.equal(root.get("entitySlug"), request.getEntitySlug().trim()));
            }
            if (request.getEffect() != null && !request.getEffect().isBlank()) {
                predicates.add(cb.equal(root.get("effect"), request.getEffect().trim()));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    @Override
    protected String entityName() {
        return "数据权限规则";
    }

    @Override
    protected String entitySlug() {
        return "data-access-rule";
    }

    @Override
    protected String permissionCode(String action) {
        return "system:data-access-rule:manage";
    }

    /**
     * 构建行级数据权限 Specification。
     *
     * <p>实体完全没有配置规则时返回 null，调用方跳过 L3；实体存在规则但当前用户角色无匹配规则时返回 1=0，
     * 让列表为空、详情/更新/删除统一表现为 404。
     */
    @Override
    public <T> Specification<T> buildAccessSpec(String entitySlug, Long userId) {
        if (entitySlug == null || entitySlug.isBlank() || userId == null) {
            return null;
        }

        var userRoleCodes = getUserRoleCodes(userId);
        if (isSuperAdmin(userRoleCodes)) {
            return null;
        }

        var version = accessVersion(entitySlug, userId);
        var cachedDomain = ruleCache.get(entitySlug, userId, version);
        if (DataAccessRuleCache.NO_RULE.equals(cachedDomain)) {
            return null;
        }
        if (cachedDomain == null) {
            cachedDomain = buildNormalizedDomain(entitySlug, userRoleCodes);
            ruleCache.put(
                    entitySlug,
                    userId,
                    version,
                    cachedDomain == null ? DataAccessRuleCache.NO_RULE : cachedDomain);
        }
        if (cachedDomain == null || DataAccessRuleCache.NO_RULE.equals(cachedDomain)) {
            return null;
        }

        var userContext = buildUserContext(userId);
        return (root, query, cb) -> {
            try {
                var predicate =
                        buildPredicate(objectMapper.readTree(cachedDomain), root, cb, userContext);
                return predicate == null ? cb.disjunction() : predicate;
            } catch (Exception e) {
                log.warn("解析缓存的数据权限规则失败, entitySlug={}: {}", entitySlug, e.getMessage());
                return cb.disjunction();
            }
        };
    }

    @Override
    public String accessVersion(String entitySlug, Long userId) {
        if (entitySlug == null || entitySlug.isBlank() || userId == null) {
            return "0";
        }
        var roleCodes = getUserRoleCodes(userId).stream().sorted().toList();
        var ruleVersion =
                ruleRepository.findByEntitySlugAndDeletedFalse(entitySlug).stream()
                        .map(DataAccessRule::getVersion)
                        .filter(java.util.Objects::nonNull)
                        .max(Integer::compareTo)
                        .orElse(0);
        return versionService.ruleVersion(entitySlug) + ":" + ruleVersion + ":" + roleCodes.hashCode();
    }

    /** @deprecated 请使用 {@link #buildAccessSpec(String, Long)}，名称更准确地表达行级数据权限语义。 */
    @Deprecated(forRemoval = false)
    public <T> Specification<T> buildSpecification(String entitySlug, Long userId) {
        return buildAccessSpec(entitySlug, userId);
    }

    private Set<String> getUserRoleCodes(Long userId) {
        var userRoles = userRoleRepository.findByUserIdAndDeletedFalse(userId);
        var roleIds = userRoles.stream().map(UserRole::getRoleId).toList();
        return roleRepository.findAllById(roleIds).stream()
                .map(r -> r.getCode())
                .collect(Collectors.toSet());
    }

    private boolean isSuperAdmin(Set<String> roleCodes) {
        return roleCodes.stream()
                .map(code -> code == null ? "" : code.trim())
                .anyMatch(code -> "SUPER_ADMIN".equalsIgnoreCase(code) || "super_admin".equals(code));
    }

    private boolean matchesRole(DataAccessRule rule, Set<String> userRoleCodes) {
        try {
            List<String> ruleRoles =
                    objectMapper.readValue(rule.getRoles(), new TypeReference<>() {});
            return ruleRoles.stream().anyMatch(userRoleCodes::contains);
        } catch (Exception e) {
            log.warn("解析规则 roles 失败, ruleId={}: {}", rule.getId(), e.getMessage());
            return false;
        }
    }

    private String buildNormalizedDomain(String entitySlug, Set<String> userRoleCodes) {
        var rules = ruleRepository.findByEntitySlugAndDeletedFalse(entitySlug);
        if (rules.isEmpty()) {
            return null;
        }

        var matchedRules = rules.stream().filter(rule -> matchesRole(rule, userRoleCodes)).toList();
        if (matchedRules.isEmpty()) {
            return "{\"deny_all\":true}";
        }

        var allowNodes = new java.util.ArrayList<JsonNode>();
        var denyNodes = new java.util.ArrayList<JsonNode>();
        for (var rule : matchedRules) {
            try {
                var node = objectMapper.readTree(rule.getCondition());
                if ("deny".equals(rule.getEffect())) {
                    denyNodes.add(node);
                } else {
                    allowNodes.add(node);
                }
            } catch (Exception e) {
                log.warn("解析规则条件失败, ruleId={}: {}", rule.getId(), e.getMessage());
            }
        }
        if (allowNodes.isEmpty()) {
            return "{\"deny_all\":true}";
        }

        var root = objectMapper.createObjectNode();
        var and = objectMapper.createArrayNode();
        var or = objectMapper.createObjectNode();
        var allow = objectMapper.createArrayNode();
        allowNodes.forEach(allow::add);
        or.set("or", allow);
        and.add(or);
        denyNodes.forEach(
                deny -> {
                    var not = objectMapper.createObjectNode();
                    not.set("not", deny);
                    and.add(not);
                });
        root.set("and", and);
        return root.toString();
    }

    private Map<String, Object> buildUserContext(Long userId) {
        var user = userRepository.findById(userId).orElse(null);
        var context = new java.util.HashMap<String, Object>();
        context.put("id", userId);
        context.put("orgId", user == null ? null : user.getOrgId());
        context.put("workspaceId", user == null ? null : user.getWorkspaceId());
        context.put("teamIds", List.of());
        return context;
    }

    private Predicate buildPredicate(
            DataAccessRule rule,
            Root<?> root,
            CriteriaBuilder cb,
            Map<String, Object> userContext) {
        try {
            return buildPredicate(objectMapper.readTree(rule.getCondition()), root, cb, userContext);
        } catch (Exception e) {
            log.warn("解析规则条件失败, ruleId={}: {}", rule.getId(), e.getMessage());
            return null;
        }
    }

    private Predicate buildPredicate(
            JsonNode node, Root<?> root, CriteriaBuilder cb, Map<String, Object> userContext) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.has("deny_all")) {
            return cb.disjunction();
        }
        if (node.has("and")) {
            var predicates = childPredicates(node.get("and"), root, cb, userContext);
            return predicates.isEmpty() ? null : cb.and(predicates.toArray(new Predicate[0]));
        }
        if (node.has("or")) {
            var predicates = childPredicates(node.get("or"), root, cb, userContext);
            return predicates.isEmpty() ? null : cb.or(predicates.toArray(new Predicate[0]));
        }
        if (node.has("not")) {
            var predicate = buildPredicate(node.get("not"), root, cb, userContext);
            return predicate == null ? null : cb.not(predicate);
        }
        return buildLeafPredicate(node, root, cb, userContext);
    }

    private List<Predicate> childPredicates(
            JsonNode nodes, Root<?> root, CriteriaBuilder cb, Map<String, Object> userContext) {
        if (nodes == null || !nodes.isArray()) {
            return List.of();
        }
        var predicates = new java.util.ArrayList<Predicate>();
        nodes.forEach(
                child -> {
                    var predicate = buildPredicate(child, root, cb, userContext);
                    if (predicate != null) {
                        predicates.add(predicate);
                    }
                });
        return predicates;
    }

    private Predicate buildLeafPredicate(
            JsonNode node, Root<?> root, CriteriaBuilder cb, Map<String, Object> userContext) {
        var field = text(node, "field");
        var op = text(node, "op");
        if (op == null) {
            op = text(node, "operator");
        }
        if (field == null || op == null || !node.has("value")) {
            return null;
        }

        Path<?> path = root.get(field);
        var value = resolveValue(node.get("value"), userContext, path.getJavaType());
        if (value == null) {
            return null;
        }

        return switch (op) {
            case "eq" -> cb.equal(path, value);
            case "ne" -> cb.notEqual(path, value);
            case "gt" -> compare(path, value, cb, true);
            case "lt" -> compare(path, value, cb, false);
            case "in" -> value instanceof Iterable<?> values ? path.in(values) : path.in(value);
            case "like" -> cb.like(path.as(String.class), "%" + value + "%");
            default -> null;
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Predicate compare(Path<?> path, Object value, CriteriaBuilder cb, boolean greaterThan) {
        if (!(value instanceof Comparable comparable)) {
            return null;
        }
        var expression = path.as((Class<? extends Comparable>) value.getClass());
        return greaterThan ? cb.greaterThan(expression, comparable) : cb.lessThan(expression, comparable);
    }

    private String text(JsonNode node, String fieldName) {
        var value = node.get(fieldName);
        return value == null || value.isNull() ? null : value.asText();
    }

    /** 解析 $user.xxx 表达式为实际值，并按字段类型做基础转换。 */
    private Object resolveValue(JsonNode valueNode, Map<String, Object> userContext, Class<?> targetType) {
        if (valueNode == null || valueNode.isNull()) {
            return null;
        }
        if (valueNode.isArray()) {
            var values = new java.util.ArrayList<>();
            valueNode.forEach(value -> values.add(resolveValue(value, userContext, targetType)));
            return values;
        }
        if (valueNode.isTextual() && valueNode.asText().startsWith("$user.")) {
            var key = valueNode.asText().substring("$user.".length());
            return userContext.get(key);
        }
        if (Long.class.equals(targetType) || long.class.equals(targetType)) {
            return valueNode.asLong();
        }
        if (Integer.class.equals(targetType) || int.class.equals(targetType)) {
            return valueNode.asInt();
        }
        if (Boolean.class.equals(targetType) || boolean.class.equals(targetType)) {
            return valueNode.asBoolean();
        }
        return valueNode.isTextual() ? valueNode.asText() : valueNode.toString();
    }

    private void applyDTO(DataAccessRule rule, DataAccessRuleCreateDTO dto) {
        rule.setEntitySlug(dto.entitySlug());
        rule.setRoles(dto.roles());
        rule.setCondition(dto.condition());
        rule.setEffect(dto.effect() != null ? dto.effect() : "allow");
    }

    @Override
    @Transactional
    public DataAccessRuleVO create(DataAccessRuleCreateDTO request) {
        var vo = super.create(request);
        versionService.bumpRuleVersion(request.entitySlug());
        ruleCache.evictEntity(request.entitySlug());
        return vo;
    }

    @Override
    @Transactional
    public DataAccessRuleVO update(Long id, DataAccessRuleCreateDTO request) {
        var vo = super.update(id, request);
        versionService.bumpRuleVersion(request.entitySlug());
        ruleCache.evictEntity(request.entitySlug());
        return vo;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        var entity = requireEntity(id);
        super.delete(id);
        versionService.bumpRuleVersion(entity.getEntitySlug());
        ruleCache.evictEntity(entity.getEntitySlug());
    }
}
