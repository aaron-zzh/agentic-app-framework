package com.xuejiai.aaf.module.chat.livechat.seat.service;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.chat.livechat.seat.domain.LivechatSeat;
import com.xuejiai.aaf.module.chat.livechat.seat.repository.LivechatSeatRepository;
import com.xuejiai.aaf.module.chat.livechat.seat.vo.SeatCreateDTO;
import com.xuejiai.aaf.module.chat.livechat.seat.vo.SeatPageDTO;
import com.xuejiai.aaf.module.chat.livechat.seat.vo.SeatUpdateDTO;
import com.xuejiai.aaf.module.chat.livechat.seat.vo.SeatVO;

import lombok.RequiredArgsConstructor;

/**
 * 坐席 CRUD 服务。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatCrudService
        extends BaseCrudService<LivechatSeat, SeatVO, SeatCreateDTO, SeatUpdateDTO, SeatPageDTO> {

    private final LivechatSeatRepository seatRepository;

    @Override
    protected JpaRepository<LivechatSeat, Long> getRepository() {
        return seatRepository;
    }

    @Override
    protected JpaSpecificationExecutor<LivechatSeat> getSpecExecutor() {
        return seatRepository;
    }

    @Override
    protected SeatVO toVO(LivechatSeat e) {
        return new SeatVO(
                e.getId(),
                e.getSeatType(),
                e.getUserId(),
                e.getAssistantId(),
                e.getNickname(),
                e.getSkillGroup(),
                e.getStatus(),
                e.getCurrentSessions(),
                e.getMaxSessions(),
                e.getCreateTime());
    }

    @Override
    protected LivechatSeat toEntity(SeatCreateDTO dto) {
        var entity = new LivechatSeat();
        entity.setSeatType(dto.seatType());
        entity.setUserId(dto.userId());
        entity.setAssistantId(dto.assistantId());
        entity.setNickname(dto.nickname());
        entity.setSkillGroup(dto.skillGroup());
        if (dto.maxSessions() != null) entity.setMaxSessions(dto.maxSessions());
        return entity;
    }

    @Override
    protected void updateEntity(LivechatSeat entity, SeatUpdateDTO dto) {
        if (dto.nickname() != null) entity.setNickname(dto.nickname());
        if (dto.skillGroup() != null) entity.setSkillGroup(dto.skillGroup());
        if (dto.status() != null) entity.setStatus(dto.status());
        if (dto.maxSessions() != null) entity.setMaxSessions(dto.maxSessions());
    }

    @Override
    protected org.springframework.data.jpa.domain.Specification<LivechatSeat> buildSpec(
            SeatPageDTO query) {
        return SpecificationBuilder.<LivechatSeat>builder()
                .eqIfPresent("seatType", query.getSeatType())
                .eqIfPresent("status", query.getStatus())
                .eqIfPresent("skillGroup", query.getSkillGroup())
                .build();
    }

    @Override
    protected String entityName() {
        return "坐席";
    }
}
