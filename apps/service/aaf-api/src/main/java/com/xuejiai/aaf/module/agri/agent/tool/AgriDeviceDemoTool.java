package com.xuejiai.aaf.module.agri.agent.tool;

import java.util.ArrayList;
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
import com.xuejiai.aaf.module.agri.agent.tool.AgriDemoData.Device;
import com.xuejiai.aaf.module.agri.agent.tool.AgriDemoData.DeviceCommand;
import com.xuejiai.aaf.module.agri.agent.tool.AgriDemoData.DeviceState;
import com.xuejiai.aaf.module.agri.agent.tool.AgriDemoData.Greenhouse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 智慧农业物联网演示工具：实时读数、设备列表、设备状态与两步设备控制。
 *
 * <p>设备与状态全部来自 {@link AgriDemoData} 的内存示例数据，结果带 {@code demoData: true} 标记，不下发任何真实设备指令。
 * 接入真实设备时，确认工具改为调用 {@code module.device} 的 {@code DeviceMessageApi}，其余工具不变。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgriDeviceDemoTool {

    static final String SENSOR_REALTIME = "agriSensorRealtime";
    static final String DEVICE_LIST = "agriDeviceList";
    static final String DEVICE_STATUS = "agriDeviceStatus";
    static final String DRAFT_COMMAND = "agriDraftDeviceCommand";
    static final String CONFIRM_COMMAND = "agriConfirmDeviceCommand";

    private static final Set<String> ACTIONS = Set.of("ON", "OFF", "SET_LEVEL");
    private static final int RECENT_OPERATION_LIMIT = 5;

    private final AgriDemoData data;
    private final OperatorContext operatorContext;

    @Tool(description = "查询某个大棚当前的温度、空气湿度、光照、土壤含水率实时读数，已反映设备当前运行状态（示例数据）；只读。")
    public String agriSensorRealtime(
            @ToolParam(description = "大棚，支持编号 GH-2、名称 2号棚、数字 2 或作物名 番茄") String greenhouse) {
        return invoke(
                SENSOR_REALTIME,
                () -> {
                    var house = requireGreenhouse(greenhouse);
                    var readings = new ArrayList<Map<String, Object>>();
                    for (var metric : data.metricCatalog().values()) {
                        var value = data.realtimeValue(house, metric);
                        var row = new LinkedHashMap<String, Object>();
                        row.put("key", metric.key());
                        row.put("label", metric.label());
                        row.put("unit", metric.unit());
                        row.put("value", value);
                        row.put("high", metric.high());
                        row.put("low", metric.low());
                        row.put("overThreshold", AgriDemoData.overThreshold(metric, value));
                        readings.add(row);
                    }
                    var output = demoOutput();
                    output.put("greenhouse", greenhouseView(house));
                    output.put("readAt", java.time.Instant.now().toString());
                    output.put("readings", readings);
                    return output;
                });
    }

    @Tool(description = "列出大棚内的物联网设备及其在线状态、开关、开度、运行模式（示例数据）；只读。不传大棚则列出全部。")
    public String agriDeviceList(
            @ToolParam(description = "大棚，支持编号、名称、数字或作物名；不传返回全部大棚", required = false)
                    String greenhouse) {
        return invoke(
                DEVICE_LIST,
                () -> {
                    List<Device> devices =
                            greenhouse == null || greenhouse.isBlank()
                                    ? data.devices()
                                    : data.devices(requireGreenhouse(greenhouse));
                    var output = demoOutput();
                    output.put("devices", devices.stream().map(this::deviceView).toList());
                    return output;
                });
    }

    @Tool(description = "查询单台设备的详细状态与最近 5 条操作记录（示例数据）；只读。")
    public String agriDeviceStatus(
            @ToolParam(description = "设备编号，如 GH-2-FAN，须来自 agriDeviceList") String deviceId) {
        return invoke(
                DEVICE_STATUS,
                () -> {
                    var device = requireDevice(deviceId);
                    var operations =
                            data.recentOperations(device, RECENT_OPERATION_LIMIT).stream()
                                    .map(
                                            operation -> {
                                                var row = new LinkedHashMap<String, Object>();
                                                row.put("commandNo", operation.commandNo());
                                                row.put(
                                                        "executedAt",
                                                        operation.executedAt().toString());
                                                row.put(
                                                        "command",
                                                        commandView(operation.command()));
                                                return row;
                                            })
                                    .toList();
                    var output = demoOutput();
                    output.put("device", deviceView(device));
                    output.put("recentOperations", operations);
                    return output;
                });
    }

    @Tool(description = "起草一条设备控制指令，保存十分钟草稿但不执行；返回预期效果与提示。必须向用户复述设备、动作、参数与理由并等待明确确认。")
    public String agriDraftDeviceCommand(
            @ToolParam(description = "设备编号，如 GH-2-FAN，须来自 agriDeviceList") String deviceId,
            @ToolParam(description = "动作：ON 开启 / OFF 关闭 / SET_LEVEL 设置开度（仅通风窗、遮阳网）") String action,
            @ToolParam(description = "开度 0 至 100，SET_LEVEL 时必填", required = false) Integer level,
            @ToolParam(description = "运行分钟数 1 至 60，开启滴灌阀时必填", required = false)
                    Integer durationMinutes,
            @ToolParam(description = "控制理由，引用具体读数或趋势，如 2号棚湿度连续三天高于 90%") String reason) {
        return invoke(
                DRAFT_COMMAND,
                () -> {
                    var device = requireDevice(deviceId);
                    if (!device.online()) {
                        throw invalid(device.name() + " 当前离线，无法控制，请先检查设备网络");
                    }
                    var normalizedAction = requireText(action, "动作").toUpperCase();
                    if (!ACTIONS.contains(normalizedAction)) {
                        throw invalid("动作只能是 ON、OFF 或 SET_LEVEL");
                    }
                    var type = device.type();
                    Integer resolvedLevel = null;
                    Integer resolvedDuration = null;
                    if ("SET_LEVEL".equals(normalizedAction)) {
                        if (!type.levelControl()) {
                            throw invalid(type.label() + " 不支持设置开度，请使用 ON 或 OFF");
                        }
                        if (level == null || level < 0 || level > 100) {
                            throw invalid("开度必须在 0 至 100 之间");
                        }
                        resolvedLevel = level;
                    }
                    if ("ON".equals(normalizedAction) && type.levelControl()) {
                        throw invalid(type.label() + " 请使用 SET_LEVEL 指定开度");
                    }
                    if ("ON".equals(normalizedAction) && type.timed()) {
                        if (durationMinutes == null
                                || durationMinutes < 1
                                || durationMinutes > AgriDemoData.MAX_IRRIGATION_MINUTES) {
                            throw invalid(
                                    "开启滴灌必须指定 1 至 "
                                            + AgriDemoData.MAX_IRRIGATION_MINUTES
                                            + " 分钟的运行时长");
                        }
                        resolvedDuration = durationMinutes;
                    }
                    var command =
                            new DeviceCommand(
                                    device.deviceId(),
                                    normalizedAction,
                                    resolvedLevel,
                                    resolvedDuration,
                                    requireText(reason, "控制理由"));
                    var current = data.stateOf(device);
                    var warnings = new ArrayList<String>();
                    if ("AUTO".equals(current.mode())) {
                        warnings.add("设备当前为自动模式，执行后将切换为手动模式，自动策略暂停");
                    }
                    if (sameAsCurrent(command, current)) {
                        warnings.add("设备当前已处于该状态，执行后不会有变化");
                    }
                    var draft = data.saveCommandDraft(currentOwnerId(), command);
                    log.info(
                            "[农业设备] 控制草稿已起草：draftId={}，deviceId={}，action={}，level={}，durationMinutes={}",
                            draft.draftId(),
                            device.deviceId(),
                            normalizedAction,
                            resolvedLevel,
                            resolvedDuration);
                    var output = demoOutput();
                    output.put("draftId", draft.draftId());
                    output.put("expiresAt", draft.expiresAt().toString());
                    output.put("effective", false);
                    output.put("confirmationRequired", true);
                    output.put("device", deviceView(device));
                    output.put("command", commandView(command));
                    output.put("expectedEffect", expectedEffect(device, command));
                    output.put("warnings", warnings);
                    return output;
                });
    }

    @Tool(description = "仅在用户明确确认控制草稿后调用；执行示例设备指令并返回新状态与指令编号。")
    public String agriConfirmDeviceCommand(
            @ToolParam(description = "agriDraftDeviceCommand 返回的草稿号") String draftId) {
        return invoke(
                CONFIRM_COMMAND,
                () -> {
                    var draft =
                            data.claimCommandDraft(draftId, currentOwnerId())
                                    .orElseThrow(() -> invalid("草稿不存在或已过期，请重新起草"));
                    var command = draft.command();
                    var device = requireDevice(command.deviceId());
                    if (!device.online()) {
                        throw invalid(device.name() + " 当前离线，指令未执行");
                    }
                    var commandNo = data.nextCommandNo();
                    data.applyCommand(device, command, commandNo);
                    log.info(
                            "[农业设备] 控制指令已执行：commandNo={}，deviceId={}，action={}，level={}，durationMinutes={}",
                            commandNo,
                            device.deviceId(),
                            command.action(),
                            command.level(),
                            command.durationMinutes());
                    var output = demoOutput();
                    output.put("commandNo", commandNo);
                    output.put("status", "EXECUTED");
                    output.put("device", deviceView(device));
                    output.put("command", commandView(command));
                    return output;
                });
    }

    private static boolean sameAsCurrent(DeviceCommand command, DeviceState current) {
        return switch (command.action()) {
            case "ON" -> current.on() && command.durationMinutes() == null;
            case "OFF" -> !current.on();
            case "SET_LEVEL" -> command.level() != null && command.level() == current.level();
            default -> false;
        };
    }

    /** 按设备效果模型估算执行后各指标变化量，只列出受影响的指标。 */
    private Map<String, Object> expectedEffect(Device device, DeviceCommand command) {
        var current = data.stateOf(device);
        var next =
                switch (command.action()) {
                    case "ON" -> new DeviceState(true, current.level(), "MANUAL", null);
                    case "OFF" -> new DeviceState(false, current.level(), "MANUAL", null);
                    default ->
                            new DeviceState(command.level() > 0, command.level(), "MANUAL", null);
                };
        var effect = new LinkedHashMap<String, Object>();
        for (var metric : data.metricCatalog().values()) {
            var delta =
                    device.type().effect(metric.key(), next)
                            - device.type().effect(metric.key(), current);
            if (Math.abs(delta) >= 0.05) {
                effect.put(metric.label(), AgriDemoData.round(delta, 1) + " " + metric.unit());
            }
        }
        return effect;
    }

    private Map<String, Object> deviceView(Device device) {
        var state = data.stateOf(device);
        var view = new LinkedHashMap<String, Object>();
        view.put("deviceId", device.deviceId());
        view.put("name", device.name());
        view.put("type", device.type().name());
        view.put("typeLabel", device.type().label());
        view.put("greenhouseCode", device.greenhouseCode());
        view.put("online", device.online());
        view.put("on", state.on());
        if (device.type().levelControl()) {
            view.put("level", state.level());
        }
        view.put("mode", state.mode());
        view.put("runUntil", state.runUntil() == null ? null : state.runUntil().toString());
        return view;
    }

    private static Map<String, Object> commandView(DeviceCommand command) {
        var view = new LinkedHashMap<String, Object>();
        view.put("deviceId", command.deviceId());
        view.put("action", command.action());
        view.put("level", command.level());
        view.put("durationMinutes", command.durationMinutes());
        view.put("reason", command.reason());
        return view;
    }

    private static Map<String, Object> greenhouseView(Greenhouse house) {
        return Map.of("code", house.code(), "name", house.name(), "crop", house.crop());
    }

    private Greenhouse requireGreenhouse(String keyword) {
        return data.findGreenhouse(keyword).orElseThrow(() -> invalid("未找到大棚：" + keyword));
    }

    private Device requireDevice(String deviceId) {
        return data.findDevice(deviceId).orElseThrow(() -> invalid("未找到设备：" + deviceId));
    }

    private static LinkedHashMap<String, Object> demoOutput() {
        var output = new LinkedHashMap<String, Object>();
        output.put("demoData", true);
        return output;
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
        } catch (IllegalArgumentException failure) {
            return error(toolName, "INVALID_ARGUMENT", failure.getMessage());
        } catch (RuntimeException failure) {
            log.error("[农业设备] 工具调用失败：tool={}，err={}", toolName, failure.getMessage());
            return error(toolName, "AGRI_DEVICE_TOOL_ERROR", "农业设备演示工具执行失败，请稍后重试");
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
}
