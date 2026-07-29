package com.xuejiai.aaf.framework.intelligent.assistant.port;

import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.assistant.model.Role;

/** 角色定义只读端口。 */
public interface RoleDefinitionPort {

    Optional<Role> findByCode(String roleCode);
}
