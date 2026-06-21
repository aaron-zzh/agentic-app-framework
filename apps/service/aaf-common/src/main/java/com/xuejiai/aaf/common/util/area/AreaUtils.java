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
            var globalRoot = new Area();
            globalRoot.setId(Area.ID_GLOBAL);
            globalRoot.setName("全球");
            globalRoot.setType(0);
            globalRoot.setChildren(new ArrayList<>());
            AREAS.put(Area.ID_GLOBAL, globalRoot);

            var is = AreaUtils.class.getClassLoader().getResourceAsStream("area.csv");
            if (is == null) throw new IllegalStateException("area.csv 未找到");

            List<String[]> rows = new ArrayList<>();
            try (var reader =
                    new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                boolean header = true;
                while ((line = reader.readLine()) != null) {
                    if (header) {
                        header = false;
                        continue;
                    } // 跳过 header
                    rows.add(line.split(",", -1));
                }
            }

            // 第一遍：建节点。csv 列 0-3=id/name/type/parentId；4-5=longitude/latitude（兼容旧 4 列格式）
            for (var row : rows) {
                var area = new Area();
                area.setId(Integer.parseInt(row[0].trim()));
                area.setName(row[1].trim());
                area.setType(Integer.parseInt(row[2].trim()));
                area.setChildren(new ArrayList<>());
                if (row.length >= 6) {
                    area.setLongitude(parseCoordinate(row[4]));
                    area.setLatitude(parseCoordinate(row[5]));
                }
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
            log.info(
                    "AreaUtils 初始化完成，共 {} 条，耗时 {} ms",
                    AREAS.size(),
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            throw new RuntimeException("AreaUtils 初始化失败", e);
        }
    }

    /** 解析经纬度字段，空字符串/无效返回 null（不抛异常以兼容部分行政区无坐标） */
    private static Double parseCoordinate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 按 ID 获取区域。 */
    public static Area getArea(Integer id) {
        return AREAS.get(id);
    }

    /**
     * 按 ip2region 返回的省/市名字反查 Area。
     *
     * <p>策略：
     *
     * <ol>
     *   <li>先按 province 找到省级 Area（type=2），再在其 children 里按 cityName 精确匹配
     *   <li>失败则全局按 cityName 匹配 type=3 城市（处理省名不一致情况，如 ip2region 返回"内蒙古"而 area.csv 是"内蒙古自治区"）
     *   <li>仍失败则按 province 名匹配返回省级 Area（精度降级到省）
     * </ol>
     *
     * @param provinceName 省级名字，如"山东省"。可空
     * @param cityName 城市名，如"济南市"。可空
     * @return 匹配的 Area，未找到返回 null
     */
    public static Area findCity(String provinceName, String cityName) {
        boolean hasCity = cityName != null && !cityName.isBlank();
        boolean hasProvince = provinceName != null && !provinceName.isBlank();

        // 1) 省级精确匹配
        if (hasProvince && hasCity) {
            var province =
                    AREAS.values().stream()
                            .filter(a -> AreaTypeEnum.PROVINCE.getType().equals(a.getType()))
                            .filter(a -> Objects.equals(a.getName(), provinceName))
                            .findFirst()
                            .orElse(null);
            if (province != null && province.getChildren() != null) {
                var city =
                        province.getChildren().stream()
                                .filter(c -> Objects.equals(c.getName(), cityName))
                                .findFirst()
                                .orElse(null);
                if (city != null) return city;
            }
        }
        // 2) 全局城市名匹配
        if (hasCity) {
            var city =
                    AREAS.values().stream()
                            .filter(a -> AreaTypeEnum.CITY.getType().equals(a.getType()))
                            .filter(a -> Objects.equals(a.getName(), cityName))
                            .findFirst()
                            .orElse(null);
            if (city != null) return city;
        }
        // 3) 省级降级
        if (hasProvince) {
            return AREAS.values().stream()
                    .filter(a -> AreaTypeEnum.PROVINCE.getType().equals(a.getType()))
                    .filter(a -> Objects.equals(a.getName(), provinceName))
                    .findFirst()
                    .orElse(null);
        }
        return null;
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
