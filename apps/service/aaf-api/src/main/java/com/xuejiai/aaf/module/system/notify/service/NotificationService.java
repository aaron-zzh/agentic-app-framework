package com.xuejiai.aaf.module.system.notify.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.module.system.log.domain.domain.Notification;
import com.xuejiai.aaf.module.system.notify.repository.NotificationRepository;
import com.xuejiai.aaf.module.system.notify.vo.NotificationPageDTO;
import com.xuejiai.aaf.module.system.notify.vo.NotificationVO;

import lombok.RequiredArgsConstructor;

/** 通知业务逻辑。 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /** 分页查询当前用户通知 */
    public PageResult<NotificationVO> page(Long userId, NotificationPageDTO req) {
        var pageable = req.toPageable(Sort.by("id").descending());
        Specification<Notification> spec =
                SpecificationBuilder.<Notification>builder()
                        .eqIfPresent("userId", userId)
                        .eqIfPresent("type", req.getType())
                        .eqIfPresent("isRead", req.getIsRead())
                        .build();
        var page = notificationRepository.findAll(spec, pageable);
        return new PageResult<>(
                page.getContent().stream().map(this::toVO).toList(), page.getTotalElements());
    }

    /** 未读计数 */
    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    /** 标记已读 */
    @Transactional
    public void markAsRead(Long userId, List<Long> ids) {
        notificationRepository.markAsRead(ids, userId);
    }

    /** 删除通知（物理删除） */
    @Transactional
    public void delete(Long userId, Long id) {
        notificationRepository
                .findById(id)
                .filter(n -> n.getUserId().equals(userId))
                .ifPresent(notificationRepository::delete);
    }

    /** 发送系统通知 */
    @Transactional
    public void sendSystemNotification(Long userId, String title, String body) {
        var notification = new Notification();
        notification.setUserId(userId);
        notification.setType("system");
        notification.setTitle(title);
        notification.setBody(body);
        notification.setIsRead(false);
        notificationRepository.save(notification);
    }

    private NotificationVO toVO(Notification n) {
        return new NotificationVO(
                n.getId(),
                n.getUserId(),
                n.getType(),
                n.getTitle(),
                n.getBody(),
                n.getEntityType(),
                n.getEntityId(),
                n.getIsRead(),
                n.getCreateTime());
    }
}
