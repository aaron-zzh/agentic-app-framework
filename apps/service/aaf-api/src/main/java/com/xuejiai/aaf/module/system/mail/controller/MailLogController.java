package com.xuejiai.aaf.module.system.mail.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.system.mail.service.MailService;
import com.xuejiai.aaf.module.system.mail.vo.MailLogVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 邮件日志查询接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "邮件日志")
@RestController
@RequestMapping("/api/system/mail/logs")
@RequiredArgsConstructor
public class MailLogController {

    private final MailService mailService;

    @GetMapping
    public Result<PageResult<MailLogVO>> list(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        var page = mailService.listLogs(pageNo, pageSize);
        return Result.success(new PageResult<>(page.getContent(), page.getTotalElements()));
    }
}
