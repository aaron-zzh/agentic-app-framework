package com.xuejiai.aaf.framework.net;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 出站请求配置（M39 / M46）。
 *
 * <p>{@code allowed-hosts} 为空表示不做主机白名单、只做网段与协议校验（内网/元数据端点仍然禁止）；
 * 配置后则变为白名单模式，只有列出的主机（支持 {@code *.example.com}）可出站。
 */
@ConfigurationProperties(prefix = "aaf.outbound")
public class OutboundUrlProperties {

    private List<String> allowedHosts = List.of();

    /** 抓取/HTTP 节点响应体上限（字节），默认 10MB，防止无上限响应造成内存 DoS。 */
    private int maxResponseBytes = 10 * 1024 * 1024;

    public List<String> getAllowedHosts() {
        return allowedHosts;
    }

    public void setAllowedHosts(List<String> allowedHosts) {
        this.allowedHosts = allowedHosts == null ? List.of() : allowedHosts;
    }

    public int getMaxResponseBytes() {
        return maxResponseBytes;
    }

    public void setMaxResponseBytes(int maxResponseBytes) {
        this.maxResponseBytes = maxResponseBytes > 0 ? maxResponseBytes : 10 * 1024 * 1024;
    }

    /** 配置入口——与属性类同文件声明，避免为一个属性再建一个空 Configuration 类。 */
    @Configuration
    @EnableConfigurationProperties(OutboundUrlProperties.class)
    public static class OutboundUrlConfig {}
}
