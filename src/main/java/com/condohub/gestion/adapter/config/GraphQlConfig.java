package com.condohub.gestion.adapter.config;

import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import java.math.BigDecimal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

@Configuration
class GraphQlConfig {

    /** Scalar {@code Decimal}: BigDecimal en Java, string decimal en JSON (F10). */
    static final GraphQLScalarType DECIMAL = GraphQLScalarType.newScalar()
            .name("Decimal")
            .description("Dinero como string decimal con 2 decimales")
            .coercing(new Coercing<BigDecimal, String>() {
                @Override
                public String serialize(Object resultado) {
                    if (resultado instanceof BigDecimal valor) {
                        return valor.toPlainString();
                    }
                    throw new CoercingSerializeException("Decimal espera BigDecimal, recibió " + resultado);
                }

                @Override
                public BigDecimal parseValue(Object entrada) {
                    if (entrada instanceof String texto) {
                        try {
                            return new BigDecimal(texto);
                        } catch (NumberFormatException e) {
                            throw new CoercingParseValueException("Decimal inválido: " + texto);
                        }
                    }
                    throw new CoercingParseValueException("Decimal debe enviarse como string");
                }

                @Override
                public BigDecimal parseLiteral(Object entrada) {
                    if (entrada instanceof StringValue texto) {
                        try {
                            return new BigDecimal(texto.getValue());
                        } catch (NumberFormatException e) {
                            throw new CoercingParseLiteralException("Decimal inválido: " + texto.getValue());
                        }
                    }
                    throw new CoercingParseLiteralException("Decimal debe enviarse como string");
                }
            })
            .build();

    @Bean
    RuntimeWiringConfigurer escalares() {
        return builder -> builder.scalar(DECIMAL);
    }
}