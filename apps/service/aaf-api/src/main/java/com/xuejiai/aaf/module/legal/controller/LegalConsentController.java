package com.xuejiai.aaf.module.legal.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.legal.service.LegalDocumentService;
import com.xuejiai.aaf.module.legal.vo.ConsentSubmitDTO;
import com.xuejiai.aaf.module.legal.vo.PendingConsentVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * 法律文档同意接口（需登录）。
 *
 * <p>登录成功后，前端调用 {@link #pending()} 获取用户尚未同意的最新版本列表，弹窗强制确认。 用户确认后调用 {@link #submit} 写入同意快照。
 *
 * @author AaronZZH &amp; Kiro
 */
@Tag(name = "法律文档同意")
@RestController
@RequestMapping("/api/legal/consent")
public class LegalConsentController {

    private final LegalDocumentService legalDocumentService;
    private final OperatorContext operatorContext;

    public LegalConsentController(
            LegalDocumentService legalDocumentService, OperatorContext operatorContext) {
        this.legalDocumentService = legalDocumentService;
        this.operatorContext = operatorContext;
    }

    @Operation(summary = "查询当前用户待同意的法律文档")
    @GetMapping("/pending")
    public Result<PendingConsentVO> pending() {
        Long userId = operatorContext.currentUserId().orElseThrow();
        return Result.success(legalDocumentService.listPendingForUser(userId));
    }

    @Operation(summary = "提交对某文档的同意")
    @PostMapping
    public Result<Void> submit(
            @Valid @RequestBody ConsentSubmitDTO dto, HttpServletRequest request) {
        Long userId = operatorContext.currentUserId().orElseThrow();
        legalDocumentService.recordConsent(
                userId, dto.documentId(), getClientIp(request), getSourceApp(request));
        return Result.success();
    }

    /** 获取客户端真实 IP，优先读 X-Forwarded-For（反向代理场景）。 */
    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank()) return xri.trim();
        return request.getRemoteAddr();
    }

    /** 来源应用：通过 X-Source-App 头识别 web / uniapp / api。 */
    private String getSourceApp(HttpServletRequest request) {
        String header = request.getHeader("X-Source-App");
        return header != null && !header.isBlank() ? header : "web";
    }
}
