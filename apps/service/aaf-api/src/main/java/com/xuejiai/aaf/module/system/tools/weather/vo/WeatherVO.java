package com.xuejiai.aaf.module.system.tools.weather.vo;

/** 天气响应 VO。 */
public record WeatherVO(
        String city,
        String description,
        Integer temperature,
        Integer humidity,
        String windDirection,
        Integer windSpeed,
        String forecast3Days,
        String dataSource,
        String updatedAt) {}
