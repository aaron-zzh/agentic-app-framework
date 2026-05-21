package com.xuejiai.aaf.module.system.auth.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.xuejiai.aaf.module.system.auth.vo.SendCodeDTO;

/**
 * 邮件发送端到端测试。
 *
 * <p>默认 @Disabled，手动去掉注解后运行，在真实邮箱中验证邮件样式和内容。
 *
 * <p>运行前在 .env 中配置 SMTP 和收件邮箱：
 *
 * <pre>
 *   MAIL_HOST=smtp.qq.com
 *   MAIL_PORT=465
 *   MAIL_USERNAME=your@qq.com
 *   MAIL_PASSWORD=your-smtp-auth-code
 *   EMAIL_FROM=your@qq.com
 *   TEST_EMAIL=your@qq.com   # 收件邮箱，默认与发件人相同
 * </pre>
 */
@Disabled("手动运行：去掉 @Disabled，在 .env 中配置 TEST_EMAIL 后执行")
@SpringBootTest
@ActiveProfiles("dev")
class AuthServiceEmailE2ETest {

    @Autowired private AuthService authService;

    /** 收件邮箱：优先读 TEST_EMAIL 环境变量，其次用发件人地址 */
    @Value("${TEST_EMAIL:${EMAIL_FROM:}}")
    private String testEmail;

    @Test
    void send_verify_code_email() {
        if (testEmail == null || testEmail.isBlank()) {
            throw new IllegalStateException("请在 .env 中配置 TEST_EMAIL 或 EMAIL_FROM");
        }
        var dto = new SendCodeDTO(testEmail, "register");
        authService.sendCode(dto);
        // 去收件箱查看邮件样式，验证码同时打印在日志中
    }
}
