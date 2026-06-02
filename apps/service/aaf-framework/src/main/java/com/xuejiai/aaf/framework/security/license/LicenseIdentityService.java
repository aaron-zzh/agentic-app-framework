package com.xuejiai.aaf.framework.security.license;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

/** License 用户标识与耦合 seed 派生服务。 */
@Service
public class LicenseIdentityService {

    private final LicenseIdentityProperties properties;
    private final SecureRandom random = new SecureRandom();

    public LicenseIdentityService(LicenseIdentityProperties properties) {
        this.properties = properties;
    }

    public String generate() {
        var bytes = new byte[8];
        random.nextBytes(bytes);
        var body = HexFormat.of().formatHex(bytes);
        return properties.getPrefix() + body + "_" + checksum(body);
    }

    public boolean isValid(String userId) {
        if (userId == null) {
            return false;
        }
        var pattern =
                Pattern.compile(
                        "^" + Pattern.quote(properties.getPrefix()) + "[0-9a-f]{16}_[0-9a-f]{8}$");
        if (!pattern.matcher(userId).matches()) {
            return false;
        }
        var bodyStart = properties.getPrefix().length();
        var body = userId.substring(bodyStart, bodyStart + 16);
        var checksum = userId.substring(bodyStart + 17);
        return checksum(body).equals(checksum);
    }

    public long couplingSeed(String userId) {
        if (!isValid(userId)) {
            return 0L;
        }
        var digest = sha256(properties.getSeedSalt() + userId);
        long seed = 0L;
        for (int i = 0; i < Long.BYTES; i++) {
            seed = (seed << 8) | (digest[i] & 0xffL);
        }
        return seed;
    }

    private String checksum(String body) {
        return HexFormat.of().formatHex(sha256(properties.getChecksumSalt() + body), 0, 4);
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
