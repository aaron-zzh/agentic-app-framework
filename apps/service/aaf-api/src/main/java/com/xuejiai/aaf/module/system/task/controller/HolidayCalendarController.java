package com.xuejiai.aaf.module.system.task.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.system.task.domain.SysHolidayCalendar;
import com.xuejiai.aaf.module.system.task.service.HolidayCalendarService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

/** 排除日历管理接口——维护任务执行的排除日期（节假日、停服日、项目封版期等）。 */
@Tag(name = "排除日历管理")
@RestController
@RequestMapping("/api/admin/holiday-calendars")
@RequiredArgsConstructor
public class HolidayCalendarController {

    private final HolidayCalendarService calendarService;

    /** 添加排除日期请求体 */
    public record AddExcludeDateDTO(
            @NotBlank String calendarCode,
            @NotNull LocalDate excludeDate,
            /** HOLIDAY=排除跳过 / WORKDAY=调休不跳过（优先级更高） */
            String dayType) {}

    @Operation(summary = "查询日历排除日期列表")
    @GetMapping("/{calendarCode}")
    public Result<List<SysHolidayCalendar>> list(@PathVariable String calendarCode) {
        return Result.success(calendarService.list(calendarCode));
    }

    @Operation(summary = "添加排除日期")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public Result<SysHolidayCalendar> add(@RequestBody AddExcludeDateDTO dto) {
        return Result.success(
                calendarService.add(dto.calendarCode(), dto.excludeDate(), dto.dayType()));
    }

    @Operation(summary = "删除排除日期")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        calendarService.delete(id);
        return Result.success();
    }

    @Operation(summary = "查询指定日期是否被排除")
    @GetMapping("/{calendarCode}/check")
    public Result<Boolean> check(@PathVariable String calendarCode, @RequestParam LocalDate date) {
        return Result.success(calendarService.isExcluded(date, calendarCode));
    }
}
