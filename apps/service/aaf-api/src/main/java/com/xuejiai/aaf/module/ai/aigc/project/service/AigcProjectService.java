package com.xuejiai.aaf.module.ai.aigc.project.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProject;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectDoc;
import com.xuejiai.aaf.module.ai.aigc.project.repository.*;
import com.xuejiai.aaf.module.ai.aigc.project.vo.*;
import com.xuejiai.aaf.module.document.domain.Document;
import com.xuejiai.aaf.module.document.repository.DocumentRepository;
import com.xuejiai.aaf.module.user.growth.event.UserGrowthEvent;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

/** 创作项目服务。 */
@Service
@RequiredArgsConstructor
public class AigcProjectService
        extends BaseCrudService<
                AigcProject,
                AigcProjectVO,
                AigcProjectCreateDTO,
                AigcProjectUpdateDTO,
                AigcProjectPageDTO> {

    public static final String COMMAND_UPDATE_OWNED = "AIGC_PROJECT_UPDATE_OWNED";
    public static final String COMMAND_LINK_DOCUMENT = "AIGC_PROJECT_LINK_DOCUMENT";
    public static final String COMMAND_UNLINK_DOCUMENT = "AIGC_PROJECT_UNLINK_DOCUMENT";

    private static final String FIELD_DOCUMENTS = "documents";

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("id", "name", "type", "status", "createTime", "updateTime");

    private final AigcProjectRepository repository;
    private final AigcStoryboardRepository storyboardRepository;
    private final AigcTimelineRepository timelineRepository;
    private final AigcContentRepository contentRepository;
    private final AigcProjectDocRepository projectDocRepository;
    private final DocumentRepository documentRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired private OperatorContext operatorContext;

    @Override
    public AigcProjectVO create(AigcProjectCreateDTO request) {
        var vo = super.create(request);
        operatorContext
                .currentUserId()
                .ifPresent(
                        uid ->
                                eventPublisher.publishEvent(
                                        new UserGrowthEvent(uid, "project.created")));
        return vo;
    }

    @Override
    protected AigcProjectRepository getRepository() {
        return repository;
    }

    @Override
    protected AigcProjectVO toVO(AigcProject e) {
        var vo = new AigcProjectVO();
        vo.setId(e.getId());
        vo.setName(e.getName());
        vo.setCoverUrl(e.getCoverUrl());
        vo.setDescription(e.getDescription());
        vo.setType(e.getType());
        vo.setStatus(e.getStatus());
        vo.setUserId(e.getUserId());
        vo.setPrompt(e.getPrompt());
        vo.setCreateTime(e.getCreateTime());
        vo.setUpdateTime(e.getUpdateTime());
        return vo;
    }

    @Override
    protected AigcProject toEntity(AigcProjectCreateDTO dto) {
        var e = new AigcProject();
        e.setName(dto.name());
        e.setCoverUrl(dto.coverUrl());
        e.setDescription(dto.description());
        if (dto.type() != null) e.setType(dto.type());
        if (dto.prompt() != null) e.setPrompt(dto.prompt());
        e.setUserId(operatorContext.currentUserId().orElseThrow());
        return e;
    }

    @Override
    protected void updateEntity(AigcProject e, AigcProjectUpdateDTO dto) {
        if (dto.name() != null) e.setName(dto.name());
        if (dto.coverUrl() != null) e.setCoverUrl(dto.coverUrl());
        if (dto.description() != null) e.setDescription(dto.description());
        if (dto.type() != null) e.setType(dto.type());
        if (dto.status() != null) e.setStatus(dto.status());
        if (dto.prompt() != null) e.setPrompt(dto.prompt());
    }

    @Override
    protected Map<String, Object> authorizationAttributes(AigcProject project) {
        var attributes = new java.util.LinkedHashMap<>(super.authorizationAttributes(project));
        attributes.put("name", project.getName());
        attributes.put("coverUrl", project.getCoverUrl());
        attributes.put("description", project.getDescription());
        attributes.put("type", project.getType());
        attributes.put("status", project.getStatus());
        attributes.put("prompt", project.getPrompt());
        attributes.put("userId", project.getUserId());
        return attributes;
    }

    /** DELETE 已授权加载项目后，在同一流程内校验业务 owner。 */
    @Override
    protected void beforeDelete(AigcProject project) {
        requireOwner(project);
    }

    @Override
    protected Specification<AigcProject> buildSpec(AigcProjectPageDTO p) {
        // BE-8 数据隔离：强制按当前 userId 过滤，防止跨用户读取
        Long userId = operatorContext.currentUserId().orElseThrow();
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("userId"), userId));
            if (p.getName() != null && !p.getName().isBlank())
                predicates.add(cb.like(root.get("name"), "%" + p.getName() + "%"));
            if (p.getStatus() != null) predicates.add(cb.equal(root.get("status"), p.getStatus()));
            if (p.getType() != null) predicates.add(cb.equal(root.get("type"), p.getType()));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** BE-8 数据隔离：单条查询后校验 ownership，跨用户返回 404 防探测。 */
    public AigcProjectVO getByIdOwned(Long id) {
        var entity = requireEntity(id);
        Long userId = operatorContext.currentUserId().orElseThrow();
        if (!entity.getUserId().equals(userId)) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "项目不存在");
        }
        return toVO(entity);
    }

    /** BE-8 数据隔离：通过具名 UPDATE 命令校验 ownership、字段策略和对象策略。 */
    @Transactional
    public AigcProjectVO updateOwned(Long id, AigcProjectUpdateDTO dto) {
        var fields = projectUpdateFields(dto);
        if (fields.isEmpty()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "至少需要修改一个项目字段");
        }
        var plan =
                new CustomUpdatePlan<AigcProject, AigcProjectUpdateDTO, Void, AigcProjectVO>(
                        COMMAND_UPDATE_OWNED,
                        fields,
                        (project, command) -> requireOwner(project),
                        this::updateEntity,
                        (project, command) -> null,
                        true,
                        (project, command, ignored) -> {},
                        (project, command, ignored) -> toVO(project));
        return executeCustomUpdateCommand(id, dto, plan);
    }

    /** 删除直接进入 BaseCrud 唯一 DELETE PEP；ownership 由 {@link #beforeDelete(AigcProject)} 校验。 */
    @Transactional
    public void deleteOwned(Long id) {
        delete(id);
    }

    /** 获取项目概览统计（分镜板/时间轴/内容产出数量）。 */
    public AigcProjectSummaryVO getSummary(Long id) {
        AigcProject project = requireEntity(id);
        var vo = new AigcProjectSummaryVO();
        vo.setId(project.getId());
        vo.setName(project.getName());
        vo.setStoryboardCount(storyboardRepository.findByProjectIdOrderByCreateTimeDesc(id).size());
        vo.setTimelineCount(timelineRepository.findByProjectIdOrderByCreateTimeDesc(id).size());
        vo.setContentCount(contentRepository.findByProjectIdOrderByCreateTimeDesc(id).size());
        vo.setAssetCount(0); // 暂无独立素材 repository，保留扩展点
        return vo;
    }

    /** 获取项目关联的文档列表。 */
    public List<AigcProjectDocVO> getProjectDocs(Long projectId) {
        requireEntity(projectId);
        List<AigcProjectDoc> links =
                projectDocRepository.findByProjectIdOrderBySortOrder(projectId);
        if (links.isEmpty()) return List.of();

        List<Long> docIds = links.stream().map(AigcProjectDoc::getDocId).toList();
        Map<Long, Document> docMap =
                documentRepository.findAllById(docIds).stream()
                        .collect(Collectors.toMap(Document::getId, d -> d));

        return links.stream()
                .map(link -> toDocVO(link, docMap.get(link.getDocId())))
                .collect(Collectors.toList());
    }

    /** 关联文档到项目。 */
    @Transactional
    public AigcProjectDocVO linkDoc(Long projectId, AigcProjectDocLinkDTO dto) {
        var command = new LinkDocumentCommand(dto.docId(), dto.role());
        var plan =
                new CustomUpdatePlan<
                        AigcProject, LinkDocumentCommand, AigcProjectDoc, AigcProjectDocVO>(
                        COMMAND_LINK_DOCUMENT,
                        Set.of(FIELD_DOCUMENTS),
                        (project, ignored) -> requireOwner(project),
                        (project, ignored) -> {},
                        (project, request) -> linkDocument(project.getId(), request),
                        false,
                        (project, request, link) -> {},
                        (project, request, link) ->
                                toDocVO(
                                        link,
                                        documentRepository.findById(request.docId()).orElse(null)));
        return executeCustomUpdateCommand(projectId, command, plan);
    }

    /** 取消文档与项目的关联。 */
    @Transactional
    public void unlinkDoc(Long projectId, Long docId) {
        var command = new UnlinkDocumentCommand(docId);
        var plan =
                new CustomUpdatePlan<AigcProject, UnlinkDocumentCommand, Void, Void>(
                        COMMAND_UNLINK_DOCUMENT,
                        Set.of(FIELD_DOCUMENTS),
                        (project, ignored) -> requireOwner(project),
                        (project, ignored) -> {},
                        (project, request) -> {
                            projectDocRepository.deleteByProjectIdAndDocId(
                                    project.getId(), request.docId());
                            return null;
                        },
                        false,
                        (project, request, ignored) -> {},
                        (project, request, ignored) -> null);
        executeCustomUpdateCommand(projectId, command, plan);
    }

    private AigcProjectDoc linkDocument(Long projectId, LinkDocumentCommand command) {
        documentRepository
                .findById(command.docId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "文档不存在"));
        return projectDocRepository
                .findByProjectIdAndDocId(projectId, command.docId())
                .orElseGet(
                        () -> {
                            var link = new AigcProjectDoc();
                            link.setProjectId(projectId);
                            link.setDocId(command.docId());
                            link.setRole(command.role() != null ? command.role() : "ref");
                            return projectDocRepository.save(link);
                        });
    }

    private Set<String> projectUpdateFields(AigcProjectUpdateDTO dto) {
        var fields = new java.util.LinkedHashSet<String>();
        if (dto.name() != null) fields.add("name");
        if (dto.coverUrl() != null) fields.add("coverUrl");
        if (dto.description() != null) fields.add("description");
        if (dto.type() != null) fields.add("type");
        if (dto.status() != null) fields.add("status");
        if (dto.prompt() != null) fields.add("prompt");
        return Set.copyOf(fields);
    }

    private void requireOwner(AigcProject project) {
        Long userId = operatorContext.currentUserId().orElseThrow();
        if (!project.getUserId().equals(userId)) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "项目不存在");
        }
    }

    private record LinkDocumentCommand(Long docId, String role) {}

    private record UnlinkDocumentCommand(Long docId) {}

    private AigcProjectDocVO toDocVO(AigcProjectDoc link, Document doc) {
        var vo = new AigcProjectDocVO();
        vo.setId(link.getId());
        vo.setProjectId(link.getProjectId());
        vo.setDocId(link.getDocId());
        vo.setRole(link.getRole());
        vo.setSortOrder(link.getSortOrder());
        vo.setCreateTime(link.getCreateTime());
        if (doc != null) {
            vo.setDocTitle(doc.getTitle());
            vo.setDocType(doc.getDocType());
            vo.setSourceFileId(doc.getSourceFileId());
        }
        return vo;
    }
}
