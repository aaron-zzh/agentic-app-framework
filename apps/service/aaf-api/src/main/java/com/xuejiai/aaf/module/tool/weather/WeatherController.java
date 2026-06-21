package com.xuejiai.aaf.module.tool.weather;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.common.util.IpUtils;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.common.util.area.Area;
import com.xuejiai.aaf.common.util.area.AreaUtils;
import com.xuejiai.aaf.module.tool.weather.vo.CaiyunWeatherResponse;
import com.xuejiai.aaf.module.tool.weather.vo.WeatherByIpResult;

import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 天气查询 REST 接口——供前端页面直接调用。
 *
 * <p>统一使用项目标准 {@link Result} 包装，前端通过 {@code backendApi} 直连。
 * 综合接口反序列化为强类型 {@link CaiyunWeatherResponse}（对照彩云 v2.6 文档完整建模）；
 * 行政区划接口字段较少且与天气主链路无关，暂用 {@link JsonNode} 直传。
 */
@Slf4j
@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final CaiyunWeatherClient weatherClient;

    /**
     * 综合天气查询（实况 + 逐小时 + 逐日 + 预警）。
     *
     * @param longitude 经度，如 116.3883
     * @param latitude 纬度，如 39.9289
     * @param dailysteps 天级预报天数，默认 7
     * @param hourlysteps 小时预报小时数，默认 24
     */
    @GetMapping
    public Result<CaiyunWeatherResponse> weather(
            @RequestParam double longitude,
            @RequestParam double latitude,
            @RequestParam(defaultValue = "7") int dailysteps,
            @RequestParam(defaultValue = "24") int hourlysteps) {
        String json = weatherClient.weather(longitude, latitude, dailysteps, hourlysteps);
        return Result.success(JsonUtils.parseObject(json, CaiyunWeatherResponse.class));
    }

    /**
     * 按客户端 IP 自动定位查天气。
     *
     * <p>用于免授权场景（用户首次进页面、或拒绝浏览器 geolocation 时降级）。
     * 链路：HTTP IP → ip2region → Area → 中心点经纬度 → 彩云综合接口。
     *
     * <p>返回结构封装了「IP / 可读地址 / 经纬度 / 天气」四要素，前端无需再调 {@code /location}
     * 做逆地理编码。
     *
     * @param ip 可选，指定 IP（调试用），不传则取 X-Forwarded-For / X-Real-IP / RemoteAddr
     * @param request 用于获取真实客户端 IP
     */
    @GetMapping("/by-ip")
    public Result<WeatherByIpResult> weatherByIp(
            @RequestParam(required = false) String ip, HttpServletRequest request) {
        String resolveIp = (ip != null && !ip.isBlank()) ? ip.trim() : getClientIp(request);

        if (IpUtils.isInternalIp(resolveIp)) {
            log.info("[Weather] 跳过内网 IP 定位: ip={}", resolveIp);
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST,
                    "IP（" + resolveIp + "）为内网地址，请手动选择城市或传 ip 参数");
        }

        Integer areaId = IpUtils.getAreaId(resolveIp);
        Area area = areaId != null ? AreaUtils.getArea(areaId) : null;
        double[] coord = IpUtils.getCoordinate(resolveIp);
        if (coord == null) {
            log.info("[Weather] IP 无法定位: ip={}", resolveIp);
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST,
                    "无法根据 IP 定位（IP=" + resolveIp + "），请手动选择城市或输入坐标");
        }
        String json = weatherClient.weather(coord[0], coord[1], 7, 24);
        CaiyunWeatherResponse weather = JsonUtils.parseObject(json, CaiyunWeatherResponse.class);
        String location = area != null ? AreaUtils.format(area.getId()) : null;
        return Result.success(
                new WeatherByIpResult(resolveIp, location, coord[0], coord[1], weather));
    }

    /**
     * 行政区划查询（经纬度 → 省市区），仅支持中国大陆。
     *
     * @param longitude 经度
     * @param latitude 纬度
     */
    @GetMapping("/location")
    public Result<JsonNode> location(
            @RequestParam double longitude, @RequestParam double latitude) {
        String json = weatherClient.reverseAdmins(longitude, latitude);
        return Result.success(JsonUtils.readTree(json));
    }

    // ─── 私有工具 ────────────────────────────────────────────────────────────

    /**
     * 获取客户端真实 IP，优先读 {@code X-Forwarded-For}（反向代理场景）。
     *
     * <p>注：项目中 AuthController / AafAguiRestController 等多处也有相同模式，待协调者评估抽到 IpUtils。
     */
    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank()) return xri.trim();
        return request.getRemoteAddr();
    }
}
