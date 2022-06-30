package org.springframework.graphql.server.webmvc;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.GenericTypeResolver;

import javax.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

public class JacksonPartReader implements PartReader {

    private final ObjectMapper objectMapper;

    public JacksonPartReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public <T> T readPart(Part part, Type targetType) {
        try(InputStream inputStream = part.getInputStream()) {
            try {
                JavaType javaType = getJavaType(targetType);
                return objectMapper.readValue(inputStream, javaType);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private JavaType getJavaType(Type type) {
        return this.objectMapper.constructType(GenericTypeResolver.resolveType(type, (Class<?>)null));
    }
}
