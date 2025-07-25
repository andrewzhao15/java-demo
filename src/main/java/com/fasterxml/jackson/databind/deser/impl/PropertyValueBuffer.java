package com.fasterxml.jackson.databind.deser.impl;

import java.io.IOException;
import java.util.BitSet;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.SettableAnyProperty;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;

/**
 * Simple container used for temporarily buffering a set of
 * <code>PropertyValue</code>s.
 * Using during construction of beans (and Maps) that use Creators,
 * and hence need buffering before instance (that will have properties
 * to assign values to) is constructed.
 */
public class PropertyValueBuffer
{
    /*
    /**********************************************************
    /* Configuration
    /**********************************************************
     */

    protected final JsonParser _parser;
    protected final DeserializationContext _context;

    protected final ObjectIdReader _objectIdReader;

    /*
    /**********************************************************
    /* Accumulated properties, other stuff
    /**********************************************************
     */

    /**
     * Buffer used for storing creator parameters for constructing
     * instance.
     */
    protected final Object[] _creatorParameters;

    /**
     * Number of creator parameters for which we have not yet received
     * values.
     */
    protected int _paramsNeeded;

    /**
     * Bitflag used to track parameters found from incoming data
     * when number of parameters is
     * less than 32 (fits in int).
     */
    protected int _paramsSeen;

    /**
     * Bitflag used to track parameters found from incoming data
     * when number of parameters is
     * 32 or higher.
     */
    protected final BitSet _paramsSeenBig;

    /**
     * If we get non-creator parameters before or between
     * creator parameters, those need to be buffered. Buffer
     * is just a simple linked list
     */
    protected PropertyValue _buffered;

    /**
     * In case there is an Object Id property to handle, this is the value
     * we have for it.
     */
    protected Object _idValue;

    /**
     * "Any setter" property bound to a Creator parameter (via {@code @JsonAnySetter})
     *
     * @since 2.18
     */
    protected final SettableAnyProperty _anyParamSetter;

    /**
     * If "Any-setter-via-Creator" exists, we will need to buffer values to feed it,
     * separate from regular, non-creator properties (see {@code _buffered}).
     *
     * @since 2.18
     */
    protected PropertyValue _anyParamBuffered;

    /*
    /**********************************************************
    /* Life-cycle
    /**********************************************************
     */

    /**
     * @since 2.18
     */
    public PropertyValueBuffer(JsonParser p, DeserializationContext ctxt, int paramCount,
            ObjectIdReader oir, SettableAnyProperty anyParamSetter)
    {
        _parser = p;
        _context = ctxt;
        _paramsNeeded = paramCount;
        _objectIdReader = oir;
        _creatorParameters = new Object[paramCount];
        if (paramCount < 32) {
            _paramsSeenBig = null;
        } else {
            _paramsSeenBig = new BitSet();
        }
        // Only care about Creator-bound Any setters:
        if ((anyParamSetter == null) || (anyParamSetter.getParameterIndex() < 0)) {
            _anyParamSetter = null;
        } else {
            _anyParamSetter = anyParamSetter;
        }
    }

    @Deprecated // since 2.18
    public PropertyValueBuffer(JsonParser p, DeserializationContext ctxt, int paramCount,
            ObjectIdReader oir)
    {
        this(p, ctxt, paramCount, oir, null);
    }

    /**
     * Returns {@code true} if the given property was seen in the JSON source by
     * this buffer.
     *
     * @since 2.8
     */
    public final boolean hasParameter(SettableBeanProperty prop)
    {
        final int ix = prop.getCreatorIndex();

        if (_paramsSeenBig == null) {
            if (((_paramsSeen >> ix) & 1) == 1) {
                return true;
            }
        } else {
            if (_paramsSeenBig.get(ix)) {
                return true;
            }
        }
        // 28-Sep-2024 : [databind#4508] Support any-setter flowing through creator
        if (_anyParamSetter != null) {
            if (ix == _anyParamSetter.getParameterIndex()) {
                return true;
            }
        }
        return false;
    }

