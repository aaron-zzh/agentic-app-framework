package com.xuejiai.aaf.module.system.authorization;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "授权确认")
@RestController
@RequestMapping("/api/system/authorization-challenges")
@RequiredArgsConstructor
public class AuthorizationChallengeController {

    private final AuthorizationService authorizationService;

    @Operation(summary = "批准本人待处理 challenge")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/approve")
    public Result<Boolean> approve(@PathVariable UUID id) {
        return Result.success(authorizationService.approveChallenge(id));
    }
}
