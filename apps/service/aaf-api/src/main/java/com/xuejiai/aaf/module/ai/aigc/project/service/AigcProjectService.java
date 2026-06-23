package com.xuejiai.aaf.module.ai.aigc.project.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProject;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectDoc;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcContentRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectDocRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcStoryboardRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcTimelineRepository;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectDocLinkDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectDocVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectPageDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectSummaryVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectVO;
import com.xuejiai.aaf.module.document.domain.Document;
import com.xuejiai.aaf.module.document.repository.DocumentRepository;

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

    private final AigcProjectRepository repository;
    private final AigcStoryboardRepository storyboardRepository;
    private final AigcTimelineRepository timelineRepository;
    private final AigcContentRepository contentRepository;
    private final AigcProjectDocRepository projectDocRepository;
    private final DocumentRepository documentRepository;

    @Autowired private OperatorContext operatorContext;

    @Override
    protected JpaRepository<AigcProject, Long> getRepository() {
        return repository;
    }

    @Override
    protected JpaSpecificationExecutor<AigcProject> getSpecExecutor() {
        return repository;
    }

    @Override
    protected String entityName() {
        return "创作项目";
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

    /** BE-8 数据隔离：更新前校验 ownership，跨用户返回 404 防探测。 */
    @Transactional
    public AigcProjectVO updateOwned(Long id, AigcProjectUpdateDTO dto) {
        var entity = requireEntity(id);
        Long userId = operatorContext.currentUserId().orElseThrow();
        if (!entity.getUserId().equals(userId)) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "项目不存在");
        }
        updateEntity(entity, dto);
        return toVO(entity);
    }

    /** BE-8 数据隔离：删除前校验 ownership，跨用户返回 404 防探测。 */
    @Transactional
    public void deleteOwned(Long id) {
        var entity = requireEntity(id);
        Long userId = operatorContext.currentUserId().orElseThrow();
        if (!entity.getUserId().equals(userId)) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "项目不存在");
        }
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
        requireEntity(projectId);
        Document doc =
                documentRepository
                        .findById(dto.docId())
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "文档不存在"));

        // 幂等：已存在则直接返回
        return projectDocRepository
                .findByProjectIdAndDocId(projectId, dto.docId())
                .map(existing -> toDocVO(existing, doc))
                .orElseGet(
                        () -> {
                            var link = new AigcProjectDoc();
                            link.setProjectId(projectId);
                            link.setDocId(dto.docId());
                            link.setRole(dto.role() != null ? dto.role() : "ref");
                            projectDocRepository.save(link);
                            return toDocVO(link, doc);
                        });
    }

    /** 取消文档与项目的关联。 */
    @Transactional
    public void unlinkDoc(Long projectId, Long docId) {
        projectDocRepository.deleteByProjectIdAndDocId(projectId, docId);
    }

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
