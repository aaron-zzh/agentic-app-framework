package com.xuejiai.aaf.framework.intelligent.agent.application;

import java.util.Locale;
import java.util.Map;

import com.xuejiai.aaf.framework.intelligent.agent.model.AuthorizationGrant;
import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolCatalogPort.ToolDefinition;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolParameterPolicyPort;

/** 工具参数的系统级敏感字段与 tenant 边界策略。 */
public final class DefaultToolParameterPolicy implements ToolParameterPolicyPort {

    @Override
    public void validate(
            ToolDefinition definition,
            Map<String, Object> arguments,
            InvocationContext context,
            AuthorizationGrant grant) {
        arguments.forEach((key, value) -> validateEntry(key, value, context));
        if (!definition.readOnly() && grant == null) {
            throw new IllegalStateException("写工具必须绑定持久任务授权: " + definition.ref().name());
        }
        if (grant != null && !grant.tenantId().equals(context.tenantId())) {
            throw new IllegalStateException("授权 tenant 与调用 tenant 不一致");
        }
    }

    private static void validateEntry(String key, Object value, InvocationContext context) {
        var normalized = key.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        if (normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("accesstoken")
                || normalized.contains("refreshtoken")
                || normalized.contains("apikey")
                || normalized.equals("credential")
                || normalized.equals("credentialhandle")) {
            throw new IllegalArgumentException("工具参数禁止携带凭证明文或凭证句柄: " + key);
        }
        if ("tenantid".equals(normalized)
                && value != null
                && !context.tenantId().value().equals(value.toString())) {
            throw new IllegalArgumentException("工具参数不能越过当前 tenant");
        }
        if (value instanceof Map<?, ?> nested) {
            nested.forEach(
                    (nestedKey, nestedValue) ->
                            validateEntry(nestedKey.toString(), nestedValue, context));
        } else if (value instanceof Iterable<?> values) {
            values.forEach(item -> validateNestedValue(item, context));
        }
    }

    private static void validateNestedValue(Object value, InvocationContext context) {
        if (value instanceof Map<?, ?> nested) {
            nested.forEach(
                    (key, nestedValue) -> validateEntry(key.toString(), nestedValue, context));
        } else if (value instanceof Iterable<?> values) {
            values.forEach(item -> validateNestedValue(item, context));
        }
    }
}
