package com.xuejiai.aaf.module.system.tools.weather.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.module.system.tools.weather.vo.WeatherVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 天气工具服务：调外部 API（OpenWeatherMap），Redis 缓存 30 分钟，无 key 时降级 mock。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private static final String CACHE_PREFIX = "tools:weather:";

    @Value("${aaf.tools.weather.api-key:}")
    private String apiKey;

    private final StringRedisTemplate redisTemplate;
    private final RestClient restClient = RestClient.builder().build();

    public WeatherVO getWeather(String city) {
        var cacheKey = CACHE_PREFIX + city;
        // 先读缓存
        var cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return JsonUtils.parseObject(cached, WeatherVO.class);
            } catch (Exception e) {
                log.warn("解析缓存天气数据失败，重新拉取");
            }
        }

        var vo = fetchWeather(city);
        // 写缓存
        try {
            redisTemplate
                    .opsForValue()
                    .set(cacheKey, JsonUtils.toJsonString(vo), CACHE_TTL);
        } catch (Exception e) {
            log.warn("写入天气缓存失败：{}", e.getMessage());
        }
        return vo;
    }

    private WeatherVO fetchWeather(String city) {
        if (apiKey == null || apiKey.isBlank()) {
            return mockWeather(city);
        }
        try {
            @SuppressWarnings("unchecked")
            var resp =
                    restClient
                            .get()
                            .uri(
                                    uriBuilder ->
                                            uriBuilder
                                                    .scheme("https")
                                                    .host("api.openweathermap.org")
                                                    .path("/data/2.5/weather")
                                                    .queryParam("q", city)
                                                    .queryParam("appid", apiKey)
                                                    .queryParam("units", "metric")
                                                    .queryParam("lang", "zh_cn")
                                                    .build())
                            .retrieve()
                            .body(Map.class);
            return parseOWM(city, resp);
        } catch (Exception ex) {
            log.warn("天气接口调用失败，降级 mock：city={}, err={}", city, ex.getMessage());
            return mockWeather(city);
        }
    }

    @SuppressWarnings("unchecked")
    private WeatherVO parseOWM(String city, Map<?, ?> resp) {
        var main = (Map<String, Object>) resp.get("main");
        var weatherArr = (List<Map<String, Object>>) resp.get("weather");
        var wind = (Map<String, Object>) resp.get("wind");

        var description =
                weatherArr != null && !weatherArr.isEmpty()
                        ? String.valueOf(weatherArr.get(0).get("description"))
                        : "未知";
        int temp = main != null ? ((Number) main.getOrDefault("temp", 0)).intValue() : 0;
        int humidity = main != null ? ((Number) main.getOrDefault("humidity", 0)).intValue() : 0;
        int windSpeed = wind != null ? ((Number) wind.getOrDefault("speed", 0)).intValue() : 0;

        return new WeatherVO(
                city,
                description,
                temp,
                humidity,
                null,
                windSpeed,
                null,
                "openweathermap",
                java.time.LocalDateTime.now().toString());
    }

    private WeatherVO mockWeather(String city) {
        return new WeatherVO(
                city,
                "晴",
                25,
                60,
                "东南风",
                12,
                "未来 3 天晴转多云",
                "mock",
                java.time.LocalDateTime.now().toString());
    }
}
