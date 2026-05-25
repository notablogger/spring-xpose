package io.github.notablogger.springxpose.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Id;

import java.io.IOException;
import java.lang.reflect.Field;

/**
 * Runtime Jackson serializer that handles relation fields based on the
 * SerializationContext mode set by generated controllers.
 */
public class RelationAwareSerializer extends JsonSerializer<Object> {

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }

        SerializationContext.Mode mode = SerializationContext.get();
        Class<?> type = value.getClass();

        boolean isRelation = isRelationObject(type);

        if (isRelation) {
            if (mode == SerializationContext.Mode.LIST) {
                // In list mode: write just the ID scalar
                writeId(value, type, gen);
            } else {
                // In single mode: delegate to default serializer (full object)
                provider.defaultSerializeValue(value, gen);
            }
        } else {
            provider.defaultSerializeValue(value, gen);
        }
    }

    private boolean isRelationObject(Class<?> type) {
        for (Field f : type.getDeclaredFields()) {
            if (f.isAnnotationPresent(Id.class)) return true;
        }
        return false;
    }

    private void writeId(Object value, Class<?> type, JsonGenerator gen) throws IOException {
        try {
            for (Field f : type.getDeclaredFields()) {
                if (f.isAnnotationPresent(Id.class)) {
                    f.setAccessible(true);
                    Object id = f.get(value);
                    gen.writeObject(id);
                    return;
                }
            }
            gen.writeNull();
        } catch (IllegalAccessException e) {
            gen.writeNull();
        }
    }
}

