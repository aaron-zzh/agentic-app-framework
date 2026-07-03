/**
 * 访问策略管理 Service（ABAC 层）。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.module.system.role.policy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.security.access.PermissionVersionService;
import com.xuejiai.aaf.framework.security.access.PolicyEngine;
import com.xuejiai.aaf.framework.security.access.PolicyInput;
import com.xuejiai.aaf.framework.security.access.PolicyResult;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

@Service
@RequiredArgsConstructor
public class AccessPolicyService implements PolicyEngine {

    private final AccessPolicyRepository repository;
    private final PermissionVersionService versionService;
    private final SpelExpressionParser expressionParser = new SpelExpressionParser();
    private final ConcurrentHashMap<String, Expression> expressionCache = new ConcurrentHashMap<>();

    /**
     * 创建策略
     *
     * @param dto 创建请求
     * @return 策略信息
     */
    @Transactional
    public AccessPolicyVO create(AccessPolicyCreateDTO dto) {
        var entity = new AccessPolicy();
        entity.setName(dto.name());
        entity.setDescription(dto.description());
        entity.setConditionJson(dto.conditionJson());
        entity.setEffect(dto.effect());
        entity.setPriority(dto.priority() != null ? dto.priority() : 100);
        entity.setTargetResource(dto.targetResource());
        entity.setTargetAction(dto.targetAction());
        var saved = repository.save(entity);
        versionService.bumpPolicyVersion();
        return toVO(saved);
    }

    /**
     * 更新策略
     *
     * @param id 策略编号
     * @param dto 更新请求
     * @return 更新后的策略信息
     */
    @Transactional
    public AccessPolicyVO update(Long id, AccessPolicyCreateDTO dto) {
        var entity = getEntity(id);
        entity.setName(dto.name());
        entity.setDescription(dto.description());
        entity.setConditionJson(dto.conditionJson());
        entity.setEffect(dto.effect());
        if (dto.priority() != null) entity.setPriority(dto.priority());
        entity.setTargetResource(dto.targetResource());
        entity.setTargetAction(dto.targetAction());
        var saved = repository.save(entity);
        versionService.bumpPolicyVersion();
        return toVO(saved);
    }

    /**
     * 删除策略
     *
     * @param id 策略编号
     */
    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
        versionService.bumpPolicyVersion();
    }

    /**
     * 查询所有启用策略
     *
     * @return 策略列表
     */
    public List<AccessPolicyVO> listEnabled() {
        return repository.findByStatusOrderByPriority(1).stream().map(this::toVO).toList();
    }

    /**
     * 策略测试——评估给定上下文是否通过策略
     *
     * @param dto 测试请求
     * @return 测试结果
     */
    public PolicyTestResultVO test(PolicyTestDTO dto) {
        var policies = repository.findByStatusOrderByPriority(1);
        var matched = new java.util.ArrayList<String>();
        for (var policy : policies) {
            if (!matchesResource(policy, dto.resourceType())
                    || !matchesAction(policy, dto.action())
                    || !matchesCondition(policy, dto.context(), null, policy)) {
                continue;
            }
            matched.add(policy.getName());
            if ("DENY".equalsIgnoreCase(policy.getEffect())) {
                return new PolicyTestResultVO(false, "命中拒绝策略：" + policy.getName(), matched);
            }
        }
        return new PolicyTestResultVO(true, "策略通过", matched);
    }

    @Override
    public PolicyResult evaluate(PolicyInput input) {
        var policies = repository.findByStatusOrderByPriority(1);
        for (var policy : policies) {
            if (!matchesResource(policy, input.objectType())
                    || !matchesAction(policy, input.action())
                    || !matchesCondition(policy, input.attributes(), input, policy)) {
                continue;
            }
            if ("DENY".equalsIgnoreCase(policy.getEffect())) {
                return PolicyResult.deny("命中拒绝策略：" + policy.getName());
            }
            if ("ALLOW".equalsIgnoreCase(policy.getEffect())) {
                return PolicyResult.allow();
            }
        }
        return PolicyResult.allow();
    }

    private AccessPolicy getEntity(Long id) {
        return repository
                .findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "策略不存在"));
    }

    private AccessPolicyVO toVO(AccessPolicy e) {
        return new AccessPolicyVO(
                e.getId(),
                e.getName(),
                e.getDescription(),
                e.getConditionJson(),
                e.getEffect(),
                e.getPriority(),
                e.getTargetResource(),
                e.getTargetAction(),
                e.getStatus());
    }

    private boolean matchesAction(AccessPolicy policy, String action) {
        return policy.getTargetAction() == null
                || policy.getTargetAction().isBlank()
                || policy.getTargetAction().equals(action);
    }

    private boolean matchesResource(AccessPolicy policy, String resourceType) {
        return policy.getTargetResource() == null
                || policy.getTargetResource().isBlank()
                || policy.getTargetResource().equals(resourceType);
    }

    private boolean matchesCondition(
            AccessPolicy policy, Map<String, Object> context, PolicyInput input) {
        if (policy.getConditionJson() == null || policy.getConditionJson().isBlank()) {
            return true;
        }
        try {
            return matchesCondition(policy, context, input, policy);
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean matchesCondition(
            AccessPolicy policy,
            Map<String, Object> context,
            PolicyInput input,
            AccessPolicy cacheOwner) {
        var expression = policy.getConditionJson().trim();
        try {
            if (expression.startsWith("{") || expression.startsWith("[")) {
                return matchesCondition(JsonUtils.readTree(expression), context);
            }
            var cacheKey = versionService.policyVersion() + ":" + cacheOwner.getId();
            var compiled =
                    expressionCache.computeIfAbsent(
                            cacheKey, ignored -> expressionParser.parseExpression(expression));
            var evaluationContext = new StandardEvaluationContext();
            if (context != null) {
                context.forEach(evaluationContext::setVariable);
            }
            if (input != null) {
                evaluationContext.setVariable("operatorId", input.operatorId());
                evaluationContext.setVariable("ownerId", input.ownerId());
                evaluationContext.setVariable("action", input.action());
                evaluationContext.setVariable("objectType", input.objectType());
                evaluationContext.setVariable("objectId", input.objectId());
            }
            return Boolean.TRUE.equals(compiled.getValue(evaluationContext, Boolean.class));
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean matchesCondition(JsonNode node, Map<String, Object> context) {
        if (node == null || node.isNull() || node.isEmpty()) {
            return true;
        }
        if (node.has("and")) {
            for (var child : node.get("and")) {
                if (!matchesCondition(child, context)) {
                    return false;
                }
            }
            return true;
        }
        if (node.has("or")) {
            for (var child : node.get("or")) {
                if (matchesCondition(child, context)) {
                    return true;
                }
            }
            return false;
        }
        if (node.has("not")) {
            return !matchesCondition(node.get("not"), context);
        }
        var field = text(node, "field");
        var op = text(node, "op");
        var expected = node.get("value");
        if (field == null || op == null || expected == null) {
            return false;
        }
        var actual = context == null ? null : context.get(field);
        return switch (op) {
            case "eq" -> actual != null && actual.toString().equals(expected.asString());
            case "ne" -> actual == null || !actual.toString().equals(expected.asString());
            case "in" ->
                    expected.isArray()
                            && java.util.stream.StreamSupport.stream(expected.spliterator(), false)
                                    .anyMatch(
                                            value ->
                                                    actual != null
                                                            && actual.toString()
                                                                    .equals(value.asString()));
            default -> false;
        };
    }

    private String text(JsonNode node, String fieldName) {
        var value = node.get(fieldName);
        return value == null || value.isNull() ? null : value.asString();
    }
}
