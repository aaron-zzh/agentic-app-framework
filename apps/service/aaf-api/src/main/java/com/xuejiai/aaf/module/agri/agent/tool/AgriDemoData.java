package com.xuejiai.aaf.module.agri.agent.tool;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/**
 * 智慧农业演示数据：大棚传感器、农产品销售、库存、预约与物联网设备。
 *
 * <p>全部为固定规则生成的示例数据，不读写数据库；库存扣减、预约草稿、预约单、设备状态与控制指令只保存在内存中，服务重启后恢复初始状态。 数据内置一条演示故事线：2
 * 号棚（番茄）近三天湿度持续高于 90%，番茄近 30 天销量下滑且库存偏高。
 */
@Component
class AgriDemoData {

    /** 预约草稿有效期。 */
    static final long DRAFT_TTL_MINUTES = 10;

    /** 可预约的时段。 */
    static final List<String> TIME_SLOTS = List.of("09:00-11:00", "14:00-16:00");

    /** 预约最远可提前天数。 */
    static final int MAX_BOOKING_DAYS_AHEAD = 14;

    private static final List<Greenhouse> GREENHOUSES =
            List.of(
                    new Greenhouse("GH-1", "1号棚", "黄瓜", 0.0),
                    new Greenhouse("GH-2", "2号棚", "番茄", 1.3),
                    new Greenhouse("GH-3", "3号棚", "草莓", 2.6));

    private static final Map<String, Metric> METRICS = metrics();

    private static final List<Product> PRODUCTS =
            List.of(
                    new Product("草莓", new BigDecimal("38.00"), 46, 6.5, 320),
                    new Product("番茄", new BigDecimal("8.00"), 95, -18.2, 1200),
                    new Product("黄瓜", new BigDecimal("6.00"), 120, 4.3, 600),
                    new Product("生菜", new BigDecimal("10.00"), 52, 9.8, 180),
                    new Product("辣椒", new BigDecimal("12.00"), 38, -2.1, 260));

    /** 设备控制草稿有效期。 */
    static final long COMMAND_DRAFT_TTL_MINUTES = 10;

    /** 滴灌单次最长时长。 */
    static final int MAX_IRRIGATION_MINUTES = 60;

    /** 单台设备保留的操作记录条数。 */
    private static final int MAX_OPERATION_LOGS = 20;

    /**
     * 演示设备：每个大棚一组。初始状态即日均值对应的工况，读数只叠加"当前状态相对初始状态"的效果。 演示故事线：2 号棚湿度偏高，通风窗与风机关闭；1 号棚通风窗处于自动模式；3
     * 号棚补光灯离线。
     */
    private static final List<Device> DEVICES = deviceCatalog();

    private final Map<String, Integer> stockKg = new ConcurrentHashMap<>();
    private final Map<String, BookingDraft> drafts = new ConcurrentHashMap<>();
    private final AtomicInteger bookingSequence = new AtomicInteger();
    private final Map<String, DeviceState> deviceStates = new ConcurrentHashMap<>();
    private final Map<String, CommandDraft> commandDrafts = new ConcurrentHashMap<>();
    private final Map<String, java.util.Deque<DeviceOperation>> deviceOperations =
            new ConcurrentHashMap<>();
    private final AtomicInteger commandSequence = new AtomicInteger();

    AgriDemoData() {
        PRODUCTS.forEach(product -> stockKg.put(product.name(), product.initialStockKg()));
        DEVICES.forEach(device -> deviceStates.put(device.deviceId(), device.initialState()));
    }

    // ==================== 传感器 ====================

    List<Greenhouse> greenhouses() {
        return GREENHOUSES;
    }

