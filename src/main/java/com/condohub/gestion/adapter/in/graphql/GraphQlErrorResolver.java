package com.condohub.gestion.adapter.in.graphql;

import com.condohub.gestion.domain.exception.RecursoNoEncontradoException;
import com.condohub.gestion.domain.exception.ReglaNegocioVioladaException;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import java.util.Map;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** Errores de GraphQL con {@code extensions.code} = código de la regla violada (ej. F1). */
@Component
class GraphQlErrorResolver extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        if (ex instanceof ReglaNegocioVioladaException regla) {
            return error(env, ErrorType.FORBIDDEN, regla.getMessage(), regla.codigo());
        }
        if (ex instanceof AccessDeniedException) {
            return error(env, ErrorType.FORBIDDEN, "Tu rol no permite esta operación", "FORBIDDEN");
        }
        if (ex instanceof RecursoNoEncontradoException noEncontrado) {
            return error(env, ErrorType.NOT_FOUND, noEncontrado.getMessage(), "NOT_FOUND");
        }
        return null;
    }

    private static GraphQLError error(DataFetchingEnvironment env, ErrorType tipo, String mensaje, String codigo) {
        return GraphqlErrorBuilder.newError(env)
                .errorType(tipo)
                .message(mensaje)
                .extensions(Map.of("code", codigo))
                .build();
    }
}