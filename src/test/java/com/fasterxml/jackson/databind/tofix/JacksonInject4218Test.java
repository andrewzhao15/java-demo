package com.fasterxml.jackson.databind.tofix;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.testutil.DatabindTestUtil;
import com.fasterxml.jackson.databind.testutil.failure.JacksonTestFailureExpected;

import static org.junit.jupiter.api.Assertions.assertEquals;

// [databind#4218]
class JacksonInject4218Test extends DatabindTestUtil
{
    static class Dto {
        @JacksonInject("id")
        String id;

        @JsonCreator
        Dto(@JacksonInject("id")
            @JsonProperty("id")
            String id
        ) {
            this.id = id;
        }
    }

    class MyInjectableValues extends InjectableValues.Std
    {
        private static final long serialVersionUID = 1L;

        int nextId = 1; // count up if injected

        @Override
        public Object findInjectableValue(
                DeserializationContext ctxt,
                Object valueId,
                BeanProperty forProperty,
                Object beanInstance,
                Boolean optional
        ) throws JsonMappingException {
            if (valueId.equals("id")) {
                return "id" + nextId++;
            } else {
                return super.findInjectableValue(ctxt, valueId, forProperty, beanInstance, optional);
            }
        }
    }

    // [databind#4218]
    @JacksonTestFailureExpected
    @Test
    void injectFail4218() throws Exception
    {
        ObjectReader reader = newJsonMapper()
                .readerFor(Dto.class)
                .with(new MyInjectableValues());

        Dto dto = reader.readValue("{}");
        String actual = dto.id;

        assertEquals("id1", actual);
    }
}
