package com.xuejiai.aaf.module.system.license.service;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.security.license.LicenseFeature;
import com.xuejiai.aaf.framework.security.license.LicenseIdentityService;
import com.xuejiai.aaf.module.system.license.vo.LicenseIssueDTO;
import com.xuejiai.aaf.module.system.license.vo.LicenseIssueVO;
import com.xuejiai.aaf.module.system.log.service.AuditLogService;

/** 官方 license.jwt 签发服务。 */
@Service
public class LicenseIssueService {

    private final String privateKeyPem;
    private final String issuer;
    private final LicenseIdentityService identityService;
    private final AuditLogService auditLogService;

    public LicenseIssueService(
            @Value("${aaf.license.signing.private-key:}") String privateKeyValue,
            @Value("${aaf.license.signing.private-key-file:}") String privateKeyFile,
            @Value("${aaf.license.signing.issuer:aaf.xuejiai.com}") String issuer,
            LicenseIdentityService identityService,
            AuditLogService auditLogService) {
        // 优先使用文件路径，其次使用直接配置的 PEM 内容
        this.privateKeyPem = resolvePrivateKey(privateKeyFile, privateKeyValue);
        this.issuer = issuer;
        this.identityService = identityService;
        this.auditLogService = auditLogService;
    }

    private static String resolvePrivateKey(String filePath, String pemValue) {
        if (filePath != null && !filePath.isBlank()) {
            try {
                var path =
                        java.nio.file.Path.of(
                                filePath.replace("~", System.getProperty("user.home")));
                return java.nio.file.Files.readString(path);
            } catch (Exception e) {
                throw new IllegalStateException("读取 license 私钥文件失败: " + filePath, e);
            }
        }
        return pemValue;
    }

    public LicenseIssueVO issue(LicenseIssueDTO dto) {
        if (privateKeyPem == null || privateKeyPem.isBlank()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "未配置 license 签发私钥");
        }
        try {
            var subject = resolveSubject(dto.subject());
            var features = normalizeFeatures(dto.features());
            var expiresAt = dto.expiresAt();
            var builder =
                    new JWTClaimsSet.Builder()
                            .issuer(issuer)
                            .subject(subject)
                            .issueTime(Date.from(Instant.now()))
                            .expirationTime(Date.from(expiresAt))
                            .claim("tier", dto.tier())
                            .claim("features", features.stream().toList());
            if (dto.org() != null && !dto.org().isBlank()) {
                builder.claim("org", dto.org());
            }
            var signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), builder.build());
            signedJwt.sign(new RSASSASigner(parsePrivateKey(privateKeyPem)));
            auditLogService.record(
                    "license", 0L, "ISSUE", auditChanges(subject, dto.tier(), features));
            return new LicenseIssueVO(
                    signedJwt.serialize(), subject, dto.tier(), features, expiresAt);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "license 签发失败，请检查私钥配置");
        }
    }

    private String auditChanges(String subject, String tier, Set<String> features) {
        var featureJson =
                features.stream().map(this::jsonString).collect(Collectors.joining(",", "[", "]"));
        return """
                {"subject":%s,"tier":%s,"features":%s}
                """
                .formatted(jsonString(subject), jsonString(tier), featureJson);
    }

    private String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private Set<String> normalizeFeatures(Set<String> features) {
        if (features == null) {
            return Set.of();
        }
        var normalized =
                features.stream()
                        .filter(v -> v != null && !v.isBlank())
                        .map(String::trim)
                        .collect(Collectors.toUnmodifiableSet());
        var unknown =
                normalized.stream()
                        .filter(v -> !LicenseFeature.isKnown(v))
                        .collect(Collectors.toUnmodifiableSet());
        if (!unknown.isEmpty()) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "features 只能包含已登记的高级模块：" + LicenseFeature.codes());
        }
        return normalized;
    }

    private String resolveSubject(String subject) {
        if (subject == null || subject.isBlank()) {
            return identityService.generate();
        }
        if (!identityService.isValid(subject)) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "license user_id 格式不合法，请留空自动生成");
        }
        return subject;
    }

    private RSAPrivateKey parsePrivateKey(String pem) throws Exception {
        var normalized =
                pem.replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replaceAll("\\s", "");
        var decoded = Base64.getDecoder().decode(normalized);
        var spec = new PKCS8EncodedKeySpec(decoded);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
    }
}
