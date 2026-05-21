package com.xuejiai.aaf.common.util.area;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * 行政区划工具类。
 *
 * <p>数据来自 classpath:area.csv（格式：id,name,type,parentId），启动时一次性加载到内存。
 */
@Slf4j
@UtilityClass
public class AreaUtils {

    private static final Map<Integer, Area> AREAS = new HashMap<>();

    static {
        init();
    }

    private static void init() {
        try {
            long start = System.currentTimeMillis();
            AREAS.put(Area.ID_GLOBAL, new Area(Area.ID_GLOBAL, "全球", 0, null, new ArrayList<>()));

            var is = AreaUtils.class.getClassLoader().getResourceAsStream("area.csv");
            if (is == null) throw new IllegalStateException("area.csv 未找到");

            List<String[]> rows = new ArrayList<>();
            try (var reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                boolean header = true;
                while ((line = reader.readLine()) != null) {
                    if (header) { header = false; continue; } // 跳过 header
                    rows.add(line.split(",", -1));
                }
            }

            // 第一遍：建节点
            for (var row : rows) {
                var area = new Area(
                        Integer.parseInt(row[0].trim()),
                        row[1].trim(),
                        Integer.parseInt(row[2].trim()),
                        null,
                        new ArrayList<>());
                AREAS.put(area.getId(), area);
            }
            // 第二遍：建父子关系
            for (var row : rows) {
                var area = AREAS.get(Integer.parseInt(row[0].trim()));
                var parent = AREAS.get(Integer.parseInt(row[3].trim()));
                if (parent != null && !Objects.equals(area, parent)) {
                    area.setParent(parent);
                    parent.getChildren().add(area);
                }
            }
            log.info("AreaUtils 初始化完成，共 {} 条，耗时 {} ms", AREAS.size(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            throw new RuntimeException("AreaUtils 初始化失败", e);
        }
    }

    /** 按 ID 获取区域。 */
    public static Area getArea(Integer id) {
        return AREAS.get(id);
    }

    /**
     * 格式化区域为可读字符串，如：广东 深圳市 南山区。
     *
     * <p>中国节点默认不显示。
     */
    public static String format(Integer id) {
        return format(id, " ");
    }

    public static String format(Integer id, String separator) {
        var area = AREAS.get(id);
        if (area == null) return null;
        var sb = new StringBuilder();
        for (int i = 0; i < AreaTypeEnum.values().length; i++) {
            sb.insert(0, area.getName());
            area = area.getParent();
            if (area == null
                    || Objects.equals(area.getId(), Area.ID_GLOBAL)
                    || Objects.equals(area.getId(), Area.ID_CHINA)) {
                break;
            }
            sb.insert(0, separator);
        }
        return sb.toString();
    }

    /** 获取指定类型的区域列表。 */
    public static <T> List<T> getByType(AreaTypeEnum type, Function<Area, T> func) {
        return AREAS.values().stream()
                .filter(a -> type.getType().equals(a.getType()))
                .map(func)
                .toList();
    }

    /** 向上查找指定类型的祖先区域 ID。 */
    public static Integer getParentIdByType(Integer id, @NonNull AreaTypeEnum type) {
        for (int i = 0; i < Byte.MAX_VALUE; i++) {
            var area = AREAS.get(id);
            if (area == null) return null;
            if (type.getType().equals(area.getType())) return area.getId();
            if (area.getParent() == null) return null;
            id = area.getParent().getId();
        }
        return null;
    }
}
