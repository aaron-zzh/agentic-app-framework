package com.xuejiai.aaf.module.system.dashboard.service;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.module.system.dashboard.domain.Dashboard;
import com.xuejiai.aaf.module.system.dashboard.domain.DashboardPreset;
import com.xuejiai.aaf.module.system.dashboard.domain.DashboardWidget;
import com.xuejiai.aaf.module.system.dashboard.repository.DashboardRepository;
import com.xuejiai.aaf.module.system.dashboard.repository.DashboardWidgetRepository;
import com.xuejiai.aaf.module.system.dashboard.vo.DashboardCreateDTO;
import com.xuejiai.aaf.module.system.dashboard.vo.DashboardPresetVO;
import com.xuejiai.aaf.module.system.dashboard.vo.DashboardUpdateDTO;
import com.xuejiai.aaf.module.system.dashboard.vo.DashboardVO;
import com.xuejiai.aaf.module.system.dashboard.vo.DashboardWidgetVO;
import com.xuejiai.aaf.module.system.dashboard.vo.WidgetCreateDTO;
import com.xuejiai.aaf.module.system.dashboard.vo.WidgetDataVO;
import com.xuejiai.aaf.module.system.dashboard.vo.WidgetPositionVO;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;

/**
 * 仪表盘业务逻辑。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    /** list 组件单次查询行数硬上限，防止恶意请求触发全表扫描 */
    private static final int MAX_LIST_LIMIT = 200;

    /** 解析 preset.widgets / DashboardLayoutDTO 时复用的列表泛型引用 */
    private static final TypeReference<List<DashboardWidgetVO>> WIDGET_LIST_TYPE =
            new TypeReference<>() {};

    /** 解析 widget.config 时复用的 Map 泛型引用 */
    private static final TypeReference<Map<String, Object>> CONFIG_MAP_TYPE =
            new TypeReference<>() {};

    private final DashboardRepository dashboardRepository;
    private final DashboardWidgetRepository widgetRepository;
    private final JdbcTemplate jdbcTemplate;

    /** 查询用户默认仪表盘，无默认则取第一个，均无则返回 null */
    public DashboardVO getDefault(Long ownerId) {
        return dashboardRepository
                .findByOwnerIdAndIsDefaultTrue(ownerId)
                .or(
                        () ->
                                dashboardRepository
                                        .findByOwnerIdOrderByIsDefaultDescCreateTimeDesc(ownerId)
                                        .stream()
                                        .findFirst())
                .map(this::toVO)
                .orElse(null);
    }

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
            replaceWidgets(id, dto.widgets());
        }
        return toVO(dashboard);
    }

    /**
     * 保存仪表盘布局：覆盖式替换 widget 列表，对应前端 {@code useSaveDashboardLayout}。
     *
     * <p>与 {@link #update} 的区别：仅处理 widget 列表，不动 dashboard 元数据；语义清晰、链路独立，便于前端拖拽布局后单独保存。
     */
    @Transactional
    public DashboardVO saveLayout(Long id, List<WidgetCreateDTO> layout) {
        var dashboard =
                dashboardRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "仪表盘不存在"));
        replaceWidgets(id, layout);
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

    /** 可用指标元数据（counter Widget 配置用） */
    public record MetricMeta(
            /** 指标 key，填入 Widget config.entity */
            String key,
            /** 中文名称 */
            String label,
            /** 分组 */
            String group,
            /** 聚合方式：count / sum */
            String aggregation,
            /** 是否支持用户级过滤（普通用户只看自己） */
            boolean userScopable) {}

    /** 返回所有可用指标（预定义 + 白名单表） */
    public List<MetricMeta> listMetrics() {
        return List.of(
                // ── 预定义指标（@ 前缀）──
                new MetricMeta("@user_count", "注册用户数", "用户", "count", false),
                new MetricMeta("@paid_member", "付费会员数", "用户", "count", false),
                new MetricMeta("@total_credit", "积分余额", "积分", "sum", true),
                new MetricMeta("@spent_credit", "已消耗积分", "积分", "sum", true),
                new MetricMeta("@order_count", "支付订单数", "订单", "count", true),
                new MetricMeta("@order_amount", "订单总金额(分)", "订单", "sum", true),
                // ── 白名单表（通用 COUNT） ──
                new MetricMeta("aigc_task", "AIGC 任务数", "AIGC", "count", true),
                new MetricMeta("media_asset", "素材数量", "AIGC", "count", true),
                new MetricMeta("ai_knowledge_base", "知识库数量", "知识库", "count", true),
                new MetricMeta("ai_knowledge_document", "文档数量", "知识库", "count", true),
                new MetricMeta("ai_workflow_definition", "工作流定义数", "工作流", "count", false),
                new MetricMeta("ai_agent_definition", "Agent 数量", "Agent", "count", false),
                new MetricMeta("ai_skill_definition", "技能数量", "Agent", "count", false),
                new MetricMeta("sys_todo", "待办数量", "任务", "count", true),
                // ── 访客线索指标（marketing 看板用）──
                new MetricMeta("ops_guest_lead", "访客线索总数", "营销", "count", false),
                new MetricMeta("@lead_visit", "访客访问数", "营销", "count", false),
                new MetricMeta("@lead_chat", "对话意向数", "营销", "count", false),
                new MetricMeta("@lead_newsletter", "邮箱订阅数", "营销", "count", false),
                new MetricMeta("@lead_contact", "联系留言数", "营销", "count", false),
                new MetricMeta("@lead_feedback", "用户反馈数", "营销", "count", false));
    }

    /**
     * 查询组件数据：直接按前端传入的 config 查询，不再依赖 widget 在 DB 的存在。
     *
     * <p>原因：仪表盘 widget 同时支持 (1) 预设里的字符串 ID（如 "admin-kb-count"，DB 无对应行）和 (2) 用户保存的数字 ID。两种场景下前端均会把完整
     * config 发到 body，后端无需再去 DB 反查。
     *
     * @param widgetId 组件标识（透传给前端关联结果，不解析语义）
     * @param config 组件配置（必须包含 type 字段）
     * @param userId 用户 ID，null 表示管理员查全局数据
     */
    public WidgetDataVO getWidgetData(String widgetId, Map<String, Object> config, Long userId) {
        if (config == null || config.isEmpty()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "缺少组件 config");
        }
        var type = (String) config.get("type");
        if (type == null || type.isBlank()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "config.type 缺失");
        }
        var data = queryWidgetData(type, config, userId);
        return new WidgetDataVO(widgetId, type, data);
    }

    // ========== 序列化辅助：JSON 文本 <-> 结构化对象 ==========

    /**
     * 把预设的 widgets JSON 文本解析成结构化列表。
     *
     * <p>预设 widgets 是 jsonb 列里的数组文本（如前端 dashboardPresets 落库的形态），解析失败抛 500。
     */
    public DashboardPresetVO toPresetVO(DashboardPreset p) {
        List<DashboardWidgetVO> widgets;
        var raw = p.getWidgets();
        if (raw == null || raw.isBlank()) {
            widgets = List.of();
        } else {
            try {
                widgets = JsonUtils.parseObject(raw, WIDGET_LIST_TYPE);
                if (widgets == null) widgets = List.of();
            } catch (RuntimeException e) {
                throw new BusinessException(
                        GlobalErrorCode.INTERNAL_SERVER_ERROR, "预设 widgets 解析失败: id=" + p.getId());
            }
        }
        return new DashboardPresetVO(
                String.valueOf(p.getId()),
                p.getPresetKey(),
                p.getName(),
                p.getDescription(),
                Boolean.TRUE.equals(p.getAdminOnly()),
                p.getRefreshInterval() != null ? p.getRefreshInterval() : 300,
                widgets,
                p.getSortOrder() != null ? p.getSortOrder() : 0);
    }

    /** 序列化结构化的 widget 列表为 jsonb 列待存的 JSON 文本 */
    public String serializeWidgets(List<WidgetCreateDTO> widgets) {
        if (widgets == null) return "[]";
        return JsonUtils.toJsonString(widgets);
    }

    // ========== 内部方法 ==========

    /** 覆盖式替换某仪表盘下的 widget 列表 */
    private void replaceWidgets(Long dashboardId, List<WidgetCreateDTO> widgets) {
        widgetRepository.deleteByDashboardId(dashboardId);
        saveWidgets(dashboardId, widgets);
    }

    private void saveWidgets(Long dashboardId, List<WidgetCreateDTO> widgets) {
        for (int i = 0; i < widgets.size(); i++) {
            var dto = widgets.get(i);
            var widget = new DashboardWidget();
            widget.setDashboardId(dashboardId);
            widget.setType(dto.type());
            widget.setTitle(dto.title());
            widget.setPosition(JsonUtils.toJsonString(dto.position()));
            widget.setConfig(JsonUtils.toJsonString(dto.config()));
            widget.setSortOrder(dto.sortOrder() != null ? dto.sortOrder() : i);
            widgetRepository.save(widget);
        }
    }

    private Object queryWidgetData(String type, Map<String, Object> config, Long userId) {
        return switch (type) {
            case "counter" -> queryCounter(config, userId);
            case "chart" -> queryChart(config, userId);
            case "list" -> queryList(config, userId);
            case "progress" -> queryProgress(config);
            case "billing" -> queryBilling(config, userId);
            default -> throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "未知组件类型: " + type);
        };
    }

    /**
     * billing 复合 widget 路由：按 {@code config.component} 分发到具体查询函数。
     *
     * <p>component 取值与前端 {@code BillingWidget} 分发分支保持一致：
     *
     * <ul>
     *   <li>{@code overview} → 积分总览
     *   <li>{@code expenses-category} → 消耗分类
     *   <li>{@code transaction-list} → 流水列表（支持 config.limit）
     *   <li>{@code multi-series-chart} → 30 天多系列图
     * </ul>
     */
    private Object queryBilling(Map<String, Object> config, Long userId) {
        var component = (String) config.get("component");
        if (component == null || component.isBlank()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "billing widget 缺少 component");
        }
        return switch (component) {
            case "overview" -> queryBillingOverview(userId);
            case "expenses-category" -> queryBillingCategory(userId);
            case "transaction-list" -> queryBillingTransactions(config, userId);
            case "multi-series-chart" -> queryBillingMultiSeries(userId);
            default ->
                    throw new BusinessException(
                            GlobalErrorCode.BAD_REQUEST, "未知 billing 组件: " + component);
        };
    }

    // ==================== 个人积分仪表盘数据查询 ====================

    /** 30 天窗口的起始 timestamp（含今日）。 */
    private static final String WINDOW_30D = "(CURRENT_DATE - INTERVAL '29 days')";

    /**
     * billing-overview：积分总览 = 当前余额 + 30 天累计 EARN + 30 天累计 SPEND + 30 天每日时序。
     *
     * <p>userId=null 时（管理员视角）按全局聚合，userId 非空按用户过滤。
     */
    private Object queryBillingOverview(Long userId) {
        // 余额 = balance + frozen，credit_account 表自带 user_id
        var balance =
                jdbcTemplate.queryForObject(
                        "SELECT COALESCE(SUM(ca.balance + ca.frozen), 0) FROM credit_account ca"
                                + " WHERE ca.deleted = false"
                                + userFilter("ca", userId),
                        Long.class);
        var monthEarn = sumByTypeWithin30d(userId, "EARN");
        var monthSpend = sumByTypeWithin30d(userId, "SPEND");
        var earnTrend = trendByTypeWithin30d(userId, "EARN");
        var spendTrend = trendByTypeWithin30d(userId, "SPEND");

        return Map.of(
                "balance", balance == null ? 0L : balance,
                "monthEarn", monthEarn,
                "monthSpend", monthSpend,
                "earnTrend", earnTrend,
                "spendTrend", spendTrend);
    }

    /** billing-category：30 天 SPEND 按 biz_type 分组聚合（通过 account JOIN 用户过滤）。 */
    private Object queryBillingCategory(Long userId) {
        var sql =
                """
                SELECT COALESCE(ct.biz_type, 'OTHER') AS biz_type, SUM(ct.amount) AS total
                FROM credit_transaction ct
                JOIN credit_account ca ON ca.id = ct.account_id AND ca.deleted = false
                WHERE ct.deleted = false AND ct.type = 'SPEND'
                  AND ct.create_time >= %s%s
                GROUP BY COALESCE(ct.biz_type, 'OTHER')
                ORDER BY total DESC
                LIMIT 8
                """
                        .formatted(WINDOW_30D, userFilter("ca", userId));
        var rows = jdbcTemplate.queryForList(sql);
        return Map.of("categories", rows);
    }

    /** billing-transactions：最近 N 条流水（默认 10），按时间降序。 */
    private Object queryBillingTransactions(Map<String, Object> config, Long userId) {
        int limit = 10;
        var raw = config.get("limit");
        if (raw instanceof Number n) {
            limit = Math.min(Math.max(n.intValue(), 1), 50);
        }
        var sql =
                """
                SELECT ct.id, ct.type, ct.amount, ct.balance_after,
                       ct.biz_type, ct.batch_type, ct.create_time, ct.remark
                FROM credit_transaction ct
                JOIN credit_account ca ON ca.id = ct.account_id AND ca.deleted = false
                WHERE ct.deleted = false%s
                ORDER BY ct.create_time DESC
                LIMIT %d
                """
                        .formatted(userFilter("ca", userId), limit);
        var rows = jdbcTemplate.queryForList(sql);
        return Map.of("items", rows);
    }

    /** billing-multi-series：30 天每日 EARN vs SPEND 双系列。 */
    private Object queryBillingMultiSeries(Long userId) {
        return Map.of(
                "earn", trendByTypeWithin30d(userId, "EARN"),
                "spend", trendByTypeWithin30d(userId, "SPEND"));
    }

    /** 工具方法：30 天窗口内某 type 的累计金额（amount 列恒为正，方向由 type 决定）。 */
    private long sumByTypeWithin30d(Long userId, String type) {
        var sql =
                """
                SELECT COALESCE(SUM(ct.amount), 0)
                FROM credit_transaction ct
                JOIN credit_account ca ON ca.id = ct.account_id AND ca.deleted = false
                WHERE ct.deleted = false AND ct.type = ?
                  AND ct.create_time >= %s%s
                """
                        .formatted(WINDOW_30D, userFilter("ca", userId));
        var v = jdbcTemplate.queryForObject(sql, Long.class, type);
        return v == null ? 0L : v;
    }

    /** 工具方法：30 天窗口内按日聚合的时序点（缺日补 0）。 */
    private List<Map<String, Object>> trendByTypeWithin30d(Long userId, String type) {
        var sql =
                """
                SELECT TO_CHAR(d.day, 'MM-DD') AS time,
                       COALESCE(SUM(ct.amount), 0) AS value
                FROM generate_series(%s, CURRENT_DATE, INTERVAL '1 day') AS d(day)
                LEFT JOIN credit_transaction ct
                       ON ct.deleted = false AND ct.type = ?
                      AND DATE(ct.create_time) = d.day
                LEFT JOIN credit_account ca
                       ON ca.id = ct.account_id AND ca.deleted = false
                WHERE 1 = 1%s
                GROUP BY d.day
                ORDER BY d.day
                """
                        .formatted(WINDOW_30D, userFilter("ca", userId));
        return jdbcTemplate.queryForList(sql, type);
    }

    /** counter：COUNT 或 SUM(field)，userId!=null 时加用户过滤 */
    private Object queryCounter(Map<String, Object> config, Long userId) {
        var entity = (String) config.get("entity");
        if (entity != null && entity.startsWith("@")) {
            return Map.of("value", queryPresetMetric(entity, userId));
        }
        var safeEntity = sanitizeIdentifier(entity);
        var aggregation = (String) config.getOrDefault("aggregation", "count");
        var field = config.get("field");
        var userFilter = userFilterByEntity(safeEntity, userId);
        var sql =
                "count".equals(aggregation)
                        ? "SELECT COUNT(*) FROM %s WHERE deleted = false%s"
                                .formatted(safeEntity, userFilter)
                        : "SELECT COALESCE(SUM(%s), 0) FROM %s WHERE deleted = false%s"
                                .formatted(
                                        sanitizeIdentifier((String) field), safeEntity, userFilter);
        return Map.of("value", jdbcTemplate.queryForObject(sql, Long.class));
    }

    /**
     * 预定义指标查询（以 @ 开头，绕过动态表名，避免安全风险）。
     *
     * <p>支持的指标：
     *
     * <ul>
     *   <li>{@code @user_count} — 注册用户总数（管理员全局/用户自己=1）
     *   <li>{@code @paid_member} — 付费订阅用户数（status=ACTIVE，非免费套餐）
     *   <li>{@code @total_credit} — 积分余额合计
     *   <li>{@code @spent_credit} — 已消耗积分合计
     *   <li>{@code @order_count} — 支付成功订单数
     *   <li>{@code @order_amount} — 支付成功订单总金额（分）
     * </ul>
     */
    private Object queryPresetMetric(String metric, Long userId) {
        // 仅 userScopable=true 的指标应用 userId 过滤；其余按全局视角查询。
        // 与 listMetrics() 中 MetricMeta.userScopable 字段对齐——前端 UI 据此决定是否显示"我自己"开关。
        // 注：@user_count 对应表 sys_user 主键是 id 而非 user_id，无法直接套 userFilter，
        // 因此即使是普通用户调用也按全局展示（语义上"注册用户数"本就不该按用户过滤）。
        Long scopedUserId = USER_SCOPABLE_PRESET_METRICS.contains(metric) ? userId : null;
        String userFilter = userFilter(scopedUserId);
        String sql =
                switch (metric) {
                    case "@user_count" ->
                            "SELECT COUNT(*) FROM sys_user WHERE deleted = false" + userFilter;
                    case "@paid_member" ->
                            // 非免费套餐（排除 code 对应 FREE 的订阅）且状态 ACTIVE
                            """
                SELECT COUNT(DISTINCT bs.user_id) FROM billing_subscription bs
                JOIN billing_subscription_plan p ON p.id = bs.plan_id AND p.deleted = false
                WHERE bs.deleted = false AND bs.status = 'ACTIVE'
                  AND p.code != 'FREE'
                """
                                    + userFilter("bs", scopedUserId);
                    case "@total_credit" ->
                            "SELECT COALESCE(SUM(balance + frozen), 0) FROM credit_account WHERE deleted = false"
                                    + userFilter;
                    case "@spent_credit" ->
                            "SELECT COALESCE(SUM(total_spent), 0) FROM credit_account WHERE deleted = false"
                                    + userFilter;
                    case "@order_count" ->
                            "SELECT COUNT(*) FROM pay_order WHERE deleted = false AND status = 10"
                                    + userFilter;
                    case "@order_amount" ->
                            "SELECT COALESCE(SUM(amount), 0) FROM pay_order WHERE deleted = false AND status = 10"
                                    + userFilter;
                    // 访客线索（ops_guest_lead）按 channel 分计数；userScopable=false，userFilter 始终为空
                    case "@lead_visit" ->
                            "SELECT COUNT(*) FROM ops_guest_lead WHERE deleted = false AND channel = 'VISIT'";
                    case "@lead_chat" ->
                            "SELECT COUNT(*) FROM ops_guest_lead WHERE deleted = false AND channel = 'CHAT'";
                    case "@lead_newsletter" ->
                            "SELECT COUNT(*) FROM ops_guest_lead WHERE deleted = false AND channel = 'NEWSLETTER'";
                    case "@lead_contact" ->
                            "SELECT COUNT(*) FROM ops_guest_lead WHERE deleted = false AND channel = 'CONTACT'";
                    case "@lead_feedback" ->
                            "SELECT COUNT(*) FROM ops_guest_lead WHERE deleted = false AND channel = 'FEEDBACK'";
                    default ->
                            throw new BusinessException(
                                    GlobalErrorCode.BAD_REQUEST, "未知预定义指标: " + metric);
                };
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    /** chart：按 xField 分组聚合，userId!=null 时加用户过滤 */
    private Object queryChart(Map<String, Object> config, Long userId) {
        var entity = sanitizeIdentifier((String) config.get("entity"));
        var xField = sanitizeIdentifier((String) config.get("xField"));
        var yField = sanitizeIdentifier((String) config.get("yField"));
        var userFilter = userFilterByEntity(entity, userId);
        var sql =
                "SELECT %s AS label, COUNT(%s) AS value FROM %s WHERE deleted = false%s GROUP BY %s ORDER BY %s"
                        .formatted(xField, yField, entity, userFilter, xField, xField);
        return jdbcTemplate.queryForList(sql);
    }

    /** list：查询指定列，userId!=null 时加用户过滤 */
    @SuppressWarnings("unchecked")
    private Object queryList(Map<String, Object> config, Long userId) {
        var entity = sanitizeIdentifier((String) config.get("entity"));
        var columns = (List<String>) config.get("columns");
        if (columns == null || columns.isEmpty()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "list 组件缺少 columns");
        }
        var rawLimit = config.get("limit") != null ? ((Number) config.get("limit")).intValue() : 10;
        // 限制 1-200，防止恶意请求触发全表扫描
        var limit = Math.min(Math.max(rawLimit, 1), MAX_LIST_LIMIT);
        var cols =
                columns.stream()
                        .map(this::sanitizeIdentifier)
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("*");
        var userFilter = userFilterByEntity(entity, userId);
        var sql =
                "SELECT %s FROM %s WHERE deleted = false%s ORDER BY create_time DESC LIMIT %d"
                        .formatted(cols, entity, userFilter, limit);
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

    /**
     * 生成 user_id 过滤片段（含前导空格），userId 为 null 时返回空串。
     *
     * <p>userId 是 Long 类型从 Security 上下文取出，无 SQL 注入风险，故直接拼接。
     */
    private String userFilter(Long userId) {
        return userId != null ? " AND user_id = " + userId : "";
    }

    /** 同 {@link #userFilter(Long)}，但带表别名前缀（用于多表 JOIN 场景） */
    private String userFilter(String tableAlias, Long userId) {
        return userId != null ? " AND " + tableAlias + ".user_id = " + userId : "";
    }

    /**
     * 部分白名单表的"用户归属"列名不是默认的 {@code user_id}，按 entity 名映射到真实列。
     *
     * <p>未列出的表默认仍用 {@code user_id}。
     */
    private static final Map<String, String> USER_SCOPED_COLUMN =
            Map.of(
                    "ai_knowledge_base", "owner_id",
                    "ai_knowledge_document", "owner_id",
                    "sys_todo", "assignee_id");

    /** 全局指标表（不按用户过滤）：marketing 看板等管理员全局视角的数据。 */
    private static final java.util.Set<String> NO_USER_FILTER_TABLES =
            java.util.Set.of("ops_guest_lead");

    /**
     * 支持按用户过滤的预定义指标白名单（@ 前缀指标）。
     *
     * <p>需与 {@link #listMetrics()} 中 {@link MetricMeta#userScopable()} 字段对齐：
     *
     * <ul>
     *   <li>true → 加入此集合，普通用户视角时按 userId 过滤；
     *   <li>false → 不加入此集合，所有用户都看全局视角（如注册用户总数）。
     * </ul>
     *
     * <p>对齐的另一关键原因：{@code @user_count} 对应的 {@code sys_user} 表主键是 {@code id} 而非 {@code user_id}，
     * 即使想按用户过滤也不能直接套 {@code userFilter}（会报 column not found）。
     */
    private static final java.util.Set<String> USER_SCOPABLE_PRESET_METRICS =
            java.util.Set.of("@total_credit", "@spent_credit", "@order_count", "@order_amount");

    /** 按白名单表名挑选用户归属列拼接过滤片段，userId 为 null 或表为全局表时返回空串。 */
    private String userFilterByEntity(String entity, Long userId) {
        if (userId == null) return "";
        if (NO_USER_FILTER_TABLES.contains(entity)) return "";
        var column = USER_SCOPED_COLUMN.getOrDefault(entity, "user_id");
        return " AND " + column + " = " + userId;
    }

    private DashboardVO toVO(Dashboard d) {
        var widgets =
                widgetRepository.findByDashboardIdOrderBySortOrder(d.getId()).stream()
                        .map(this::widgetToVO)
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

    /** 把 DB widget 实体转成结构化 VO，position/config 反序列化为对象 */
    private DashboardWidgetVO widgetToVO(DashboardWidget w) {
        WidgetPositionVO position;
        Map<String, Object> config;
        try {
            position =
                    w.getPosition() == null || w.getPosition().isBlank()
                            ? new WidgetPositionVO(0, 0, 4, 3)
                            : JsonUtils.parseObject(w.getPosition(), WidgetPositionVO.class);
        } catch (RuntimeException e) {
            throw new BusinessException(
                    GlobalErrorCode.INTERNAL_SERVER_ERROR, "widget position 解析失败: id=" + w.getId());
        }
        try {
            config =
                    w.getConfig() == null || w.getConfig().isBlank()
                            ? Map.of()
                            : JsonUtils.parseObject(w.getConfig(), CONFIG_MAP_TYPE);
        } catch (RuntimeException e) {
            throw new BusinessException(
                    GlobalErrorCode.INTERNAL_SERVER_ERROR, "widget config 解析失败: id=" + w.getId());
        }
        return new DashboardWidgetVO(
                String.valueOf(w.getId()),
                w.getType(),
                w.getTitle(),
                position,
                config,
                w.getSortOrder());
    }
}
