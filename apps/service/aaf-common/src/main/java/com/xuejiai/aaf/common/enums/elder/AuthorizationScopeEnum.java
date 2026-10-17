package com.xuejiai.aaf.common.enums.elder;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 家属访问长者数据的授权范围。 */
@Getter
@AllArgsConstructor
public enum AuthorizationScopeEnum implements ArrayValuable<String> {
    VIEW_PROFILE("VIEW_PROFILE", "查看档案"),
    VIEW_HEALTH("VIEW_HEALTH", "查看健康信息"),
    VIEW_MEDICATION("VIEW_MEDICATION", "查看用药信息"),
    VIEW_ALERT("VIEW_ALERT", "查看预警"),
    VIEW_SERVICE("VIEW_SERVICE", "查看服务"),
    BOOK_SERVICE("BOOK_SERVICE", "代为预约"),
    VIEW_ALBUM("VIEW_ALBUM", "查看相册");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(AuthorizationScopeEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
