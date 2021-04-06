package org.imanity.framework.reflect;

import lombok.Getter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Getter
public class ReflectObject {

    private final ReflectCache accessorCache;
    private final Class<?> type;

    private Object instance;

    public ReflectObject(Object instance) {
        this(instance.getClass());
        this.instance(instance);
    }

    public ReflectObject(Class<?> type) {
        this.type = type;
        this.accessorCache = ReflectCache.get(type);
    }

    public ReflectObject instance(Object instance) {
        this.instance = instance;
        return this;
    }

    public <T> T get(Class<T> type, int index) {
        Field field = this.accessorCache.resolveField(new ReflectQuery(type, index));

        Object obj = Reflect.getField(this.instance, field);
        return obj != null ? type.cast(obj) : null;
    }

    public <T> T get(String name) {
        Field field = this.accessorCache.resolveField(new ReflectQuery(name));

        Object obj = Reflect.getField(this.instance, field);
        return obj != null ? (T) obj : null;
    }

    public void set(Class<?> type, int index, Object value) {
        Field field = this.accessorCache.resolveField(new ReflectQuery(type, index));

        Reflect.setField(this.instance, field, value);
    }

    public void set(String name, Object value) {
        Field field = this.accessorCache.resolveField(new ReflectQuery(name));

        Reflect.setField(this.instance, field, value);
    }

    public <T> T invoke(String name, Object... parameters) {
        Class[] types = new Class[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            types[i] = parameters[i].getClass();
        }

        Method method = this.accessorCache.resolveMethod(new ReflectQuery(name, types));
        try {
            Object result = method.invoke(this.instance, parameters);
            return result != null ? (T) result : null;
        } catch (ReflectiveOperationException ex) {
            throw new ImanityReflectException(ex);
        }
    }

}
