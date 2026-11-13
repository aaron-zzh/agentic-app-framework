package com.xuejiai.aaf.module.agri.agent.tool;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.tool.ToolCallDispatcher.ToolCallResult;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.agri.agent.tool.AgriDemoData.BookingRequest;
import com.xuejiai.aaf.module.agri.agent.tool.AgriDemoData.Greenhouse;
import com.xuejiai.aaf.module.agri.agent.tool.AgriDemoData.Metric;
import com.xuejiai.aaf.module.agri.agent.tool.AgriDemoData.Product;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 智慧农业演示工具：传感器趋势、异常、销售汇总、可预约产品与两步预约。
 *
 * <p>返回的都是 {@link AgriDemoData} 生成的示例数据，结果带 {@code demoData: true} 标记，接入真实业务数据后替换本类。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgriDemoTool {

    static final String SENSOR_TREND = "agriSensorTrend";
    static final String SENSOR_ALERTS = "agriSensorAlerts";
    static final String SALES_SUMMARY = "agriSalesSummary";
    static final String BOOKABLE_PRODUCTS = "agriBookableProducts";
    static final String DRAFT_BOOKING = "agriDraftBooking";
    static final String CONFIRM_BOOKING = "agriConfirmBooking";

    private static final Set<String> BOOKING_TYPES = Set.of("PICKING", "DELIVERY");
    private static final int MAX_SENSOR_DAYS = 30;
    private static final int MAX_SALES_DAYS = 90;

    private final AgriDemoData data;
    private final OperatorContext operatorContext;

    @Tool(description = "查询某个大棚某项环境指标最近 N 天的日均值趋势（示例数据）；只读。")
    public String agriSensorTrend(
            @ToolParam(description = "大棚，支持编号 GH-2、名称 2号棚、数字 2 或作物名 番茄") String greenhouse,
            @ToolParam(
                            description =
                                    "指标：temperature 温度 / humidity 空气湿度 / light 光照 / soilMoisture 土壤含水率")
                    String metric,
            @ToolParam(description = "天数 1 至 30，缺省 7", required = false) Integer days) {
        return invoke(
                SENSOR_TREND,
                () -> {
                    var house =
                            data.findGreenhouse(greenhouse)
                                    .orElseThrow(() -> invalid("未找到大棚：" + greenhouse));
                    var item =
                            data.findMetric(metric).orElseThrow(() -> invalid("不支持的指标：" + metric));
                    var range = range(days, 7, MAX_SENSOR_DAYS, "天数");
                    var points = new ArrayList<Map<String, Object>>();
                    var values = new ArrayList<Double>();
                    for (var offset = range - 1; offset >= 0; offset--) {
                        var date = LocalDate.now().minusDays(offset);
                        var value = data.dailyValue(house, item, date);
                        values.add(value);
                        points.add(Map.of("date", date.toString(), "value", value));
                    }
                    var output = demoOutput();
                    output.put("greenhouse", greenhouseView(house));
                    output.put("metric", metricView(item));
                    output.put("points", points);
                    output.put("summary", summary(item, values));
                    return output;
                });
    }

    @Tool(description = "扫描全部大棚最近 N 天的环境指标，列出超过告警阈值的异常（示例数据）；只读。")
    public String agriSensorAlerts(
            @ToolParam(description = "天数 1 至 30，缺省 7", required = false) Integer days) {
        return invoke(
                SENSOR_ALERTS,
                () -> {
                    var range = range(days, 7, MAX_SENSOR_DAYS, "天数");
                    var alerts = new ArrayList<Map<String, Object>>();
                    for (var house : data.greenhouses()) {
                        for (var item : data.metricCatalog().values()) {
                            var abnormalDates = new ArrayList<String>();
                            var peak = Double.NaN;
                            for (var offset = range - 1; offset >= 0; offset--) {
                                var date = LocalDate.now().minusDays(offset);
                                var value = data.dailyValue(house, item, date);
                                if (AgriDemoData.overThreshold(item, value)) {
                                    abnormalDates.add(date.toString());
                                    peak = Double.isNaN(peak) ? value : extreme(item, peak, value);
                                }
                            }
                            if (abnormalDates.isEmpty()) {
                                continue;
                            }
                            var alert = new LinkedHashMap<String, Object>();
                            alert.put("greenhouse", greenhouseView(house));
                            alert.put("metric", metricView(item));
                            alert.put("abnormalDays", abnormalDates.size());
                            alert.put("dates", abnormalDates);
                            alert.put("peakValue", peak);
                            alerts.add(alert);
                        }
                    }
                    var output = demoOutput();
                    output.put("days", range);
                    output.put("alerts", alerts);
                    return output;
                });
    }

    @Tool(description = "汇总最近 N 天农产品的销量、销售额、库存与环比变化，按销售额降序（示例数据）；只读。")
    public String agriSalesSummary(
            @ToolParam(description = "产品名，如 番茄；不传则返回全部产品", required = false) String product,
            @ToolParam(description = "天数 7 至 90，缺省 30", required = false) Integer days) {
        return invoke(
                SALES_SUMMARY,
                () -> {
                    var range = range(days, 30, MAX_SALES_DAYS, "天数");
                    if (range < 7) {
                        throw invalid("天数必须在 7 至 90 之间");
                    }
                    List<Product> products =
                            product == null || product.isBlank()
                                    ? data.products()
                                    : List.of(
                                            data.findProduct(product)
                                                    .orElseThrow(
                                                            () -> invalid("未找到产品：" + product)));
                    var rows =
                            products.stream()
                                    .map(item -> salesRow(item, range))
                                    .sorted(
                                            Comparator.comparing(
                                                            (Map<String, Object> row) ->
                                                                    (BigDecimal) row.get("revenue"))
                                                    .reversed())
                                    .toList();
                    var output = demoOutput();
                    output.put("days", range);
                    output.put("products", rows);
                    return output;
                });
    }

    @Tool(description = "列出可预约的农产品、当前库存、单价、预约方式与未来可预约日期和时段（示例数据）；只读。")
    public String agriBookableProducts() {
        return invoke(
                BOOKABLE_PRODUCTS,
                () -> {
                    var dates = new ArrayList<String>();
                    for (var offset = 1; offset <= 7; offset++) {
                        dates.add(LocalDate.now().plusDays(offset).toString());
                    }
                    var products =
                            data.products().stream()
                                    .map(
                                            item -> {
                                                var row = new LinkedHashMap<String, Object>();
                                                row.put("product", item.name());
                                                row.put("stockKg", data.stockOf(item));
                                                row.put("pricePerKg", item.pricePerKg());
                                                return row;
                                            })
                                    .toList();
                    var output = demoOutput();
                    output.put("products", products);
                    output.put("bookingTypes", Map.of("PICKING", "到园采摘", "DELIVERY", "配送到家"));
                    output.put("dates", dates);
                    output.put("timeSlots", AgriDemoData.TIME_SLOTS);
                    return output;
                });
    }

    @Tool(description = "起草采摘或配送预约，保存十分钟草稿但不创建预约；必须向用户逐项复述并等待明确确认。")
    public String agriDraftBooking(
            @ToolParam(description = "产品名，须来自 agriBookableProducts") String product,
            @ToolParam(description = "数量，单位 kg，正整数") Integer quantityKg,
            @ToolParam(description = "预约方式：PICKING 到园采摘 / DELIVERY 配送到家") String bookingType,
            @ToolParam(description = "预约日期 yyyy-MM-dd，须在未来 1 至 14 天内") String date,
            @ToolParam(description = "时段：09:00-11:00 或 14:00-16:00") String timeSlot,
            @ToolParam(description = "联系人称呼") String contactName,
            @ToolParam(description = "联系电话，由用户提供", required = false) String contactPhone,
            @ToolParam(description = "配送地址，配送时必填", required = false) String address) {
        return invoke(
                DRAFT_BOOKING,
                () -> {
                    var item =
                            data.findProduct(product)
                                    .orElseThrow(() -> invalid("未找到产品：" + product));
                    if (quantityKg == null || quantityKg <= 0 || quantityKg > 500) {
                        throw invalid("数量必须在 1 至 500 kg 之间");
                    }
                    if (quantityKg > data.stockOf(item)) {
                        throw invalid("库存不足，当前库存 " + data.stockOf(item) + " kg");
                    }
                    var type = requireText(bookingType, "预约方式").toUpperCase();
                    if (!BOOKING_TYPES.contains(type)) {
                        throw invalid("预约方式只能是 PICKING 或 DELIVERY");
                    }
                    var bookingDate = LocalDate.parse(requireText(date, "预约日期"));
                    if (!bookingDate.isAfter(LocalDate.now())
                            || bookingDate.isAfter(
                                    LocalDate.now()
                                            .plusDays(AgriDemoData.MAX_BOOKING_DAYS_AHEAD))) {
                        throw invalid("预约日期须在未来 1 至 14 天内");
                    }
                    var slot = requireText(timeSlot, "时段");
                    if (!AgriDemoData.TIME_SLOTS.contains(slot)) {
                        throw invalid("时段只能是 " + String.join(" 或 ", AgriDemoData.TIME_SLOTS));
                    }
                    var deliveryAddress = trimToNull(address);
                    if ("DELIVERY".equals(type) && deliveryAddress == null) {
                        throw invalid("配送预约必须提供配送地址");
                    }
                    var request =
                            new BookingRequest(
                                    item.name(),
                                    quantityKg,
                                    type,
                                    bookingDate,
                                    slot,
                                    requireText(contactName, "联系人"),
                                    trimToNull(contactPhone),
                                    deliveryAddress,
                                    item.pricePerKg().multiply(BigDecimal.valueOf(quantityKg)));
                    var draft = data.saveDraft(currentOwnerId(), request);
                    var output = demoOutput();
                    output.put("draftId", draft.draftId());
                    output.put("expiresAt", draft.expiresAt().toString());
                    output.put("effective", false);
                    output.put("confirmationRequired", true);
                    output.put("summary", bookingView(request));
                    return output;
                });
    }

    @Tool(description = "仅在用户明确确认预约草稿后调用；扣减示例库存并生成预约单号。")
    public String agriConfirmBooking(
            @ToolParam(description = "agriDraftBooking 返回的草稿号") String draftId) {
        return invoke(
                CONFIRM_BOOKING,
                () -> {
                    var draft =
                            data.claimDraft(draftId, currentOwnerId())
                                    .orElseThrow(() -> invalid("草稿不存在或已过期，请重新起草"));
                    var request = draft.request();
                    if (!data.deductStock(request.productName(), request.quantityKg())) {
                        throw invalid("库存不足，预约未创建");
                    }
                    var output = demoOutput();
                    output.put("bookingNo", data.nextBookingNo());
                    output.put("status", "CONFIRMED");
                    output.put("booking", bookingView(request));
                    return output;
                });
    }

    private Map<String, Object> salesRow(Product item, int days) {
        var salesKg = AgriDemoData.salesKg(item, days);
        var row = new LinkedHashMap<String, Object>();
        row.put("product", item.name());
        row.put("salesKg", salesKg);
        row.put("revenue", item.pricePerKg().multiply(BigDecimal.valueOf(salesKg)));
        row.put("pricePerKg", item.pricePerKg());
        row.put("stockKg", data.stockOf(item));
        row.put("changePercent", item.changePercent());
        row.put(
                "stockDays",
                item.dailySalesKg() == 0 ? null : data.stockOf(item) / item.dailySalesKg());
        return row;
    }

    private static Map<String, Object> summary(Metric metric, List<Double> values) {
        var stats = values.stream().mapToDouble(Double::doubleValue).summaryStatistics();
        var over =
                values.stream().filter(value -> AgriDemoData.overThreshold(metric, value)).count();
        var output = new LinkedHashMap<String, Object>();
        output.put("avg", AgriDemoData.round(stats.getAverage(), 1));
        output.put("max", stats.getMax());
        output.put("min", stats.getMin());
        output.put("daysOverThreshold", over);
        return output;
    }

    private static double extreme(Metric metric, double current, double value) {
        return metric.high() != null && value > metric.high()
                ? Math.max(current, value)
                : Math.min(current, value);
    }

    private static Map<String, Object> greenhouseView(Greenhouse house) {
        return Map.of("code", house.code(), "name", house.name(), "crop", house.crop());
    }

    private static Map<String, Object> metricView(Metric metric) {
        var view = new LinkedHashMap<String, Object>();
        view.put("key", metric.key());
        view.put("label", metric.label());
        view.put("unit", metric.unit());
        view.put("high", metric.high());
        view.put("low", metric.low());
        return view;
    }

    private static Map<String, Object> bookingView(BookingRequest request) {
        var view = new LinkedHashMap<String, Object>();
        view.put("product", request.productName());
        view.put("quantityKg", request.quantityKg());
        view.put("bookingType", request.bookingType());
        view.put("date", request.date().toString());
        view.put("timeSlot", request.timeSlot());
        view.put("contactName", request.contactName());
        view.put("contactPhone", request.contactPhone());
        view.put("address", request.address());
        view.put("amount", request.amount());
        return view;
    }

    private static LinkedHashMap<String, Object> demoOutput() {
        var output = new LinkedHashMap<String, Object>();
        output.put("demoData", true);
        return output;
    }

    private static int range(Integer value, int defaultValue, int max, String field) {
        var resolved = value == null ? defaultValue : value;
        if (resolved < 1 || resolved > max) {
            throw invalid(field + "必须在 1 至 " + max + " 之间");
        }
        return resolved;
    }

    private Long currentOwnerId() {
        return operatorContext
                .currentOwnerId()
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED));
    }

    private String invoke(String toolName, Supplier<Object> action) {
        try {
            return JsonUtils.toJsonString(
                    ToolCallResult.success(toolName, JsonUtils.toJsonString(action.get())));
        } catch (BusinessException failure) {
            return error(toolName, String.valueOf(failure.getCode()), failure.getMessage());
        } catch (IllegalArgumentException | java.time.format.DateTimeParseException failure) {
            return error(toolName, "INVALID_ARGUMENT", failure.getMessage());
        } catch (RuntimeException failure) {
            log.error("[AgriDemoTool] 工具调用失败: tool={}, err={}", toolName, failure.getMessage());
            return error(toolName, "AGRI_TOOL_ERROR", "农业演示工具执行失败，请稍后重试");
        }
    }

    private static String error(String name, String code, String message) {
        return JsonUtils.toJsonString(
                ToolCallResult.error(
                        name, code, message == null || message.isBlank() ? "请求未完成" : message));
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw invalid(field + "不能为空");
        }
        return value.trim();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
