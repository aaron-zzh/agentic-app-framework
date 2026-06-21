package com.xuejiai.aaf.module.system.auth.captcha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;

/**
 * {@link EsaCaptchaVerifier} 单测。
 *
 * @author AaronZZH &amp; Kiro
 */
class EsaCaptchaVerifierTest {

    @Nested
    @DisplayName("开关关闭：始终通过")
    class WhenDisabled {

        @Test
        @DisplayName("param 为 null 也通过")
        void shouldPassWhenParamIsNull() {
            EsaCaptchaVerifier verifier =
                    new EsaCaptchaVerifier(new EsaCaptchaProperties(false, "header-required"));

            verifier.verify(null, "login");
            // 无异常即通过
        }

        @Test
        @DisplayName("param 空串也通过")
        void shouldPassWhenParamIsBlank() {
            EsaCaptchaVerifier verifier =
                    new EsaCaptchaVerifier(new EsaCaptchaProperties(false, "header-required"));

            verifier.verify("", "send-email-code");
            verifier.verify("  ", "send-sms-code");
        }
    }

    @Nested
    @DisplayName("开关开启 + header-required 模式")
    class WhenEnabledHeaderRequired {

        private final EsaCaptchaVerifier verifier =
                new EsaCaptchaVerifier(new EsaCaptchaProperties(true, "header-required"));

        @Test
        @DisplayName("param 为 null 抛 FORBIDDEN")
        void shouldRejectNullParam() {
            assertThatThrownBy(() -> verifier.verify(null, "login"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(GlobalErrorCode.FORBIDDEN.code());
        }

        @Test
        @DisplayName("param 为空串抛 FORBIDDEN")
        void shouldRejectBlankParam() {
            assertThatThrownBy(() -> verifier.verify("   ", "register"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("param 非空通过")
        void shouldPassWithNonBlankParam() {
            verifier.verify("some-token-value", "send-sms-code");
            // 无异常即通过
        }
    }

    @Nested
    @DisplayName("默认值与未知模式回退")
    class DefaultsAndFallback {

        @Test
        @DisplayName("传 null 走默认：enabled=false, mode=header-required")
        void shouldApplyDefaults() {
            EsaCaptchaProperties props = new EsaCaptchaProperties(null, null);

            assertThat(props.enabled()).isFalse();
            assertThat(props.mode()).isEqualTo("header-required");
        }

        @Test
        @DisplayName("未知 mode 回退到 header-required")
        void unknownModeFallsBackToHeaderRequired() {
            EsaCaptchaVerifier verifier =
                    new EsaCaptchaVerifier(new EsaCaptchaProperties(true, "unknown-mode"));

            // 未知 mode + param 为空 → 仍应拒绝（fallback 到 header-required）
            assertThatThrownBy(() -> verifier.verify(null, "login"))
                    .isInstanceOf(BusinessException.class);
        }
    }
}
