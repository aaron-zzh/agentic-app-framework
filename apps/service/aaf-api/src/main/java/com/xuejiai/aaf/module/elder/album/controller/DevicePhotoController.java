package com.xuejiai.aaf.module.elder.album.controller;

import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.elder.album.service.ElderPhotoService;
import com.xuejiai.aaf.module.elder.album.vo.ElderPhotoVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;

/** 设备照片上传接口；设备凭据只允许上传，不能读取相册。 */
@Tag(name = "养老-设备照片")
@RestController
@RequestMapping("/api/device/photos")
public class DevicePhotoController {

    private final ElderPhotoService service;

    public DevicePhotoController(ElderPhotoService service) {
        this.service = service;
    }

    @Operation(summary = "设备上传照片")
    @PreAuthorize("hasRole('DEVICE')")
    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<ElderPhotoVO> upload(
            @AuthenticationPrincipal(expression = "deviceId") Long deviceId,
            @AuthenticationPrincipal(expression = "scenarioKey") String scenarioKey,
            @AuthenticationPrincipal(expression = "subjectId") Long subjectId,
            @AuthenticationPrincipal(expression = "ownerUserId") Long ownerUserId,
            @AuthenticationPrincipal(expression = "orgId") Long orgId,
            @AuthenticationPrincipal(expression = "workspaceId") Long workspaceId,
            @RequestParam @NotBlank String clientPhotoId,
            @RequestParam String source,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime capturedAt,
            @RequestParam("file") MultipartFile file) {
        return Result.success(
                service.uploadFromDevice(
                        deviceId,
                        scenarioKey,
                        subjectId,
                        ownerUserId,
                        orgId,
                        workspaceId,
                        clientPhotoId,
                        source,
                        capturedAt,
                        file));
    }
}
