package com.xuejiai.aaf.common.enums.elder;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 长者联系人关系。 */
@Getter
@AllArgsConstructor
public enum ContactRelationshipEnum implements ArrayValuable<String> {
    SPOUSE("SPOUSE", "配偶"),
    CHILD("CHILD", "子女"),
    SIBLING("SIBLING", "兄弟姐妹"),
    GUARDIAN("GUARDIAN", "监护人"),
    RELATIVE("RELATIVE", "其他亲属"),
    FRIEND("FRIEND", "朋友"),
    OTHER("OTHER", "其他");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(ContactRelationshipEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
