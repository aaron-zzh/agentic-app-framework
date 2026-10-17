package com.xuejiai.aaf.module.elder.agent.tool;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.enums.elder.AuthorizationScopeEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.tool.ToolCallDispatcher.ToolCallResult;
import com.xuejiai.aaf.module.elder.agent.tool.ElderDraftStore.MedicationDraft;
import com.xuejiai.aaf.module.elder.agent.tool.ElderDraftStore.ServiceOrderDraft;
import com.xuejiai.aaf.module.elder.album.api.AlbumQueryApi;
import com.xuejiai.aaf.module.elder.guard.api.GuardQueryApi;
import com.xuejiai.aaf.module.elder.mall.api.ServiceCatalogApi;
import com.xuejiai.aaf.module.elder.mall.api.ServiceOrderApi;
import com.xuejiai.aaf.module.elder.profile.api.MedicationApi;
import com.xuejiai.aaf.module.elder.rehab.api.RehabGuideApi;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 养老助理业务工具；只调用各子域 api，不访问任何实体或仓储。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ElderAssistantTool {

    static final String QUERY_ELDER_OVERVIEW = "queryElderOverview";
    static final String LIST_AVAILABLE_SERVICES = "listAvailableServices";
    static final String DRAFT_SERVICE_ORDER = "draftServiceOrder";
    static final String CONFIRM_SERVICE_ORDER = "confirmServiceOrder";
    static final String QUERY_SERVICE_ORDER_STATUS = "queryServiceOrderStatus";
    static final String QUERY_ALERT_STATUS = "queryAlertStatus";
    static final String QUERY_DEVICE_STATUS = "queryDeviceStatus";
    static final String LIST_MEDICATION_PLANS = "listMedicationPlans";
    static final String DRAFT_MEDICATION_PLAN = "draftMedicationPlan";
    static final String CONFIRM_MEDICATION_PLAN = "confirmMedicationPlan";
    static final String LIST_RECENT_PHOTOS = "listRecentPhotos";
    static final String SEARCH_REHAB_GUIDE = "searchRehabGuide";

    private static final int DEFAULT_RECENT_LIMIT = 10;
    private static final String DOSAGE_NOT_PROVIDED = "剂量未提供，需联系人核对";

    private final ElderToolContext toolContext;
    private final ElderDraftStore draftStore;
    private final ServiceCatalogApi serviceCatalogApi;
    private final ServiceOrderApi serviceOrderApi;
    private final GuardQueryApi guardQueryApi;
    private final MedicationApi medicationApi;
    private final AlbumQueryApi albumQueryApi;
    private final RehabGuideApi rehabGuideApi;

    @Tool(description = "查询当前受信上下文中长者的脱敏概况。不得要求或接收模型传入 elderId。")
    public String queryElderOverview() {
        return invoke(
                QUERY_ELDER_OVERVIEW,
                () -> {
                    var access =
                            toolContext.requireElder(AuthorizationScopeEnum.VIEW_PROFILE.getCode());
                    var overview = access.overview();
                    var output = new LinkedHashMap<String, Object>();
                    output.put("preferredName", textOrDefault(overview.preferredName(), "长者"));
                    output.put("gender", textOrDefault(overview.gender(), "未登记"));
                    output.put(
                            "age",
                            overview.birthDate() == null
                                    ? "未登记"
                                    : Period.between(overview.birthDate(), LocalDate.now())
                                            .getYears());
                    output.put("livingArrangement", overview.livingArrangement());
                    output.put("status", overview.status());
                    return output;
                });
    }

    @Tool(description = "列出当前可预约的养老服务项目；只查询，不创建预约。")
    public String listAvailableServices() {
        return invoke(
                LIST_AVAILABLE_SERVICES,
                () -> {
                    toolContext.currentOwnerId();
                    return serviceCatalogApi.listAvailableServices();
                });
    }

    @Tool(description = "起草服务预约，保存十分钟草稿但不创建预约；必须向用户复述并等待明确确认。")
    public String draftServiceOrder(
            @ToolParam(description = "服务项目 ID") Long serviceItemId,
            @ToolParam(description = "数量，缺省为 1", required = false) Integer quantity,
            @ToolParam(description = "预约时间，ISO 本地日期时间，如 2026-10-20T09:00:00") String scheduledAt,
            @ToolParam(description = "服务地址") String serviceAddress,
            @ToolParam(description = "备注", required = false) String notes) {
        return invoke(
                DRAFT_SERVICE_ORDER,
                () -> {
                    var access =
                            toolContext.requireElder(AuthorizationScopeEnum.BOOK_SERVICE.getCode());
                    var normalizedQuantity = quantity == null ? 1 : quantity;
                    var normalizedTime = LocalDateTime.parse(requireText(scheduledAt, "预约时间"));
                    var normalizedAddress = requireText(serviceAddress, "服务地址");
                    if (serviceItemId == null
                            || serviceItemId <= 0
                            || normalizedQuantity <= 0
                            || !normalizedTime.isAfter(LocalDateTime.now())
                            || normalizedAddress.length() > 500
                            || notes != null && notes.length() > 1000) {
                        throw new IllegalArgumentException("预约草稿字段不合法");
                    }
                    var draft =
                            new ServiceOrderDraft(
                                    serviceItemId,
                                    normalizedQuantity,
                                    normalizedTime,
                                    normalizedAddress,
                                    trimToNull(notes));
                    var receipt =
                            draftStore.saveServiceOrder(
                                    access.requestingOwnerId(), access.overview().id(), draft);
                    return draftSummary(
                            receipt,
                            Map.of(
                                    "serviceItemId", draft.serviceItemId(),
                                    "quantity", draft.quantity(),
                                    "scheduledAt", draft.scheduledAt().toString(),
                                    "serviceAddress", draft.serviceAddress()));
                });
    }

    @Tool(description = "仅在用户明确确认预约草稿后调用；创建的预约状态仍为待运营确认。")
    public String confirmServiceOrder(
            @ToolParam(description = "draftServiceOrder 返回的草稿号") String draftId) {
        return invoke(
                CONFIRM_SERVICE_ORDER,
                () -> {
                    var access =
                            toolContext.requireElder(AuthorizationScopeEnum.BOOK_SERVICE.getCode());
                    var claim =
                            draftStore.claimServiceOrder(
                                    draftId, access.requestingOwnerId(), access.overview().id());
                    try {
                        var draft = claim.payload();
                        return serviceOrderApi.createPendingOrder(
                                new ServiceOrderApi.CreatePendingOrderCommand(
                                        access.overview().id(),
                                        draft.serviceItemId(),
                                        draft.quantity(),
                                        draft.scheduledAt(),
                                        draft.serviceAddress(),
                                        draft.notes()));
                    } catch (RuntimeException failure) {
                        draftStore.restore(claim);
                        throw failure;
                    }
                });
    }

    @Tool(description = "查询当前受信长者最近的服务预约状态；只读。")
    public String queryServiceOrderStatus() {
        return invoke(
                QUERY_SERVICE_ORDER_STATUS,
                () -> {
                    var access =
                            toolContext.requireElder(AuthorizationScopeEnum.VIEW_SERVICE.getCode());
                    return serviceOrderApi.listRecentOrders(
                            access.overview().id(), DEFAULT_RECENT_LIMIT);
                });
    }

    @Tool(description = "查询当前受信长者最近的预警进展；只读，不认领、不处置预警。")
    public String queryAlertStatus() {
        return invoke(
                QUERY_ALERT_STATUS,
                () -> {
                    var access =
                            toolContext.requireElder(AuthorizationScopeEnum.VIEW_ALERT.getCode());
                    return guardQueryApi.listRecentAlerts(
                            access.overview().id(), DEFAULT_RECENT_LIMIT);
                });
    }

    @Tool(description = "查询当前受信长者绑定设备的在线、心跳、摄像头与隐私模式状态；只读。")
    public String queryDeviceStatus() {
        return invoke(
                QUERY_DEVICE_STATUS,
                () -> {
                    var access =
                            toolContext.requireElder(AuthorizationScopeEnum.VIEW_ALERT.getCode());
                    return guardQueryApi.listDevices(access.overview().id());
                });
    }

    @Tool(description = "查询当前受信长者的用药计划和今日服药事实；只读，不能修改或停用计划。")
    public String listMedicationPlans() {
        return invoke(
                LIST_MEDICATION_PLANS,
                () -> {
                    var access =
                            toolContext.requireElder(
                                    AuthorizationScopeEnum.VIEW_MEDICATION.getCode());
                    return medicationApi.listPlans(access.overview().id(), LocalDate.now());
                });
    }

    @Tool(description = "起草用药提醒计划，保存十分钟草稿但不生效；药名、剂量和时间均由用户提供，不得推断剂量。")
    public String draftMedicationPlan(
            @ToolParam(description = "药品名称，由用户提供") String medicationName,
            @ToolParam(description = "剂量说明，由用户提供；未提供时留作联系人核对", required = false)
                    String dosageInstructions,
            @ToolParam(description = "每日提醒时间，HH:mm 字符串数组") List<String> reminderTimes,
            @ToolParam(description = "开始日期 yyyy-MM-dd，缺省为今天", required = false) String startDate,
            @ToolParam(description = "结束日期 yyyy-MM-dd，可不传", required = false) String endDate) {
        return invoke(
                DRAFT_MEDICATION_PLAN,
                () -> {
                    var access =
                            toolContext.requireElder(
                                    AuthorizationScopeEnum.VIEW_MEDICATION.getCode());
                    if (reminderTimes == null || reminderTimes.isEmpty()) {
                        throw new IllegalArgumentException("每日提醒时间不能为空");
                    }
                    var normalizedName = requireText(medicationName, "药品名称");
                    var normalizedDosage =
                            textOrDefault(trimToNull(dosageInstructions), DOSAGE_NOT_PROVIDED);
                    var normalizedStart =
                            startDate == null || startDate.isBlank()
                                    ? LocalDate.now()
                                    : LocalDate.parse(startDate);
                    var normalizedEnd =
                            endDate == null || endDate.isBlank() ? null : LocalDate.parse(endDate);
                    if (normalizedName.length() > 200
                            || normalizedDosage.length() > 500
                            || reminderTimes.size() > 24
                            || reminderTimes.stream()
                                    .anyMatch(
                                            time ->
                                                    time == null
                                                            || !time.matches(
                                                                    "(?:[01]\\d|2[0-3]):[0-5]\\d"))
                            || normalizedEnd != null && normalizedEnd.isBefore(normalizedStart)) {
                        throw new IllegalArgumentException("用药草稿字段不合法");
                    }
                    var draft =
                            new MedicationDraft(
                                    normalizedName,
                                    normalizedDosage,
                                    reminderTimes,
                                    normalizedStart,
                                    normalizedEnd);
                    var receipt =
                            draftStore.saveMedication(
                                    access.requestingOwnerId(), access.overview().id(), draft);
                    var summary = new LinkedHashMap<String, Object>();
                    summary.put("medicationName", draft.medicationName());
                    summary.put("dosageInstructions", draft.dosageInstructions());
                    summary.put("reminderTimes", draft.reminderTimes());
                    summary.put("startDate", draft.startDate().toString());
                    if (draft.endDate() != null) {
                        summary.put("endDate", draft.endDate().toString());
                    }
                    return draftSummary(receipt, summary);
                });
    }

    @Tool(description = "仅在用户明确确认用药草稿后调用；创建立即生效的对话来源计划，并通知联系人核对。绝不用于停用或删除计划。")
    public String confirmMedicationPlan(
            @ToolParam(description = "draftMedicationPlan 返回的草稿号") String draftId) {
        return invoke(
                CONFIRM_MEDICATION_PLAN,
                () -> {
                    var access =
                            toolContext.requireElder(
                                    AuthorizationScopeEnum.VIEW_MEDICATION.getCode());
                    var claim =
                            draftStore.claimMedication(
                                    draftId, access.requestingOwnerId(), access.overview().id());
                    try {
                        var draft = claim.payload();
                        return medicationApi.createConversationPlan(
                                new MedicationApi.CreateConversationPlan(
                                        access.overview().id(),
                                        draft.medicationName(),
                                        draft.dosageInstructions(),
                                        draft.reminderTimes(),
                                        draft.startDate(),
                                        draft.endDate()));
                    } catch (RuntimeException failure) {
                        draftStore.restore(claim);
                        throw failure;
                    }
                });
    }

    @Tool(description = "查询当前受信长者最近照片的受控地址；只读，不删除照片。")
    public String listRecentPhotos(
            @ToolParam(description = "返回条数，1 至 20，缺省 10", required = false) Integer limit) {
        return invoke(
                LIST_RECENT_PHOTOS,
                () -> {
                    var access =
                            toolContext.requireElder(AuthorizationScopeEnum.VIEW_ALBUM.getCode());
                    var requested = limit == null ? DEFAULT_RECENT_LIMIT : limit;
                    if (requested <= 0 || requested > 20) {
                        throw new IllegalArgumentException("照片条数必须在 1 至 20 之间");
                    }
                    return albumQueryApi.listRecentPhotos(access.overview().id(), requested);
                });
    }

    @Tool(description = "仅依据已发布康复指导关联文档回答，返回引用与免责声明；资料无依据时明确拒绝编造。")
    public String searchRehabGuide(
            @ToolParam(description = "康复知识问题，不得要求个体化诊断、处方或训练强度") String question) {
        return invoke(
                SEARCH_REHAB_GUIDE,
                () -> {
                    toolContext.currentOwnerId();
                    return rehabGuideApi.answer(requireText(question, "问题"), 5);
                });
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
            log.error(
                    "[ElderAssistantTool] 工具调用失败: tool={}, err={}", toolName, failure.getMessage());
            return error(toolName, "ELDER_TOOL_ERROR", "养老工具执行失败，请稍后重试");
        }
    }

    private static Map<String, Object> draftSummary(
            ElderDraftStore.DraftReceipt receipt, Map<String, Object> summary) {
        var output = new LinkedHashMap<String, Object>();
        output.put("draftId", receipt.draftId());
        output.put("expiresAt", receipt.expiresAt().toString());
        output.put("effective", false);
        output.put("summary", summary);
        output.put("confirmationRequired", true);
        return output;
    }

    private static String error(String name, String code, String message) {
        return JsonUtils.toJsonString(
                ToolCallResult.error(
                        name, code, message == null || message.isBlank() ? "请求未完成" : message));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        return value.trim();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String textOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
