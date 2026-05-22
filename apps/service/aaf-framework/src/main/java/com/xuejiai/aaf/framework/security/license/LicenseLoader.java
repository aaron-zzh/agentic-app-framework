package com.xuejiai.aaf.framework.security.license;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 启动时加载 license.jwt 文件，验签后激活 License 单例。 */
@Component
public class LicenseLoader {

    private static final Logger log = LoggerFactory.getLogger(LicenseLoader.class);

    // 测试用 RSA 2048 公钥（PEM 格式，生产环境替换）
    static final String PUBLIC_KEY_PEM =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA0Z3VS5JJcds3xfn/ygWe"
                    + "h3XPHiWMp5HqOPgEsXbdDoSMCWfMZjFHBYXnYMhjJTLEqFJBx1caIqPaLUcq3JOZ"
                    + "ZMlMrSvGMbSG5G9bNFPaO0YHzYFmJOaKSi6aFpXJnqHln9ZbGN0fRsMiGMEn0GE"
                    + "FWBV1gOECmXq0UXOS0kFz0MZWB3pLOaQnGCFm0fVSYTHOHVhGYMBh7TkYGMz3E"
                    + "R1HMpifa0nbTNEYCPkBSMa0GbTtGMSoS7BPKRZ9ASl0vd/5bBMEVaNFMnQn8xaO"
                    + "VFpGNjpqTimFhMi0mAHBfnSaOj9AJHfIL0dBMqhHMzBFkWJJdKrGMSt0oeFakVX"
                    + "5wIDAQAB";

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
            var expiresAt = exp != null ? exp.toInstant() : null;

            License.get().activate(sub, tier != null ? tier : "premium", expiresAt);
            log.info("License loaded: premium [{}]", sub);
        } catch (Exception e) {
            log.warn("Invalid or expired license, falling back to free mode");
        }
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
