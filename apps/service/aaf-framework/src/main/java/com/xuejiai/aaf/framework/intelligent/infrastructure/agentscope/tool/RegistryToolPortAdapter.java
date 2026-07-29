package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.tool;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.tool.ConnectorToolCallback;
import com.xuejiai.aaf.framework.engine.tool.ToolCatalogEntry;
import com.xuejiai.aaf.framework.engine.tool.ToolCatalogProvider;
import com.xuejiai.aaf.framework.engine.tool.ToolRegistry;
import com.xuejiai.aaf.framework.intelligent.agent.model.ToolRef;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolCatalogPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.core.type.TypeReference;

/** 基于生产工具注册中心和 SQL 目录实现 P2 工具端口。 */
public final class RegistryToolPortAdapter implements ToolCatalogPort, ToolInvocationPort {

    private static final long SUPPORTED_VERSION = 1;
    private static final Set<String> RESERVED_CONNECTOR_PARAMETERS =
            Set.of("credentialhandle", "vaultref", "idempotencykey", "provideridempotencykey");

    private final ToolRegistry registry;
    private final ToolCatalogProvider catalog;

    public RegistryToolPortAdapter(ToolRegistry registry, ToolCatalogProvider catalog) {
        this.registry = Objects.requireNonNull(registry, "registry 不能为空");
        this.catalog = Objects.requireNonNull(catalog, "catalog 不能为空");
    }

    @Override
    public List<ToolDefinition> resolve(List<ToolRef> tools) {
        Objects.requireNonNull(tools, "tools 不能为空");
        return tools.stream().map(this::resolveOne).toList();
    }

    @Override
    public Mono<ToolInvocationResult> invoke(ToolInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation 不能为空");
        return Mono.fromCallable(
                        () -> {
                            var definition = resolveOne(invocation.tool());
                            if (definition.connectorAction()) {
                                throw new IllegalStateException(
                                        "Connector 禁止绕过 ConnectorActionPort 调用");
                            }
                            var callback =
                                    registry.getCallback(invocation.tool().name())
                                            .orElseThrow(
                                                    () ->
                                                            new IllegalStateException(
                                                                    "工具 callback 未注册: "
                                                                            + invocation
                                                                                    .tool()
                                                                                    .name()));
                            var output =
                                    callback.call(JsonUtils.toJsonString(invocation.arguments()));
                            return new ToolInvocationResult(
                                    output,
                                    Map.of(
                                            "toolId", invocation.tool().toolId(),
                                            "toolVersion", invocation.tool().version()));
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private ToolDefinition resolveOne(ToolRef ref) {
        Objects.requireNonNull(ref, "tool ref 不能为空");
        if (ref.version() != SUPPORTED_VERSION) {
            throw new IllegalArgumentException(
                    "仅支持工具定义版本 1: " + ref.toolId() + "@" + ref.version());
        }
        if (!ref.toolId().equals(ref.name())) {
            throw new IllegalArgumentException("P2 工具稳定标识必须等于 callback 名称: " + ref);
        }
        var entry =
                catalog.find(ref.toolId())
                        .orElseThrow(
                                () -> new IllegalArgumentException("工具目录不存在: " + ref.toolId()));
        requireEnabled(entry);
        var callback =
                registry.getCallback(ref.name())
                        .orElseThrow(
                                () -> new IllegalStateException("工具 callback 未注册: " + ref.name()));
        var schema = inputSchema(entry);
        if (connectorAction(entry)) {
            if (!(callback instanceof ConnectorToolCallback)) {
                throw new IllegalStateException(
                        "Connector 必须实现 ConnectorToolCallback: " + entry.toolName());
            }
            validateConnectorSchema(entry, schema);
        }
        return new ToolDefinition(
                ref,
                Objects.requireNonNullElse(callback.getToolDefinition().description(), ""),
                schema,
                entry.source(),
                entry.permissionCode(),
                entry.readOnly(),
                entry.reversible(),
                entry.requireConfirm(),
                entry.idempotencyRequired());
    }

    private void requireEnabled(ToolCatalogEntry entry) {
        if (!entry.enabled()) {
            throw new IllegalStateException("工具未启用: " + entry.toolName());
        }
        if (entry.readOnly() && entry.reversible()) {
            throw new IllegalStateException("只读工具不能标记为可撤销写入: " + entry.toolName());
        }
        if (connectorAction(entry) && !entry.readOnly() && !entry.idempotencyRequired()) {
            throw new IllegalStateException(
                    "Connector 写动作必须声明 idempotency_required: " + entry.toolName());
        }
    }

    private void validateConnectorSchema(ToolCatalogEntry entry, Map<String, Object> schema) {
        validateNoReservedProperties(entry.toolName(), schema);
    }

    private void validateNoReservedProperties(String toolName, Object node) {
        if (node instanceof Map<?, ?> map) {
            var properties = map.get("properties");
            if (properties instanceof Map<?, ?> declared) {
                for (var key : declared.keySet()) {
                    var normalized =
                            key.toString().replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
                    if (RESERVED_CONNECTOR_PARAMETERS.contains(normalized)) {
                        throw new IllegalStateException(
                                "Connector schema 禁止模型提供受信参数 " + key + ": " + toolName);
                    }
                }
            }
            map.values().forEach(value -> validateNoReservedProperties(toolName, value));
        } else if (node instanceof List<?> list) {
            list.forEach(value -> validateNoReservedProperties(toolName, value));
        }
    }

    private Map<String, Object> inputSchema(ToolCatalogEntry entry) {
        if (entry.inputSchema() == null || entry.inputSchema().isBlank()) {
            throw new IllegalStateException("工具缺少 input schema: " + entry.toolName());
        }
        var schema =
                JsonUtils.parseObject(
                        entry.inputSchema(), new TypeReference<Map<String, Object>>() {});
        if (schema == null || !"object".equals(schema.get("type"))) {
            throw new IllegalStateException(
                    "工具 input schema 必须是 JSON object schema: " + entry.toolName());
        }
        return schema;
    }

    private static boolean connectorAction(ToolCatalogEntry entry) {
        return "MCP".equalsIgnoreCase(entry.source())
                || "CONNECTOR".equalsIgnoreCase(entry.source());
    }
}
