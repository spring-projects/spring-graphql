package org.springframework.graphql;

public record LibraryId(String id, LocationId location) {

    public record LocationId(String id) {
    }

}
