package com.fasterxml.jackson.databind.deser.std;

import java.io.IOException;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import com.fasterxml.jackson.databind.deser.impl.PropertyBasedCreator;
import com.fasterxml.jackson.databind.deser.impl.PropertyValueBuffer;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.databind.util.ClassUtil;
import com.fasterxml.jackson.databind.util.EnumResolver;

/**
 * Deserializer that uses a single-String static factory method
 * for locating Enum values by String id.
 *
 * @since 2.8 (as stand-alone class; was static inner class of {@link EnumDeserializer}
 */
class FactoryBasedEnumDeserializer
    extends StdDeserializer<Object>
    implements ContextualDeserializer
{
    private static final long serialVersionUID = 1;

    // Marker type; null if String expected; otherwise usually numeric wrapper
    protected final JavaType _inputType;
    protected final AnnotatedMethod _factory;
    protected final JsonDeserializer<?> _deser;
    protected final ValueInstantiator _valueInstantiator;
    protected final SettableBeanProperty[] _creatorProps;

    // @since 2.19
    protected final Enum<?> _defaultValue;

    protected final boolean _hasArgs;

    /**
     * Lazily instantiated property-based creator.
     *
     * @since 2.8
     */
    private transient volatile PropertyBasedCreator _propCreator;

    /**
     * @since 2.8
     * @deprecated since 2.19
     */
    @Deprecated
    public FactoryBasedEnumDeserializer(Class<?> cls, AnnotatedMethod f, JavaType paramType,
            ValueInstantiator valueInstantiator, SettableBeanProperty[] creatorProps)
    {
        this(cls, f, paramType, valueInstantiator, creatorProps, null);
    }

    /**
     * @since 2.19
     */
    public FactoryBasedEnumDeserializer(Class<?> cls, AnnotatedMethod f, JavaType paramType,
            ValueInstantiator valueInstantiator, SettableBeanProperty[] creatorProps,
            EnumResolver enumResolver)
    {
        super(cls);
        _factory = f;
        _hasArgs = true;
        // We'll skip case of `String`, as well as no type (zero-args):
        _inputType = (paramType.hasRawClass(String.class) || paramType.hasRawClass(CharSequence.class))
                ? null : paramType;
        _deser = null;
        _valueInstantiator = valueInstantiator;
        _creatorProps = creatorProps;
        _defaultValue = (enumResolver == null) ? null : enumResolver.getDefaultValue();
    }

    /**
     * @since 2.8
     */
    public FactoryBasedEnumDeserializer(Class<?> cls, AnnotatedMethod f)
    {
        super(cls);
        _factory = f;
        _hasArgs = false;
        _inputType = null;
        _deser = null;
        _valueInstantiator = null;
        _creatorProps = null;
        _defaultValue = null;
    }

    protected FactoryBasedEnumDeserializer(FactoryBasedEnumDeserializer base,
            JsonDeserializer<?> deser) {
        super(base._valueClass);
        _inputType = base._inputType;
        _factory = base._factory;
        _hasArgs = base._hasArgs;
        _valueInstantiator = base._valueInstantiator;
        _creatorProps = base._creatorProps;
        _defaultValue = base._defaultValue;

        _deser = deser;
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext ctxt,
            BeanProperty property)
        throws JsonMappingException
    {
        // So: no need to fetch if we had it; or if target is `String`(-like); or
        // if we have properties-based Creator (for which we probably SHOULD do
        // different contextualization?)
        if ((_deser == null) && (_inputType != null) && (_creatorProps == null)) {
            return new FactoryBasedEnumDeserializer(this,
                    ctxt.findContextualValueDeserializer(_inputType, property));
        }
        return this;
    }

    @Override // since 2.9
    public Boolean supportsUpdate(DeserializationConfig config) {
        return Boolean.FALSE;
    }

    @Override // since 2.12
    public LogicalType logicalType() {
        return LogicalType.Enum;
    }

    // since 2.9.7: should have been the case earlier but
    @Override
    public boolean isCachable() { return true; }

    @Override
    public ValueInstantiator getValueInstantiator() { return _valueInstantiator; }

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException
    {
        Object value;

        // First: the case of having deserializer for non-String input for delegating
        // Creator method
        if (_deser != null) {
            value = _deser.deserialize(p, ctxt);

        // Second: property- and delegating-creators
        } else if (_hasArgs) {
            // 30-Mar-2020, tatu: For properties-based one, MUST get JSON Object (before
            //   2.11, was just assuming match)
            if (_creatorProps != null) {
                if (p.isExpectedStartObjectToken()) {
                    PropertyBasedCreator pc = _propCreator;
                    if (pc == null) {
                        _propCreator = pc = PropertyBasedCreator.construct(ctxt,
                                _valueInstantiator, _creatorProps,
                                ctxt.isEnabled(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES));
                    }
                    p.nextToken();
                    return deserializeEnumUsingPropertyBased(p, ctxt, pc);
                }
                // If value cannot possibly be delegating-creator,
                if (!_valueInstantiator.canCreateFromString()) {
                    final JavaType targetType = getValueType(ctxt);
                    final JsonToken t = p.currentToken();
                    return ctxt.reportInputMismatch(targetType,
                        "Input mismatch reading Enum %s: properties-based `@JsonCreator` (%s) expects Object Value, got %s (`JsonToken.%s`)",
                        ClassUtil.getTypeDescription(targetType), _factory,
                        JsonToken.valueDescFor(t), t.name());
                }
            }

            // 12-Oct-2021, tatu: We really should only get here if and when String
            //    value is expected; otherwise Deserializer should have been used earlier
            // 14-Jan-2022, tatu: as per [databind#3369] need to consider structured
            //    value types (Object, Array) as well.
            // 15-Nov-2022, tatu: Fix for [databind#3655] requires handling of possible
            //    unwrapping, do it here
            JsonToken t = p.currentToken();
            boolean unwrapping = (t == JsonToken.START_ARRAY)
                    && ctxt.isEnabled(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS);
            if (unwrapping) {
                t = p.nextToken();
            }
            // 24-Nov-2024, tatu: New! "Scalar from Object" (mostly for XML) -- see
            //   https://github.com/FasterXML/jackson-databind/issues/4807
            if (t == JsonToken.START_OBJECT) {
                value = ctxt.extractScalarFromObject(p, this, _valueClass);
                // 17-May-2025, tatu: [databind#4656] need to check for `null`
                if (value == null) {
                    return ctxt.handleUnexpectedToken(_valueClass, p);
                }
            } else if ((t == null) || !t.isScalarValue()) {
                // Could argue we should throw an exception but...
                // 01-Jun-2023, tatu: And now we will finally do it!
                final JavaType targetType = getValueType(ctxt);
                return ctxt.reportInputMismatch(targetType,
                    "Input mismatch reading Enum %s: properties-based `@JsonCreator` (%s) expects String Value, got %s (`JsonToken.%s`)",
                    ClassUtil.getTypeDescription(targetType), _factory,
                    JsonToken.valueDescFor(t), t.name());
            } else {
                value = p.getValueAsString();
            }
            if (unwrapping) {
                if (p.nextToken() != JsonToken.END_ARRAY) {
                    handleMissingEndArrayForSingle(p, ctxt);
                }
            }
        } else { // zero-args; just skip whatever value there may be
            p.skipChildren();
            try {
                return _factory.call();
            } catch (Exception e) {
                Throwable t = ClassUtil.throwRootCauseIfIOE(e);
                return ctxt.handleInstantiationProblem(_valueClass, null, t);
            }
        }
        try {
            return _factory.callOnWith(_valueClass, value);
        } catch (Exception e) {
            Throwable t = ClassUtil.throwRootCauseIfIOE(e);
            if (t instanceof IllegalArgumentException) {
                // [databind#4979]: unknown as default
                if (ctxt.isEnabled(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)) {
                    // ... only if we DO have a default
                    if (_defaultValue != null) {
                        return _defaultValue;
                    }
                }
                // [databind#1642]: unknown as null
                if (ctxt.isEnabled(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)) {
                    return null;
                }
                // 12-Oct-2021, tatu: Should probably try to provide better exception since
                //   we likely hit argument incompatibility... Or can this happen?
            }
            return ctxt.handleInstantiationProblem(_valueClass, value, t);
        }
    }

    @Override
    public Object deserializeWithType(JsonParser p, DeserializationContext ctxt, TypeDeserializer typeDeserializer) throws IOException {
        // 27-Feb-2023, tatu: [databind#3796] required we do NOT skip call
        //    to typed handling even for "simple" Strings
        // if (_deser == null) { return deserialize(p, ctxt); }
        return typeDeserializer.deserializeTypedFromAny(p, ctxt);
    }

    // Method to deserialize the Enum using property based methodology
    protected Object deserializeEnumUsingPropertyBased(final JsonParser p, final DeserializationContext ctxt,
    		final PropertyBasedCreator creator) throws IOException
    {
        PropertyValueBuffer buffer = creator.startBuilding(p, ctxt, null);

        JsonToken t = p.currentToken();
        for (; t == JsonToken.FIELD_NAME; t = p.nextToken()) {
            String propName = p.currentName();
            p.nextToken(); // to point to value

            final SettableBeanProperty creatorProp = creator.findCreatorProperty(propName);
            if (buffer.readIdProperty(propName) && creatorProp == null) {
                continue;
            }
            if (creatorProp != null) {
                buffer.assignParameter(creatorProp, _deserializeWithErrorWrapping(p, ctxt, creatorProp));
                continue;
            }
            // 26-Nov-2020, tatu: ... what should we do here tho?
            p.skipChildren();
        }
        return creator.build(ctxt, buffer);
    }

    // ************ Got the below methods from BeanDeserializer ********************//

    protected final Object _deserializeWithErrorWrapping(JsonParser p, DeserializationContext ctxt,
            SettableBeanProperty prop) throws IOException
    {
        try {
            return prop.deserialize(p, ctxt);
        } catch (Exception e) {
            return wrapAndThrow(e, handledType(), prop.getName(), ctxt);
        }
    }

    protected Object wrapAndThrow(Throwable t, Object bean, String fieldName, DeserializationContext ctxt)
            throws IOException
    {
        throw JsonMappingException.wrapWithPath(throwOrReturnThrowable(t, ctxt), bean, fieldName);
    }

    private Throwable throwOrReturnThrowable(Throwable t, DeserializationContext ctxt) throws IOException
    {
        t = ClassUtil.getRootCause(t);
        // Errors to be passed as is
        ClassUtil.throwIfError(t);
        boolean wrap = (ctxt == null) || ctxt.isEnabled(DeserializationFeature.WRAP_EXCEPTIONS);
        // Ditto for IOExceptions; except we may want to wrap JSON exceptions
        if (t instanceof IOException) {
            if (!wrap || !(t instanceof JacksonException)) {
                throw (IOException) t;
            }
        } else if (!wrap) {
            ClassUtil.throwIfRTE(t);
        }
        return t;
    }
}
