package com.xuejiai.aaf.module.device.mqtt;

import java.time.ZoneOffset;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.module.device.domain.Device;
import com.xuejiai.aaf.module.device.domain.DeviceMessage;

import tools.jackson.databind.node.ObjectNode;

/** 下行设备信封工厂。签名覆盖除 integrity.signature 外的全部字段。 */
@Component
public class DeviceDownlinkEnvelopeFactory {

    private static final String SPEC_VERSION = "1.0";

    private final Rfc8785Canonicalizer canonicalizer;
    private final DeviceCommandSigningService signingService;

    public DeviceDownlinkEnvelopeFactory(
            Rfc8785Canonicalizer canonicalizer, DeviceCommandSigningService signingService) {
        this.canonicalizer = canonicalizer;
        this.signingService = signingService;
    }

    public byte[] create(Device device, DeviceMessage message) {
        var root = JsonUtils.createObjectNode();
        root.put("specVersion", SPEC_VERSION);
        root.put("messageId", message.getMessageId());
        root.put("messageType", message.getDirection());
        root.put("commandId", message.getCommandId());
        putOptional(root, "correlationId", message.getCorrelationId());
        putOptional(root, "traceId", message.getTraceId());
        root.put("targetDeviceKey", device.getDeviceKey());
        root.put("issuedAt", message.getIssuedAt().toInstant(ZoneOffset.UTC).toString());
        root.put("expiresAt", message.getExpiresAt().toInstant(ZoneOffset.UTC).toString());
        root.put("sequence", message.getSequenceNo());

        var command = JsonUtils.createObjectNode();
        command.put("capability", message.getCapability());
        command.put("action", message.getAction());
        command.put("schemaVersion", 1);
        command.set("arguments", JsonUtils.readTreeStrict(message.getPayload()));
        root.set("command", command);

        var governance = JsonUtils.createObjectNode();
        governance.putNull("policyDecisionId");
        governance.put("riskLevel", "LOW");
        governance.putNull("approvalGrantId");
        root.set("governance", governance);

        var integrity = JsonUtils.createObjectNode();
        integrity.put("keyId", signingService.keyId());
        integrity.put("algorithm", "Ed25519");
        root.set("integrity", integrity);

        var signed = signingService.sign(canonicalizer.canonicalize(root));
        integrity.put("signature", signed.signature());
        return canonicalizer.canonicalize(root);
    }

    private static void putOptional(ObjectNode node, String field, String value) {
        if (value == null || value.isBlank()) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }
}
