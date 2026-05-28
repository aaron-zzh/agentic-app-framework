package com.xuejiai.aaf.module.system.dashboard.service;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.system.dashboard.domain.Dashboard;
import com.xuejiai.aaf.module.system.dashboard.domain.DashboardWidget;
import com.xuejiai.aaf.module.system.dashboard.repository.DashboardRepository;
import com.xuejiai.aaf.module.system.dashboard.repository.DashboardWidgetRepository;
import com.xuejiai.aaf.module.system.dashboard.vo.DashboardCreateDTO;
import com.xuejiai.aaf.module.system.dashboard.vo.DashboardUpdateDTO;
import com.xuejiai.aaf.module.system.dashboard.vo.DashboardVO;
import com.xuejiai.aaf.module.system.dashboard.vo.DashboardWidgetVO;
import com.xuejiai.aaf.module.system.dashboard.vo.WidgetCreateDTO;
import com.xuejiai.aaf.module.system.dashboard.vo.WidgetDataVO;

import lombok.RequiredArgsConstructor;

/**
 * 仪表盘业务逻辑。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final DashboardRepository dashboardRepository;
    private final DashboardWidgetRepository widgetRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /** 查询用户所有仪表盘 */
    public List<DashboardVO> listByOwner(Long ownerId) {
        return dashboardRepository.findByOwnerIdOrderByIsDefaultDescCreateTimeDesc(ownerId).stream()
                .map(this::toVO)
                .toList();
    }

    /** 查询单个仪表盘详情 */
    public DashboardVO getById(Long id) {
        var dashboard =
                dashboardRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "仪表盘不存在"));
        return toVO(dashboard);
    }

    /** 创建仪表盘 */
    @Transactional
    public DashboardVO create(Long ownerId, DashboardCreateDTO dto) {
        var dashboard = new Dashboard();
        dashboard.setName(dto.name());
        dashboard.setDescription(dto.description());
        dashboard.setIsDefault(dto.isDefault() != null && dto.isDefault());
        dashboard.setOwnerId(ownerId);
        dashboardRepository.save(dashboard);

        if (dto.widgets() != null) {
            saveWidgets(dashboard.getId(), dto.widgets());
        }
        return toVO(dashboard);
    }

    /** 更新仪表盘 */
    @Transactional
    public DashboardVO update(Long id, DashboardUpdateDTO dto) {
        var dashboard =
                dashboardRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "仪表盘不存在"));
        if (dto.name() != null) {
            dashboard.setName(dto.name());
        }
        if (dto.description() != null) {
            dashboard.setDescription(dto.description());
        }
        if (dto.isDefault() != null) {
            dashboard.setIsDefault(dto.isDefault());
        }
        dashboardRepository.save(dashboard);

        if (dto.widgets() != null) {
            widgetRepository.deleteByDashboardId(id);
            saveWidgets(id, dto.widgets());
        }
        return toVO(dashboard);
    }

    /** 删除仪表盘 */
    @Transactional
    public void delete(Long id) {
        var dashboard =
                dashboardRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "仪表盘不存在"));
        dashboardRepository.delete(dashboard);
    }

    /** 查询组件数据 */
    public WidgetDataVO getWidgetData(Long widgetId) {
        var widget =
                widgetRepository
                        .findById(widgetId)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "组件不存在"));
        var data = queryWidgetData(widget);
        return new WidgetDataVO(widgetId, widget.getType(), data);
    }

    // ========== 内部方法 ==========

    private void saveWidgets(Long dashboardId, List<WidgetCreateDTO> widgets) {
        for (int i = 0; i < widgets.size(); i++) {
            var dto = widgets.get(i);
            var widget = new DashboardWidget();
            widget.setDashboardId(dashboardId);
            widget.setType(dto.type());
            widget.setTitle(dto.title());
            widget.setPosition(dto.position());
            widget.setConfig(dto.config());
            widget.setSortOrder(dto.sortOrder() != null ? dto.sortOrder() : i);
            widgetRepository.save(widget);
        }
    }

    private Object queryWidgetData(DashboardWidget widget) {
        Map<String, Object> config = parseJson(widget.getConfig());
        return switch (widget.getType()) {
            case "counter" -> queryCounter(config);
            case "chart" -> queryChart(config);
            case "list" -> queryList(config);
            case "progress" -> queryProgress(config);
            default -> null;
        };
    }

    /** counter：COUNT 或 SUM(field) */
    private Object queryCounter(Map<String, Object> config) {
        var entity = sanitizeIdentifier((String) config.get("entity"));
        var aggregation = (String) config.getOrDefault("aggregation", "count");
        var field = config.get("field");
        var sql =
                "count".equals(aggregation)
                        ? "SELECT COUNT(*) FROM %s WHERE deleted = false".formatted(entity)
                        : "SELECT COALESCE(SUM(%s), 0) FROM %s WHERE deleted = false"
                                .formatted(sanitizeIdentifier((String) field), entity);
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    /** chart：按 xField 分组聚合 yField */
    private Object queryChart(Map<String, Object> config) {
        var entity = sanitizeIdentifier((String) config.get("entity"));
        var xField = sanitizeIdentifier((String) config.get("xField"));
        var yField = sanitizeIdentifier((String) config.get("yField"));
        var sql =
                "SELECT %s AS label, COUNT(%s) AS value FROM %s WHERE deleted = false GROUP BY %s ORDER BY %s"
                        .formatted(xField, yField, entity, xField, xField);
        return jdbcTemplate.queryForList(sql);
    }

    /** list：查询指定列 */
    @SuppressWarnings("unchecked")
    private Object queryList(Map<String, Object> config) {
        var entity = sanitizeIdentifier((String) config.get("entity"));
        var columns = (List<String>) config.get("columns");
        var limit = config.get("limit") != null ? ((Number) config.get("limit")).intValue() : 10;
        var cols =
                columns.stream()
                        .map(this::sanitizeIdentifier)
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("*");
        var sql =
                "SELECT %s FROM %s WHERE deleted = false ORDER BY create_time DESC LIMIT %d"
                        .formatted(cols, entity, limit);
        return jdbcTemplate.queryForList(sql);
    }

    /** progress：直接返回 current/target */
    private Object queryProgress(Map<String, Object> config) {
        return Map.of(
                "label", config.getOrDefault("label", ""),
                "current", config.getOrDefault("current", 0),
                "target", config.getOrDefault("target", 0));
    }

    /** 防止 SQL 注入：只允许字母、数字、下划线 */
    private String sanitizeIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "非法标识符: " + identifier);
        }
        return identifier;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private DashboardVO toVO(Dashboard d) {
        var widgets =
                widgetRepository.findByDashboardIdOrderBySortOrder(d.getId()).stream()
                        .map(
                                w ->
                                        new DashboardWidgetVO(
                                                w.getId(),
                                                w.getType(),
                                                w.getTitle(),
                                                w.getPosition(),
                                                w.getConfig(),
                                                w.getSortOrder()))
                        .toList();
        return new DashboardVO(
                d.getId(),
                d.getName(),
                d.getDescription(),
                d.getIsDefault(),
                widgets,
                d.getCreateTime(),
                d.getUpdateTime());
    }
}
