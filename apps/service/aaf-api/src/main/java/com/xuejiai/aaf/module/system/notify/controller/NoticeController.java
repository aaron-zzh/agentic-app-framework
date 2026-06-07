package com.xuejiai.aaf.module.system.notify.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.system.notify.domain.Notice;
import com.xuejiai.aaf.module.system.notify.service.NoticeService;
import com.xuejiai.aaf.module.system.notify.vo.NoticeCreateDTO;
import com.xuejiai.aaf.module.system.notify.vo.NoticePageDTO;
import com.xuejiai.aaf.module.system.notify.vo.NoticeUpdateDTO;
import com.xuejiai.aaf.module.system.notify.vo.NoticeVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
public class NoticeController
        extends BaseCrudController<
                Notice, NoticeVO, NoticeCreateDTO, NoticeUpdateDTO, NoticePageDTO> {

    private final NoticeService noticeService;

    @Override
    protected BaseCrudService<Notice, NoticeVO, NoticeCreateDTO, NoticeUpdateDTO, NoticePageDTO>
            getService() {
        return noticeService;
    }

    @Operation(summary = "发布公告")
    @PostMapping("/{id}/publish")
    public Result<NoticeVO> publish(@PathVariable Long id) {
        return Result.success(noticeService.publish(id));
    }
}
