package com.xuejiai.aaf.module.system.notify.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.messaging.MessageChannel;
import com.xuejiai.aaf.framework.messaging.MessageRequest;
import com.xuejiai.aaf.framework.messaging.MessageService;
import com.xuejiai.aaf.module.system.notify.service.MessageTemplateService;
import com.xuejiai.aaf.module.system.notify.vo.MessageTemplateCreateDTO;
import com.xuejiai.aaf.module.system.notify.vo.MessageTemplatePreviewDTO;
import com.xuejiai.aaf.module.system.notify.vo.MessageTemplateUpdateDTO;
import com.xuejiai.aaf.module.system.notify.vo.MessageTemplateVO;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;

/**
 * 消息模板管理接口。
 *
 * @author AaronZZH & Kiro
 */
@RestController
@RequestMapping("/api/message-templates")
@RequiredArgsConstructor
public class MessageTemplateController {

    private final MessageTemplateService service;
    private final MessageService messageService;

    @GetMapping
    public Result<List<MessageTemplateVO>> list() {
        return Result.success(service.list());
    }

    @GetMapping("/{id}")
    public Result<MessageTemplateVO> getById(@PathVariable Long id) {
        return Result.success(service.getById(id));
    }

    @PostMapping
    public Result<MessageTemplateVO> create(@Valid @RequestBody MessageTemplateCreateDTO dto) {
        return Result.success(service.create(dto));
    }

    @PutMapping("/{id}")
    public Result<MessageTemplateVO> update(
            @PathVariable Long id, @RequestBody MessageTemplateUpdateDTO dto) {
        return Result.success(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success(null);
    }

    @PostMapping("/{id}/preview")
    public Result<String> preview(
            @PathVariable Long id, @RequestBody MessageTemplatePreviewDTO dto) {
        return Result.success(service.preview(id, dto.variables()));
    }

    @Operation(summary = "测试发送消息（支持 EMAIL/SMS/DINGTALK 等所有渠道）")
    @PostMapping("/test-send")
    public Result<String> testSend(@Valid @RequestBody TestSendDTO dto) {
        messageService.send(new MessageRequest(
                MessageChannel.valueOf(dto.channel().toUpperCase()),
                dto.templateCode(),
                dto.recipients(),
                dto.variables() != null ? dto.variables() : Map.of(),
                dto.subject()));
        return Result.success("发送成功");
    }

    public record TestSendDTO(
            @NotBlank String channel,
            @NotBlank String templateCode,
            @NotEmpty List<String> recipients,
            String subject,
            Map<String, Object> variables) {}
}
