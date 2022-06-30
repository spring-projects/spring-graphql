package org.springframework.graphql.coercing.webflux;

import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.springframework.http.codec.multipart.FilePart;

public class UploadCoercing implements Coercing<FilePart, Object> {

    @Override
    public Object serialize(Object dataFetcherResult) throws CoercingSerializeException {
        throw new CoercingSerializeException("Upload is an input-only type");
    }

    @Override
    public FilePart parseValue(Object input) throws CoercingParseValueException {
        if (input instanceof FilePart) {
            return (FilePart) input;
        }
        throw new CoercingParseValueException(
                String.format("Expected 'FilePart' like object but was '%s'.",
                        input != null ? input.getClass() : null)
        );
    }

    @Override
    public FilePart parseLiteral(Object input) throws CoercingParseLiteralException {
        throw new CoercingParseLiteralException("Parsing literal of 'MultipartFile' is not supported");
    }
}

