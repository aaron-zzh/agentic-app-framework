package com.xuejiai.aaf.framework.engine.tool;

import java.util.Map;

import org.springframework.ai.tool.ToolCallback;

/**
 * 外部 Connector 专用回调。
 *
 * <p>业务参数来自模型；vaultRef 与 idempotencyKey 仅由服务端基础设施注入。写动作实现必须把 idempotencyKey 原样传给上游
 * provider，并保证同键重复调用返回同一业务结果。
 */
public interface ConnectorToolCallback extends ToolCallback {

    String callConnector(
            Map<String, Object> businessArguments, String vaultRef, String idempotencyKey);

    @Override
    default String call(String toolInput) {
        throw new UnsupportedOperationException("Connector 只能通过受信基础设施调用");
    }
}
