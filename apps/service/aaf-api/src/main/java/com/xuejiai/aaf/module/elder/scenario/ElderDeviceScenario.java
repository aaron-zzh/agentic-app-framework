package com.xuejiai.aaf.module.elder.scenario;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.enums.device.DeviceTypeEnum;
import com.xuejiai.aaf.common.enums.elder.AlertTypeEnum;
import com.xuejiai.aaf.common.enums.elder.AuthorizationScopeEnum;
import com.xuejiai.aaf.module.device.api.DeviceInboundHandler.DeviceInboundMessage;
import com.xuejiai.aaf.module.device.api.DeviceInboundHandler.InboundType;
import com.xuejiai.aaf.module.device.scenario.ChatIntercept;
import com.xuejiai.aaf.module.device.scenario.DeviceBindingContext;
import com.xuejiai.aaf.module.device.scenario.DeviceScenario;
import com.xuejiai.aaf.module.device.security.DevicePrincipal;
import com.xuejiai.aaf.module.elder.agent.tool.ElderToolContext;
import com.xuejiai.aaf.module.elder.guard.service.AlertEventService;
import com.xuejiai.aaf.module.elder.profile.api.ElderQueryApi;
import com.xuejiai.aaf.module.elder.profile.api.MedicationApi;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/** 养老设备场景：长者绑定、简报、紧急拦截、健康提示和工具上下文。 */
@Slf4j
@Component
public class ElderDeviceScenario implements DeviceScenario {

    public static final String KEY = "elder";
    private static final String SUBJECT_RESOURCE = "elder.profile.elder";
    private static final String DEFAULT_ROLE_KEY = "system.role.elder-companion";

    /** 养老场景下发给机器人的业务能力：用药与服务预约都是屏幕提醒。 */
    private static final Set<String> COMMAND_CAPABILITIES = Set.of("reminder.show");

    private static final String HEALTH_DISCLAIMER = "\n\n仅供健康知识参考，不构成诊疗建议，不能替代执业医师的面诊与处方。";
    private static final String EMERGENCY_REPLY =
            "这是紧急情况，请立即拨打 120，或请身边的人帮助联系急救。我已向养老守护系统发出一键求助预警。";
    private static final String EMERGENCY_ALERT_FAILED_REPLY =
            "这是紧急情况，请立即拨打 120，或请身边的人帮助联系急救。系统预警发送失败，请同时联系家属或养老运营人员。";
    private static final Set<String> EMERGENCY_TERMS =
            Set.of("胸痛", "呼吸困难", "喘不上气", "意识不清", "昏迷", "大出血", "救命");
    private static final Set<String> HEALTH_TERMS =
            Set.of("头晕", "疼", "痛", "发烧", "高热", "咳嗽", "血压", "血糖", "药", "康复", "不舒服");

    private final ElderQueryApi elderQueryApi;
    private final MedicationApi medicationApi;
    private final AlertEventService alertEventService;
    private final ElderToolContext toolContext;

    public ElderDeviceScenario(
            ElderQueryApi elderQueryApi,
            MedicationApi medicationApi,
            AlertEventService alertEventService,
            ElderToolContext toolContext) {
        this.elderQueryApi = elderQueryApi;
        this.medicationApi = medicationApi;
        this.alertEventService = alertEventService;
        this.toolContext = toolContext;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String name() {
        return "养老";
    }

    @Override
    public String defaultRoleKey() {
        return DEFAULT_ROLE_KEY;
    }

    @Override
    public String supportedSubjectResourceKey() {
        return SUBJECT_RESOURCE;
    }

    @Override
    public String subjectRequiredHint() {
        return "请先在「养老 → 长者」中新建长者档案，再在场景主体里选择该长者";
    }

    @Override
    public Set<String> commandCapabilities() {
        return COMMAND_CAPABILITIES;
    }

    @Override
    public String commandDeviceType() {
        return DeviceTypeEnum.ROBOT.getCode();
    }

    @Override
    public void validateBinding(DeviceBindingContext context) {
        elderQueryApi.requireAccess(
                context.subjectId(), AuthorizationScopeEnum.VIEW_PROFILE.getCode());
    }

    @Override
    public Map<String, Object> briefExtras(DevicePrincipal principal) {
        var subjectId = principal.subjectId();
        var profile = elderQueryApi.requireRobotBrief(subjectId);
        var zoneId = ZoneId.systemDefault();
        var today = LocalDate.now(zoneId);
        var reminders =
                medicationApi
                        .listDuePlans(
                                today.atStartOfDay(zoneId).toInstant(),
                                today.plusDays(1).atStartOfDay(zoneId).toInstant(),
                                zoneId)
                        .stream()
                        .filter(plan -> subjectId.equals(plan.elderId()))
                        .map(
                                plan -> {
                                    var item = new LinkedHashMap<String, Object>();
                                    item.put("planId", plan.planId());
                                    putIfPresent(item, "medicationName", plan.medicationName());
                                    putIfPresent(
                                            item, "dosageInstructions", plan.dosageInstructions());
                                    item.put("scheduledAt", plan.scheduledAt().toString());
                                    return Map.copyOf(item);
                                })
                        .toList();
        var values = new LinkedHashMap<String, Object>();
        putIfPresent(values, "salutation", profile.preferredName());
        putIfPresent(values, "preferences", profile.preferences());
        putIfPresent(values, "taboos", profile.taboos());
        values.put("todayReminders", reminders);
        return Map.copyOf(values);
    }

    @Override
    public Optional<ChatIntercept> preChat(DevicePrincipal principal, String text) {
        if (!containsAny(text, EMERGENCY_TERMS)) {
            return Optional.empty();
        }
        try {
            var now = Instant.now();
            var eventId = "voice-help-" + UUID.randomUUID();
            alertEventService.handle(
                    new DeviceInboundMessage(
                            principal.deviceId(),
                            principal.deviceKey(),
                            principal.scenarioKey(),
                            InboundType.ALERT,
                            eventId,
                            eventId,
                            null,
                            null,
                            "voice-gateway",
                            0,
                            now,
                            null,
                            Map.of(
                                    "type",
                                    AlertTypeEnum.HELP_REQUEST.getCode(),
                                    "confidence",
                                    1,
                                    "payload",
                                    Map.of("source", "VOICE_GATEWAY"))));
            return Optional.of(new ChatIntercept(EMERGENCY_REPLY));
        } catch (RuntimeException exception) {
            log.error(
                    "设备语音紧急预警发送失败: deviceId={}, subjectId={}, err={}",
                    principal.deviceId(),
                    principal.subjectId(),
                    exception.getMessage());
            return Optional.of(new ChatIntercept(EMERGENCY_ALERT_FAILED_REPLY));
        }
    }

    @Override
    public Flux<String> postChat(DevicePrincipal principal, String text, Flux<String> stream) {
        return containsAny(text, HEALTH_TERMS)
                ? stream.concatWithValues(HEALTH_DISCLAIMER)
                : stream;
    }

    @Override
    public AutoCloseable openToolScope(DevicePrincipal principal) {
        return toolContext.openTrustedElder(
                principal.ownerUserId(), principal.subjectId(), principal.deviceId());
    }

    @Override
    public Map<String, Object> chatVariables(DevicePrincipal principal) {
        return Map.of("robotBrief", briefExtras(principal));
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private static boolean containsAny(String text, Set<String> terms) {
        return text != null && terms.stream().anyMatch(text::contains);
    }
}
