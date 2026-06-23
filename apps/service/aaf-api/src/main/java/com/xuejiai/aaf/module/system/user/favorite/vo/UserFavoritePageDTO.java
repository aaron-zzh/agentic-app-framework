package com.xuejiai.aaf.module.system.user.favorite.vo;

import com.xuejiai.aaf.common.model.PageParam;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserFavoritePageDTO extends PageParam {
    private String targetType;
}
