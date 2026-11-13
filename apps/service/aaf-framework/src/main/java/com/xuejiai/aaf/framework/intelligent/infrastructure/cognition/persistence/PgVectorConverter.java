package com.xuejiai.aaf.framework.intelligent.infrastructure.cognition.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * {@code float[]} 与 PgVector 文本表示 {@code [0.1,0.2,...]} 之间的转换。
 *
 * <p>未引入 hibernate-vector 时，Hibernate 把 {@code float[]} 按 SQL ARRAY 读取，PostgreSQL 驱动无法把 {@code
 * vector} 列解析为数组（读取即报"查询没有传回任何结果"）。按文本读写可绕开数组解析；写入端需配合 {@code cast(? as vector)}。
 */
@Converter
public class PgVectorConverter implements AttributeConverter<float[], String> {

    @Override
    public String convertToDatabaseColumn(float[] attribute) {
        if (attribute == null) {
            return null;
        }
        var builder = new StringBuilder("[");
        for (var index = 0; index < attribute.length; index++) {
            if (index > 0) builder.append(',');
            builder.append(attribute[index]);
        }
        return builder.append(']').toString();
    }

    @Override
    public float[] convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        var body = dbData.trim();
        if (!body.startsWith("[") || !body.endsWith("]")) {
            throw new IllegalArgumentException("非法的 vector 文本: " + body);
        }
        body = body.substring(1, body.length() - 1).trim();
        if (body.isEmpty()) {
            return new float[0];
        }
        var parts = body.split(",");
        var values = new float[parts.length];
        for (var index = 0; index < parts.length; index++) {
            values[index] = Float.parseFloat(parts[index].trim());
        }
        return values;
    }
}
