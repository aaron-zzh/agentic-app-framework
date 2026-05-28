package com.xuejiai.aaf.framework.security.access;

/**
 * 功能开关检查器——框架层 SPI，由业务层实现。
 *
 * @author AaronZZH & Kiro
 */
public interface FeatureToggleChecker {

    /**
     * 检查指定功能是否启用。
     *
     * @param feature 功能标识
     * @return true=已启用
     */
    boolean isEnabled(String feature);
}
