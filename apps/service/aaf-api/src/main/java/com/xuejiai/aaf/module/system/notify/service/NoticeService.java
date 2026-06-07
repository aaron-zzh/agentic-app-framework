package com.xuejiai.aaf.module.system.notify.service;

import java.time.LocalDateTime;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.system.notify.domain.Notice;
import com.xuejiai.aaf.module.system.notify.event.NoticePublishedEvent;
import com.xuejiai.aaf.module.system.notify.repository.NoticeRepository;
import com.xuejiai.aaf.module.system.notify.vo.NoticeCreateDTO;
import com.xuejiai.aaf.module.system.notify.vo.NoticePageDTO;
import com.xuejiai.aaf.module.system.notify.vo.NoticeUpdateDTO;
import com.xuejiai.aaf.module.system.notify.vo.NoticeVO;

import lombok.RequiredArgsConstructor;

/**
 * 通知公告服务，继承 BaseCrudService 获得标准 CRUD 能力。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class NoticeService
        extends BaseCrudService<Notice, NoticeVO, NoticeCreateDTO, NoticeUpdateDTO, NoticePageDTO> {

    private final NoticeRepository noticeRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    protected JpaRepository<Notice, Long> getRepository() {
        return noticeRepository;
    }

    @Override
    protected JpaSpecificationExecutor<Notice> getSpecExecutor() {
        return noticeRepository;
    }

    @Override
    protected NoticeVO toVO(Notice n) {
        return new NoticeVO(
                n.getId(),
                n.getTitle(),
                n.getContent(),
                n.getType(),
                n.getStatus(),
                n.getPublishTime(),
                n.getCreateTime());
    }

    @Override
    protected Notice toEntity(NoticeCreateDTO dto) {
        var notice = new Notice();
        notice.setTitle(dto.title());
        notice.setContent(dto.content());
        notice.setType(dto.type());
        return notice;
    }

    @Override
    protected void updateEntity(Notice notice, NoticeUpdateDTO dto) {
        if (dto.title() != null) notice.setTitle(dto.title());
        if (dto.content() != null) notice.setContent(dto.content());
        if (dto.type() != null) notice.setType(dto.type());
    }

    @Override
    protected Specification<Notice> buildSpec(NoticePageDTO req) {
        return SpecificationBuilder.<Notice>builder()
                .eqIfPresent("type", req.getType())
                .eqIfPresent("status", req.getStatus())
                .build();
    }

    @Override
    protected Sort defaultSort() {
        return Sort.by("id").descending();
    }

    @Override
    protected String entityName() {
        return "通知公告";
    }

    @Override
    protected String entitySlug() {
        return "notice";
    }

    /** 发布公告 */
    @Transactional
    public NoticeVO publish(Long id) {
        var notice =
                noticeRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "公告不存在"));
        notice.setStatus((short) 1);
        notice.setPublishTime(LocalDateTime.now());
        var saved = noticeRepository.save(notice);
        eventPublisher.publishEvent(
                new NoticePublishedEvent(saved.getId(), saved.getTitle(), saved.getContent()));
        return toVO(saved);
    }
}
