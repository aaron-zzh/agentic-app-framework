package com.xuejiai.aaf.framework.security.access;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 默认功能开关检查器——全部放行。业务层可提供自定义实现覆盖。
 *
 * @author AaronZZH & Kiro
 */
@Component
@ConditionalOnMissingBean(FeatureToggleChecker.class)
public class DefaultFeatureToggleChecker implements FeatureToggleChecker {

    @Override
    public boolean isEnabled(String feature) {
        return true;
    }
}
