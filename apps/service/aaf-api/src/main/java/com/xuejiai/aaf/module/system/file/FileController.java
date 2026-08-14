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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.storage.PresignedUploadRequest;
import com.xuejiai.aaf.framework.storage.PresignedUploadTicket;
import com.xuejiai.aaf.framework.storage.StorageProperties;
import com.xuejiai.aaf.module.system.file.api.FileStoragePort;
import com.xuejiai.aaf.module.system.file.api.StoredFile;
import com.xuejiai.aaf.module.system.file.service.FileRecordService;
import com.xuejiai.aaf.module.system.file.service.FileStorageReferenceService;
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
@PreAuthorize("isAuthenticated()")
public class FileController {

    private final FileStoragePort fileUploadService;
    private final FileRecordService fileRecordService;
    private final FileStorageReferenceService storageReferenceService;
    private final StorageProperties storageProperties;

    /** m20：预签名上传的大小上限与普通上传共用同一份配置，避免两条链路策略不一致 */
    private StorageProperties.UploadLimits uploadLimits() {
        return storageProperties.uploadOrDefault();
    }

    @Operation(summary = "分页查询文件列表")
    @GetMapping
    public Result<PageResult<FileRecordVO>> page(
            @Validated @ParameterObject FileRecordPageDTO req) {
        return Result.success(fileRecordService.page(req));
    }

    @Operation(summary = "下载文件")
    @GetMapping("/{key}/download")
    public ResponseEntity<Resource> download(@PathVariable String key) {
        var record = fileRecordService.requireOwnedByKey(key);
        var filename =
                record.getOriginalName() == null || record.getOriginalName().isBlank()
                        ? key
                        : record.getOriginalName();
        var contentDisposition =
                ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build();
        var input = fileRecordService.downloadOwnedByKey(key);
        var resource = new InputStreamResource(input);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @Operation(summary = "上传文件")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/upload")
    public Result<StoredFile> upload(@RequestParam("file") MultipartFile file) {
        return Result.success(fileUploadService.uploadCurrent(file));
    }

    @Operation(summary = "前端直传完成确认（预签名/STS 分片上传后调用）")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/confirm")
    public Result<StoredFile> confirm(@Validated @RequestBody FileConfirmDTO dto) {
        return Result.success(
                fileRecordService.confirmCurrentUpload(
                        dto.key(), dto.originalName(), dto.mimeType(), dto.size()));
    }

    @Operation(summary = "删除文件")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping
    public Result<Void> delete(@RequestParam String key) {
        var record = fileRecordService.requireOwnedByKey(key);
        fileRecordService.requestDelete(record.getId());
        return Result.success();
    }

    /**
     * 获取预签名上传 URL（前端直传）。
     *
     * <p>M29/m20：不再由客户端提交 key——只提交文件名与类型，key 由存储层按当前用户命名空间生成； 签名同时绑定 contentType
     * 与大小上限，拿到签名也无法上传任意类型/超大对象。
     */
    @Operation(summary = "获取预签名上传 URL")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/presigned-url")
    public Result<PresignedUploadTicket> getPresignedUrl(
            @RequestParam String filename, @RequestParam String contentType) {
        var target = storageReferenceService.resolveCurrentMaster();
        var ticket =
                target.storageService()
                        .getPresignedUploadUrl(
                                new PresignedUploadRequest(
                                        fileRecordService.currentOwnerStorageNamespace(
                                                target.storageConfigId()),
                                        filename,
                                        contentType,
                                        uploadLimits().maxSizeBytes(),
                                        Duration.ofMinutes(30)));
        return Result.success(
                new PresignedUploadTicket(
                        ticket.key(),
                        ticket.url(),
                        ticket.contentType(),
                        ticket.maxSizeBytes(),
                        target.storageConfigId()));
    }
}
