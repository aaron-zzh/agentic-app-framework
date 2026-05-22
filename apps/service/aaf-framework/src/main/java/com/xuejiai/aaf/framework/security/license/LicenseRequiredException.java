package com.xuejiai.aaf.framework.security.license;

/** 访问高级功能但未授权时抛出。 */
public class LicenseRequiredException extends RuntimeException {

    private final String featureName;
    private final String upgradeUrl;

    public LicenseRequiredException(String featureName, String upgradeUrl) {
        super("此功能需要 Premium 授权：" + featureName + "，升级请访问：" + upgradeUrl);
        this.featureName = featureName;
        this.upgradeUrl = upgradeUrl;
    }

    public String getFeatureName() {
        return featureName;
    }

    public String getUpgradeUrl() {
        return upgradeUrl;
    }
}
