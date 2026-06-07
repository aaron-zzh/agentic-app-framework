package com.xuejiai.aaf.module.system.notify.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.messaging.internal.InternalMessageSender;
import com.xuejiai.aaf.module.system.notify.domain.Notification;
import com.xuejiai.aaf.module.system.notify.repository.NotificationRepository;
import com.xuejiai.aaf.module.system.notify.vo.NotificationPageDTO;
import com.xuejiai.aaf.module.system.notify.vo.NotificationVO;

import lombok.RequiredArgsConstructor;

/**
 * 通知业务逻辑。查询/已读/删除在此处理；发送统一委托给 InternalMessageSender。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final InternalMessageSender messageSender;

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

    /** 发送通知（委托给 InternalMessageSender，存库 + WS 推送） */
    public void send(
            Long userId,
            String type,
            String title,
            String body,
            String relatedUrl,
            String entityType,
            Long entityId) {
        messageSender.send(userId, type, title, body, relatedUrl, entityType, entityId);
    }

    /** 批量发送给多个用户（公告场景） */
    public void sendToUsers(
            List<Long> userIds, String type, String title, String body, String relatedUrl) {
        userIds.forEach(uid -> messageSender.send(uid, type, title, body, relatedUrl, null, null));
    }

    /** 发送系统通知（无正文、无关联实体的简单通知，便捷方法） */
    public void sendSystemNotification(Long userId, String title, String body) {
        messageSender.send(userId, "SYSTEM", title, body, null, null, null);
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
                n.getRelatedUrl(),
                n.getIsRead(),
                n.getCreateTime());
    }
}
