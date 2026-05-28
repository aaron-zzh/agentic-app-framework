package com.xuejiai.aaf.module.system.entity.vo;

import java.util.Map;

import com.xuejiai.aaf.module.system.auth.vo.FieldAccessVO;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 实体级权限访问结果。
 *
 * @author AaronZZH & Kiro
 */
public record EntityAccessVO(
        @Schema(description = "是否已读") boolean read,
        boolean create,
        boolean update,
        boolean delete,
        Map<String, FieldAccessVO> fieldAccess) {}
