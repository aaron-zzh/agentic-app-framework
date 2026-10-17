package com.xuejiai.aaf.module.device.mqtt;

import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.springframework.stereotype.Service;

/** Ed25519 指令签名服务。私钥使用 PKCS#8、公钥使用 X.509 的 Base64 编码。 */
@Service
public class DeviceCommandSigningService {

    private final MqttConnectionConfig config;

    public DeviceCommandSigningService(MqttConnectionConfig config) {
        this.config = config;
    }

    public String keyId() {
        return config.signing().keyId();
    }

    public SignedValue sign(byte[] canonicalPayload) {
        try {
            var privateKeyValue = requireConfigured(config.signing().privateKey(), "签名私钥");
            var keyFactory = KeyFactory.getInstance("Ed25519");
            var privateKey =
                    keyFactory.generatePrivate(
                            new PKCS8EncodedKeySpec(decodeConfigured(privateKeyValue)));
            var signature = Signature.getInstance("Ed25519");
            signature.initSign(privateKey);
            signature.update(canonicalPayload);
            return new SignedValue(
                    config.signing().keyId(),
                    "Ed25519",
                    Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign()));
        } catch (Exception exception) {
            throw new IllegalStateException("Ed25519 指令签名失败", exception);
        }
    }

    /** 返回配对/简报接口可下发的 X.509 Base64 公钥，不返回私钥。 */
    public PublicKeyView publicKey() {
        try {
            var configured = requireConfigured(config.signing().publicKey(), "签名公钥");
            var publicKey =
                    KeyFactory.getInstance("Ed25519")
                            .generatePublic(new X509EncodedKeySpec(decodeConfigured(configured)));
            return new PublicKeyView(
                    config.signing().keyId(),
                    "Ed25519",
                    Base64.getEncoder().encodeToString(publicKey.getEncoded()));
        } catch (Exception exception) {
            throw new IllegalStateException("Ed25519 签名公钥不可用", exception);
        }
    }

    private static byte[] decodeConfigured(String value) {
        var normalized =
                value.replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replace("-----BEGIN PUBLIC KEY-----", "")
                        .replace("-----END PUBLIC KEY-----", "")
                        .replaceAll("\\s", "");
        return Base64.getDecoder().decode(normalized);
    }

    private static String requireConfigured(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(label + "未配置");
        }
        return value;
    }

    public record SignedValue(String keyId, String algorithm, String signature) {}

    public record PublicKeyView(String keyId, String algorithm, String publicKey) {}
}
