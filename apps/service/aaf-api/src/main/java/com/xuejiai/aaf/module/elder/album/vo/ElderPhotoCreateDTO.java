package com.xuejiai.aaf.module.elder.album.vo;

import java.time.LocalDateTime;

import com.xuejiai.aaf.common.enums.elder.PhotoSourceEnum;
import com.xuejiai.aaf.common.validation.InEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 长者照片创建数据。标准管理端创建能力关闭，仅供设备上传流程内部转换。 */
@Schema(description = "创建长者照片")
public record ElderPhotoCreateDTO(
        @NotNull @Positive Long elderId,
        @NotNull @Positive Long deviceId,
        @NotBlank @Size(max = 100) String clientPhotoId,
        @NotNull @Positive Long fileId,
        @NotBlank @InEnum(value = PhotoSourceEnum.class, message = "照片来源必须是 {value}") String source,
        @NotNull LocalDateTime capturedAt,
        @NotBlank @Size(max = 500) String originalName,
        @NotBlank @Size(max = 100) String mimeType,
        @NotNull @Positive Long fileSize,
        @NotBlank @Size(min = 64, max = 64) String contentHash,
        @Size(max = 500) String description) {}
