package com.xuejiai.aaf.module.system.file;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.storage.PresignedUploadRequest;
import com.xuejiai.aaf.framework.storage.PresignedUploadTicket;
import com.xuejiai.aaf.module.system.file.api.FileStoragePort;
import com.xuejiai.aaf.module.system.file.api.StoredFile;
import com.xuejiai.aaf.module.system.file.config.FileStorageProperties;
import com.xuejiai.aaf.module.system.file.service.FileRecordService;
import com.xuejiai.aaf.module.system.file.service.FileStorageReferenceService;
import com.xuejiai.aaf.module.system.file.vo.FileConfirmDTO;
import com.xuejiai.aaf.module.system.file.vo.FileRecordPageDTO;
import com.xuejiai.aaf.module.system.file.vo.FileRecordVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 文件管理接口。 */
@Tag(name = "文件管理")
@RestController
@RequestMapping("/api/system/files")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class FileController {

    private final FileStoragePort fileStoragePort;
    private final FileRecordService fileRecordService;
    private final FileStorageReferenceService storageReferenceService;
    private final FileStorageProperties storageProperties;

    @Operation(summary = "分页查询文件列表")
    @GetMapping
    public Result<PageResult<FileRecordVO>> page(
            @Validated @ParameterObject FileRecordPageDTO request) {
        return Result.success(fileRecordService.page(request));
    }

    @Operation(summary = "浏览器读取文件内容")
    @GetMapping("/{fileId}/content")
    public ResponseEntity<Resource> content(@PathVariable Long fileId) {
        return fileResponse(fileStoragePort.requireCurrentOwner(fileId), false);
    }

    @Operation(summary = "下载文件")
    @GetMapping("/{fileId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long fileId) {
        return fileResponse(fileStoragePort.requireCurrentOwner(fileId), true);
    }

    @Operation(summary = "上传文件")
    @PostMapping("/upload")
    public Result<StoredFile> upload(@RequestParam("file") MultipartFile file) {
        return Result.success(fileStoragePort.uploadCurrent(file));
    }

    @Operation(summary = "前端直传完成确认")
    @PostMapping("/confirm")
    public Result<StoredFile> confirm(@Validated @RequestBody FileConfirmDTO request) {
        return Result.success(
                fileRecordService.confirmCurrentUpload(
                        request.key(), request.originalName(), request.mimeType(), request.size()));
    }

    @Operation(summary = "删除文件")
    @DeleteMapping("/{fileId}")
    public Result<Void> delete(@PathVariable Long fileId) {
        fileStoragePort.requireCurrentOwner(fileId);
        fileStoragePort.requestDelete(fileId);
        return Result.success();
    }

    @Operation(summary = "获取预签名上传 URL")
    @GetMapping("/presigned-url")
    public Result<PresignedUploadTicket> getPresignedUrl(
            @RequestParam String filename, @RequestParam String contentType) {
        var target = storageReferenceService.resolveCurrentMaster();
        var limits = storageProperties.uploadLimits();
        var ticket =
                target.client()
                        .getPresignedUploadUrl(
                                new PresignedUploadRequest(
                                        fileRecordService.currentOwnerStorageNamespace(
                                                target.storageConfigId()),
                                        filename,
                                        contentType,
                                        limits.maxSizeBytes(),
                                        Duration.ofMinutes(30)));
        return Result.success(
                new PresignedUploadTicket(
                        ticket.key(),
                        ticket.url(),
                        ticket.contentType(),
                        ticket.maxSizeBytes(),
                        target.storageConfigId()));
    }

    private ResponseEntity<Resource> fileResponse(StoredFile file, boolean attachment) {
        var input = fileStoragePort.openByKey(file.key());
        var filename =
                file.originalName() == null || file.originalName().isBlank()
                        ? file.key()
                        : file.originalName();
        var disposition =
                ContentDisposition.builder(attachment ? "attachment" : "inline")
                        .filename(filename, StandardCharsets.UTF_8)
                        .build();
        var contentType =
                file.mimeType() == null
                        ? MediaType.APPLICATION_OCTET_STREAM
                        : MediaType.parseMediaType(file.mimeType());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(contentType)
                .contentLength(file.size())
                .body(new InputStreamResource(input));
    }
}
