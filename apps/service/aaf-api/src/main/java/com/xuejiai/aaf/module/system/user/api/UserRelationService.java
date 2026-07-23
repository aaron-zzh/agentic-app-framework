package com.xuejiai.aaf.module.system.user.api;

import java.util.Collection;
import java.util.Map;

import com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO;

/**
 * 用户关联引用查询接口。
 *
 * @author AaronZZH & Kiro
 */
public interface UserRelationService {

    /** 批量解析用户的轻量展示引用。 */
    Map<Long, ResourceRefDTO> findRefs(Collection<Long> userIds);
}
