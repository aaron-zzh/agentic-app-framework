package com.xuejiai.aaf.framework.security.license;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/** 拦截 @PremiumRequired 类或方法，未授权时抛出 LicenseRequiredException。 */
@Aspect
@Component
public class LicenseAspect {

    @Around("@annotation(premiumRequired)")
    public Object checkLicense(ProceedingJoinPoint pjp, PremiumRequired premiumRequired)
            throws Throwable {
        if (!License.get().isPremium()) {
            throw new LicenseRequiredException(premiumRequired.value(), LicensePortal.UPGRADE_URL);
        }
        return pjp.proceed();
    }

    @Around(
            "@within(premiumRequired) && !@annotation(com.xuejiai.aaf.framework.security.license.PremiumRequired)")
    public Object checkClassLicense(ProceedingJoinPoint pjp, PremiumRequired premiumRequired)
            throws Throwable {
        if (!License.get().isPremium()) {
            throw new LicenseRequiredException(premiumRequired.value(), LicensePortal.UPGRADE_URL);
        }
        return pjp.proceed();
    }

    @Around("@annotation(ownerRequired)")
    public Object checkOwnerLicense(ProceedingJoinPoint pjp, LicenseOwnerRequired ownerRequired)
            throws Throwable {
        if (!License.get().isOwner()) {
            throw new LicenseRequiredException(
                    ownerRequired.value(), LicensePortal.UPGRADE_URL, "官方 owner 授权");
        }
        return pjp.proceed();
    }

    @Around(
            "@within(ownerRequired) && !@annotation(com.xuejiai.aaf.framework.security.license.LicenseOwnerRequired)")
    public Object checkClassOwnerLicense(ProceedingJoinPoint pjp, LicenseOwnerRequired ownerRequired)
            throws Throwable {
        if (!License.get().isOwner()) {
            throw new LicenseRequiredException(
                    ownerRequired.value(), LicensePortal.UPGRADE_URL, "官方 owner 授权");
        }
        return pjp.proceed();
    }

    @Around("@annotation(featureRequired)")
    public Object checkFeatureLicense(ProceedingJoinPoint pjp, FeatureRequired featureRequired)
            throws Throwable {
        if (!License.get().hasFeature(featureRequired.value())) {
            throw new LicenseRequiredException(
                    featureRequired.value(), LicensePortal.UPGRADE_URL, "商业功能授权");
        }
        return pjp.proceed();
    }

    @Around(
            "@within(featureRequired) && !@annotation(com.xuejiai.aaf.framework.security.license.FeatureRequired)")
    public Object checkClassFeatureLicense(ProceedingJoinPoint pjp, FeatureRequired featureRequired)
            throws Throwable {
        if (!License.get().hasFeature(featureRequired.value())) {
            throw new LicenseRequiredException(
                    featureRequired.value(), LicensePortal.UPGRADE_URL, "商业功能授权");
        }
        return pjp.proceed();
    }
}
