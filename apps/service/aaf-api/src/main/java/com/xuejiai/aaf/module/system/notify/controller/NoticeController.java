package com.xuejiai.aaf.module.system.notify.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.system.notify.service.NoticeService;
import com.xuejiai.aaf.module.system.notify.vo.NoticeCreateDTO;
import com.xuejiai.aaf.module.system.notify.vo.NoticeVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 通知公告管理接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "通知公告")
@RestController
@RequestMapping("/api/system/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    @GetMapping
    public Result<List<NoticeVO>> list() {
        return Result.success(noticeService.list());
    }

    @PostMapping
    public Result<NoticeVO> create(@Valid @RequestBody NoticeCreateDTO dto) {
        return Result.success(noticeService.create(dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        noticeService.delete(id);
        return Result.success(null);
    }

    @Operation(summary = "发布公告")
    @PostMapping("/{id}/publish")
    public Result<NoticeVO> publish(@PathVariable Long id) {
        return Result.success(noticeService.publish(id));
    }
}
