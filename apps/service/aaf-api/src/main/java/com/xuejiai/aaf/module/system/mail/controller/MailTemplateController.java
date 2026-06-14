package com.xuejiai.aaf.module.system.mail.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.messaging.MessageChannel;
import com.xuejiai.aaf.framework.messaging.MessageRequest;
import com.xuejiai.aaf.framework.messaging.MessageService;
import com.xuejiai.aaf.module.system.mail.service.MailService;
import com.xuejiai.aaf.module.system.mail.vo.MailTemplateCreateDTO;
import com.xuejiai.aaf.module.system.mail.vo.MailTemplateVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 邮件模板管理接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "邮件模板管理")
@RestController
@RequestMapping("/api/system/mail/templates")
@RequiredArgsConstructor
public class MailTemplateController {

    private final MailService mailService;
    private final MessageService messageService;

    @GetMapping
    public Result<List<MailTemplateVO>> list() {
        return Result.success(mailService.listTemplates());
    }

    @PostMapping
    public Result<MailTemplateVO> create(@Valid @RequestBody MailTemplateCreateDTO dto) {
        return Result.success(mailService.createTemplate(dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        mailService.deleteTemplate(id);
        return Result.success(null);
    }

    @Operation(summary = "发送测试邮件")
    @PostMapping("/test-send")
    public Result<String> testSend(@Valid @RequestBody TestSendDTO dto) {
        Map<String, Object> variables =
                dto.params() == null
                        ? Map.of()
                        : dto.params().entrySet().stream()
                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        messageService.send(
                new MessageRequest(
                        MessageChannel.EMAIL,
                        dto.templateCode(),
                        List.of(dto.toAddress()),
                        variables,
                        null));
        return Result.success("发送成功");
    }

    public record TestSendDTO(
            @jakarta.validation.constraints.NotBlank String toAddress,
            @jakarta.validation.constraints.NotBlank String templateCode,
            Map<String, String> params) {}
}
