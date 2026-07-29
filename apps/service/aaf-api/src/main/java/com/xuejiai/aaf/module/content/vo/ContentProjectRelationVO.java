package com.xuejiai.aaf.module.content.vo;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 项目关系响应。
 *
 * @author AaronZZH & Kiro
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "项目关系信息")
public record ContentProjectRelationVO(
        Long id,
        Long projectId,
        String relationType,
        String layer,
        Long sourceObjectId,
        Long targetObjectId,
        Map<String, Object> relationMeta) {}
