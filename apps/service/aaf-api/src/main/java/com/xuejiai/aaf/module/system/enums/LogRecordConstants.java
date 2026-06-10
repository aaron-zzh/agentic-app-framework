package com.xuejiai.aaf.module.system.enums;

/** System 模块操作日志常量。 统一管理 type / subType / success 模板，避免 Service 中散落字符串字面量。 */
public interface LogRecordConstants {

    // ======================= 用户 =======================

    String SYSTEM_USER_TYPE = "用户管理";

    String SYSTEM_USER_CREATE_SUB_TYPE = "新增用户";
    String SYSTEM_USER_CREATE_SUCCESS = "新增了用户【{{#user.username}}】";

    String SYSTEM_USER_UPDATE_SUB_TYPE = "修改用户";

    /** {_DIFF{#updateReqVO}} 自动生成字段级变更描述，需在方法内 putVariable(OLD_OBJECT, oldUser) */
    String SYSTEM_USER_UPDATE_SUCCESS = "修改了用户【{{#user.username}}】: {_DIFF{#updateReqVO}}";

    String SYSTEM_USER_DELETE_SUB_TYPE = "删除用户";
    String SYSTEM_USER_DELETE_SUCCESS = "删除了用户【{{#user.username}}】";

    String SYSTEM_USER_UPDATE_PASSWORD_SUB_TYPE = "重置密码";
    String SYSTEM_USER_UPDATE_PASSWORD_SUCCESS = "重置了用户【{{#user.username}}】的密码";

    String SYSTEM_USER_UPDATE_STATUS_SUB_TYPE = "修改状态";
    String SYSTEM_USER_UPDATE_STATUS_SUCCESS = "将用户【{{#user.username}}】状态修改为【{{#status}}】";

    String SYSTEM_USER_DELETE_BATCH_SUB_TYPE = "批量删除用户";
    String SYSTEM_USER_DELETE_BATCH_SUCCESS = "批量删除了 {{#ids.size()}} 个用户";

    // ======================= 角色 =======================

    String SYSTEM_ROLE_TYPE = "角色管理";

    String SYSTEM_ROLE_CREATE_SUB_TYPE = "新增角色";
    String SYSTEM_ROLE_CREATE_SUCCESS = "新增了角色【{{#request.name()}}】";

    String SYSTEM_ROLE_UPDATE_SUB_TYPE = "修改角色";
    String SYSTEM_ROLE_UPDATE_SUCCESS = "修改了角色【{{#_ret.name()}}】: {_DIFF{#request}}";

    String SYSTEM_ROLE_DELETE_SUB_TYPE = "删除角色";
    String SYSTEM_ROLE_DELETE_SUCCESS = "删除了角色【{{#id}}】";
}
