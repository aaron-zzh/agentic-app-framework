package com.xuejiai.aaf.common.util;

import org.lionsoul.ip2region.xdb.Searcher;

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
            var is = IpUtils.class.getClassLoader().getResourceAsStream("ip2region_v4.xdb");
            if (is == null) throw new IllegalStateException("ip2region_v4.xdb 未找到");
            var cBuff = new org.lionsoul.ip2region.xdb.LongByteArray(is.readAllBytes());
            SEARCHER = Searcher.newWithBuffer(org.lionsoul.ip2region.xdb.Version.IPv4, cBuff);
            log.info("IpUtils 初始化完成，耗时 {} ms", System.currentTimeMillis() - start);
        } catch (Exception e) {
            throw new RuntimeException("IpUtils 初始化失败", e);
        }
    }

    /**
     * 查询 IP 对应的区域 ID。
     *
     * @param ip IPv4 地址，如 "114.114.114.114"
     * @return 区域 ID（对应 area.csv 中的 id 列），查询失败返回 null
     */
    public static Integer getAreaId(String ip) {
        try {
            return Integer.parseInt(SEARCHER.search(ip.trim()));
        } catch (Exception e) {
            log.warn("IP 查询失败: {}", ip, e);
            return null;
        }
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
}
