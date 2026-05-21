package com.xuejiai.aaf.module.examples.neo4j.domain;

import java.util.List;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

/**
 * 电影节点（Neo4j）。
 *
 * <p>示例数据模型：(Person)-[:ACTED_IN]->(Movie)
 */
@Node("Movie")
public class Movie {

    @Id
    private final String title;

    private final String tagline;

    private Integer released;

    @Relationship(type = "ACTED_IN", direction = Relationship.Direction.INCOMING)
    private List<Person> actors;

    public Movie(String title, String tagline) {
        this.title = title;
        this.tagline = tagline;
    }

    public String getTitle() { return title; }
    public String getTagline() { return tagline; }
    public Integer getReleased() { return released; }
    public List<Person> getActors() { return actors; }
    public void setReleased(Integer released) { this.released = released; }
}
