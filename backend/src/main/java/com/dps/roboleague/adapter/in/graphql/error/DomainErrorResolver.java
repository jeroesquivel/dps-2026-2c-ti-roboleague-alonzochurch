package com.dps.roboleague.adapter.in.graphql.error;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import java.util.Map;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.stereotype.Component;

/**
 * Traduce las excepciones del dominio a errores GraphQL, como {@code ApiExceptionHandler} lo hace a respuestas HTTP:
 * mensaje del dominio, {@code path} del campo que falló, clasificación y {@code extensions.code}. Cualquier otra
 * excepción la deja pasar: Spring for GraphQL la informa como {@code INTERNAL_ERROR} sin detalles internos.
 */
@Component
public final class DomainErrorResolver extends DataFetcherExceptionResolverAdapter {

    static final String CODE = "code";

    @Override
    protected GraphQLError resolveToSingleError(Throwable exception, DataFetchingEnvironment environment) {
        return DomainError.of(exception)
                .map(error -> GraphqlErrorBuilder.newError(environment)
                        .errorType(error.classification())
                        .message(exception.getMessage())
                        .extensions(Map.of(CODE, error.code()))
                        .build())
                .orElse(null);
    }
}