    /** 按编号（GH-2）、名称（2号棚）或数字（2）查找大棚。 */
    Optional<Greenhouse> findGreenhouse(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Optional.empty();
        }
        var normalized = keyword.trim().toUpperCase();
        return GREENHOUSES.stream()
                .filter(
                        greenhouse ->
                                greenhouse.code().equals(normalized)
                                        || greenhouse.name().equals(keyword.trim())
                                        || greenhouse.code().equals("GH-" + normalized)
                                        || greenhouse.crop().equals(keyword.trim()))
                .findFirst();
    }

    Optional<Metric> findMetric(String key) {
        return key == null ? Optional.empty() : Optional.ofNullable(METRICS.get(key.trim()));
    }

    Map<String, Metric> metricCatalog() {
        return METRICS;
    }

    /** 某大棚某指标在指定日期的日均值；同一日期多次调用结果一致。 */
    double dailyValue(Greenhouse greenhouse, Metric metric, LocalDate date) {
        var daysAgo = ChronoUnit.DAYS.between(date, LocalDate.now());
        if ("GH-2".equals(greenhouse.code()) && "humidity".equals(metric.key()) && daysAgo <= 2) {
            return switch ((int) daysAgo) {
                case 0 -> 91.6;
                case 1 -> 93.1;
                default -> 92.4;
            };
        }
        var dayIndex = date.toEpochDay();
        var wave = Math.sin(dayIndex * 0.9 + greenhouse.phase() + metric.phase());
        return round(metric.base() + metric.amplitude() * wave, 1);
    }

    /** 判断日均值是否越界。 */
    static boolean overThreshold(Metric metric, double value) {
        return (metric.high() != null && value > metric.high())
                || (metric.low() != null && value < metric.low());
    }

    // ==================== 销售与库存 ====================

    List<Product> products() {
        return PRODUCTS;
    }

    Optional<Product> findProduct(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return PRODUCTS.stream().filter(product -> product.name().equals(name.trim())).findFirst();
    }

    int stockOf(Product product) {
        return stockKg.getOrDefault(product.name(), 0);
    }

    /** 指定天数内的销量（kg），按日均销量折算。 */
    static int salesKg(Product product, int days) {
        return product.dailySalesKg() * days;
    }

    // ==================== 预约 ====================

    BookingDraft saveDraft(Long ownerId, BookingRequest request) {
        purgeExpiredDrafts();
        var draft =
                new BookingDraft(
                        UUID.randomUUID().toString(),
                        ownerId,
                        request,
                        Instant.now().plus(DRAFT_TTL_MINUTES, ChronoUnit.MINUTES));
        drafts.put(draft.draftId(), draft);
        return draft;
    }

    /** 取出并作废草稿：草稿必须属于当前用户且未过期，同一草稿只能确认一次。 */
    Optional<BookingDraft> claimDraft(String draftId, Long ownerId) {
        if (draftId == null) {
            return Optional.empty();
        }
        var draft = drafts.get(draftId.trim());
        if (draft == null || !draft.ownerId().equals(ownerId)) {
            return Optional.empty();
        }
        if (!drafts.remove(draft.draftId(), draft) || draft.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(draft);
    }

    /** 扣减库存；库存不足时返回 false 且不扣减。 */
    boolean deductStock(String productName, int quantityKg) {
        var result = new boolean[1];
        stockKg.computeIfPresent(
                productName,
                (name, current) -> {
                    if (current < quantityKg) {
                        return current;
                    }
                    result[0] = true;
                    return current - quantityKg;
                });
        return result[0];
    }

    String nextBookingNo() {
        return "AGRI-%s-%04d"
                .formatted(
                        LocalDate.now().toString().replace("-", ""),
                        bookingSequence.incrementAndGet());
    }

    private void purgeExpiredDrafts() {
        var now = Instant.now();
        drafts.values().removeIf(draft -> draft.expiresAt().isBefore(now));
    }

    // ==================== 物联网设备 ====================

    List<Device> devices(Greenhouse greenhouse) {
        return DEVICES.stream()
                .filter(device -> device.greenhouseCode().equals(greenhouse.code()))
                .toList();
    }

    List<Device> devices() {
        return DEVICES;
    }

    /** 按设备编号（GH-2-FAN）查找，大小写不敏感。 */
    Optional<Device> findDevice(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return Optional.empty();
        }
        var normalized = deviceId.trim().toUpperCase();
        return DEVICES.stream().filter(device -> device.deviceId().equals(normalized)).findFirst();
    }

    /** 当前状态；滴灌等定时运行到期后视为已关闭。 */
    DeviceState stateOf(Device device) {
        var state = deviceStates.get(device.deviceId());
        if (state.on() && state.runUntil() != null && state.runUntil().isBefore(Instant.now())) {
            var stopped = new DeviceState(false, state.level(), state.mode(), null);
            deviceStates.replace(device.deviceId(), state, stopped);
            return stopped;
        }
        return state;
    }

    /** 某大棚某指标的实时读数：当日均值叠加各设备当前状态相对初始状态的效果。 */
    double realtimeValue(Greenhouse greenhouse, Metric metric) {
        var value = dailyValue(greenhouse, metric, LocalDate.now());
        for (var device : devices(greenhouse)) {
            value +=
                    device.type().effect(metric.key(), stateOf(device))
                            - device.type().effect(metric.key(), device.initialState());
        }
        if ("humidity".equals(metric.key()) || "soilMoisture".equals(metric.key())) {
            value = Math.max(0, Math.min(100, value));
        } else if ("light".equals(metric.key())) {
            value = Math.max(0, value);
        }
        return round(value, 1);
    }

    CommandDraft saveCommandDraft(Long ownerId, DeviceCommand command) {
        var now = Instant.now();
        commandDrafts.values().removeIf(draft -> draft.expiresAt().isBefore(now));
        var draft =
                new CommandDraft(
                        UUID.randomUUID().toString(),
                        ownerId,
                        command,
                        now.plus(COMMAND_DRAFT_TTL_MINUTES, ChronoUnit.MINUTES));
        commandDrafts.put(draft.draftId(), draft);
        return draft;
    }

    /** 取出并作废指令草稿：草稿必须属于当前用户且未过期，同一草稿只能执行一次。 */
    Optional<CommandDraft> claimCommandDraft(String draftId, Long ownerId) {
        if (draftId == null) {
            return Optional.empty();
        }
        var draft = commandDrafts.get(draftId.trim());
        if (draft == null || !draft.ownerId().equals(ownerId)) {
            return Optional.empty();
        }
        if (!commandDrafts.remove(draft.draftId(), draft)
                || draft.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(draft);
    }

    /** 执行控制指令：更新设备状态并切为手动模式，记录操作日志，返回新状态。 */
    DeviceState applyCommand(Device device, DeviceCommand command, String commandNo) {
        var current = stateOf(device);
        var next =
                switch (command.action()) {
                    case "ON" ->
                            new DeviceState(
                                    true,
                                    current.level(),
                                    "MANUAL",
                                    command.durationMinutes() == null
                                            ? null
                                            : Instant.now()
                                                    .plus(
                                                            command.durationMinutes(),
                                                            ChronoUnit.MINUTES));
                    case "OFF" -> new DeviceState(false, current.level(), "MANUAL", null);
                    case "SET_LEVEL" ->
                            new DeviceState(command.level() > 0, command.level(), "MANUAL", null);
                    default -> throw new IllegalArgumentException("不支持的动作：" + command.action());
                };
        deviceStates.put(device.deviceId(), next);
        var logs =
                deviceOperations.computeIfAbsent(
                        device.deviceId(),
                        ignored -> new java.util.concurrent.ConcurrentLinkedDeque<>());
        logs.addFirst(new DeviceOperation(commandNo, command, Instant.now()));
        while (logs.size() > MAX_OPERATION_LOGS) {
            logs.pollLast();
        }
        return next;
    }

    List<DeviceOperation> recentOperations(Device device, int limit) {
        var logs = deviceOperations.get(device.deviceId());
        return logs == null ? List.of() : logs.stream().limit(limit).toList();
    }

    String nextCommandNo() {
        return "IOT-%s-%04d"
                .formatted(
                        LocalDate.now().toString().replace("-", ""),
                        commandSequence.incrementAndGet());
    }

    private static List<Device> deviceCatalog() {
        var list = new ArrayList<Device>();
        for (var house : GREENHOUSES) {
            var code = house.code();
            var autoVent = "GH-1".equals(code);
            list.add(
                    new Device(
                            code + "-VENT",
                            code,
                            house.name() + "通风窗",
                            DeviceType.VENT,
                            true,
                            new DeviceState(
                                    autoVent,
                                    autoVent ? 30 : 0,
                                    autoVent ? "AUTO" : "MANUAL",
                                    null)));
            list.add(
                    new Device(
                            code + "-FAN",
                            code,
                            house.name() + "循环风机",
                            DeviceType.FAN,
                            true,
                            new DeviceState(false, 0, "MANUAL", null)));
            list.add(
                    new Device(
                            code + "-CURTAIN",
                            code,
                            house.name() + "湿帘",
                            DeviceType.WET_CURTAIN,
                            true,
                            new DeviceState(false, 0, "MANUAL", null)));
            list.add(
                    new Device(
                            code + "-SHADE",
                            code,
                            house.name() + "遮阳网",
                            DeviceType.SHADE,
                            true,
                            new DeviceState(false, 0, "MANUAL", null)));
            list.add(
                    new Device(
                            code + "-LIGHT",
                            code,
                            house.name() + "补光灯",
                            DeviceType.FILL_LIGHT,
                            !"GH-3".equals(code),
                            new DeviceState(false, 0, "MANUAL", null)));
            list.add(
                    new Device(
                            code + "-DRIP",
                            code,
                            house.name() + "滴灌阀",
                            DeviceType.DRIP_VALVE,
                            true,
                            new DeviceState(false, 0, "MANUAL", null)));
        }
        return List.copyOf(list);
    }

    static double round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    private static Map<String, Metric> metrics() {
        var list = new ArrayList<Metric>();
        list.add(new Metric("temperature", "温度", "℃", 24.0, 4.0, 0.0, 35.0, 8.0));
        list.add(new Metric("humidity", "空气湿度", "%", 74.0, 8.0, 0.7, 90.0, null));
        list.add(new Metric("light", "光照强度", "klux", 32.0, 9.0, 1.4, null, 10.0));
        list.add(new Metric("soilMoisture", "土壤含水率", "%", 38.0, 6.0, 2.1, null, 25.0));
        var map = new LinkedHashMap<String, Metric>();
        list.forEach(metric -> map.put(metric.key(), metric));
        return Map.copyOf(map);
    }

    /** 大棚；phase 让各棚的示例曲线错开。 */
    record Greenhouse(String code, String name, String crop, double phase) {}

    /** 监测指标；high / low 为告警阈值，可为空。 */
    record Metric(
            String key,
            String label,
            String unit,
            double base,
            double amplitude,
            double phase,
            Double high,
            Double low) {}

    /** 农产品；changePercent 为近期销量相对上一周期的变化。 */
    record Product(
            String name,
            BigDecimal pricePerKg,
            int dailySalesKg,
            double changePercent,
            int initialStockKg) {}

    /** 预约请求。 */
    record BookingRequest(
            String productName,
            int quantityKg,
            String bookingType,
            LocalDate date,
            String timeSlot,
            String contactName,
            String contactPhone,
            String address,
            BigDecimal amount) {}

    /** 预约草稿。 */
    record BookingDraft(String draftId, Long ownerId, BookingRequest request, Instant expiresAt) {}

    /**
     * 设备类型：label 为中文名，levelControl 表示支持开度（0 至 100），timed 表示开启需指定时长。
     *
     * <p>{@link #effect} 给出设备在某状态下对指标的影响量，实时读数只叠加当前状态与初始状态的差值。
     */
    enum DeviceType {
        VENT("通风窗", true, false),
        FAN("循环风机", false, false),
        WET_CURTAIN("湿帘", false, false),
        SHADE("遮阳网", true, false),
        FILL_LIGHT("补光灯", false, false),
        DRIP_VALVE("滴灌阀", false, true);

        private final String label;
        private final boolean levelControl;
        private final boolean timed;

        DeviceType(String label, boolean levelControl, boolean timed) {
            this.label = label;
            this.levelControl = levelControl;
            this.timed = timed;
        }

        String label() {
            return label;
        }

        boolean levelControl() {
            return levelControl;
        }

        boolean timed() {
            return timed;
        }

        double effect(String metricKey, DeviceState state) {
            var ratio = !state.on() ? 0.0 : levelControl ? state.level() / 100.0 : 1.0;
            return switch (this) {
                case VENT ->
                        switch (metricKey) {
                            case "humidity" -> -12.0 * ratio;
                            case "temperature" -> -4.0 * ratio;
                            default -> 0.0;
                        };
                case FAN ->
                        switch (metricKey) {
                            case "humidity" -> -5.0 * ratio;
                            case "temperature" -> -1.5 * ratio;
                            default -> 0.0;
                        };
                case WET_CURTAIN ->
                        switch (metricKey) {
                            case "humidity" -> 6.0 * ratio;
                            case "temperature" -> -5.0 * ratio;
                            default -> 0.0;
                        };
                case SHADE ->
                        switch (metricKey) {
                            case "light" -> -20.0 * ratio;
                            case "temperature" -> -3.0 * ratio;
                            default -> 0.0;
                        };
                case FILL_LIGHT -> "light".equals(metricKey) ? 12.0 * ratio : 0.0;
                case DRIP_VALVE -> "soilMoisture".equals(metricKey) ? 10.0 * ratio : 0.0;
            };
        }
    }

    /** 演示设备；online 为假时不接受控制。 */
    record Device(
            String deviceId,
            String greenhouseCode,
            String name,
            DeviceType type,
            boolean online,
            DeviceState initialState) {}

    /** 设备状态：on 开关，level 开度（仅开度类设备有意义），mode 为 AUTO / MANUAL，runUntil 为定时运行截止时间。 */
    record DeviceState(boolean on, int level, String mode, Instant runUntil) {}

    /** 控制指令：action 为 ON / OFF / SET_LEVEL。 */
    record DeviceCommand(
            String deviceId,
            String action,
            Integer level,
            Integer durationMinutes,
            String reason) {}

    /** 控制指令草稿。 */
    record CommandDraft(String draftId, Long ownerId, DeviceCommand command, Instant expiresAt) {}

    /** 已执行的操作记录。 */
    record DeviceOperation(String commandNo, DeviceCommand command, Instant executedAt) {}
}
