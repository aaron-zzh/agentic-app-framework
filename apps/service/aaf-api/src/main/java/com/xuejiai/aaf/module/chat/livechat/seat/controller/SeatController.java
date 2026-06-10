package com.xuejiai.aaf.module.chat.livechat.seat.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.chat.livechat.seat.domain.LivechatSeat;
import com.xuejiai.aaf.module.chat.livechat.seat.service.SeatCrudService;
import com.xuejiai.aaf.module.chat.livechat.seat.vo.SeatCreateDTO;
import com.xuejiai.aaf.module.chat.livechat.seat.vo.SeatPageDTO;
import com.xuejiai.aaf.module.chat.livechat.seat.vo.SeatUpdateDTO;
import com.xuejiai.aaf.module.chat.livechat.seat.vo.SeatVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 客服坐席管理接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "Chat - 客服坐席")
@RestController
@RequestMapping("/api/chat/livechat/seats")
@RequiredArgsConstructor
public class SeatController
        extends BaseCrudController<
                LivechatSeat, SeatVO, SeatCreateDTO, SeatUpdateDTO, SeatPageDTO> {

    private final SeatCrudService seatCrudService;

    @Override
    protected SeatCrudService getService() {
        return seatCrudService;
    }
}
