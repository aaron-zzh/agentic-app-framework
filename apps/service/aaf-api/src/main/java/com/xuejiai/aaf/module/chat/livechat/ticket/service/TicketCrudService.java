package com.xuejiai.aaf.module.chat.livechat.ticket.service;

import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.chat.livechat.ticket.domain.Ticket;
import com.xuejiai.aaf.module.chat.livechat.ticket.repository.TicketRepository;
import com.xuejiai.aaf.module.chat.livechat.ticket.vo.TicketCreateDTO;
import com.xuejiai.aaf.module.chat.livechat.ticket.vo.TicketPageDTO;
import com.xuejiai.aaf.module.chat.livechat.ticket.vo.TicketUpdateDTO;
import com.xuejiai.aaf.module.chat.livechat.ticket.vo.TicketVO;

import lombok.RequiredArgsConstructor;

/**
 * 客服工单 CRUD Service。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class TicketCrudService
        extends BaseCrudService<Ticket, TicketVO, TicketCreateDTO, TicketUpdateDTO, TicketPageDTO> {

    private final TicketRepository ticketRepository;

    @Override
    protected JpaRepository<Ticket, Long> getRepository() {
        return ticketRepository;
    }

    @Override
    protected JpaSpecificationExecutor<Ticket> getSpecExecutor() {
        return ticketRepository;
    }

    @Override
    protected TicketVO toVO(Ticket entity) {
        return new TicketVO(
                entity.getId(),
                entity.getTicketNo(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getUserId(),
                entity.getConversationId(),
                entity.getType(),
                entity.getPriority(),
                entity.getStatus(),
                entity.getAssigneeId(),
                entity.getSlaDueTime(),
                entity.getClosedTime(),
                entity.getCreateTime(),
                entity.getUpdateTime());
    }

    @Override
    protected Ticket toEntity(TicketCreateDTO dto) {
        var ticket = new Ticket();
        // 生成唯一工单编号：TK + 时间戳后8位 + UUID前6位
        ticket.setTicketNo(
                "TK"
                        + System.currentTimeMillis() % 100_000_000L
                        + UUID.randomUUID()
                                .toString()
                                .replace("-", "")
                                .substring(0, 6)
                                .toUpperCase());
        ticket.setTitle(dto.title());
        ticket.setDescription(dto.description());
        ticket.setUserId(dto.userId());
        ticket.setConversationId(dto.conversationId());
        ticket.setType(dto.type());
        ticket.setPriority(dto.priority());
        ticket.setStatus("PENDING");
        return ticket;
    }

    @Override
    protected void updateEntity(Ticket entity, TicketUpdateDTO dto) {
        if (StringUtils.hasText(dto.title())) entity.setTitle(dto.title());
        if (dto.description() != null) entity.setDescription(dto.description());
        if (dto.assigneeId() != null) entity.setAssigneeId(dto.assigneeId());
        if (StringUtils.hasText(dto.priority())) entity.setPriority(dto.priority());
        if (dto.slaDueTime() != null) entity.setSlaDueTime(dto.slaDueTime());
    }

    @Override
    protected Specification<Ticket> buildSpec(TicketPageDTO pageDTO) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (StringUtils.hasText(pageDTO.getStatus())) {
                predicates.add(cb.equal(root.get("status"), pageDTO.getStatus()));
            }
            if (StringUtils.hasText(pageDTO.getType())) {
                predicates.add(cb.equal(root.get("type"), pageDTO.getType()));
            }
            if (StringUtils.hasText(pageDTO.getPriority())) {
                predicates.add(cb.equal(root.get("priority"), pageDTO.getPriority()));
            }
            if (pageDTO.getAssigneeId() != null) {
                predicates.add(cb.equal(root.get("assigneeId"), pageDTO.getAssigneeId()));
            }
            if (pageDTO.getUserId() != null) {
                predicates.add(cb.equal(root.get("userId"), pageDTO.getUserId()));
            }
            if (pageDTO.getConversationId() != null) {
                predicates.add(cb.equal(root.get("conversationId"), pageDTO.getConversationId()));
            }
            return predicates.isEmpty()
                    ? null
                    : cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }
}
