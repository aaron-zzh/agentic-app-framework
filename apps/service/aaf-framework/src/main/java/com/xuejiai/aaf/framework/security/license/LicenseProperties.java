package com.xuejiai.aaf.framework.security.license;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * License 验签配置（M32）。
 *
 * <p>修复前：验签公钥硬编码在 {@code LicenseLoader.PUBLIC_KEY_PEM}，无法按环境配置或轮换，也无法静态确认 各环境使用独立信任根——一份泄漏的私钥即可为所有环境签发许可。
 *
 * <p>现在：公钥经配置注入，支持多把公钥并存以完成轮换（新旧公钥同时受信，待旧许可自然过期后移除旧值）。 生产环境必须显式配置，未配置时启动即失败（见 {@code
 * LicenseLoader#resolveTrustedKeys}）。
 */
@ConfigurationProperties(prefix = "aaf.license")
public class LicenseProperties {

    /**
     * 受信公钥列表（Base64 X.509，不含 PEM 头尾）。
     *
     * <p>支持配置多把以支持轮换：验签依次尝试，任一通过即受信。
     */
    private List<String> publicKeys = List.of();

    public List<String> getPublicKeys() {
        return publicKeys;
    }

    public void setPublicKeys(List<String> publicKeys) {
        this.publicKeys = publicKeys == null ? List.of() : publicKeys;
    }
}
