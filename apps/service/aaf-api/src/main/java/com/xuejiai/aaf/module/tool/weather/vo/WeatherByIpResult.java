package com.xuejiai.aaf.module.tool.weather.vo;

/**
 * IP 定位查天气的返回结构。
 *
 * <p>包含定位过程的中间产物（ip / location / 经纬度）+ 最终天气数据，方便前端一次拿全所需信息，
 * 无需再单独调 {@code /weather/location} 做逆地理编码。
 *
 * @param ip 实际用于定位的客户端 IP（来自 query 参数 / X-Forwarded-For / RemoteAddr）
 * @param location 可读地址，如 "山东 济南市"，无法解析时为 null
 * @param longitude 经度（行政区中心点）
 * @param latitude 纬度（行政区中心点）
 * @param weather 彩云综合接口完整响应
 */
public record WeatherByIpResult(
        String ip,
        String location,
        Double longitude,
        Double latitude,
        CaiyunWeatherResponse weather) {}
