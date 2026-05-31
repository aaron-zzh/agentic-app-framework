package com.xuejiai.aaf.framework.security.license;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** License 标识派生配置。 */
@ConfigurationProperties(prefix = "aaf.license.identity")
public class LicenseIdentityProperties {

    /** user_id 前缀。 */
    private String prefix = "aaf_";

    /** 校验段派生盐。官方服务应使用私有配置覆盖。 */
    private String checksumSalt = "aaf-license-user:v1:";

    /** 功能耦合 seed 派生盐。官方服务应使用私有配置覆盖。 */
    private String seedSalt = "aaf-license-seed:v1:";

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getChecksumSalt() {
        return checksumSalt;
    }

    public void setChecksumSalt(String checksumSalt) {
        this.checksumSalt = checksumSalt;
    }

    public String getSeedSalt() {
        return seedSalt;
    }

    public void setSeedSalt(String seedSalt) {
        this.seedSalt = seedSalt;
    }
}
