package com.xuejiai.aaf.module.ui.tracking;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

/**
 * 行为追踪服务：事件存储 + 聚合分析。
 * 当前使用内存存储，后续可替换为持久化方案。
 */
@Service
public class TrackingService {

    private final CopyOnWriteArrayList<UserTrackingEvent> events = new CopyOnWriteArrayList<>();

    /** 批量存储事件 */
    public int saveEvents(TrackingEventDTO dto) {
        var now = LocalDateTime.now();
        var items = dto.events().stream()
                .map(e -> new UserTrackingEvent(
                        e.type(), e.page(), e.target(),
                        e.x(), e.y(), e.timestamp(), e.extra(), now))
                .toList();
        events.addAll(items);
        return items.size();
    }

    /** 生成指定页面的热力图数据 */
    public HeatmapVO getHeatmap(String page) {
        var points = events.stream()
                .filter(e -> page.equals(e.page()) && e.x() != null && e.y() != null)
                .collect(Collectors.groupingBy(
                        e -> e.x() / 10 * 10 + "," + e.y() / 10 * 10,
                        Collectors.counting()))
                .entrySet().stream()
                .map(entry -> {
                    var parts = entry.getKey().split(",");
                    return new HeatmapVO.HeatPoint(
                            Integer.parseInt(parts[0]),
                            Integer.parseInt(parts[1]),
                            entry.getValue().intValue());
                })
                .toList();
        return new HeatmapVO(page, points);
    }

    /** 识别操作模式 */
    public List<PatternVO> getPatterns() {
        // 按事件类型统计频次
        var total = events.size();
        if (total == 0) return List.of();

        return events.stream()
                .collect(Collectors.groupingBy(UserTrackingEvent::type, Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new PatternVO(
                        entry.getKey(),
                        entry.getKey() + " 操作",
                        entry.getValue().intValue(),
                        (double) entry.getValue() / total))
                .sorted((a, b) -> Integer.compare(b.frequency(), a.frequency()))
                .toList();
    }
}
