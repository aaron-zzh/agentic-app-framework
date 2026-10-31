package com.xuejiai.aaf.module.system.user.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO;

/**
 * 用户关联引用查询接口。
 *
 * @author AaronZZH & Kiro
 */
public interface UserRelationService {

    /** 解析指定用户名的启用用户 ID，供跨模块的可信服务调用。 */
    Optional<Long> findActiveUserIdByUsername(String username);

    /** 批量解析用户的轻量展示引用。 */
    Map<Long, ResourceRefDTO> findRefs(Collection<Long> userIds);
}
