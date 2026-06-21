package com.xuejiai.aaf.module.tool.weather;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.tool.ToolCallDispatcher.ToolCallResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 天气查询工具——基于彩云天气 v2.6 综合接口，供 AI Agent 通过 Function Calling 调用。
 *
 * <p>工具名：{@code queryWeather}，返回实况 + 逐小时 + 逐日完整天气数据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherTool {

    private static final String TOOL_NAME = "queryWeather";

    private final CaiyunWeatherClient weatherClient;

    @Tool(
            description =
                    "查询指定经纬度的天气信息，包含实时天气（温度/湿度/风速/天气现象）、"
                            + "未来 24 小时逐小时预报、未来 7 天天级预报及生活指数。"
                            + "参数：longitude（经度，必填）、latitude（纬度，必填）。"
                            + "如用户输入城市名，需先将城市名转换为经纬度再调用本工具。")
    public String queryWeather(
            @ToolParam(description = "经度，如北京 116.3883") double longitude,
            @ToolParam(description = "纬度，如北京 39.9289") double latitude) {
        try {
            String raw = weatherClient.weather(longitude, latitude, 7, 24);
            return JsonUtils.toJsonString(ToolCallResult.success(TOOL_NAME, raw));
        } catch (Exception e) {
            log.error(
                    "[WeatherTool] 查询失败: lon={}, lat={}, err={}",
                    longitude,
                    latitude,
                    e.getMessage(),
                    e);
            return JsonUtils.toJsonString(
                    ToolCallResult.error(TOOL_NAME, "WEATHER_ERROR", e.getMessage()));
        }
    }
}
