package com.xuejiai.aaf.module.system.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.module.system.domain.ActivityLog;
import com.xuejiai.aaf.module.system.domain.Comment;
import com.xuejiai.aaf.module.system.repository.ActivityLogRepository;
import com.xuejiai.aaf.module.system.repository.CommentRepository;

import lombok.RequiredArgsConstructor;

/** 活动流业务逻辑。 */
@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityLogRepository activityLogRepository;
    private final CommentRepository commentRepository;

    /** 记录活动日志 */
    @Transactional
    public void record(String entityType, Long entityId, String action, String changes) {
        var log = new ActivityLog();
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setAction(action);
        log.setChanges(changes);
        activityLogRepository.save(log);
    }

    /** 查询活动流（活动+评论混合时间线，按时间倒序） */
    public PageResult<ActivityTimelineItem> timeline(String entityType, Long entityId, PageParam pageParam) {
        var pageable = pageParam.toPageable(Sort.by("createTime").descending());

        var activityPage = activityLogRepository.findByEntityTypeAndEntityId(entityType, entityId, pageable);
        var commentPage = commentRepository.findByEntityTypeAndEntityId(entityType, entityId, pageable);

        // 混合两种数据为时间线
        var items = new ArrayList<ActivityTimelineItem>();
        activityPage.getContent().forEach(a -> items.add(fromActivity(a)));
        commentPage.getContent().forEach(c -> items.add(fromComment(c)));

        // 按时间倒序排列
        items.sort(Comparator.comparing(ActivityTimelineItem::createTime).reversed());

        long total = activityPage.getTotalElements() + commentPage.getTotalElements();
        return new PageResult<>(items, total);
    }

    private ActivityTimelineItem fromActivity(ActivityLog a) {
        return new ActivityTimelineItem(
                a.getId(), "activity", a.getEntityType(), a.getEntityId(),
                a.getAction(), a.getChanges(), null, null,
                a.getCreateBy(), a.getCreateTime());
    }

    private ActivityTimelineItem fromComment(Comment c) {
        return new ActivityTimelineItem(
                c.getId(), "comment", c.getEntityType(), c.getEntityId(),
                null, null, c.getContent(), c.getMentions(),
                c.getCreateBy(), c.getCreateTime());
    }

    /** 活动流时间线条目 */
    public record ActivityTimelineItem(
            Long id,
            String type,
            String entityType,
            Long entityId,
            String action,
            String changes,
            String content,
            String mentions,
            Long createBy,
            LocalDateTime createTime) {}
}
