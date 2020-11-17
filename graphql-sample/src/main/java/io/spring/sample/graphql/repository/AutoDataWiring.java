package io.spring.sample.graphql.repository;

import graphql.schema.idl.RuntimeWiring;
import org.springframework.boot.graphql.GraphQLProperties;
import org.springframework.boot.graphql.RepositoryProperties;
import org.springframework.boot.graphql.RuntimeWiringCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * todo: should move to spring-graph-web module if this approache agreed
 */
@Component
public class AutoDataWiring implements RuntimeWiringCustomizer {
    private Map<String, RepositoryProperties> springDataRepoGraphqlMap;
    private ApplicationContext applicationContext;

    public AutoDataWiring(GraphQLProperties properties, ApplicationContext applicationContext) {
        this.springDataRepoGraphqlMap = properties.getSpringData();
        this.applicationContext = applicationContext;
    }

    @Override
    public void customize(RuntimeWiring.Builder builder) {


        builder.type("QueryType", typeWiring -> {


            springDataRepoGraphqlMap.entrySet().stream().forEach(entry -> {
                String query = entry.getKey();
                RepositoryProperties repoProperty = entry.getValue();
                CrudRepository repoBean = (CrudRepository) applicationContext.getBean(repoProperty.getBeanName());

                typeWiring.dataFetcher(query, env -> {
                            Class[] clazzes = repoProperty.getArguments().values().stream().map(name -> {
                                try {
                                    return Class.forName(name);
                                } catch (ClassNotFoundException e) {
                                    e.printStackTrace();
                                    return Object.class;
                                }
                            }).toArray(Class[]::new);
                            Method method = repoBean.getClass().getMethod(repoProperty.getMethod(), clazzes);
                            Object[] argValue = repoProperty.getArguments().keySet().stream().map(arg -> env.getArgument(arg)).toArray();
                            return method.invoke(repoBean, argValue);
                        }
                );
            });

            return typeWiring;
        });


    }
}
