package org.springframework.graphql;

public record LocationAreaId(LocationId location) {

    public record LocationId(String id) {
    }

}
