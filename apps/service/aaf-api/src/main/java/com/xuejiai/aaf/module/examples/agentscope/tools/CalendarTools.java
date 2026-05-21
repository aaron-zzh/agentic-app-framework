package com.xuejiai.aaf.module.examples.agentscope.tools;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

/** 日历工具集（Stub 实现），演示 Supervisor 多智能体模式中的子 Agent 工具。 */
@Component
@ConditionalOnProperty(
        name = "aaf.examples.agentscope.enabled",
        havingValue = "true",
        matchIfMissing = false)
public class CalendarTools {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 查询可用时间段（Stub） */
    @Tool(name = "get_available_time_slots", description = "查询指定日期的可用时间段")
    public String getAvailableTimeSlots(
            @ToolParam(name = "date", description = "日期，格式 yyyy-MM-dd") String date) {
        // Stub：返回固定可用时间段
        return "日期 " + date + " 的可用时间段：09:00-10:00、14:00-15:00、16:00-17:00";
    }

    /** 创建日程（Stub） */
    @Tool(name = "create_calendar_event", description = "创建日程事件")
    public String createCalendarEvent(
            @ToolParam(name = "title", description = "日程标题") String title,
            @ToolParam(name = "start_time", description = "开始时间，格式 yyyy-MM-dd HH:mm")
                    String startTime,
            @ToolParam(name = "duration_minutes", description = "持续时长（分钟）") int durationMinutes) {
        try {
            LocalDateTime start = LocalDateTime.parse(startTime, FMT);
            LocalDateTime end = start.plusMinutes(durationMinutes);
            return String.format(
                    "✅ 日程已创建：%s，时间：%s ~ %s", title, start.format(FMT), end.format(FMT));
        } catch (Exception e) {
            return "创建失败：时间格式错误，请使用 yyyy-MM-dd HH:mm";
        }
    }

    /** 查询今日日程（Stub） */
    @Tool(name = "list_today_events", description = "查询今日所有日程")
    public String listTodayEvents() {
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        List<String> events =
                List.of("09:30 - 团队站会（30分钟）", "14:00 - 产品评审（60分钟）", "16:30 - 1on1（30分钟）");
        return today + " 今日日程：\n" + String.join("\n", events);
    }
}
