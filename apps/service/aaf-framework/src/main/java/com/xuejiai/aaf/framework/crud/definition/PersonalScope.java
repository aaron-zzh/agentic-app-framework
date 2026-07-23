package com.xuejiai.aaf.framework.crud.definition;

/** 资源个人视角；NONE 表示不追加个人字段条件。 */
public record PersonalScope(String property) {

    private static final PersonalScope NONE = new PersonalScope(null);

    public PersonalScope {
        property = property == null || property.isBlank() ? null : property.trim();
    }

    public static PersonalScope none() {
        return NONE;
    }

    public static PersonalScope byProperty(String property) {
        if (property == null || property.isBlank()) {
            throw new IllegalArgumentException("个人视角属性不能为空");
        }
        return new PersonalScope(property);
    }

    public boolean enabled() {
        return property != null;
    }
}
