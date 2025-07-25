package com.fasterxml.jackson.databind.ser.jdk;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.testutil.DatabindTestUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for verifying serialization of simple basic non-structured
 * types; primitives (and/or their wrappers), Strings.
 */
public class NumberSerTest extends DatabindTestUtil
{
    private final ObjectMapper MAPPER = sharedMapper();

    private final ObjectMapper NON_EMPTY_MAPPER = newJsonMapper()
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_EMPTY)
            ;

    static class IntWrapper {
        public int i;
        public IntWrapper(int value) { i = value; }
    }

    static class DoubleWrapper {
        public double value;
        public DoubleWrapper(double v) { value = v; }
    }

    static class BigDecimalWrapper {
        public BigDecimal value;
        public BigDecimalWrapper(BigDecimal v) { value = v; }
    }

    static class IntAsString {
        @JsonFormat(shape=JsonFormat.Shape.STRING)
        @JsonProperty("value")
        public int foo = 3;
    }

    static class LongAsString {
        @JsonFormat(shape=JsonFormat.Shape.STRING)
        public long value = 4;
    }

    static class DoubleAsString {
        @JsonFormat(shape=JsonFormat.Shape.STRING)
        public double value = -0.5;
    }

    static class BigIntegerAsString {
        @JsonFormat(shape=JsonFormat.Shape.STRING)
        public BigInteger value = BigInteger.valueOf(123456L);
    }

    static class BigDecimalAsString {
        @JsonFormat(shape=JsonFormat.Shape.STRING)
        public BigDecimal value;

        public BigDecimalAsString() { this(BigDecimal.valueOf(0.25)); }
        public BigDecimalAsString(BigDecimal v) { value = v; }
    }

    static class NumberWrapper {
        // ensure it will use `Number` as statically force type, when looking for serializer
        @JsonSerialize(as=Number.class)
        public Number value;

        public NumberWrapper(Number v) { value = v; }
    }

    static class BigDecimalHolder {
        private final BigDecimal value;

        public BigDecimalHolder(String num) {
            value = new BigDecimal(num);
        }

        public BigDecimal getValue() {
            return value;
        }
    }

    static class BigDecimalAsStringSerializer extends JsonSerializer<BigDecimal> {
        private final DecimalFormat df = createDecimalFormatForDefaultLocale("0.0");

        @Override
        public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(df.format(value));
        }
    }

    static class BigDecimalAsNumberSerializer extends JsonSerializer<BigDecimal> {
        private final DecimalFormat df = createDecimalFormatForDefaultLocale("0.0");

        @Override
        public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeNumber(df.format(value));
        }
    }

    static class MyBigDecimal extends BigDecimal {
        public MyBigDecimal(String value) {
            super(value);
        }
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    @Test
    public void testDouble() throws Exception
    {
        double[] values = new double[] {
            0.0, 1.0, 0.1, -37.01, 999.99, 0.3, 33.3, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY
        };
        for (double d : values) {
            String expected = String.valueOf(d);
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                expected = "\""+d+"\"";
            }
            assertEquals(expected, MAPPER.writeValueAsString(Double.valueOf(d)));
        }
    }

    @Test
    public void testBigInteger() throws Exception
    {
        BigInteger[] values = new BigInteger[] {
                BigInteger.ONE, BigInteger.TEN, BigInteger.ZERO,
                BigInteger.valueOf(1234567890L),
                new BigInteger("123456789012345678901234568"),
                new BigInteger("-1250000124326904597090347547457")
                };

        for (BigInteger value : values) {
            String expected = value.toString();
            assertEquals(expected, MAPPER.writeValueAsString(value));
        }
    }

    @Test
    public void testNumbersAsString() throws Exception
    {
        assertEquals(a2q("{'value':'3'}"), MAPPER.writeValueAsString(new IntAsString()));
        assertEquals(a2q("{'value':'4'}"), MAPPER.writeValueAsString(new LongAsString()));
        assertEquals(a2q("{'value':'-0.5'}"), MAPPER.writeValueAsString(new DoubleAsString()));
        assertEquals(a2q("{'value':'0.25'}"), MAPPER.writeValueAsString(new BigDecimalAsString()));
        assertEquals(a2q("{'value':'123456'}"), MAPPER.writeValueAsString(new BigIntegerAsString()));
    }

    @Test
    public void testNumbersAsStringNonEmpty() throws Exception
    {
        assertEquals(a2q("{'value':'3'}"), NON_EMPTY_MAPPER.writeValueAsString(new IntAsString()));
        assertEquals(a2q("{'value':'4'}"), NON_EMPTY_MAPPER.writeValueAsString(new LongAsString()));
        assertEquals(a2q("{'value':'-0.5'}"), NON_EMPTY_MAPPER.writeValueAsString(new DoubleAsString()));
        assertEquals(a2q("{'value':'0.25'}"), NON_EMPTY_MAPPER.writeValueAsString(new BigDecimalAsString()));
        assertEquals(a2q("{'value':'123456'}"), NON_EMPTY_MAPPER.writeValueAsString(new BigIntegerAsString()));
    }

    @Test
    public void testConfigOverridesForNumbers() throws Exception
    {
        ObjectMapper mapper = newJsonMapper();
        mapper.configOverride(Integer.TYPE) // for `int`
            .setFormat(JsonFormat.Value.forShape(JsonFormat.Shape.STRING));
        mapper.configOverride(Double.TYPE) // for `double`
            .setFormat(JsonFormat.Value.forShape(JsonFormat.Shape.STRING));
        mapper.configOverride(BigDecimal.class)
            .setFormat(JsonFormat.Value.forShape(JsonFormat.Shape.STRING));

        assertEquals(a2q("{'i':'3'}"),
                mapper.writeValueAsString(new IntWrapper(3)));
        assertEquals(a2q("{'value':'0.75'}"),
                mapper.writeValueAsString(new DoubleWrapper(0.75)));
        assertEquals(a2q("{'value':'-0.5'}"),
                mapper.writeValueAsString(new BigDecimalWrapper(BigDecimal.valueOf(-0.5))));
    }

    @Test
    public void testNumberType() throws Exception
    {
        assertEquals(a2q("{'value':1}"), MAPPER.writeValueAsString(new NumberWrapper(Byte.valueOf((byte) 1))));
        assertEquals(a2q("{'value':2}"), MAPPER.writeValueAsString(new NumberWrapper(Short.valueOf((short) 2))));
        assertEquals(a2q("{'value':3}"), MAPPER.writeValueAsString(new NumberWrapper(Integer.valueOf(3))));
        assertEquals(a2q("{'value':4}"), MAPPER.writeValueAsString(new NumberWrapper(Long.valueOf(4L))));
        assertEquals(a2q("{'value':0.5}"), MAPPER.writeValueAsString(new NumberWrapper(Float.valueOf(0.5f))));
        assertEquals(a2q("{'value':0.05}"), MAPPER.writeValueAsString(new NumberWrapper(Double.valueOf(0.05))));
        assertEquals(a2q("{'value':123}"), MAPPER.writeValueAsString(new NumberWrapper(BigInteger.valueOf(123))));
        assertEquals(a2q("{'value':0.025}"), MAPPER.writeValueAsString(new NumberWrapper(BigDecimal.valueOf(0.025))));
    }

    @Test
    public void testCustomSerializationBigDecimalAsString() throws Exception {
        SimpleModule module = new SimpleModule();
        module.addSerializer(BigDecimal.class, new BigDecimalAsStringSerializer());
        ObjectMapper mapper = jsonMapperBuilder().addModule(module).build();
        assertEquals(a2q("{'value':'2.0'}"), mapper.writeValueAsString(new BigDecimalHolder("2")));
    }

    @Test
    public void testCustomSerializationBigDecimalAsNumber() throws Exception {
        SimpleModule module = new SimpleModule();
        module.addSerializer(BigDecimal.class, new BigDecimalAsNumberSerializer());
        ObjectMapper mapper = jsonMapperBuilder().addModule(module).build();
        assertEquals(a2q("{'value':2.0}"), mapper.writeValueAsString(new BigDecimalHolder("2")));
    }

    @Test
    public void testConfigOverrideJdkNumber() throws Exception {
        ObjectMapper mapper = jsonMapperBuilder().withConfigOverride(BigDecimal.class,
                        c -> c.setFormat(JsonFormat.Value.forShape(JsonFormat.Shape.STRING)))
                .build();
        String value = mapper.writeValueAsString(new BigDecimal("123.456"));
        assertEquals(a2q("'123.456'"), value);
    }

    @Test
    public void testConfigOverrideNonJdkNumber() throws Exception {
        ObjectMapper mapper = jsonMapperBuilder().withConfigOverride(MyBigDecimal.class,
                c -> c.setFormat(JsonFormat.Value.forShape(JsonFormat.Shape.STRING)))
                .build();
        String value = mapper.writeValueAsString(new MyBigDecimal("123.456"));
        assertEquals(a2q("'123.456'"), value);
    }

    // default locale is en_US
    static DecimalFormat createDecimalFormatForDefaultLocale(final String pattern) {
        return new DecimalFormat(pattern, new DecimalFormatSymbols(Locale.ENGLISH));
    }
}
