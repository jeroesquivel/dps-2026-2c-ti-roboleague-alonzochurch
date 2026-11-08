package com.dps.roboleague.adapter.in.graphql.scalar;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import graphql.schema.idl.RuntimeWiring;
import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.stereotype.Component;

/**
 * El escalar {@code Points}: un monto decimal exacto que se escribe como número JSON, igual que en la API REST, sin
 * pasar por {@code Float}. El esquema sólo lo usa como salida, así que rechaza recibirlo como argumento.
 */
@Component
public final class PointsScalar implements RuntimeWiringConfigurer {

    static final GraphQLScalarType POINTS = GraphQLScalarType.newScalar()
            .name("Points")
            .description("Puntos con decimales exactos")
            .coercing(new PointsCoercing())
            .build();

    @Override
    public void configure(RuntimeWiring.Builder builder) {
        builder.scalar(POINTS);
    }

    private static final class PointsCoercing implements Coercing<BigDecimal, BigDecimal> {

        @Override
        public BigDecimal serialize(Object value, GraphQLContext context, Locale locale) {
            if (value instanceof BigDecimal amount) {
                return amount;
            }
            throw new CoercingSerializeException("Points expects a decimal amount but got " + value);
        }

        @Override
        public BigDecimal parseValue(Object input, GraphQLContext context, Locale locale) {
            throw new CoercingParseValueException("Points is an output type and cannot be sent as input");
        }

        @Override
        public BigDecimal parseLiteral(Value<?> input, CoercedVariables variables, GraphQLContext context,
                Locale locale) {
            throw new CoercingParseLiteralException("Points is an output type and cannot be sent as input");
        }
    }
}
