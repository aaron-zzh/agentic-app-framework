package com.xuejiai.aaf.framework.security.license;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class LicenseAspectTest extends BaseMockitoUnitTest {

    @Mock private ProceedingJoinPoint pjp;
    @Mock private PremiumRequired premiumRequired;

    private final LicenseAspect aspect = new LicenseAspect();

    @BeforeEach
    @AfterEach
    void resetLicense() {
        License.get().reset();
    }

    @Test
    @DisplayName("Given premium=true When 调用 @PremiumRequired 方法 Then 正常执行")
    void should_proceed_when_premium() throws Throwable {
        // 准备参数
        License.get().activate("user-1", "pro", Instant.now().plusSeconds(3600));
        when(pjp.proceed()).thenReturn("result");

        // 调用
        var result = aspect.checkLicense(pjp, premiumRequired);

        // 断言
        assertThat(result).isEqualTo("result");
        verify(pjp).proceed();
    }

    @Test
    @DisplayName("Given premium=false When 调用 @PremiumRequired 方法 Then 抛出 LicenseRequiredException")
    void should_throw_when_not_premium() {
        // mock 方法
        when(premiumRequired.value()).thenReturn("知识图谱");

        // 调用 + 断言
        assertThatThrownBy(() -> aspect.checkLicense(pjp, premiumRequired))
                .isInstanceOf(LicenseRequiredException.class)
                .hasMessageContaining("知识图谱")
                .extracting("upgradeUrl")
                .isEqualTo("https://aaf.xuejiai.com/pricing");
    }
}
