package com.xuejiai.aaf.module.system.controller;

import java.time.Duration;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.storage.FileService;
import com.xuejiai.aaf.framework.storage.FileVO;
import com.xuejiai.aaf.framework.storage.StorageService;

import lombok.RequiredArgsConstructor;

/**
 * 文件管理接口。
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final StorageService storageService;

    /** 上传文件 */
    @PostMapping("/upload")
    public Result<FileVO> upload(@RequestParam("file") MultipartFile file) {
        return Result.success(fileService.upload(file));
    }

    /** 上传图片（自动生成缩略图） */
    @PostMapping("/upload-image")
    public Result<FileVO> uploadImage(@RequestParam("file") MultipartFile file) {
        return Result.success(fileService.uploadImage(file));
    }

    /** 删除文件 */
    @DeleteMapping("/{key}")
    public Result<Void> delete(@PathVariable String key) {
        fileService.delete(key);
        return Result.success();
    }

    /** 获取预签名上传 URL */
    @GetMapping("/presigned-url")
    public Result<String> getPresignedUrl(@RequestParam String key) {
        var url = storageService.getPresignedUploadUrl(key, Duration.ofMinutes(30));
        return Result.success(url);
    }
}
