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
import com.xuejiai.aaf.module.system.mail.vo.MailAccountCreateDTO;
import com.xuejiai.aaf.module.system.mail.vo.MailAccountVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 邮件账号管理接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "邮件账号管理")
@RestController
@RequestMapping("/api/system/mail/accounts")
@RequiredArgsConstructor
public class MailAccountController {

    private final MailService mailService;

    @GetMapping
    public Result<List<MailAccountVO>> list() {
        return Result.success(mailService.listAccounts());
    }

    @PostMapping
    public Result<MailAccountVO> create(@Valid @RequestBody MailAccountCreateDTO dto) {
        return Result.success(mailService.createAccount(dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        mailService.deleteAccount(id);
        return Result.success(null);
    }
}
