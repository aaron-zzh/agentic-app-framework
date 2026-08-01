package com.xuejiai.aaf.framework.net;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;

/**
 * 出站 URL 防护（M39 / M46）——SSRF 统一校验入口。
 *
 * <p>背景：工作流 {@code HttpNode} 的 url 来自流程变量、知识库抓取的 url 来自用户导入，两处都直接发起请求， 可打内网服务与云元数据端点（{@code
 * 169.254.169.254}）。此前各自无任何校验。
 *
 * <p>校验顺序：协议 → URL 内嵌凭证 → 主机白名单（配置了才生效） → DNS 解析后逐个 IP 判定网段。 解析后判定可拦住"域名指向内网 IP"（DNS rebinding
 * 的静态形态）。
 *
 * <p>只做一件事：判断某个出站 URL 是否允许。两个调用方共用，不各写一套。
 */
@Component
public class OutboundUrlGuard {

    private static final Logger log = LoggerFactory.getLogger(OutboundUrlGuard.class);

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    /** 云厂商元数据端点——即使部署在允许出网的网络里也一律禁止 */
    private static final Set<String> BLOCKED_HOSTS =
            Set.of(
                    "169.254.169.254",
                    "metadata.google.internal",
                    "metadata.goog",
                    "100.100.100.200");

    private final OutboundUrlProperties properties;

    public OutboundUrlGuard(OutboundUrlProperties properties) {
        this.properties = properties;
    }

    /**
     * 校验出站 URL，不通过直接抛业务异常。
     *
     * @param url 待校验 URL
     * @param purpose 用途描述，仅用于日志与错误信息（如 "工作流 HTTP 节点"）
     */
    public void check(String url, String purpose) {
        if (url == null || url.isBlank()) {
            throw reject(purpose, "URL 为空");
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            throw reject(purpose, "URL 格式非法");
        }
        var scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!ALLOWED_SCHEMES.contains(scheme)) {
            throw reject(purpose, "仅允许 http/https，实际: " + scheme);
        }
        if (uri.getUserInfo() != null) {
            // 形如 http://user:pass@host/ 常用于绕过基于前缀的检查
            throw reject(purpose, "URL 不允许内嵌凭证");
        }
        var host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw reject(purpose, "URL 缺少主机名");
        }
        var normalizedHost = host.toLowerCase(Locale.ROOT);
        if (BLOCKED_HOSTS.contains(normalizedHost)) {
            throw reject(purpose, "禁止访问元数据端点: " + host);
        }
        if (!properties.getAllowedHosts().isEmpty() && !matchesAllowList(normalizedHost)) {
            throw reject(purpose, "主机不在出站白名单内: " + host);
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(normalizedHost);
        } catch (UnknownHostException e) {
            throw reject(purpose, "主机无法解析: " + host);
        }
        for (var address : addresses) {
            if (isBlockedAddress(address)) {
                log.warn(
                        "[OutboundUrlGuard] 拒绝内网/保留地址出站: purpose={}, host={}, ip={}",
                        purpose,
                        host,
                        address.getHostAddress());
                throw reject(purpose, "禁止访问内网或保留地址");
            }
        }
    }

    /** 校验并返回是否允许（供批量场景跳过单条而非整体失败）。 */
    public boolean isAllowed(String url, String purpose) {
        try {
            check(url, purpose);
            return true;
        } catch (BusinessException e) {
            return false;
        }
    }

    private boolean matchesAllowList(String host) {
        for (var allowed : properties.getAllowedHosts()) {
            var pattern = allowed.toLowerCase(Locale.ROOT).trim();
            if (pattern.isEmpty()) continue;
            // 支持 *.example.com 形式的子域匹配
            if (pattern.startsWith("*.")) {
                var suffix = pattern.substring(1);
                if (host.endsWith(suffix)) return true;
            } else if (host.equals(pattern)) {
                return true;
            }
        }
        return false;
    }

    /** 环回/链路本地/私网/组播/任意地址一律拒绝；IPv6 unique-local 同理。 */
    private boolean isBlockedAddress(InetAddress address) {
        if (address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isAnyLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        var bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            // 100.64.0.0/10 运营商级 NAT、198.18.0.0/15 基准测试、0.0.0.0/8
            if (first == 0) return true;
            if (first == 100 && second >= 64 && second <= 127) return true;
            if (first == 198 && (second == 18 || second == 19)) return true;
            // 127/8 已由 isLoopbackAddress 覆盖；169.254/16 由 isLinkLocalAddress 覆盖
        } else if (bytes.length == 16) {
            int first = bytes[0] & 0xFF;
            // fc00::/7 unique-local
            if ((first & 0xFE) == 0xFC) return true;
        }
        return false;
    }

    private BusinessException reject(String purpose, String reason) {
        return new BusinessException(
                GlobalErrorCode.BAD_REQUEST, "%s 出站请求被拒绝：%s".formatted(purpose, reason));
    }

    /** 供无法注入 Bean 的场景使用的默认实例（无白名单，仅网段与协议校验）。 */
    public static OutboundUrlGuard withDefaults() {
        return new OutboundUrlGuard(new OutboundUrlProperties());
    }

    /** 当前配置的白名单（只读），便于日志与自检。 */
    public List<String> allowedHosts() {
        return properties.getAllowedHosts();
    }
}
