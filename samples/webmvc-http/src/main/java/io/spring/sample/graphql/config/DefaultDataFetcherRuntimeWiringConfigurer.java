package io.spring.sample.graphql.config;

import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.stereotype.Component;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.PropertyDataFetcher;
import graphql.schema.idl.FieldWiringEnvironment;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.WiringFactory;

/**
 * @author dugenkui <dugenkui@kuaishou.com>
 * Created on 2022/1/12
 **/
@Component
public class DefaultDataFetcherRuntimeWiringConfigurer implements RuntimeWiringConfigurer {

    class NotPropertyDataFetcher implements DataFetcher {

        private PropertyDataFetcher propertyDataFetcher;

        public NotPropertyDataFetcher(PropertyDataFetcher propertyDataFetcher) {
            this.propertyDataFetcher = propertyDataFetcher;
        }

        @Override
        public Object get(DataFetchingEnvironment environment) throws Exception {
            return propertyDataFetcher.get(environment);
        }
    }

    @Override
    public void configure(RuntimeWiring.Builder builder) {
        builder.wiringFactory(new WiringFactory() {
            @Override
            public DataFetcher getDefaultDataFetcher(FieldWiringEnvironment environment) {
                /**
                 * return code below with {@code return PropertyDataFetcher.fetching(environment.getFieldDefinition().getName());}
                 *
                 * unit test will success.
                 */
                return new NotPropertyDataFetcher(PropertyDataFetcher.fetching(environment.getFieldDefinition().getName()));
            }
        });
    }
}
