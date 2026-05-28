package com.xuejiai.aaf.module.examples.neo4j.domain;

import java.util.List;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 电影节点（Neo4j）。
 *
 * <p>数据模型：{@code (Person)-[:ACTED_IN]->(Movie)}
 *
 * <p>title 作为 Neo4j 节点的主键（@Id），在图数据库中唯一标识一部电影。
 */
@Schema(description = "电影节点")
@Node("Movie")
public class Movie {

    /** 电影标题，同时作为 Neo4j 节点主键。 */
    @Schema(description = "电影标题（主键）", example = "The Matrix")
    @Id
    private final String title;

    /** 电影宣传语。 */
    @Schema(description = "电影宣传语", example = "Welcome to the Real World")
    private final String tagline;

    /** 上映年份。 */
    @Schema(description = "上映年份", example = "1999")
    private Integer released;

    /**
     * 参演演员列表。
     *
     * <p>通过 {@code ACTED_IN} 关系从 Person 节点加载，方向为 INCOMING（Person → Movie）。
     */
    @Schema(description = "参演演员列表")
    @Relationship(type = "ACTED_IN", direction = Relationship.Direction.INCOMING)
    private List<Person> actors;

    public Movie(String title, String tagline) {
        this.title = title;
        this.tagline = tagline;
    }

    public String getTitle() {
        return title;
    }

    public String getTagline() {
        return tagline;
    }

    public Integer getReleased() {
        return released;
    }

    public List<Person> getActors() {
        return actors;
    }

    public void setReleased(Integer released) {
        this.released = released;
    }
}
