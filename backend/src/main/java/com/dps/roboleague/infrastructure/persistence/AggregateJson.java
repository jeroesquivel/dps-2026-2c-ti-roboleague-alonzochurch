package com.dps.roboleague.infrastructure.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

/**
 * Convierte un agregado del dominio en un documento JSON y lo reconstruye, sin que el dominio sepa nada de esto:
 * ninguna clase del núcleo lleva anotaciones de persistencia ni de serialización.
 *
 * <p>Los agregados son inmutables y sus invariantes se validan al construirlos. Al leer de la base se reconstruyen
 * por sus campos (Gson no pasa por los constructores de las clases comunes y sí por el canónico de los records) a
 * partir de un estado que el propio dominio generó, así que no hace falta validarlo de nuevo.
 *
 * <p>Los tipos polimórficos (las reglas de puntaje, las políticas de ranking, los criterios de desempate, las
 * variantes "no aplica") se guardan con el nombre de su clase concreta para poder volver a instanciarlos.
 */
public final class AggregateJson {

    private final Gson gson = new GsonBuilder()
            .enableComplexMapKeySerialization()
            .registerTypeAdapter(Instant.class, textAdapter(Instant::parse))
            .registerTypeAdapter(LocalDate.class, textAdapter(LocalDate::parse))
            .registerTypeAdapter(LocalDateTime.class, textAdapter(LocalDateTime::parse))
            .registerTypeAdapter(Duration.class, textAdapter(Duration::parse))
            .registerTypeAdapterFactory(new PolymorphicTypes())
            .create();

    public String write(Object aggregate) {
        return gson.toJson(aggregate);
    }

    public <T> T read(String json, Class<T> type) {
        return gson.fromJson(json, type);
    }

    private static <T> TypeAdapter<T> textAdapter(java.util.function.Function<String, T> parse) {
        return new TypeAdapter<>() {
            @Override
            public void write(JsonWriter out, T value) throws IOException {
                if (value == null) {
                    out.nullValue();
                } else {
                    out.value(value.toString());
                }
            }

            @Override
            public T read(JsonReader in) throws IOException {
                if (in.peek() == JsonToken.NULL) {
                    in.nextNull();
                    return null;
                }
                return parse.apply(in.nextString());
            }
        };
    }

    /** Guarda el nombre de la clase concreta de todo valor cuyo tipo declarado es una interfaz o una clase abstracta. */
    private static final class PolymorphicTypes implements TypeAdapterFactory {

        private static final String TYPE = "@type";
        private static final String VALUE = "@value";

        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> token) {
            Class<? super T> declared = token.getRawType();
            if (!isPolymorphic(declared)) {
                return null;
            }
            TypeAdapter<JsonElement> elements = gson.getAdapter(JsonElement.class);
            return new TypeAdapter<>() {
                @Override
                public void write(JsonWriter out, T value) throws IOException {
                    if (value == null) {
                        out.nullValue();
                        return;
                    }
                    Class<?> concrete = concreteClassOf(value);
                    @SuppressWarnings("unchecked")
                    TypeAdapter<Object> delegate = (TypeAdapter<Object>) gson.getDelegateAdapter(
                            PolymorphicTypes.this, TypeToken.get(concrete));
                    JsonObject wrapper = new JsonObject();
                    wrapper.addProperty(TYPE, concrete.getName());
                    wrapper.add(VALUE, delegate.toJsonTree(value));
                    elements.write(out, wrapper);
                }

                @Override
                @SuppressWarnings("unchecked")
                public T read(JsonReader in) throws IOException {
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        return null;
                    }
                    JsonObject wrapper = elements.read(in).getAsJsonObject();
                    Class<?> concrete = classNamed(wrapper.get(TYPE).getAsString());
                    TypeAdapter<Object> delegate = (TypeAdapter<Object>) gson.getDelegateAdapter(
                            PolymorphicTypes.this, TypeToken.get(concrete));
                    return (T) delegate.fromJsonTree(wrapper.get(VALUE));
                }
            };
        }

        private static boolean isPolymorphic(Class<?> type) {
            if (type.isPrimitive() || type.isArray() || type.isEnum() || type.getName().startsWith("java.")
                    || Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type)) {
                return false;
            }
            return type.isInterface() || Modifier.isAbstract(type.getModifiers());
        }

        private static Class<?> concreteClassOf(Object value) {
            Class<?> type = value.getClass();
            return type.isAnonymousClass() ? type.getSuperclass() : type;
        }

        private static Class<?> classNamed(String name) throws IOException {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException e) {
                throw new IOException("unknown type in stored document: " + name, e);
            }
        }
    }
}
