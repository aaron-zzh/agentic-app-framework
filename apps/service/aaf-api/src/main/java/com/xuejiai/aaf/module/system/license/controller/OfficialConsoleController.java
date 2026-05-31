package com.xuejiai.aaf.module.system.license.controller;

import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.License;
import com.xuejiai.aaf.framework.security.license.LicenseOwnerRequired;
import com.xuejiai.aaf.module.system.license.service.LicenseIssueService;
import com.xuejiai.aaf.module.system.license.service.SourceArchiveService;
import com.xuejiai.aaf.module.system.license.vo.LicenseIssueDTO;
import com.xuejiai.aaf.module.system.license.vo.LicenseIssueVO;
import com.xuejiai.aaf.module.system.license.vo.OfficialConsoleSummaryVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 官方服务控制台接口。 */
@Tag(name = "官方服务控制台")
@RestController
@RequestMapping("/api/official/console")
@LicenseOwnerRequired("官方服务控制台")
@RequiredArgsConstructor
public class OfficialConsoleController {

    private final LicenseIssueService licenseIssueService;
    private final SourceArchiveService sourceArchiveService;

    @Operation(summary = "查询官方服务控制台摘要")
    @GetMapping("/summary")
    public Result<OfficialConsoleSummaryVO> summary() {
        var license = License.get();
        return Result.success(
                new OfficialConsoleSummaryVO(
                        license.getUserId(),
                        license.getTier(),
                        List.of("客户门户", "开发者运营", "授权签发", "订阅套餐")));
    }

    @Operation(summary = "签发 license.jwt")
    @PostMapping("/licenses")
    public Result<LicenseIssueVO> issueLicense(@Valid @RequestBody LicenseIssueDTO dto) {
        return Result.success(licenseIssueService.issue(dto));
    }

    @Operation(summary = "下载源码包")
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
