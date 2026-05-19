package com.xuejiai.aaf.module.system.vo;

import java.util.Map;

/** 实体级权限访问结果。 */
public record EntityAccessVO(
        boolean read,
        boolean create,
        boolean update,
        boolean delete,
        Map<String, FieldAccessVO> fieldAccess) {}
