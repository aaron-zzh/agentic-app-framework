package com.xuejiai.aaf.module.system.license.controller;

import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.License;
import com.xuejiai.aaf.framework.security.license.LicensePortal;
import com.xuejiai.aaf.module.system.license.service.SourceArchiveService;
import com.xuejiai.aaf.module.system.license.vo.LicenseStatusVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 框架商业授权状态接口。 */
@Tag(name = "框架授权")
@RestController
@RequestMapping("/api/license")
public class LicenseController {

    private final SourceArchiveService sourceArchiveService;

    public LicenseController(SourceArchiveService sourceArchiveService) {
        this.sourceArchiveService = sourceArchiveService;
    }

    @Operation(summary = "查询当前框架授权状态")
    @GetMapping("/current")
    public Result<LicenseStatusVO> current() {
        var license = License.get();
        return Result.success(
                new LicenseStatusVO(
                        license.isIdentityValid(),
                        license.getTier(),
                        license.getUserId(),
                        license.getExpiresAt(),
                        LicensePortal.UPGRADE_URL,
                        license.getFeatures(),
                        List.of("~/.aaf/license.jwt", "./config/license.jwt")));
    }

    @Operation(summary = "下载当前授权可用的源码包")
    @GetMapping("/source-code")
    @FeatureRequired("source-download")
    public ResponseEntity<Resource> downloadSourceCode() {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + sourceArchiveService.filename() + "\"")
                .body(sourceArchiveService.load());
    }
}
