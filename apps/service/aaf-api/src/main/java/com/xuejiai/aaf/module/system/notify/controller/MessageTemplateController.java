package com.xuejiai.aaf.module.system.notify.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.system.notify.service.MessageTemplateService;
import com.xuejiai.aaf.module.system.notify.vo.MessageTemplateCreateDTO;
import com.xuejiai.aaf.module.system.notify.vo.MessageTemplatePreviewDTO;
import com.xuejiai.aaf.module.system.notify.vo.MessageTemplateUpdateDTO;
import com.xuejiai.aaf.module.system.notify.vo.MessageTemplateVO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 消息模板管理接口。 */
@RestController
@RequestMapping("/api/message-templates")
@RequiredArgsConstructor
public class MessageTemplateController {

    private final MessageTemplateService service;

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
}
