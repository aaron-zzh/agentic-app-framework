package com.xuejiai.aaf.module.system.auth.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.xuejiai.aaf.module.system.auth.vo.SendCodeDTO;

/**
 * 邮件发送端到端测试。
 *
 * <p>默认 @Disabled，手动去掉注解后运行，在真实邮箱中验证邮件样式和内容。
 *
 * <p>运行前确保 .env 或环境变量中配置了真实 SMTP：
 * <pre>
 *   MAIL_HOST=smtp.qq.com
 *   MAIL_PORT=465
 *   MAIL_USERNAME=your@qq.com
 *   MAIL_PASSWORD=your-smtp-auth-code
 *   EMAIL_FROM=your@qq.com
 * </pre>
 */
@Disabled("手动运行：去掉 @Disabled，填写收件邮箱后执行")
@SpringBootTest
@ActiveProfiles("dev")
class AuthServiceEmailE2ETest {

    @Autowired
    private AuthService authService;

    @Test
    void send_verify_code_email() {
        // ⚠️ 修改为你自己的邮箱
        var dto = new SendCodeDTO("your@email.com", "register");
        authService.sendCode(dto);
        // 去收件箱查看邮件样式，验证码同时打印在日志中
    }
}
