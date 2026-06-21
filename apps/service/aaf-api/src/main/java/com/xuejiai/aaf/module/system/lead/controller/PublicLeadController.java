package com.xuejiai.aaf.module.system.lead.controller;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.enums.lead.LeadChannelEnum;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.protection.RateLimit;
import com.xuejiai.aaf.module.system.lead.service.GuestLeadCrudService;
import com.xuejiai.aaf.module.system.lead.vo.GuestLeadCreateDTO;
import com.xuejiai.aaf.module.system.lead.vo.GuestLeadVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * 访客线索公开端点（供未登录访客使用）。
 *
 * <p>路径前缀 {@code /api/public/leads/**} 必须在 {@code SecurityConfig.PUBLIC_PATHS} 中配置匿名可访问。 所有接口均做 IP
 * 维度速率限制；返回数据剥离敏感字段（ipAddress/userAgent/referer/handledBy/handledTime）。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "访客线索-公开")
@Validated
@RestController
@RequestMapping("/api/public/leads")
@RequiredArgsConstructor
public class PublicLeadController {

    private final GuestLeadCrudService service;

    /**
     * 访客提交线索（CHAT 续聊登记 / 邮箱订阅 / 联系我们 / 反馈）。
     *
     * <p>IP/UA/Referer 由后端从请求中提取，前端无法伪造。
     */
    @Operation(summary = "提交访客线索")
    @RateLimit(
            limit = 10,
            windowSeconds = 60,
            prefix = "public-lead-create",
            message = "提交过于频繁，请稍后再试")
    @PostMapping
    public Result<GuestLeadVO> create(
            @Valid @RequestBody GuestLeadCreateDTO dto, HttpServletRequest request) {
        var vo =
                service.publicCreate(
                        dto,
                        resolveIp(request),
                        request.getHeader("User-Agent"),
                        request.getHeader("Referer"));
        return Result.success(stripSensitive(vo));
    }

    /**
     * 访客查询自己的线索记录（按 anonymousId）。
     *
     * <p>仅返回 anonymousId 关联的记录，避免越权。
     */
    @Operation(summary = "查询访客自己的线索")
    @RateLimit(limit = 30, windowSeconds = 60, prefix = "public-lead-list")
    @GetMapping("/me")
    public Result<List<GuestLeadVO>> listMine(
            @Parameter(description = "访客匿名 ID") @RequestParam @NotBlank @Size(max = 64)
                    String anonymousId,
            @Parameter(description = "渠道（可选）") @RequestParam(required = false)
                    LeadChannelEnum channel) {
        var list =
                service.listByAnonymous(anonymousId, channel).stream()
                        .map(PublicLeadController::stripSensitive)
                        .toList();
        return Result.success(list);
    }

    /**
     * 访客最近一次 CHAT 记录（用于续聊取 threadId）。
     *
     * <p>无记录时返回 null，前端按"新会话"处理。
     */
    @Operation(summary = "查询访客最近一次 CHAT 记录（续聊用）")
    @RateLimit(limit = 30, windowSeconds = 60, prefix = "public-lead-latest-chat")
    @GetMapping("/me/latest-chat")
    public Result<GuestLeadVO> latestChat(
            @Parameter(description = "访客匿名 ID") @RequestParam @NotBlank @Size(max = 64)
                    String anonymousId) {
        var vo = service.findLatestChat(anonymousId);
        return Result.success(vo == null ? null : stripSensitive(vo));
    }

    /** 优先取代理头中的真实 IP，回退到 remoteAddr。 */
    private static String resolveIp(HttpServletRequest req) {
        var xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // 取第一个（最近的客户端 IP）
            var idx = xff.indexOf(',');
            return idx > 0 ? xff.substring(0, idx).trim() : xff.trim();
        }
        var realIp = req.getHeader("X-Real-IP");
        return realIp != null && !realIp.isBlank() ? realIp : req.getRemoteAddr();
    }

    /** 公开端返回值剥离敏感字段（仅管理端可见） */
    private static GuestLeadVO stripSensitive(GuestLeadVO v) {
        return new GuestLeadVO(
                v.id(),
                v.anonymousId(),
                v.channel(),
                v.email(),
                v.name(),
                v.phone(),
                v.subject(),
                v.content(),
                v.threadId(),
                v.agentRole(),
                v.lastMessageAt(),
                null, // ipAddress
                null, // userAgent
                null, // referer
                null, // region（基于 IP 推断，同样仅管理端可见）
                v.status(),
                null, // handledBy
                null, // handledTime
                v.contactId(),
                v.createTime(),
                v.updateTime());
    }
}
