package com.xuejiai.aaf.module.knowledge.controller;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.logging.OperationLog;
import com.xuejiai.aaf.framework.logging.OperationType;
import com.xuejiai.aaf.module.knowledge.domain.KnowledgeBase;
import com.xuejiai.aaf.module.knowledge.service.KnowledgeBaseService;
import com.xuejiai.aaf.module.knowledge.vo.BatchImportProgressVO;
import com.xuejiai.aaf.module.knowledge.vo.CreateKnowledgeBaseRequest;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeBaseMaintenancePageDTO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeBaseMaintenanceVO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeBaseStatsVO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeBaseUpdateDTO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeBaseVO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeDocumentVO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeGraphVO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeGraphVO.GraphProjectionStatusVO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeSearchDTO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeSearchResponseVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/** 知识库管理接口；标准 CRUD 统一由 BaseCrud 提供。 */
@Tag(name = "知识库管理")
@RestController
@RequestMapping("/api/knowledge-bases")
@RequiredArgsConstructor
public class KnowledgeBaseController
        extends BaseCrudController<
                KnowledgeBase,
                KnowledgeBaseVO,
                CreateKnowledgeBaseRequest,
                KnowledgeBaseUpdateDTO,
                PageParam> {

    private final KnowledgeBaseService knowledgeBaseService;

    @Override
    protected BaseCrudService<
                    KnowledgeBase,
                    KnowledgeBaseVO,
                    CreateKnowledgeBaseRequest,
                    KnowledgeBaseUpdateDTO,
                    PageParam>
            getService() {
        return knowledgeBaseService;
    }

    @Operation(summary = "后台运维知识库列表")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/maintenance")
    public Result<PageResult<KnowledgeBaseMaintenanceVO>> maintenancePage(
            @Valid KnowledgeBaseMaintenancePageDTO request) {
        return Result.success(knowledgeBaseService.pageMaintenance(request));
    }

    @Operation(summary = "后台运维知识库详情")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/maintenance/{id}")
    public Result<KnowledgeBaseMaintenanceVO> maintenanceDetail(@PathVariable Long id) {
        return Result.success(knowledgeBaseService.getMaintenance(id));
    }

    @Operation(summary = "后台运维知识库统计")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/maintenance/{id}/stats")
    public Result<KnowledgeBaseStatsVO> maintenanceStats(@PathVariable Long id) {
        return Result.success(knowledgeBaseService.getMaintenanceStats(id));
    }

    @Operation(summary = "后台运维知识库文档列表")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/maintenance/{id}/documents")
    public Result<PageResult<KnowledgeDocumentVO>> maintenanceDocuments(
            @PathVariable Long id, Pageable pageable) {
        return Result.success(knowledgeBaseService.listMaintenanceDocuments(id, pageable));
    }

    @Operation(summary = "后台重试失败知识库文档")
    @OperationLog(
            module = "知识库",
            type = OperationType.OTHER,
            description = "后台重试失败知识库文档",
            bizNo = "#{p1}")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/maintenance/{id}/documents/{documentId}/retry")
    public Result<KnowledgeDocumentVO> retryMaintenanceDocument(
            @PathVariable Long id, @PathVariable Long documentId) {
        return Result.success(knowledgeBaseService.retryMaintenanceDocument(id, documentId));
    }

    @Operation(summary = "知识库统计信息")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/stats")
    public Result<KnowledgeBaseStatsVO> stats(@PathVariable Long id) {
        return Result.success(knowledgeBaseService.getStats(id));
    }

    @Operation(summary = "授权多库检索")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/search")
    public Result<KnowledgeSearchResponseVO> search(
            @Valid @RequestBody KnowledgeSearchDTO request) {
        return Result.success(knowledgeBaseService.search(request));
    }

    @Operation(summary = "查询知识图谱")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/graph")
    public Result<KnowledgeGraphVO> graph(@PathVariable Long id) {
        return Result.success(knowledgeBaseService.getGraph(id));
    }

    @Operation(summary = "查询知识图投影状态")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/{id}/graph-projection/status")
    public Result<GraphProjectionStatusVO> graphProjectionStatus(@PathVariable Long id) {
        return Result.success(knowledgeBaseService.getGraphProjectionStatus(id));
    }

    @Operation(summary = "重建知识图投影")
    @OperationLog(
            module = "知识库",
            type = OperationType.OTHER,
            description = "重建知识图投影",
            bizNo = "#{p0}")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/{id}/graph-projection/rebuild")
    public Result<GraphProjectionStatusVO> rebuildGraphProjection(
            @PathVariable Long id,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 200) String requestKey) {
        return Result.success(knowledgeBaseService.rebuildGraphProjection(id, requestKey));
    }

    @Operation(summary = "知识库文档列表")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/documents")
    public Result<PageResult<KnowledgeDocumentVO>> documents(
            @PathVariable Long id, Pageable pageable) {
        return Result.success(knowledgeBaseService.listDocuments(id, pageable));
    }

    @Operation(summary = "知识库文档详情")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/documents/{documentId}")
    public Result<KnowledgeDocumentVO> document(
            @PathVariable Long id, @PathVariable Long documentId) {
        return Result.success(knowledgeBaseService.getDocument(id, documentId));
    }

    @Operation(summary = "删除知识库文档")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{id}/documents/{documentId}")
    public Result<Void> deleteDocument(@PathVariable Long id, @PathVariable Long documentId) {
        knowledgeBaseService.deleteDocument(id, documentId);
        return Result.success();
    }

    @Operation(summary = "重试失败知识库文档")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/documents/{documentId}/retry")
    public Result<KnowledgeDocumentVO> retryDocument(
            @PathVariable Long id, @PathVariable Long documentId) {
        return Result.success(knowledgeBaseService.retryDocument(id, documentId));
    }

    @Operation(summary = "批量上传文档")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/documents/batch")
    public Result<List<KnowledgeDocumentVO>> batchImport(
            @PathVariable Long id, @RequestParam("files") MultipartFile[] files) {
        return Result.success(knowledgeBaseService.batchImportDocuments(id, files));
    }

    @Operation(summary = "查询文档处理进度")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/documents/progress")
    public Result<BatchImportProgressVO> importProgress(@PathVariable Long id) {
        return Result.success(knowledgeBaseService.getImportProgress(id));
    }
}
