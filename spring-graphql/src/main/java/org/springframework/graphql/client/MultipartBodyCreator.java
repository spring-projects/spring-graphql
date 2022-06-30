package org.springframework.graphql.client;

import org.springframework.graphql.GraphQlRequest;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.util.MultiValueMap;

import java.util.*;
import java.util.function.BiConsumer;

public final class MultipartBodyCreator {

    public static MultiValueMap<String, ?> convertRequestToMultipartData(GraphQlRequest request) {
        MultipartClientGraphQlRequest multipartRequest = (MultipartClientGraphQlRequest) request;
        MultipartBodyBuilder builder = new MultipartBodyBuilder();

        Map<String, List<String>> partMappings = new HashMap<>();
        Map<String, Object> operations = multipartRequest.toMap();
        Map<String, Object> variables = new HashMap<>(multipartRequest.getVariables());
        createFilePartsAndMapping(multipartRequest.getFileVariables(), variables, partMappings, builder::part);
        operations.put("variables", variables);
        builder.part("operations", operations);

        builder.part("map", partMappings);
        return builder.build();
    }

    public static void createFilePartsAndMapping(
            Map<String, ?> fileVariables,
            Map<String, Object> variables,
            Map<String, List<String>> partMappings,
            BiConsumer<String, Object> partConsumer) {
        int partNumber = 0;
        for (Map.Entry<String, ?> entry : fileVariables.entrySet()) {
            Object resource = entry.getValue();
            String variableName = entry.getKey();
            if (resource instanceof Collection) {
                List<Object> placeholders = new ArrayList<>();
                int inMappingNumber = 0;
                for (Object fileResourceItem: (Collection)resource) {
                    placeholders.add(null);
                    String partName = "uploadPart" + partNumber;
                    partConsumer.accept(partName, fileResourceItem);
                    partMappings.put(partName, Collections.singletonList(
                            "variables." + variableName + "." + inMappingNumber
                    ));
                    partNumber++;
                    inMappingNumber++;
                }
                variables.put(variableName, placeholders);
            } else {
                String partName = "uploadPart" + partNumber;
                partConsumer.accept(partName, resource);
                variables.put(variableName, null);
                partMappings.put(partName, Collections.singletonList("variables." + variableName));
                partNumber++;
            }
        }
    }

}
