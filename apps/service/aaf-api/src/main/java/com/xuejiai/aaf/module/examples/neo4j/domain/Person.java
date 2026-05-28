package com.xuejiai.aaf.module.examples.neo4j.domain;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import io.swagger.v3.oas.annotations.media.Schema;

/** 人物节点（Neo4j）。name 作为节点主键。 */
@Schema(description = "人物节点")
@Node("Person")
public class Person {

    /** 姓名，同时作为 Neo4j 节点主键。 */
    @Schema(description = "姓名（主键）", example = "Keanu Reeves")
    @Id
    private final String name;

    /** 出生年份。 */
    @Schema(description = "出生年份", example = "1964")
    private Integer born;

    public Person(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Integer getBorn() {
        return born;
    }

    public void setBorn(Integer born) {
        this.born = born;
    }
}
