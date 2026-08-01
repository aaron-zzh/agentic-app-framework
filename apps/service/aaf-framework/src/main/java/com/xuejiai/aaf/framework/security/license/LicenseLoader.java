package com.xuejiai.aaf.framework.security.license;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/** 启动时加载 license.jwt 文件，验签后激活 License 单例。 */
@Component
public class LicenseLoader {

    private static final Logger log = LoggerFactory.getLogger(LicenseLoader.class);

    private final LicenseIdentityService identityService;
    private final LicenseProperties licenseProperties;
    private final org.springframework.core.env.Environment environment;

    LicenseLoader() {
        this(
                new LicenseIdentityService(new LicenseIdentityProperties()),
                new LicenseProperties(),
                new org.springframework.core.env.StandardEnvironment());
    }

    @Autowired
    public LicenseLoader(
            LicenseIdentityService identityService,
            LicenseProperties licenseProperties,
            org.springframework.core.env.Environment environment) {
        this.identityService = identityService;
        this.licenseProperties = licenseProperties;
        this.environment = environment;
    }

    /**
     * 开发/测试用内置公钥（与 {@code BootstrapLicenseTool} 生成的密钥对配套）。
     *
     * <p>M32：仅作为**非生产**兜底信任根；生产环境必须通过 {@code aaf.license.public-keys} 显式配置， 否则启动失败（见 {@link
     * #resolveTrustedKeys()}）。
     */
    static final String DEV_PUBLIC_KEY_PEM =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAjX7kNbyPNeOOYFkdoDo7"
                    + "lnaUMaBALdWj/58m3FWNUpITcwgzTt2A645zsDy0RFFAk0/xs3+/Xv0c2LTvl6SR"
                    + "syxaOdmR+tCPLh03OiR2pOsYvi0PdyJDKIYWWiEyrTDteoJ/J1XTT4dkEV7yEmJL"
                    + "YtgbawpqSJeCqWA0CmqhXzJesNzdSm+VMcjajQ4lsy5hQgx/wk4hOz8iUhah41KU"
                    + "GqRLGTCqxzqh93dqvzQvyYlVGth7xYVz7kSZZiOEa0CcXVkfwjAmIkJdfm/SyfXY"
                    + "F54KapJyveC+Ejv16LRKY7wixHOZYQ6jwqt1B3SUVIql9UgU/PfR0JFhv08I4YJQ"
                    + "oQIDAQAB";

    @EventListener(ApplicationStartedEvent.class)
    public void loadLicense() {
        var homePath = Path.of(System.getProperty("user.home"), ".aaf", "license.jwt");
        var configPath = Path.of("./config/license.jwt");

        String jwt = readFile(homePath);
        if (jwt == null) {
            jwt = readFile(configPath);
        }
        if (jwt == null) {
            log.info("License not found, running in free mode");
            return;
        }

        try {
            var signedJWT = SignedJWT.parse(jwt.trim());
            // M32：依次尝试受信公钥（支持轮换期新旧并存），全部不通过才判为无效
            if (!verifyWithTrustedKeys(signedJWT)) {
                log.warn("Invalid or expired license, falling back to free mode");
                return;
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            var exp = claims.getExpirationTime();
            if (exp != null && exp.toInstant().isBefore(Instant.now())) {
                log.warn("Invalid or expired license, falling back to free mode");
                return;
            }

            var sub = claims.getSubject();
            var tier = claims.getStringClaim("tier");
            var features = normalizeFeatures(claims.getStringListClaim("features"));
            var expiresAt = exp != null ? exp.toInstant() : null;

            License.get()
                    .activate(
                            sub,
                            tier != null ? tier : "premium",
                            expiresAt,
                            identityService,
                            features);
            log.info("License loaded: tier={}, features={}", tier, features);
        } catch (Exception e) {
            log.warn("Invalid or expired license, falling back to free mode");
        }
    }

    private Set<String> normalizeFeatures(List<String> features) {
        if (features == null) {
            return Set.of();
        }
        return features.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .collect(Collectors.toUnmodifiableSet());
    }

    private String readFile(Path path) {
        try {
            if (Files.exists(path)) {
                return Files.readString(path);
            }
        } catch (IOException e) {
            // 忽略读取异常，降级处理
        }
        return null;
    }

    static RSAPublicKey parsePublicKey(String base64) throws Exception {
        var decoded = Base64.getDecoder().decode(base64);
        var spec = new X509EncodedKeySpec(decoded);
        var keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) keyFactory.generatePublic(spec);
    }

    /** M32：用全部受信公钥依次验签，任一通过即受信（轮换期新旧公钥并存）。 */
    private boolean verifyWithTrustedKeys(SignedJWT signedJWT) {
        for (var pem : resolveTrustedKeys()) {
            try {
                if (signedJWT.verify(new RSASSAVerifier(parsePublicKey(pem)))) {
                    return true;
                }
            } catch (Exception e) {
                log.warn("License 公钥不可用，跳过该信任根: {}", e.getMessage());
            }
        }
        return false;
    }

    /**
     * M32：解析受信公钥列表。
     *
     * <p>配置了 {@code aaf.license.public-keys} 即以配置为准；未配置时：
     *
     * <ul>
     *   <li>生产 Profile → 直接抛异常终止启动，避免生产误用内置开发信任根
     *   <li>非生产 → 使用内置开发公钥并打印警告
     * </ul>
     */
    private List<String> resolveTrustedKeys() {
        var configured = licenseProperties.getPublicKeys();
        if (configured != null && !configured.isEmpty()) {
            return configured;
        }
        if (List.of(environment.getActiveProfiles()).contains("prod")) {
            throw new IllegalStateException(
                    "生产环境必须显式配置 aaf.license.public-keys，禁止使用内置开发公钥作为许可证信任根");
        }
        log.warn("未配置 aaf.license.public-keys，当前使用内置开发公钥（仅限非生产环境）");
        return List.of(DEV_PUBLIC_KEY_PEM);
    }
}
