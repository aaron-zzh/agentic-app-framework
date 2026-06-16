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

    LicenseLoader() {
        this(new LicenseIdentityService(new LicenseIdentityProperties()));
    }

    @Autowired
    public LicenseLoader(LicenseIdentityService identityService) {
        this.identityService = identityService;
    }

    // RSA 2048 公钥（与 BootstrapLicenseTool 生成的密钥对配套，运行工具后更新）
    static final String PUBLIC_KEY_PEM =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqJ22Rvvmw7B3z7U5vFKb"
                    + "MZm2Pf0ioN2HhrtI0u69huBL32twjglO9faCBDA3X/sCTc7aZ5qBDVl5f6UYT8iK"
                    + "7+2QhFTuIRjLKG4x9maMJqwE4SGf8VPcAmxjXCmg4mlMnb7ulejL7H/mqqWeM9QX"
                    + "nJTuOo2Ds+9ISjUEfHv9TfA71oTPAQUch7otyaIOGWuANutni/Dz7X8Ng82ovfu9"
                    + "0kiHtPeWXnOWF+6YvQ7OL5eAyCg0qldFXVJJDnrtLTsbzZFGOCx51CxmTP9+rH0f"
                    + "8d493P6yjihbbPy9DS4qIBpv8uqSB/3M46v1S/5yA3V9QfOpenFqiif9gTi0+lmvg"
                    + "QIDAQAB";

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
            var publicKey = parsePublicKey(PUBLIC_KEY_PEM);
            var verifier = new RSASSAVerifier(publicKey);

            if (!signedJWT.verify(verifier)) {
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
}
