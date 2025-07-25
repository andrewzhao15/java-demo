package com.fasterxml.jackson.databind;

import java.util.*;

import com.fasterxml.jackson.databind.util.ClassUtil;

/**
 * Abstract class that defines API for objects that provide value to
 * "inject" during deserialization. An instance of this object
 */
public abstract class InjectableValues
{
    /**
     * Method called to find value identified by id <code>valueId</code> to
     * inject as value of specified property during deserialization, passing
     * POJO instance in which value will be injected if it is available
     * (will be available when injected via field or setter; not available
     * when injected via constructor or factory method argument).
     *
     * @param ctxt Deserialization context
     * @param valueId Object that identifies value to inject; may be a simple
     *   name or more complex identifier object, whatever provider needs
     * @param forProperty Bean property in which value is to be injected
     * @param beanInstance Bean instance that contains property to inject,
     *    if available; null if bean has not yet been constructed.
     * @param optional Flag used for configuring the behavior when the value
     *    to inject is not found
     */
    public Object findInjectableValue(DeserializationContext ctxt,
            Object valueId, BeanProperty forProperty, Object beanInstance,
            Boolean optional)
        throws JsonMappingException
    {
        // For backwards-compatibility, must delegate to old method
        return findInjectableValue(valueId, ctxt, forProperty, beanInstance);
    }

    /**
     * @deprecated in 2.20
     */
    @Deprecated // since 2.20
    public abstract Object findInjectableValue(Object valueId, DeserializationContext ctxt,
            BeanProperty forProperty, Object beanInstance) throws JsonMappingException;

    /*
    /**********************************************************
    /* Standard implementation
    /**********************************************************
     */

    /**
     * Simple standard implementation which uses a simple Map to
     * store values to inject, identified by simple String keys.
     */
    public static class Std
        extends InjectableValues
        implements java.io.Serializable
    {
        private static final long serialVersionUID = 1L;

        protected final Map<String,Object> _values;

        public Std() {
            this(new HashMap<String,Object>());
        }

        public Std(Map<String,Object> values) {
            _values = values;
        }

        public Std addValue(String key, Object value) {
            _values.put(key, value);
            return this;
        }

        public Std addValue(Class<?> classKey, Object value) {
            _values.put(classKey.getName(), value);
            return this;
        }

        /**
         * @since 2.20
         */
        @Override
        public Object findInjectableValue(DeserializationContext ctxt, Object valueId,
                BeanProperty forProperty, Object beanInstance, Boolean optional)
            throws JsonMappingException
        {
            if (!(valueId instanceof String)) {
                throw ctxt.missingInjectableValueException(
                        String.format(
                        "Unsupported injectable value id type (%s), expecting String",
                        ClassUtil.classNameOf(valueId)),
                        valueId, forProperty, beanInstance);
            }
            String key = (String) valueId;
            Object ob = _values.get(key);
            if (ob == null && !_values.containsKey(key)) {
                if (Boolean.FALSE.equals(optional)
                        || ((optional == null)
                                && ctxt.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_INJECT_VALUE))) {
                    throw ctxt.missingInjectableValueException(
                            String.format("No injectable value with id '%s' found (for property '%s')",
                            key, forProperty.getName()),
                            valueId, forProperty, beanInstance);
                }
            }
            return ob;
        }

        /**
         * @deprecated in 2.20
         */
        @Override
        @Deprecated // since 2.20
        public Object findInjectableValue(Object valueId, DeserializationContext ctxt,
                BeanProperty forProperty, Object beanInstance) throws JsonMappingException
        {
            return this.findInjectableValue(ctxt, valueId, forProperty, beanInstance, null);
        }
    }
}
