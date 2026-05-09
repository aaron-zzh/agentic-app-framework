package com.xuejiai.aaf.module.system.util;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** 工具方法：生成 BCrypt 密码哈希，用于初始化数据。 */
class PasswordEncoderTest {

    @Test
    void generateBcryptHash() {
        var encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode("admin");
        System.out.println("BCrypt hash for 'admin': " + hash);
    }
}
