package com.xuejiai.aaf.module.system.service;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.system.domain.Comment;
import com.xuejiai.aaf.module.system.repository.CommentRepository;

import lombok.RequiredArgsConstructor;

/** 评论业务逻辑。 */
@Service
@RequiredArgsConstructor
public class CommentService {

    private static final Pattern MENTION_PATTERN = Pattern.compile("@(\\d+)");

    private final CommentRepository commentRepository;

    /** 创建评论 */
    @Transactional
    public Comment create(String entityType, Long entityId, String content) {
        var comment = new Comment();
        comment.setEntityType(entityType);
        comment.setEntityId(entityId);
        comment.setContent(content);
        comment.setMentions(extractMentions(content));
        return commentRepository.save(comment);
    }

    /** 更新评论 */
    @Transactional
    public Comment update(Long id, String content) {
        var comment = commentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "评论不存在"));
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
}
