package com.xuejiai.aaf.common.util;

import org.lionsoul.ip2region.xdb.LongByteArray;
import org.lionsoul.ip2region.xdb.Searcher;
import org.lionsoul.ip2region.xdb.Version;

import com.xuejiai.aaf.common.util.area.Area;
import com.xuejiai.aaf.common.util.area.AreaUtils;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * IP 归属地工具类。
 *
 * <p>基于 ip2region.xdb，启动时全量加载到内存，查询耗时 < 1ms。
 */
@Slf4j
@UtilityClass
public class IpUtils {

    private static Searcher SEARCHER;

    static {
        init();
    }

    private static void init() {
        try {
            long start = System.currentTimeMillis();
            try (var is = IpUtils.class.getClassLoader().getResourceAsStream("ip2region_v4.xdb")) {
                if (is == null) throw new IllegalStateException("ip2region_v4.xdb 未找到");
                var buf = new LongByteArray();
                buf.append(is.readAllBytes());
                SEARCHER = Searcher.newWithBuffer(Version.IPv4, buf);
            }
            log.info("IpUtils 初始化完成，耗时 {} ms", System.currentTimeMillis() - start);
        } catch (Exception e) {
            throw new RuntimeException("IpUtils 初始化失败", e);
        }
    }

    /**
     * 判断是否为内网 / 回环 / 链路本地等本地 IP，需要在调用 ip2region 前前置识别，避免走查询拿到 {@code "0|0|内网IP|内网IP"} 这类非数字 region
     * 字符串。
     *
     * <p>覆盖范围：
     *
     * <ul>
     *   <li>IPv4 回环：{@code 127.x.x.x}
     *   <li>IPv4 私有：{@code 10.x.x.x} / {@code 192.168.x.x} / {@code 172.16-31.x.x}
     *   <li>IPv6 回环：{@code ::1} / {@code 0:0:0:0:0:0:0:1}
     *   <li>未知占位：null / 空 / unknown
     * </ul>
     */
    public static boolean isInternalIp(String ip) {
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) return true;
        String trimmed = ip.trim();
        if (trimmed.startsWith("127.")
                || trimmed.startsWith("10.")
                || trimmed.startsWith("192.168.")
                || trimmed.equals("0:0:0:0:0:0:0:1")
                || trimmed.equals("::1")) {
            return true;
        }
        // 172.16.0.0 - 172.31.255.255
        if (trimmed.startsWith("172.")) {
            try {
                int second = Integer.parseInt(trimmed.split("\\.")[1]);
                if (second >= 16 && second <= 31) return true;
            } catch (Exception ignored) {
                // 格式异常按非内网处理
            }
        }
        return false;
    }

    /**
     * 查询 IP 对应的区域 ID。
     *
     * <p>项目用的 ip2region xdb 返回 4 段 region 字符串：{@code country|province|city|isp}（如 {@code
     * "中国|山东省|济南市|联通"}）。本方法解析后按「省+市」反查 {@link AreaUtils#findCity}，得到 area.csv 中对应的 id。
     *
     * <p>非中国 IP / 内网 IP / xdb 返回非预期格式时返回 null。
     *
     * @param ip IPv4 地址，如 "114.114.114.114"
     * @return 区域 ID（对应 area.csv 中的 id 列），未识别或非中国 IP 返回 null
     */
    public static Integer getAreaId(String ip) {
        if (isInternalIp(ip)) {
            log.debug("IP 为内网/未知，跳过 ip2region 查询: ip={}", ip);
            return null;
        }
        String result;
        try {
            result = SEARCHER.search(ip.trim());
        } catch (Exception e) {
            log.warn("IP 查询异常: ip={}", ip, e);
            return null;
        }
        if (result == null || result.isBlank()) return null;

        // ip2region xdb 返回 country|province|city|isp 格式（4 段）。
        // 兼容旧自定义版本：纯数字 areaId
        if (result.indexOf('|') < 0) {
            try {
                return Integer.parseInt(result);
            } catch (NumberFormatException e) {
                log.debug("IP region 格式未知: ip={}, region={}", ip, result);
                return null;
            }
        }

        String[] parts = result.split("\\|", -1);
        String country = parts.length > 0 ? parts[0].trim() : "";
        String province = parts.length > 1 ? parts[1].trim() : "";
        String city = parts.length > 2 ? parts[2].trim() : "";

        // 仅中国大陆 IP 能查到 area.csv（高德数据来源决定）
        if (!"中国".equals(country)) {
            log.debug("IP 非中国大陆，无 area 数据: ip={}, region={}", ip, result);
            return null;
        }

        var area = AreaUtils.findCity(province, city);
        if (area == null) {
            log.debug("IP 省/市名未匹配 area.csv: ip={}, province={}, city={}", ip, province, city);
            return null;
        }
        return area.getId();
    }

    /**
     * 查询 IP 对应的区域对象。
     *
     * @param ip IPv4 地址
     * @return 区域对象，查询失败返回 null
     */
    public static Area getArea(String ip) {
        var id = getAreaId(ip);
        return id != null ? AreaUtils.getArea(id) : null;
    }

    /**
     * 查询 IP 对应的可读地址，如 "广东 深圳市 南山区"。
     *
     * @param ip IPv4 地址
     * @return 格式化地址，查询失败返回 null
     */
    public static String getAreaName(String ip) {
        var id = getAreaId(ip);
        return id != null ? AreaUtils.format(id) : null;
    }

    /**
     * 查询 IP 对应行政区中心点经纬度。
     *
     * <p>从匹配的 Area 节点开始向上找祖先，取首个含坐标的节点（区县→市→省→国家）。 国外 IP 或无坐标数据时返回 null（"全球"/"中国"根节点未配置坐标，不会作为兜底）。
     *
     * @param ip IPv4 地址
     * @return [longitude, latitude]，无法定位返回 null
     */
    public static double[] getCoordinate(String ip) {
        var area = getArea(ip);
        while (area != null) {
            if (area.getLongitude() != null && area.getLatitude() != null) {
                return new double[] {area.getLongitude(), area.getLatitude()};
            }
            area = area.getParent();
        }
        return null;
    }
}
