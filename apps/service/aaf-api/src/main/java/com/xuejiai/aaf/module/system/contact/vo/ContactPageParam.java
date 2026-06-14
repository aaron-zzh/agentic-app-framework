package com.xuejiai.aaf.module.system.contact.vo;

import com.xuejiai.aaf.common.enums.sys.ContactStatusEnum;
import com.xuejiai.aaf.common.enums.sys.ContactTypeEnum;
import com.xuejiai.aaf.common.model.PageParam;

import lombok.Getter;
import lombok.Setter;

/** 联系人分页查询参数。 */
@Getter
@Setter
public class ContactPageParam extends PageParam {

    private ContactTypeEnum type;

    private ContactStatusEnum status;

    private String keyword;

    private Long parentId;
}
