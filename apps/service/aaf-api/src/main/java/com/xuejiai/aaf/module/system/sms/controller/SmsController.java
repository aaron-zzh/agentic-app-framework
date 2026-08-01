package com.xuejiai.aaf.module.system.sms.controller;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.messaging.MessageChannel;
import com.xuejiai.aaf.framework.messaging.MessageRequest;
import com.xuejiai.aaf.framework.messaging.MessageService;
import com.xuejiai.aaf.framework.messaging.sms.SmsProperties;
import com.xuejiai.aaf.module.system.notify.domain.MessageLog;
import com.xuejiai.aaf.module.system.notify.repository.MessageLogRepository;
import com.xuejiai.aaf.module.system.sms.service.SmsTemplateService;
import com.xuejiai.aaf.module.system.sms.vo.SmsTemplateCreateDTO;
import com.xuejiai.aaf.module.system.sms.vo.SmsTemplateUpdateDTO;
import com.xuejiai.aaf.module.system.sms.vo.SmsTemplateVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 短信管理接口（模板管理 + 日志查询 + 测试发送 + 厂商回调）。
 *
 * <p>短信发送统一走 {@link MessageService} 主链路，日志记录在 sys_message_log（filter channel='SMS'）。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Tag(name = "短信管理")
@RestController
@RequestMapping("/api/system/sms")
@RequiredArgsConstructor
public class SmsController {

    private static final String CHANNEL_SMS = MessageChannel.SMS.name();

    private final SmsTemplateService templateService;
    private final MessageService messageService;
    private final MessageLogRepository messageLogRepository;
    private final SmsProperties smsProperties;

    // ── 模板管理 ──────────────────────────────────────────────

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/templates")
    public Result<List<SmsTemplateVO>> listTemplates() {
        return Result.success(templateService.list());
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/templates")
    public Result<SmsTemplateVO> createTemplate(@Valid @RequestBody SmsTemplateCreateDTO dto) {
        return Result.success(templateService.create(dto));
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PutMapping("/templates/{id}")
    public Result<SmsTemplateVO> updateTemplate(
            @PathVariable Long id, @Valid @RequestBody SmsTemplateUpdateDTO dto) {
        return Result.success(templateService.update(id, dto));
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/templates/{id}")
    public Result<SmsTemplateVO> getTemplate(@PathVariable Long id) {
        return Result.success(templateService.getById(id));
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @DeleteMapping("/templates/{id}")
    public Result<Void> deleteTemplate(@PathVariable Long id) {
        templateService.delete(id);
        return Result.success(null);
    }

    // ── 日志查询 ──────────────────────────────────────────────

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/logs")
    public Result<PageResult<MessageLog>> listLogs(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        var page =
                messageLogRepository.findByChannelAndDeletedFalse(
                        CHANNEL_SMS,
                        PageRequest.of(
                                pageNo - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime")));
        return Result.success(new PageResult<>(page.getContent(), page.getTotalElements()));
    }

    // ── 测试发送 ──────────────────────────────────────────────

    /**
     * 测试短信发送（实际调用厂商 API，会产生真实费用）。
     *
     * <p>用于验证短信配置是否正确、模板是否可用。 仅在 aaf.messaging.sms.provider 已配置时可用。
     *
     * <p>M21：原实现直连 {@link MessageService}，无任何环境隔离或号码限制——生产环境下管理员正常测试配置就会
     * 误发真实短信并产生费用，不需要恶意行为即可触发。现受 {@code aaf.messaging.sms.test-send} 双重约束：
     * {@code enabled=false} 时整体禁用；配置 {@code phoneWhitelist} 后仅白名单号码可被测试发送。
     */
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "测试短信发送", description = "实际调用厂商 API 发送短信，会产生真实费用，仅用于配置验证")
    @PostMapping("/test-send")
    public Result<String> testSend(@Valid @RequestBody SmsTestSendDTO dto) {
        var testSendConfig = smsProperties.testSend();
        if (!testSendConfig.enabled()) {
            throw new IllegalStateException("当前环境已禁用短信测试发送（aaf.messaging.sms.test-send.enabled=false）");
        }
        if (!testSendConfig.phoneWhitelist().isEmpty()
                && !testSendConfig.phoneWhitelist().contains(dto.phone())) {
            throw new IllegalArgumentException("测试发送号码不在白名单内，请检查 aaf.messaging.sms.test-send.phone-whitelist 配置");
        }
        var variables =
                dto.params() == null
                        ? Map.<String, Object>of()
                        : dto.params().entrySet().stream()
                                .collect(
                                        java.util.stream.Collectors.toMap(
                                                Map.Entry::getKey, e -> (Object) e.getValue()));
        messageService.send(
                new MessageRequest(
                        MessageChannel.SMS, dto.code(), List.of(dto.phone()), variables, null));
        return Result.success("发送成功（异步）");
    }

    // ── 厂商回调 ──────────────────────────────────────────────

    /**
     * 阿里云短信状态回调。阿里云配置回调地址：POST /api/system/sms/callback/aliyun
     *
     * <p>m17：原实现连日志都未记录，且此前受类级 {@code @PreAuthorize} 限制——厂商回调不会携带平台 JWT，
     * 实际永远 403，端点等于不可达。现已在 {@code SecurityConfig} 加入公开路径豁免，并记录访问日志用于
     * 排查/后续对接验签开发。**当前仍是占位**：未做阿里云回调签名校验（官方回调机制细节需核对最新文档后
     * 单独实现，不在本轮臆造），也未解析 body 更新 sys_message_log；生产环境暴露该端点前必须补齐验签，
     * 否则任何人可推送伪造状态（不影响短信本身发送，仅影响状态记录的可信度）。
     */
    @PostMapping("/callback/aliyun")
    public Result<Void> aliyunCallback(@RequestBody String body) {
        // 阿里云回调为 JSON 数组：
        // [{"phone_number":"...","send_time":"...","err_code":"...","err_msg":"...","biz_id":"...","out_id":"..."}]
        log.info("[SMS回调] 阿里云状态回调（占位，未验签未落库）: {}", body);
        return Result.success(null);
    }

    /**
     * 腾讯云短信状态回调。腾讯云配置回调地址：POST /api/system/sms/callback/tencent
     *
     * <p>m17：同上，占位 + 补日志 + 公开路径豁免，验签与状态落库未实现。
     */
    @PostMapping("/callback/tencent")
    public Result<Void> tencentCallback(@RequestBody String body) {
        log.info("[SMS回调] 腾讯云状态回调（占位，未验签未落库）: {}", body);
        return Result.success(null);
    }

    // ── DTO ───────────────────────────────────────────────────

    /**
     * 测试发送请求。
     *
     * @param phone 手机号（11位）
     * @param code 业务场景编码（对应 sys_sms_template.code，如 register/login）
     * @param params 模板变量，如 {"code":"1234"}
     */
    public record SmsTestSendDTO(
            @NotBlank String phone, @NotBlank String code, Map<String, String> params) {}
}
