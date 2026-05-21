package com.xuejiai.aaf.module.examples.neo4j.domain;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

/** 人物节点（Neo4j）。 */
@Node("Person")
public class Person {

    @Id
    private final String name;

    private Integer born;

    public Person(String name) {
        this.name = name;
    }

    public String getName() { return name; }
    public Integer getBorn() { return born; }
    public void setBorn(Integer born) { this.born = born; }
}
