package com.xuejiai.aaf.module.system.log.service;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.system.log.domain.Comment;
import com.xuejiai.aaf.module.system.log.repository.CommentRepository;
import com.xuejiai.aaf.module.system.task.service.TodoService;

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
    private final TodoService todoService;

    /** 创建评论 */
    @Transactional
    public Comment create(String entityType, Long entityId, String content) {
        var comment = new Comment();
        comment.setEntityType(entityType);
        comment.setEntityId(entityId);
        comment.setContent(content);
        comment.setMentions(extractMentions(content));
        var saved = commentRepository.save(comment);

        // 为每个 @用户创建待办
        extractMentionIds(content)
                .forEach(
                        userId ->
                                todoService.create(
                                        userId, "你在评论中被提及", "comment", entityType, entityId));

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
        var ids = new java.util.ArrayList<String>();
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        if (ids.isEmpty()) {
            return null;
        }
        return "[" + String.join(",", ids) + "]";
    }

    /** 提取 @mentions，返回用户 ID 列表 */
    private List<Long> extractMentionIds(String content) {
        var matcher = MENTION_PATTERN.matcher(content);
        var ids = new java.util.ArrayList<Long>();
        while (matcher.find()) {
            ids.add(Long.parseLong(matcher.group(1)));
        }
        return ids;
    }
}
