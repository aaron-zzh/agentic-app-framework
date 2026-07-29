package com.xuejiai.aaf.module.content.vo;

import java.util.List;

/**
 * 内容项目图谱响应。
 *
 * @author AaronZZH & Kiro
 */
public record ContentProjectGraphVO(
        Long projectId,
        Integer graphRevision,
        List<ContentProjectObjectVO> objects,
        List<ContentProjectRelationVO> relations) {}
