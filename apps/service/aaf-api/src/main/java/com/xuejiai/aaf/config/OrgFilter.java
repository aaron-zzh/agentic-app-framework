package com.xuejiai.aaf.config;

import java.io.IOException;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationService;
import com.xuejiai.aaf.module.system.org.repository.OrgMemberRepository;
import com.xuejiai.aaf.module.system.org.repository.WorkspaceMemberRepository;
import com.xuejiai.aaf.module.system.org.repository.WorkspaceRepository;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 组织过滤器，从请求头 X-Org-Id 读取当前组织 ID、X-Workspace-Id 读取当前工作区 ID，并存入 OrgContext。
 *
 * <p>安全约束：orgId/workspaceId 均不能无条件采信客户端传入的值——必须校验当前登录用户是否真实属于 该组织/工作区（查 sys_org_member /
 * sys_workspace_member），不属于则拒绝（403），防止任意用户通过 篡改请求头访问其他组织/工作区的数据（横向越权）。工作区校验在组织校验之后执行（先确认 orgId 有效，
 * 再校验 workspaceId 是否属于该组织且用户是该工作区成员）。未认证请求（如登录、公开接口）或未携带 对应请求头时跳过校验，由 {@link OrgFilterAspect}
 * 负责上下文缺失场景下的 fail-closed 处理。
 */
@Component
@Order(200) // Security Filter 之后
@RequiredArgsConstructor
public class OrgFilter implements Filter {

    private static final String HEADER_ORG_ID = "X-Org-Id";
    private static final String HEADER_WORKSPACE_ID = "X-Workspace-Id";
    private static final String ALL_ORGANIZATIONS = "all";

    private final OperatorContext operatorContext;
    private final AuthorizationService authorizationService;
    private final OrgMemberRepository orgMemberRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            var httpRequest = (HttpServletRequest) request;
            if (!resolveOrgId(httpRequest, (HttpServletResponse) response)) {
                return;
            }
            if (!resolveWorkspaceId(httpRequest, (HttpServletResponse) response)) {
                return;
            }
            chain.doFilter(request, response);
        } finally {
            OrgContext.clear();
        }
    }

    /** 解析并校验 X-Org-Id，成功（含未携带该头）返回 true，失败已写响应返回 false。 */
    private boolean resolveOrgId(HttpServletRequest httpRequest, HttpServletResponse response)
            throws IOException {
        var orgIdHeader = httpRequest.getHeader(HEADER_ORG_ID);
        if (orgIdHeader == null || orgIdHeader.isBlank()) {
            return true;
        }
        if (ALL_ORGANIZATIONS.equalsIgnoreCase(orgIdHeader)) {
            if (!"GET".equalsIgnoreCase(httpRequest.getMethod())) {
                writeForbidden(response, "全组织上下文仅支持读取请求");
                return false;
            }
            if (httpRequest.getHeader(HEADER_WORKSPACE_ID) != null) {
                writeForbidden(response, "全组织上下文不能指定工作区");
                return false;
            }
            if (!authorizationService.isCurrentSubjectSuperAdmin()) {
                writeForbidden(response, "仅超级管理员可查看全部组织数据");
                return false;
            }
            OrgContext.useAllOrganizations();
            return true;
        }
        Long orgId;
        try {
            orgId = Long.valueOf(orgIdHeader);
        } catch (NumberFormatException e) {
            writeForbidden(response, "组织标识格式非法");
            return false;
        }
        // 未认证请求无法校验归属，交由后续鉴权链处理（如接口本身要求登录会在此之后拦截）
        var userId = operatorContext.currentOwnerId().orElse(null);
        var isSuperAdmin = userId != null && authorizationService.isCurrentSubjectSuperAdmin();
        // 此刻 OrgContext 尚未设置 orgId（正在校验中），这次校验查询本身需豁免
        // OrgFilterAspect 的 fail-closed 检查，否则会陷入"为校验 orgId 而查询，
        // 却因缺少 orgId 被拦截"的自相矛盾。
        var belongsToOrg =
                userId == null
                        || isSuperAdmin
                        || OrgContext.runIgnoring(
                                () ->
                                        orgMemberRepository.existsByOrgIdAndUserIdAndDeletedFalse(
                                                orgId, userId));
        if (userId != null && !belongsToOrg) {
            writeForbidden(response, "您不属于该组织，无权访问");
            return false;
        }
        OrgContext.setCurrentOrgId(orgId);
        return true;
    }

    /** 解析并校验 X-Workspace-Id，成功（含未携带该头）返回 true，失败已写响应返回 false。 */
    private boolean resolveWorkspaceId(HttpServletRequest httpRequest, HttpServletResponse response)
            throws IOException {
        var workspaceIdHeader = httpRequest.getHeader(HEADER_WORKSPACE_ID);
        if (workspaceIdHeader == null || workspaceIdHeader.isBlank()) {
            return true;
        }
        if (ALL_ORGANIZATIONS.equalsIgnoreCase(workspaceIdHeader)) {
            if (!"GET".equalsIgnoreCase(httpRequest.getMethod())) {
                writeForbidden(response, "全工作区上下文仅支持读取请求");
                return false;
            }
            var orgId = OrgContext.getCurrentOrgId();
            var userId = operatorContext.currentOwnerId().orElse(null);
            if (orgId == null || userId == null || !canReadAllWorkspaces(orgId, userId)) {
                writeForbidden(response, "仅组织所有者、管理员或超级管理员可查看全部工作区数据");
                return false;
            }
            OrgContext.useAllWorkspaces();
            return true;
        }
        Long workspaceId;
        try {
            workspaceId = Long.valueOf(workspaceIdHeader);
        } catch (NumberFormatException e) {
            writeForbidden(response, "工作区标识格式非法");
            return false;
        }
        var userId = operatorContext.currentOwnerId().orElse(null);
        var orgId = OrgContext.getCurrentOrgId();
        var isSuperAdmin = userId != null && authorizationService.isCurrentSubjectSuperAdmin();
        var belongsToWorkspace =
                userId == null
                        || isSuperAdmin
                        || OrgContext.runIgnoring(
                                () -> {
                                    var workspace =
                                            workspaceRepository.findById(workspaceId).orElse(null);
                                    if (workspace == null
                                            || (orgId != null
                                                    && !orgId.equals(workspace.getOrgId()))) {
                                        return false;
                                    }
                                    return workspaceMemberRepository
                                            .existsByWorkspaceIdAndUserIdAndDeletedFalse(
                                                    workspaceId, userId);
                                });
        if (userId != null && !belongsToWorkspace) {
            writeForbidden(response, "您不属于该工作区，无权访问");
            return false;
        }
        OrgContext.setCurrentWorkspaceId(workspaceId);
        return true;
    }

    private boolean canReadAllWorkspaces(Long orgId, Long userId) {
        if (authorizationService.isCurrentSubjectSuperAdmin()) {
            return true;
        }
        return OrgContext.runIgnoring(
                () ->
                        orgMemberRepository
                                .findByOrgIdAndUserIdAndDeletedFalse(orgId, userId)
                                .map(member -> member.getRole())
                                .filter(role -> "owner".equals(role) || "admin".equals(role))
                                .isPresent());
    }

    private void writeForbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JsonUtils.toJsonString(Result.error(403, message)));
    }
}
