package com.xuejiai.aaf.module.system.sms.controller;

import java.util.List;
import java.util.Map;

import com.xuejiai.aaf.module.system.sms.service.SmsService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
import com.xuejiai.aaf.module.system.sms.domain.SmsLog;
import com.xuejiai.aaf.module.system.sms.domain.SmsTemplate;
import com.xuejiai.aaf.module.system.sms.repository.SmsLogRepository;
import com.xuejiai.aaf.module.system.sms.repository.SmsTemplateRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 短信管理接口（模板管理 + 日志查询 + 测试发送 + 厂商回调）。 */
@Slf4j
@Tag(name = "短信管理")
@RestController
@RequestMapping("/api/system/sms")
@RequiredArgsConstructor
public class SmsController {

    private final SmsTemplateRepository templateRepository;
    private final SmsLogRepository smsLogRepository;

    private final SmsService smsService;

    // ── 模板管理 ──────────────────────────────────────────────

    @GetMapping("/templates")
    public Result<List<SmsTemplate>> listTemplates() {
        return Result.success(templateRepository.findAll());
    }

    @PostMapping("/templates")
    public Result<SmsTemplate> createTemplate(@Valid @RequestBody SmsTemplateCreateDTO dto) {
        var entity = new SmsTemplate();
        entity.setCode(dto.code());
        entity.setName(dto.name());
        entity.setSignName(dto.signName());
        entity.setApiTemplateId(dto.apiTemplateId());
        entity.setParams(dto.params());
        entity.setProvider(dto.provider());
        entity.setStatus((short) 1);
        return Result.success(templateRepository.save(entity));
    }

    @PutMapping("/templates/{id}")
    public Result<SmsTemplate> updateTemplate(
            @PathVariable Long id, @Valid @RequestBody SmsTemplateUpdateDTO dto) {
        var entity =
                templateRepository
                        .findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("模板不存在"));
        if (dto.signName() != null) entity.setSignName(dto.signName());
        if (dto.apiTemplateId() != null) entity.setApiTemplateId(dto.apiTemplateId());
        if (dto.provider() != null) entity.setProvider(dto.provider());
        if (dto.status() != null) entity.setStatus(dto.status());
        return Result.success(templateRepository.save(entity));
    }

    // ── 日志查询 ──────────────────────────────────────────────

    @GetMapping("/logs")
    public Result<PageResult<SmsLog>> listLogs(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        var page =
                smsLogRepository.findAll(
                        PageRequest.of(
                                pageNo - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return Result.success(new PageResult<>(page.getContent(), page.getTotalElements()));
    }

    // ── 测试发送 ──────────────────────────────────────────────

    /**
     * 测试短信发送（实际调用厂商 API，会产生真实费用）。
     *
     * <p>用于验证短信配置是否正确、模板是否可用。 仅在 aaf.messaging.sms.provider 已配置时可用。
     */
    @Operation(summary = "测试短信发送", description = "实际调用厂商 API 发送短信，会产生真实费用，仅用于配置验证")
    @PostMapping("/test-send")
    public Result<String> testSend(@Valid @RequestBody SmsTestSendDTO dto) {
        try {
            smsService.send(dto.phone(), dto.code(), dto.params());
            log.info("短信测试发送成功: phone={}, code={}", dto.phone(), dto.code());
            return Result.success("发送成功");
        } catch (Exception e) {
            log.warn(
                    "短信测试发送失败: phone={}, code={}, error={}",
                    dto.phone(),
                    dto.code(),
                    e.getMessage());
            return Result.error(500, "发送失败：" + e.getMessage());
        }
    }

    // ── 厂商回调 ──────────────────────────────────────────────

    /** 阿里云短信状态回调。 阿里云配置回调地址：POST /api/system/sms/callback/aliyun */
    @PostMapping("/callback/aliyun")
    public Result<Void> aliyunCallback(@RequestBody String body) {
        // 阿里云回调为 JSON
        // 数组，格式：[{"phone_number":"...","send_time":"...","err_code":"...","err_msg":"...","biz_id":"...","out_id":"..."}]
        // 目前仅记录日志，后续可解析更新 sys_sms_log 状态
        return Result.success(null);
    }

    /** 腾讯云短信状态回调。 腾讯云配置回调地址：POST /api/system/sms/callback/tencent */
    @PostMapping("/callback/tencent")
    public Result<Void> tencentCallback(@RequestBody String body) {
        return Result.success(null);
    }

    // ── DTO ───────────────────────────────────────────────────

    public record SmsTemplateCreateDTO(
            @NotBlank String code,
            @NotBlank String name,
            String signName,
            @NotBlank String apiTemplateId,
            String params,
            String provider) {}

    public record SmsTemplateUpdateDTO(
            String signName, String apiTemplateId, String provider, Short status) {}

    /**
     * 测试发送请求。
     *
     * @param phone 手机号（11位）
     * @param code 业务场景编码（对应 sys_sms_template.code，如 AUTH_LOGIN）
     * @param params 模板变量，如 {"code":"1234"}
     */
    public record SmsTestSendDTO(
            @NotBlank String phone, @NotBlank String code, Map<String, String> params) {}
}
