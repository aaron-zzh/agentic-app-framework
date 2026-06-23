package com.xuejiai.aaf.module.system.user.favorite.vo;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class UserFavoriteVO {
    private Long id;
    private String targetType;
    private Long targetId;
    private String note;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private String targetTitle;
    private String targetCoverUrl;
}