    /**
     * A variation of {@link #getParameters(SettableBeanProperty[])} that
     * accepts a single property.  Whereas the plural form eagerly fetches and
     * validates all properties, this method may be used (along with
     * {@link #hasParameter(SettableBeanProperty)}) to let applications only
     * fetch the properties defined in the JSON source itself, and to have some
     * other customized behavior for missing properties.
     *
     * @since 2.8
     */
    public Object getParameter(SettableBeanProperty prop)
        throws JsonMappingException
    {
        Object value;
        if (hasParameter(prop)) {
            value = _creatorParameters[prop.getCreatorIndex()];
        } else {
            value = _creatorParameters[prop.getCreatorIndex()] = _findMissing(prop);
        }
        // 28-Sep-2024 : [databind#4508] Support any-setter flowing through creator
        if ((value == null) && (_anyParamSetter != null )
                && (prop.getCreatorIndex() == _anyParamSetter.getParameterIndex())) {
            value = _createAndSetAnySetterValue();
        }
        if (value == null && _context.isEnabled(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)) {
            return _context.reportInputMismatch(prop,
                "Null value for creator property '%s' (index %d); `DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES` enabled",
                prop.getName(), prop.getCreatorIndex());
        }
        return value;
    }

    /**
     * Method called to do necessary post-processing such as injection of values
     * and verification of values for required properties,
     * after either {@link #assignParameter(SettableBeanProperty, Object)}
     * returns <code>true</code> (to indicate all creator properties are found), or when
     * then whole JSON Object has been processed,
     */
    public Object[] getParameters(SettableBeanProperty[] props)
        throws JsonMappingException, IOException
    {
        // quick check to see if anything else is needed
        if (_paramsNeeded > 0) {
            if (_paramsSeenBig == null) {
                int mask = _paramsSeen;
                // not optimal, could use `Integer.trailingZeroes()`, but for now should not
                // really matter for common cases
                for (int ix = 0, len = _creatorParameters.length; ix < len; ++ix, mask >>= 1) {
                    if ((mask & 1) == 0) {
                        _creatorParameters[ix] = _findMissing(props[ix]);
                    }
                }
            } else {
                final int len = _creatorParameters.length;
                for (int ix = 0; (ix = _paramsSeenBig.nextClearBit(ix)) < len; ++ix) {
                    _creatorParameters[ix] = _findMissing(props[ix]);
                }
            }
        }
        // [databind#562] since 2.18 : Respect @JsonAnySetter in @JsonCreator
        if (_anyParamSetter != null) {
            _creatorParameters[_anyParamSetter.getParameterIndex()] = _createAndSetAnySetterValue();
        }
        if (_context.isEnabled(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)) {
            for (int ix = 0; ix < props.length; ++ix) {
                if (_creatorParameters[ix] == null) {
                    SettableBeanProperty prop = props[ix];
                    _context.reportInputMismatch(prop,
                            "Null value for creator property '%s' (index %d); `DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES` enabled",
                            prop.getName(), props[ix].getCreatorIndex());
                }
            }
        }
        return _creatorParameters;
    }

    /**
     * Helper method called to create and set any values buffered for "any setter"
     */
    private Object _createAndSetAnySetterValue() throws JsonMappingException
    {
        Object anySetterParameterObject = _anyParamSetter.createParameterObject();
        for (PropertyValue pv = _anyParamBuffered; pv != null; pv = pv.next) {
            try {
                pv.setValue(anySetterParameterObject);

            // Since one of callers only exposes JsonMappingException, but pv.setValue()
            // nominally leaks IOException, need to do this unfortunate conversion
            } catch (JsonMappingException e) {
                throw e;
            } catch (IOException e) {
                throw JsonMappingException.fromUnexpectedIOE(e);
            }
        }
        return anySetterParameterObject;
    }

