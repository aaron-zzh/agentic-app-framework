package com.xuejiai.aaf.module.elder.album.vo;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.xuejiai.aaf.common.constant.DictType;
import com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO;
import com.xuejiai.aaf.framework.crud.export.DictFormat;

import io.swagger.v3.oas.annotations.media.Schema;

/** 长者照片响应；缩略图与原图地址均指向受控内容端点。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "长者相册照片")
public record ElderPhotoVO(
        Long id,
        Integer version,
        Long elderId,
        Long deviceId,
        String clientPhotoId,
        Long fileId,
        @DictFormat(DictType.Elder.PHOTO_SOURCE) String source,
        LocalDateTime capturedAt,
        String originalName,
        String mimeType,
        Long fileSize,
        String contentHash,
        String description,
        String thumbnailUrl,
        String originalUrl,
        LocalDateTime createTime,
        LocalDateTime updateTime,
        ResourceRefDTO elder,
        ResourceRefDTO createBy,
        ResourceRefDTO updateBy) {}
