package com.xuejiai.aaf.module.system.contact.vo;

import com.xuejiai.aaf.common.model.PageParam;

import lombok.Getter;
import lombok.Setter;

/** 渠道身份分页查询参数。 */
@Getter
@Setter
public class ContactIdentityPageParam extends PageParam {

    /** 联系人 ID */
    private Long contactId;

    /** 渠道标识，如 WECOM / DINGTALK */
    private String channel;
}
