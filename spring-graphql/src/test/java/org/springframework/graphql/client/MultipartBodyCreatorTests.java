package org.springframework.graphql.client;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.util.MultiValueMap;

import java.util.*;

public class MultipartBodyCreatorTests {

    @Test
    public void shouldGenerateVariableForOneFile() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("existingVar", "itsValue");
        MultipartClientGraphQlRequest multipartClientGraphQlRequest = new MultipartClientGraphQlRequest(
                "mockDoc",
                "opName",
                variables,
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.singletonMap("fileInput", new ClassPathResource("/foo.txt"))
        );
        MultiValueMap<String, ?> stringMultiValueMap = MultipartBodyCreator.convertRequestToMultipartData(multipartClientGraphQlRequest);

        HttpEntity<?> operations = (HttpEntity<?>) stringMultiValueMap.get("operations").get(0);
        Map<String, Object> operationsBody = (Map<String, Object>) operations.getBody();
        Assertions.assertEquals("mockDoc", operationsBody.get("query"));
        Assertions.assertEquals("opName", operationsBody.get("operationName"));
        Map<String, Object> resultVariables = (Map<String, Object>) operationsBody.get("variables");
        Assertions.assertTrue(resultVariables.containsKey("fileInput"));
        Assertions.assertNull(resultVariables.get("fileInput"));
        Assertions.assertEquals("itsValue", resultVariables.get("existingVar"));

        HttpEntity<?> mappings = (HttpEntity<?>) stringMultiValueMap.get("map").get(0);
        Map<String, Object> mappingsBody = (Map<String, Object>) mappings.getBody();
        Assertions.assertTrue((((List<String>)mappingsBody.get("uploadPart0")).containsAll(Collections.singletonList("variables.fileInput"))));

        HttpEntity<?> filePart = (HttpEntity<?>) stringMultiValueMap.get("uploadPart0").get(0);
        Assertions.assertTrue(filePart.getBody() instanceof ClassPathResource);
    }

    @Test
    public void shouldGenerateVariableForCollectionOfFiles() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("existingVar", "itsValue");
        List<ClassPathResource> resources = new ArrayList<>();
        resources.add(new ClassPathResource("/foo.txt"));
        resources.add(new ClassPathResource("/bar.txt"));

        MultipartClientGraphQlRequest multipartClientGraphQlRequest = new MultipartClientGraphQlRequest(
                "mockDoc",
                "opName",
                variables,
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.singletonMap("fileInput", resources)
        );
        MultiValueMap<String, ?> stringMultiValueMap = MultipartBodyCreator.convertRequestToMultipartData(multipartClientGraphQlRequest);

        HttpEntity<?> operations = (HttpEntity<?>) stringMultiValueMap.get("operations").get(0);
        Map<String, Object> operationsBody = (Map<String, Object>) operations.getBody();
        Assertions.assertEquals("mockDoc", operationsBody.get("query"));
        Assertions.assertEquals("opName", operationsBody.get("operationName"));
        Map<String, Object> resultVariables = (Map<String, Object>) operationsBody.get("variables");
        Assertions.assertTrue(resultVariables.containsKey("fileInput"));
        List<Object> fileInputValues = (List<Object>) resultVariables.get("fileInput");
        Assertions.assertNotNull(fileInputValues);
        Assertions.assertEquals(2, fileInputValues.size());
        Assertions.assertNull(fileInputValues.get(0));
        Assertions.assertNull(fileInputValues.get(1));

        Assertions.assertEquals("itsValue", resultVariables.get("existingVar"));

        HttpEntity<?> mappings = (HttpEntity<?>) stringMultiValueMap.get("map").get(0);
        Map<String, Object> mappingsBody = (Map<String, Object>) mappings.getBody();
        Assertions.assertTrue((((List<String>)mappingsBody.get("uploadPart0")).containsAll(Collections.singletonList("variables.fileInput.0"))));
        Assertions.assertTrue((((List<String>)mappingsBody.get("uploadPart1")).containsAll(Collections.singletonList("variables.fileInput.1"))));

        HttpEntity<?> filePart0 = (HttpEntity<?>) stringMultiValueMap.get("uploadPart0").get(0);
        Assertions.assertTrue(filePart0.getBody() instanceof ClassPathResource);

        HttpEntity<?> filePart1 = (HttpEntity<?>) stringMultiValueMap.get("uploadPart1").get(0);
        Assertions.assertTrue(filePart1.getBody() instanceof ClassPathResource);

    }
}
