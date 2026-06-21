package com.xuejiai.aaf.module.system.file;

import java.time.Duration;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.storage.FileService;
import com.xuejiai.aaf.framework.storage.FileVO;
import com.xuejiai.aaf.framework.storage.OssStorageService;
import com.xuejiai.aaf.framework.storage.StorageService;
import com.xuejiai.aaf.framework.storage.StsCredentials;
import com.xuejiai.aaf.module.system.file.service.FileRecordService;
import com.xuejiai.aaf.module.system.file.vo.FileConfirmDTO;
import com.xuejiai.aaf.module.system.file.vo.FileRecordPageDTO;
import com.xuejiai.aaf.module.system.file.vo.FileRecordVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 文件管理接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "文件管理")
@RestController
@RequestMapping("/api/system/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final StorageService storageService;
    private final FileRecordService fileRecordService;
    private final OperatorContext operatorContext;

    /** OSS 类型时非空，其他存储类型为 null */
    @Autowired(required = false)
    @Nullable
    private OssStorageService ossStorageService;

    @Operation(summary = "分页查询文件列表")
    @GetMapping
    public Result<PageResult<FileRecordVO>> page(
            @Validated @ParameterObject FileRecordPageDTO req) {
        return Result.success(fileRecordService.page(req));
    }

    @Operation(summary = "下载文件")
    @GetMapping("/{key}/download")
    public ResponseEntity<Resource> download(@PathVariable String key) {
        var input = storageService.download(key);
        var resource = new InputStreamResource(input);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + key + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @Operation(summary = "上传文件")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/upload")
    public Result<FileVO> upload(@RequestParam("file") MultipartFile file) {
        var vo = fileService.upload(file);
        var uploaderId = operatorContext.currentOwnerId().orElse(null);
        fileRecordService.save(
                vo.key(),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                uploaderId);
        return Result.success(vo);
    }

    @Operation(summary = "前端直传完成确认（预签名/STS 分片上传后调用）")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/confirm")
    public Result<Void> confirm(@Validated @RequestBody FileConfirmDTO dto) {
        var uploaderId = operatorContext.currentOwnerId().orElse(null);
        fileRecordService.save(
                dto.key(), dto.originalName(), dto.mimeType(), dto.size(), uploaderId);
        return Result.success();
    }

    @Operation(summary = "删除文件")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping
    public Result<Void> delete(@RequestParam String key) {
        fileService.delete(key);
        return Result.success();
    }

    @Operation(summary = "获取预签名上传 URL")
    @GetMapping("/presigned-url")
    public Result<String> getPresignedUrl(@RequestParam String key) {
        var url = storageService.getPresignedUploadUrl(key, Duration.ofMinutes(30));
        return Result.success(url);
    }

    @Operation(summary = "获取 OSS STS 临时凭证（前端直传分片上传用）")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/sts-token")
    public Result<StsCredentials> getStsToken() {
        if (ossStorageService == null) {
            return Result.error(400, "当前存储类型不支持 STS，请切换为 OSS 存储");
        }
        return Result.success(ossStorageService.getStsCredentials());
    }
}
