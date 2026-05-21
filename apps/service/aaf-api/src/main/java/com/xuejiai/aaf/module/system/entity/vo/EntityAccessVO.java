package com.xuejiai.aaf.module.system.entity.vo;

import java.util.Map;

import com.xuejiai.aaf.module.system.auth.vo.FieldAccessVO;

/** 实体级权限访问结果。 */
public record EntityAccessVO(
        boolean read,
        boolean create,
        boolean update,
        boolean delete,
        Map<String, FieldAccessVO> fieldAccess) {}
