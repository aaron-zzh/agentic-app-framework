package com.xuejiai.aaf.module.chat.livechat.ticket.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.chat.livechat.ticket.domain.Ticket;
import com.xuejiai.aaf.module.chat.livechat.ticket.service.TicketCrudService;
import com.xuejiai.aaf.module.chat.livechat.ticket.vo.TicketCreateDTO;
import com.xuejiai.aaf.module.chat.livechat.ticket.vo.TicketPageDTO;
import com.xuejiai.aaf.module.chat.livechat.ticket.vo.TicketUpdateDTO;
import com.xuejiai.aaf.module.chat.livechat.ticket.vo.TicketVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 客服工单管理接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "Chat - 客服工单")
@RestController
@RequestMapping("/api/chat/livechat/tickets")
@RequiredArgsConstructor
public class TicketController
        extends BaseCrudController<
                Ticket, TicketVO, TicketCreateDTO, TicketUpdateDTO, TicketPageDTO> {

    private final TicketCrudService ticketCrudService;

    @Override
    protected BaseCrudService<Ticket, TicketVO, TicketCreateDTO, TicketUpdateDTO, TicketPageDTO>
            getService() {
        return ticketCrudService;
    }
}
