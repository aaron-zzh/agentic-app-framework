package com.xuejiai.aaf.module.system.entity.vo;

import java.util.Map;
import java.util.Set;

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
        Set<String> capabilities,
        Map<String, FieldAccessVO> fieldAccess) {}
