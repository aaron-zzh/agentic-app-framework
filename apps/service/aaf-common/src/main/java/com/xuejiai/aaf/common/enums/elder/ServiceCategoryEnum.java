package com.xuejiai.aaf.common.enums.elder;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 养老服务分类。 */
@Getter
@AllArgsConstructor
public enum ServiceCategoryEnum implements ArrayValuable<String> {
    HOUSEKEEPING("HOUSEKEEPING", "家政服务"),
    MEAL_ASSISTANCE("MEAL_ASSISTANCE", "助餐服务"),
    PERSONAL_CARE("PERSONAL_CARE", "生活照护"),
    MEDICAL_ESCORT("MEDICAL_ESCORT", "陪诊服务"),
    REHABILITATION("REHABILITATION", "康复服务"),
    OTHER("OTHER", "其他服务");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(ServiceCategoryEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
