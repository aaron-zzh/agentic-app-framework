package com.xuejiai.aaf.framework.security.license;

import org.springframework.stereotype.Component;

/** 根据授权状态动态返回配置参数。 */
@Component
public class LicenseAwareConfig {

    /** Premium: 8192，免费: 2048。 */
    public int getMaxTokens() {
        return License.get().isPremium() ? 8192 : 2048;
    }

    /** Premium: 20，免费: 3。 */
    public int getMaxConcurrentAgents() {
        return License.get().isPremium() ? 20 : 3;
    }
}
