package com.fasterxml.jackson.databind;

import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedField;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import com.fasterxml.jackson.databind.introspect.AnnotatedParameter;

/**
 * Class that defines how names of JSON properties ("external names")
 * are derived from names of POJO methods and fields ("internal names"),
 * in cases where no explicit annotations exist for naming.
 * Methods are passed information about POJO member for which name is needed,
 * as well as default name that would be used if no custom strategy was used.
 *<p>
 * Default (empty) implementation returns suggested ("implicit" or "default") name unmodified
 *<p>
 * Note that the strategy is guaranteed to be called once per logical property
 * (which may be represented by multiple members; such as pair of a getter and
 * a setter), but may be called for each: implementations should not count on
 * exact number of times, and should work for any member that represent a
 * property.
 * Also note that calls are made during construction of serializers and deserializers
 * which are typically cached, and not for every time serializer or deserializer
 * is called.
 *<p>
 * In absence of a registered custom strategy, the default Java property naming strategy
 * is used, which leaves field names as is, and removes set/get/is prefix
 * from methods (as well as lower-cases initial sequence of capitalized
 * characters).
 *<p>
 * NOTE! Since 2.12 up until 2.19, sub-classes defined here (as well as static singleton
 * instances thereof)
 * were deprecated due to
 * <a href="https://github.com/FasterXML/jackson-databind/issues/2715">databind#2715</a>.
 * They were removed in 2.20.
 * Please use constants and classes in {@link PropertyNamingStrategies} instead.
 * In particular, {@link PropertyNamingStrategies.NamingBase} is the base class
 * to use.
 */
public class PropertyNamingStrategy // NOTE: was abstract until 2.7
    implements java.io.Serializable
{
    private static final long serialVersionUID = 2L;

    // // Constants for standard implementations: removed from Jackson 2.20

    //@Deprecated // since 2.12
    //public static final PropertyNamingStrategy LOWER_CAMEL_CASE = new PropertyNamingStrategy();

    //@Deprecated // since 2.12
    //public static final PropertyNamingStrategy UPPER_CAMEL_CASE = new UpperCamelCaseStrategy(false);

    //@Deprecated // since 2.12
    //public static final PropertyNamingStrategy SNAKE_CASE = new SnakeCaseStrategy(false);

    //@Deprecated // since 2.12
    //public static final PropertyNamingStrategy LOWER_CASE = new LowerCaseStrategy(false);

    //@Deprecated // since 2.12
    //public static final PropertyNamingStrategy KEBAB_CASE = new KebabCaseStrategy(false);

    //@Deprecated // since 2.12
    //public static final PropertyNamingStrategy LOWER_DOT_CASE = new LowerDotCaseStrategy(false);

    /*
    /**********************************************************
    /* API
    /**********************************************************
     */

    /**
     * Method called to find external name (name used in JSON) for given logical
     * POJO property,
     * as defined by given field.
     *
     * @param config Configuration in used: either <code>SerializationConfig</code>
     *   or <code>DeserializationConfig</code>, depending on whether method is called
     *   during serialization or deserialization
     * @param field Field used to access property
     * @param defaultName Default name that would be used for property in absence of custom strategy
     *
     * @return Logical name to use for property that the field represents
     */
    public String nameForField(MapperConfig<?> config, AnnotatedField field,
            String defaultName)
    {
        return defaultName;
    }

    /**
     * Method called to find external name (name used in JSON) for given logical
     * POJO property,
     * as defined by given getter method; typically called when building a serializer.
     * (but not always -- when using "getter-as-setter", may be called during
     * deserialization)
     *
     * @param config Configuration in used: either <code>SerializationConfig</code>
     *   or <code>DeserializationConfig</code>, depending on whether method is called
     *   during serialization or deserialization
     * @param method Method used to access property.
     * @param defaultName Default name that would be used for property in absence of custom strategy
     *
     * @return Logical name to use for property that the method represents
     */
    public String nameForGetterMethod(MapperConfig<?> config, AnnotatedMethod method,
            String defaultName)
    {
        return defaultName;
    }

    /**
     * Method called to find external name (name used in JSON) for given logical
     * POJO property,
     * as defined by given setter method; typically called when building a deserializer
     * (but not necessarily only then).
     *
     * @param config Configuration in used: either <code>SerializationConfig</code>
     *   or <code>DeserializationConfig</code>, depending on whether method is called
     *   during serialization or deserialization
     * @param method Method used to access property.
     * @param defaultName Default name that would be used for property in absence of custom strategy
     *
     * @return Logical name to use for property that the method represents
     */
    public String nameForSetterMethod(MapperConfig<?> config, AnnotatedMethod method,
            String defaultName)
    {
        return defaultName;
    }

    /**
     * Method called to find external name (name used in JSON) for given logical
     * POJO property,
     * as defined by given constructor parameter; typically called when building a deserializer
     * (but not necessarily only then).
     *
     * @param config Configuration in used: either <code>SerializationConfig</code>
     *   or <code>DeserializationConfig</code>, depending on whether method is called
     *   during serialization or deserialization
     * @param ctorParam Constructor parameter used to pass property.
     * @param defaultName Default name that would be used for property in absence of custom strategy
     */
    public String nameForConstructorParameter(MapperConfig<?> config, AnnotatedParameter ctorParam,
            String defaultName)
    {
        return defaultName;
    }

    /*
    /**********************************************************
    /* Public base class for simple implementations: removed from Jackson 2.20
    /**********************************************************
     */

    /*
     * Replaced by {@link PropertyNamingStrategies.NamingBase}.
     *
     * @deprecated Since 2.12 deprecated. See
     * <a href="https://github.com/FasterXML/jackson-databind/issues/2715">databind#2715</a>
     * for reasons for deprecation.
     */
    //@Deprecated
    //public static abstract class PropertyNamingStrategyBase extends PropertyNamingStrategy

    /*
    /**********************************************************
    /* Standard implementations: removed from Jackson 2.20
    /**********************************************************
     */

    //@Deprecated // since 2.12
    //public static class SnakeCaseStrategy extends PropertyNamingStrategyBase

    //@Deprecated // since 2.12
    //public static class UpperCamelCaseStrategy extends PropertyNamingStrategyBase

    //@Deprecated // since 2.12
    //public static class LowerCaseStrategy extends PropertyNamingStrategyBase

    //@Deprecated // since 2.12
    //public static class KebabCaseStrategy extends PropertyNamingStrategyBase

    //@Deprecated // since 2.12
    //public static class LowerDotCaseStrategy extends PropertyNamingStrategyBase
}
