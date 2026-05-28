package com.xuejiai.aaf.module.system.mail.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.system.mail.service.MailService;
import com.xuejiai.aaf.module.system.mail.vo.MailSendDTO;
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
    public Result<String> testSend(@Valid @RequestBody MailSendDTO dto) {
        try {
            mailService.send(dto.toAddress(), dto.templateCode(), dto.params());
            return Result.success("发送成功");
        } catch (Exception e) {
            return Result.error(500, "发送失败：" + e.getMessage());
        }
    }
}
