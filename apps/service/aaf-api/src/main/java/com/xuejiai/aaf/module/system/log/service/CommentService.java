package com.xuejiai.aaf.module.system.log.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.COMMENT_NOT_FOUND;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.system.log.domain.Comment;
import com.xuejiai.aaf.module.system.log.repository.CommentRepository;
import com.xuejiai.aaf.module.system.log.vo.CommentCreateDTO;
import com.xuejiai.aaf.module.system.log.vo.CommentPageDTO;
import com.xuejiai.aaf.module.system.log.vo.CommentUpdateDTO;
import com.xuejiai.aaf.module.system.log.vo.CommentVO;
import com.xuejiai.aaf.module.system.notify.event.MentionEvent;
import com.xuejiai.aaf.module.system.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/** 评论业务逻辑，复用嵌套资源的通用 CRUD 查询与视图能力。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService
        extends BaseCrudService<
                Comment, CommentVO, CommentCreateDTO, CommentUpdateDTO, CommentPageDTO> {

    private static final Pattern MENTION_PATTERN = Pattern.compile("@(\\d+)");
    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "createTime", "updateTime");

    private final CommentRepository commentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final OperatorContext operatorContext;
    private final UserRepository userRepository;

    @Override
    protected CommentRepository getRepository() {
        return commentRepository;
    }

    @Override
    protected CommentVO toVO(Comment comment) {
        return toVO(comment, "detail");
    }

    @Override
    protected CommentVO toVO(Comment comment, String fieldSet) {
        return switch (fieldSet) {
            case "list" ->
                    new CommentVO(
                            comment.getId(),
                            comment.getEntityType(),
                            comment.getEntityId(),
                            comment.getContent(),
                            null,
                            comment.getOwnerId(),
                            comment.getCreateTime(),
                            null);
            case "detail" ->
                    new CommentVO(
                            comment.getId(),
                            comment.getEntityType(),
                            comment.getEntityId(),
                            comment.getContent(),
                            comment.getMentions(),
                            comment.getOwnerId(),
                            comment.getCreateTime(),
                            comment.getUpdateTime());
            default -> throw new IllegalArgumentException("不支持的字段集: " + fieldSet);
        };
    }

    @Override
    protected Comment toEntity(CommentCreateDTO dto) {
        var comment = new Comment();
        comment.setEntityType(dto.entityType());
        comment.setEntityId(dto.entityId());
        comment.setContent(dto.content());
        comment.setMentions(extractMentions(dto.content()));
        return comment;
    }

    @Override
    protected void updateEntity(Comment comment, CommentUpdateDTO dto) {
        comment.setContent(dto.content());
        comment.setMentions(extractMentions(dto.content()));
    }

    @Override
    protected Specification<Comment> buildSpec(CommentPageDTO request) {
        if (request.getEntityType() == null
                || request.getEntityType().isBlank()
                || request.getEntityId() == null) {
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) ->
                cb.and(
                        cb.equal(root.get("entityType"), request.getEntityType()),
                        cb.equal(root.get("entityId"), request.getEntityId()));
    }

    @Override
    protected Long extractOwnerId(Comment comment) {
        return comment.getOwnerId();
    }

    /** 按父资源分页查询评论。 */
    public PageResult<CommentVO> pageByEntity(
            String entityType, Long entityId, CommentPageDTO request) {
        applyParentContext(entityType, entityId, request);
        return page(request);
    }

    /** 按父资源查询评论窗口。 */
    public PageResult<CommentVO> queryWindowByEntity(
            String entityType, Long entityId, CommentPageDTO request, String fieldSet) {
        applyParentContext(entityType, entityId, request);
        return queryWindow(request, fieldSet, List.of());
    }

    /** 按父资源查询评论详情。 */
    public CommentVO getByEntity(
            String entityType, Long entityId, Long commentId, String queryToken, String fieldSet) {
        requireComment(entityType, entityId, commentId);
        return super.getById(commentId, queryToken, fieldSet);
    }

    /** 在父资源下创建评论。 */
    @Transactional
    public CommentVO createByEntity(String entityType, Long entityId, CommentCreateDTO request) {
        var dto = new CommentCreateDTO(entityType, entityId, request.content());
        var comment = super.create(dto);
        publishMentionEvent(entityType, entityId, request.content());
        return comment;
    }

    /** 在父资源下更新评论，仅作者可操作。 */
    @Transactional
    public CommentVO updateByEntity(
            String entityType, Long entityId, Long commentId, CommentUpdateDTO request) {
        var comment = requireComment(entityType, entityId, commentId);
        enforceOwnership(comment);
        return super.update(commentId, request);
    }

    /** 在父资源下删除评论，仅作者可操作。 */
    @Transactional
    public void deleteByEntity(String entityType, Long entityId, Long commentId) {
        var comment = requireComment(entityType, entityId, commentId);
        enforceOwnership(comment);
        super.delete(commentId);
    }

    private void applyParentContext(String entityType, Long entityId, CommentPageDTO request) {
        request.setEntityType(entityType);
        request.setEntityId(entityId);
    }

    private Comment requireComment(String entityType, Long entityId, Long commentId) {
        return commentRepository
                .findById(commentId)
                .filter(
                        comment ->
                                entityType.equals(comment.getEntityType())
                                        && entityId.equals(comment.getEntityId()))
                .orElseThrow(() -> exception(COMMENT_NOT_FOUND));
    }

    private void publishMentionEvent(String entityType, Long entityId, String content) {
        var actorName =
                operatorContext
                        .currentOwnerId()
                        .flatMap(userRepository::findById)
                        .map(
                                user ->
                                        user.getNickname() != null
                                                ? user.getNickname()
                                                : user.getUsername())
                        .orElse("有人");
        var excerpt = content.length() > 50 ? content.substring(0, 50) + "…" : content;
        extractMentionIds(content)
                .forEach(
                        userId ->
                                eventPublisher.publishEvent(
                                        new MentionEvent(
                                                userId, actorName, entityType, entityId, excerpt)));
    }

    /** 提取 @mentions，返回 JSON 数组字符串。 */
    private String extractMentions(String content) {
        var matcher = MENTION_PATTERN.matcher(content);
        var ids = new ArrayList<String>();
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        return ids.isEmpty() ? null : "[" + String.join(",", ids) + "]";
    }

    /** 提取 @mentions，返回用户 ID 列表。 */
    private List<Long> extractMentionIds(String content) {
        var matcher = MENTION_PATTERN.matcher(content);
        var ids = new ArrayList<Long>();
        while (matcher.find()) {
            ids.add(Long.parseLong(matcher.group(1)));
        }
        return ids;
    }
}
