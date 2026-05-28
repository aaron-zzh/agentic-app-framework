package com.xuejiai.aaf.common.util.area;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** 区域节点（国家→省→市→区），数据来自 area.csv。 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = "parent")
public class Area {

    /** 全球根节点 ID */
    public static final Integer ID_GLOBAL = 0;

    /** 中国节点 ID */
    public static final Integer ID_CHINA = 1;

    private Integer id;
    private String name;

    /** 类型，见 {@link AreaTypeEnum} */
    private Integer type;

    @JsonManagedReference private Area parent;

    @JsonBackReference private List<Area> children;
}
