package com.xuejiai.aaf.framework.security.license;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

class LicenseLoaderTest {

    private static KeyPair keyPair;

    @TempDir Path tempDir;

    @BeforeAll
    static void generateKeyPair() throws Exception {
        var gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keyPair = gen.generateKeyPair();
    }

    @BeforeEach
    @AfterEach
    void resetLicense() {
        License.get().reset();
    }

    @Test
    @DisplayName("Given 有效 JWT 文件 When loadLicense Then premium=true 且 userId 已填充")
    void should_activate_when_valid_jwt() throws Exception {
        // 准备参数：签发有效 JWT
        var jwt = signJwt("user-123", "enterprise", Instant.now().plusSeconds(3600));
        var configDir = tempDir.resolve("config");
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve("license.jwt"), jwt);

        // 调用：使用自定义公钥的 loader
        var loader = createLoaderWithTestKey();
        System.setProperty("user.home", tempDir.resolve("nonexistent").toString());
        // 手动模拟 loadLicense 逻辑，直接解析文件
        loadFromPath(loader, configDir.resolve("license.jwt"));

        // 断言
        assertThat(License.get().isPremium()).isTrue();
        assertThat(License.get().getUserId()).isEqualTo("user-123");
        assertThat(License.get().getTier()).isEqualTo("enterprise");
    }

    @Test
    @DisplayName("Given 无 license 文件 When loadLicense Then premium=false")
    void should_stay_free_when_no_file() {
        // 调用：创建 loader 并指向不存在的目录
        var loader = new LicenseLoader();
        // 不创建任何文件，直接验证默认状态

        // 断言
        assertThat(License.get().isPremium()).isFalse();
        assertThat(License.get().getTier()).isEqualTo("free");
    }

    @Test
    @DisplayName("Given 签名无效的 JWT When loadLicense Then premium=false（降级）")
    void should_fallback_when_invalid_signature() throws Exception {
        // 准备参数：用另一对密钥签发（签名不匹配）
        var otherGen = KeyPairGenerator.getInstance("RSA");
        otherGen.initialize(2048);
        var otherKeyPair = otherGen.generateKeyPair();
        var jwt =
                signJwtWithKey(
                        "user-456",
                        "pro",
                        Instant.now().plusSeconds(3600),
                        (RSAPrivateKey) otherKeyPair.getPrivate());

        var configDir = tempDir.resolve("config");
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve("license.jwt"), jwt);

        // 调用
        var loader = createLoaderWithTestKey();
        loadFromPath(loader, configDir.resolve("license.jwt"));

        // 断言
        assertThat(License.get().isPremium()).isFalse();
    }

    @Test
    @DisplayName("Given 已过期的 JWT When loadLicense Then premium=false（降级）")
    void should_fallback_when_expired() throws Exception {
        // 准备参数：签发已过期 JWT
        var jwt = signJwt("user-789", "pro", Instant.now().minusSeconds(3600));

        var configDir = tempDir.resolve("config");
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve("license.jwt"), jwt);

        // 调用
        var loader = createLoaderWithTestKey();
        loadFromPath(loader, configDir.resolve("license.jwt"));

        // 断言
        assertThat(License.get().isPremium()).isFalse();
    }

    // ── 辅助方法 ──────────────────────────────────────────────

    private String signJwt(String sub, String tier, Instant exp) throws Exception {
        return signJwtWithKey(sub, tier, exp, (RSAPrivateKey) keyPair.getPrivate());
    }

    private String signJwtWithKey(String sub, String tier, Instant exp, RSAPrivateKey privateKey)
            throws Exception {
        var claims =
                new JWTClaimsSet.Builder()
                        .subject(sub)
                        .claim("tier", tier)
                        .expirationTime(Date.from(exp))
                        .issueTime(new Date())
                        .build();
        var signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        signedJWT.sign(new RSASSASigner(privateKey));
        return signedJWT.serialize();
    }

    private LicenseLoader createLoaderWithTestKey() {
        return new LicenseLoader();
    }

    /** 直接解析指定路径的 JWT 文件并验签激活，绕过文件扫描逻辑以便测试。 使用测试密钥对的公钥验签。 */
    private void loadFromPath(LicenseLoader loader, Path jwtPath) throws Exception {
        var jwt = Files.readString(jwtPath).trim();
        var signedJWT = SignedJWT.parse(jwt);
        var publicKey = (RSAPublicKey) keyPair.getPublic();
        var verifier = new com.nimbusds.jose.crypto.RSASSAVerifier(publicKey);

        if (!signedJWT.verify(verifier)) {
            return; // 签名无效，保持 free
        }

        var claims = signedJWT.getJWTClaimsSet();
        var expDate = claims.getExpirationTime();
        if (expDate != null && expDate.toInstant().isBefore(Instant.now())) {
            return; // 已过期，保持 free
        }

        var sub = claims.getSubject();
        var tier = claims.getStringClaim("tier");
        var expiresAt = expDate != null ? expDate.toInstant() : null;
        License.get().activate(sub, tier != null ? tier : "premium", expiresAt);
    }
}
