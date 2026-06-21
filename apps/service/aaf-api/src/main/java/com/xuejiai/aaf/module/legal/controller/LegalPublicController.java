package com.xuejiai.aaf.module.legal.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.legal.domain.LegalDocumentType;
import com.xuejiai.aaf.module.legal.service.LegalDocumentService;
import com.xuejiai.aaf.module.legal.vo.LegalDocumentVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 法律文档公开接口（无需登录）。
 *
 * <p>挂在 {@code /api/public/legal/**}，由 SecurityConfig.PUBLIC_PATHS 中的 {@code /api/public/**} 放行。
 *
 * @author AaronZZH &amp; Kiro
 */
@Tag(name = "法律文档（公开）")
@RestController
@RequestMapping("/api/public/legal")
public class LegalPublicController {

    private final LegalDocumentService legalDocumentService;

    public LegalPublicController(LegalDocumentService legalDocumentService) {
        this.legalDocumentService = legalDocumentService;
    }

    @Operation(summary = "获取最新已发布的法律文档（terms / privacy）")
    @GetMapping("/{type}")
    public Result<LegalDocumentVO> getLatest(@PathVariable String type) {
        LegalDocumentType resolved = resolveType(type);
        return Result.success(legalDocumentService.getLatestPublished(resolved));
    }

    /** 接受 terms / privacy 简称或完整 doc_type 字符串。 */
    private LegalDocumentType resolveType(String type) {
        if (type == null) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "type 不能为空");
        }
        return switch (type) {
            case "terms", "legal-terms" -> LegalDocumentType.LEGAL_TERMS;
            case "privacy", "legal-privacy" -> LegalDocumentType.LEGAL_PRIVACY;
            default -> throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "未知法律文档类型：" + type);
        };
    }
}
