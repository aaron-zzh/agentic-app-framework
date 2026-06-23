package com.xuejiai.aaf.module.system.tools.weather.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.system.tools.weather.service.WeatherService;
import com.xuejiai.aaf.module.system.tools.weather.vo.WeatherVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

/** 工具-天气接口。 */
@Tag(name = "工具-天气")
@RestController("systemWeatherController")
@RequestMapping("/api/tools/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;

    @Operation(summary = "查询城市天气（Redis 缓存 30 分钟）")
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public Result<WeatherVO> getWeather(@RequestParam @NotBlank String city) {
        return Result.success(weatherService.getWeather(city));
    }
}
