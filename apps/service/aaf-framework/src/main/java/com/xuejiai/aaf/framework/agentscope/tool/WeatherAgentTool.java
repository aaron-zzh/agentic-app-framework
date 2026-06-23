/*
 * Copyright 2024-2026 xuejiai.com & AaronZZH.
 * Licensed under the Apache License, Version 2.0.
 */
package com.xuejiai.aaf.framework.agentscope.tool;

import java.util.function.BiFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

/**
 * 天气查询工具——agentscope {@code @Tool}。
 *
 * <p>通过 {@code weatherFn(longitude, latitude) -> jsonString} 函数接口解耦， 由调用方（{@code
 * ContentCreationAutoConfiguration}）注入实际的天气客户端 lambda， 避免 aaf-framework 直接依赖 aaf-api 的 {@code
 * CaiyunWeatherClient}。
 */
public class WeatherAgentTool {

    private static final Logger log = LoggerFactory.getLogger(WeatherAgentTool.class);

    private final BiFunction<Double, Double, String> weatherFn;

    public WeatherAgentTool(BiFunction<Double, Double, String> weatherFn) {
        this.weatherFn = weatherFn;
    }

    @Tool(
            description =
                    "查询指定经纬度的天气信息，包含实时天气（温度/湿度/风速/天气现象）、"
                            + "未来 24 小时逐小时预报、未来 7 天预报。"
                            + "如用户输入城市名，需先将城市名换算为经纬度（如北京 116.39,39.93）再调用。")
    public String query_weather(
            @ToolParam(name = "longitude", description = "经度，如北京 116.39") double longitude,
            @ToolParam(name = "latitude", description = "纬度，如北京 39.93") double latitude) {
        log.info("[WeatherTool] lon={} lat={}", longitude, latitude);
        try {
            return weatherFn.apply(longitude, latitude);
        } catch (Exception e) {
            log.warn("[WeatherTool] 查询失败: {}", e.getMessage());
            return "{\"status\":\"error\",\"message\":\"天气查询失败: " + e.getMessage() + "\"}";
        }
    }
}
