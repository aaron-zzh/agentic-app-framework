package com.xuejiai.aaf.module.system.notify.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.module.system.notify.domain.Notice;
import com.xuejiai.aaf.module.system.notify.repository.NoticeRepository;
import com.xuejiai.aaf.module.system.notify.vo.NoticeCreateDTO;
import com.xuejiai.aaf.module.system.notify.vo.NoticeVO;

import lombok.RequiredArgsConstructor;

/**
 * 通知公告服务。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NoticeRepository noticeRepository;

    /**
     * 查询所有通知公告。
     *
     * @return 公告列表
     */
    public List<NoticeVO> list() {
        return noticeRepository.findAll().stream()
                .filter(n -> !n.getDeleted())
                .map(this::toVO)
                .toList();
    }

    /**
     * 创建通知公告（草稿状态）。
     *
     * @param dto 创建请求
     * @return 新建公告
     */
    @Transactional
    public NoticeVO create(NoticeCreateDTO dto) {
        var notice = new Notice();
        notice.setTitle(dto.title());
        notice.setContent(dto.content());
        notice.setType(dto.type());
        return toVO(noticeRepository.save(notice));
    }

    /**
     * 删除通知公告。
     *
     * @param id 公告 ID
     */
    @Transactional
    public void delete(Long id) {
        noticeRepository.deleteById(id);
    }

    /**
     * 发布通知公告。
     *
     * @param id 公告 ID
     * @return 发布后的公告
     */
    @Transactional
    public NoticeVO publish(Long id) {
        var notice =
                noticeRepository
                        .findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("公告不存在"));
        notice.setStatus((short) 1);
        notice.setPublishTime(LocalDateTime.now());
        return toVO(noticeRepository.save(notice));
    }

    private NoticeVO toVO(Notice n) {
        return new NoticeVO(
                n.getId(),
                n.getTitle(),
                n.getContent(),
                n.getType(),
                n.getStatus(),
                n.getPublishTime(),
                n.getCreateTime());
    }
}
