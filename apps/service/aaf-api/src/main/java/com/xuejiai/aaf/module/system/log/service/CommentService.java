package com.xuejiai.aaf.module.system.log.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.system.log.domain.Comment;
import com.xuejiai.aaf.module.system.log.repository.CommentRepository;
import com.xuejiai.aaf.module.system.notify.event.MentionEvent;
import com.xuejiai.aaf.module.system.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * 评论业务逻辑。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class CommentService {

    private static final Pattern MENTION_PATTERN = Pattern.compile("@(\\d+)");

    private final CommentRepository commentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final OperatorContext operatorContext;
    private final UserRepository userRepository;

    /** 创建评论 */
    @Transactional
    public Comment create(String entityType, Long entityId, String content) {
        var comment = new Comment();
        comment.setEntityType(entityType);
        comment.setEntityId(entityId);
        comment.setContent(content);
        comment.setMentions(extractMentions(content));
        var saved = commentRepository.save(comment);

        // 获取当前操作者昵称
        var actorName =
                operatorContext
                        .currentUserId()
                        .flatMap(userRepository::findById)
                        .map(u -> u.getNickname() != null ? u.getNickname() : u.getUsername())
                        .orElse("有人");

        // 评论摘要
        var excerpt = content.length() > 50 ? content.substring(0, 50) + "…" : content;

        // 发布提及事件（异步，解耦通知逻辑）
        extractMentionIds(content)
                .forEach(
                        userId ->
                                eventPublisher.publishEvent(
                                        new MentionEvent(
                                                userId, actorName, entityType, entityId, excerpt)));

        return saved;
    }

    /** 更新评论 */
    @Transactional
    public Comment update(Long id, String content) {
        var comment =
                commentRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "评论不存在"));
        comment.setContent(content);
        comment.setMentions(extractMentions(content));
        return commentRepository.save(comment);
    }

    /** 删除评论 */
    @Transactional
    public void delete(Long id) {
        commentRepository.deleteById(id);
    }

    /** 提取 @mentions，返回 JSON 数组字符串 */
    private String extractMentions(String content) {
        var matcher = MENTION_PATTERN.matcher(content);
        var ids = new ArrayList<String>();
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        return ids.isEmpty() ? null : "[" + String.join(",", ids) + "]";
    }

    /** 提取 @mentions，返回用户 ID 列表 */
    private List<Long> extractMentionIds(String content) {
        var matcher = MENTION_PATTERN.matcher(content);
        var ids = new ArrayList<Long>();
        while (matcher.find()) {
            ids.add(Long.parseLong(matcher.group(1)));
        }
        return ids;
    }
}
