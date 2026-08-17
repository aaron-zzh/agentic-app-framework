package com.xuejiai.aaf.module.ai.aigc.copywriting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.copywriting.vo.CopywritingAssetPageVO;
import com.xuejiai.aaf.module.ai.aigc.copywriting.vo.CopywritingAssetPageVO.Asset;
import com.xuejiai.aaf.module.ai.aigc.copywriting.vo.CopywritingAssetPageVO.Project;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectApi;
import com.xuejiai.aaf.module.document.api.DocumentReferenceApi;
import com.xuejiai.aaf.module.document.api.DocumentReferenceApi.DocumentQuery;

import lombok.RequiredArgsConstructor;

/** 文案资产查询服务。 */
@Service
@RequiredArgsConstructor
public class CopywritingService {

    private static final String COPYWRITING_DOCUMENT_TYPE = "copywriting";
    private static final int SUMMARY_CODE_POINT_LIMIT = 160;
    private static final Pattern WHITESPACE =
            Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private final DocumentReferenceApi documentReferenceApi;
    private final AigcProjectApi aigcProjectApi;
    private final OperatorContext operatorContext;

    public CopywritingAssetPageVO assets(
            CopywritingLinkStatus linkStatus,
            Long projectId,
            String keyword,
            int pageNo,
            int pageSize) {
        var effectiveLinkStatus = linkStatus == null ? CopywritingLinkStatus.ALL : linkStatus;
        if (effectiveLinkStatus == CopywritingLinkStatus.UNLINKED && projectId != null) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "查询未关联文案时不能指定项目");
        }
        if (projectId != null && projectId <= 0) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "项目 ID 必须为正数");
        }
        if (keyword != null && keyword.length() > 100) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "关键词长度不能超过100");
        }
        if (pageNo < 1 || pageSize < 1 || pageSize > 50) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "分页参数不正确");
        }

        var ownerId =
                operatorContext
                        .currentOwnerId()
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.UNAUTHORIZED, "账号未登录"));
        var orgId = OrgContext.getCurrentOrgId();
        var workspaceId = OrgContext.getCurrentWorkspaceId();
        var needsLinkedDocumentIds =
                projectId != null || effectiveLinkStatus != CopywritingLinkStatus.ALL;
        var linkedDocumentIds =
                needsLinkedDocumentIds
                        ? aigcProjectApi.findLinkedDocumentIds(
                                ownerId, orgId, workspaceId, projectId)
                        : Set.<Long>of();
        Collection<Long> includeIds = null;
        Collection<Long> excludeIds = null;
        switch (effectiveLinkStatus) {
            case ALL -> {
                if (projectId != null) {
                    includeIds = linkedDocumentIds;
                }
            }
            case LINKED -> includeIds = linkedDocumentIds;
            case UNLINKED -> excludeIds = linkedDocumentIds;
        }

        var page =
                documentReferenceApi.query(
                        new DocumentQuery(
                                ownerId,
                                orgId,
                                workspaceId,
                                COPYWRITING_DOCUMENT_TYPE,
                                keyword,
                                includeIds,
                                excludeIds,
                                pageNo,
                                pageSize));
        var documentIds = page.list().stream().map(item -> item.documentId()).toList();
        var projectReferences =
                aigcProjectApi.findDocumentProjects(ownerId, orgId, workspaceId, documentIds);
        var projectsByDocument = new LinkedHashMap<Long, List<Project>>();
        var seenProjectsByDocument = new LinkedHashMap<Long, Set<Long>>();
        for (var reference : projectReferences) {
            var seenProjectIds =
                    seenProjectsByDocument.computeIfAbsent(
                            reference.documentId(), ignored -> new HashSet<>());
            if (seenProjectIds.add(reference.projectId())) {
                projectsByDocument
                        .computeIfAbsent(reference.documentId(), ignored -> new ArrayList<>())
                        .add(new Project(reference.projectId(), reference.projectName()));
            }
        }

        var assets =
                page.list().stream()
                        .map(
                                document ->
                                        new Asset(
                                                document.documentId(),
                                                document.title(),
                                                summarize(document.content()),
                                                document.updateTime(),
                                                projectsByDocument.getOrDefault(
                                                        document.documentId(), List.of())))
                        .toList();
        return new CopywritingAssetPageVO(
                assets, page.total(), page.pageNo(), page.pageSize(), page.hasMore());
    }

    private static String summarize(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        var normalized = WHITESPACE.matcher(content).replaceAll(" ").trim();
        var codePointCount = normalized.codePointCount(0, normalized.length());
        if (codePointCount <= SUMMARY_CODE_POINT_LIMIT) {
            return normalized;
        }
        var endIndex = normalized.offsetByCodePoints(0, SUMMARY_CODE_POINT_LIMIT);
        return normalized.substring(0, endIndex) + "…";
    }
}
