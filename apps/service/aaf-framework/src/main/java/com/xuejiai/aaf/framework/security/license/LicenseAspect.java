package com.xuejiai.aaf.framework.security.license;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/** 拦截 @PremiumRequired 方法，未授权时抛出 LicenseRequiredException。 */
@Aspect
@Component
public class LicenseAspect {

    private static final String UPGRADE_URL = "https://aaf.xuejiai.com/pricing";

    @Around("@annotation(premiumRequired)")
    public Object checkLicense(ProceedingJoinPoint pjp, PremiumRequired premiumRequired)
            throws Throwable {
        if (!License.get().isPremium()) {
            throw new LicenseRequiredException(premiumRequired.value(), UPGRADE_URL);
        }
        return pjp.proceed();
    }
}
