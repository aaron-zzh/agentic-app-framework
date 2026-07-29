package com.xuejiai.aaf.module.ai.credential.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.credential.service.ConnectorCredentialService;
import com.xuejiai.aaf.module.ai.credential.vo.ConnectorCredentialRegisterDTO;
import com.xuejiai.aaf.module.ai.credential.vo.ConnectorCredentialVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 服务端 Connector 凭证句柄管理入口。 */
@Tag(name = "Connector 凭证")
@RestController
@RequestMapping("/api/ai/connector-credentials")
@PreAuthorize("hasAuthority('ai:connector-credential:manage')")
@RequiredArgsConstructor
public class ConnectorCredentialController {

    private final ConnectorCredentialService credentialService;
    private final OperatorContext operatorContext;

    @Operation(summary = "登记 Vault 凭证引用")
    @PostMapping
    public Result<ConnectorCredentialVO> register(
            @Validated @RequestBody ConnectorCredentialRegisterDTO request) {
        return Result.success(credentialService.register(currentTenant(), currentUser(), request));
    }

    @Operation(summary = "撤销 Connector 凭证句柄")
    @DeleteMapping("/{handleId}")
    public Result<Void> revoke(@PathVariable String handleId) {
        credentialService.revoke(currentTenant(), currentUser(), handleId);
        return Result.success();
    }

    private UserId currentUser() {
        return operatorContext
                .currentUserId()
                .map(String::valueOf)
                .map(UserId::new)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED));
    }

    private TenantId currentTenant() {
        var orgId = OrgContext.getCurrentOrgId();
        if (orgId == null) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "请求缺少已校验的组织上下文");
        }
        return new TenantId(orgId.toString());
    }
}