    protected Object _findMissing(SettableBeanProperty prop) throws JsonMappingException
    {
        // 08-Jun-2024: [databind#562] AnySetters are bit special
        if (_anyParamSetter != null) {
            if (prop.getCreatorIndex() == _anyParamSetter.getParameterIndex()) {
                // Ok if anything buffered
                if (_anyParamBuffered != null) {
                    // ... will be assigned by caller later on, for now return null
                    return null;
                }
            }
        }

        // First: do we have injectable value?
        Object injectableValueId = prop.getInjectableValueId();
        if (injectableValueId != null) {
            return _context.findInjectableValue(prop.getInjectableValueId(),
                    prop, null, null);
        }
        // Second: required?
        if (prop.isRequired()) {
            _context.reportInputMismatch(prop, "Missing required creator property '%s' (index %d)",
                    prop.getName(), prop.getCreatorIndex());
        }
        if (_context.isEnabled(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)) {
            _context.reportInputMismatch(prop,
                    "Missing creator property '%s' (index %d); `DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES` enabled",
                    prop.getName(), prop.getCreatorIndex());
        }
        try {
            // Third: NullValueProvider? (22-Sep-2019, [databind#2458])
            // 08-Aug-2021, tatu: consider [databind#3214]; not null but "absent" value...
            Object absentValue = prop.getNullValueProvider().getAbsentValue(_context);
            if (absentValue != null) {
                return absentValue;
            }

            // Fourth: default value
            JsonDeserializer<Object> deser = prop.getValueDeserializer();
            return deser.getAbsentValue(_context);
        } catch (DatabindException e) {
            // [databind#2101]: Include property name, if we have it
            AnnotatedMember member = prop.getMember();
            if (member != null) {
                e.prependPath(member.getDeclaringClass(), prop.getName());
            }
            throw e;
        }
    }

    /*
    /**********************************************************
    /* Other methods
    /**********************************************************
     */

    /**
     * Helper method called to see if given non-creator property is the "id property";
     * and if so, handle appropriately.
     *
     * @since 2.1
     */
    public boolean readIdProperty(String propName) throws IOException
    {
        if ((_objectIdReader != null) && propName.equals(_objectIdReader.propertyName.getSimpleName())) {
            _idValue = _objectIdReader.readObjectReference(_parser, _context);
            return true;
        }
        return false;
    }

    /**
     * Helper method called to handle Object Id value collected earlier, if any
     */
    public Object handleIdValue(final DeserializationContext ctxt, Object bean) throws IOException
    {
        if (_objectIdReader != null) {
            if (_idValue != null) {
                ReadableObjectId roid = ctxt.findObjectId(_idValue, _objectIdReader.generator, _objectIdReader.resolver);
                roid.bindItem(bean);
                // also: may need to set a property value as well
                SettableBeanProperty idProp = _objectIdReader.idProperty;
                if (idProp != null) {
                    return idProp.setAndReturn(bean, _idValue);
                }
            } else {
                // 07-Jun-2016, tatu: Trying to improve error messaging here...
                ctxt.reportUnresolvedObjectId(_objectIdReader, bean);
            }
        }
        return bean;
    }

    protected PropertyValue buffered() { return _buffered; }

    public boolean isComplete() { return _paramsNeeded <= 0; }

    /**
     * Method called to buffer value for given property, as well as check whether
     * we now have values for all (creator) properties that we expect to get values for.
     *
     * @return True if we have received all creator parameters
     *
     * @since 2.6
     */
    public boolean assignParameter(SettableBeanProperty prop, Object value)
    {
        final int ix = prop.getCreatorIndex();
        _creatorParameters[ix] = value;
        if (_paramsSeenBig == null) {
            int old = _paramsSeen;
            int newValue = (old | (1 << ix));
            if (old != newValue) {
                _paramsSeen = newValue;
                if (--_paramsNeeded <= 0) {
                    // 29-Nov-2016, tatu: But! May still require Object Id value
                    return (_objectIdReader == null) || (_idValue != null);
                }
            }
        } else {
            if (!_paramsSeenBig.get(ix)) {
                _paramsSeenBig.set(ix);
                if (--_paramsNeeded <= 0) {
                    // 29-Nov-2016, tatu: But! May still require Object Id value
                }
            }
        }
        return false;
    }

    public void bufferProperty(SettableBeanProperty prop, Object value) {
        _buffered = new PropertyValue.Regular(_buffered, value, prop);
    }

    public void bufferAnyProperty(SettableAnyProperty prop, String propName, Object value) {
        _buffered = new PropertyValue.Any(_buffered, value, prop, propName);
    }

    public void bufferMapProperty(Object key, Object value) {
        _buffered = new PropertyValue.Map(_buffered, value, key);
    }

    // @since 2.18
    public void bufferAnyParameterProperty(SettableAnyProperty prop, String propName, Object value) {
        _anyParamBuffered = new PropertyValue.AnyParameter(_anyParamBuffered, value, prop, propName);
    }
}
