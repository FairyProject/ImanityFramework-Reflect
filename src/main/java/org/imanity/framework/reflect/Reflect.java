package org.imanity.framework.reflect;

import com.google.common.base.Preconditions;
import com.google.gson.annotations.SerializedName;
import lombok.experimental.UtilityClass;
import org.imanity.framework.reflect.asm.AsmAnalyser;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import sun.misc.Unsafe;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@UtilityClass
public class Reflect {

    private static final Map<String, List<Field>> CACHED_FIELDS = new ConcurrentHashMap<>();
    private static final Map<String, List<Method>> CACHE_METHODS = new ConcurrentHashMap<>();

    private static final Unsafe UNSAFE;
    private static final MethodHandles.Lookup LOOKUP;

    static {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            UNSAFE = (Unsafe) theUnsafe.get(null);
            UNSAFE.ensureClassInitialized(MethodHandles.Lookup.class);
            Field lookupField = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            Object lookupBase = UNSAFE.staticFieldBase(lookupField);
            long lookupOffset = UNSAFE.staticFieldOffset(lookupField);
            LOOKUP = (MethodHandles.Lookup) UNSAFE.getObject(lookupBase, lookupOffset);
        } catch (Throwable t) {
            throw new IllegalStateException("Unsafe not found");
        }
    }

    public static Unsafe getUnsafe() {
        return UNSAFE;
    }

    public static MethodHandles.Lookup lookup() {
        return LOOKUP;
    }

    public static void setField(Object src, Field field, Object value) {
        Preconditions.checkNotNull(field);
        try {
            MethodHandle methodHandle = lookup().unreflectSetter(field);
            if (Modifier.isStatic(field.getModifiers())) {
                methodHandle.invokeWithArguments(value);
            } else {
                methodHandle.bindTo(src).invokeWithArguments(value);
            }
        } catch (Throwable t) {
            getUnsafe().throwException(t);
        }
    }

    public static <T> T getField(Object src, Field field, Class<T> cast) {
        Object obj = getField(src, field);
        return obj == null ? null : (T) cast.cast(obj);
    }

    public static Object getField(Object src, Field field) {
        Preconditions.checkNotNull(field);
        try {
            MethodHandle methodHandle = lookup().unreflectGetter(field);
            if (Modifier.isStatic(field.getModifiers())) {
                return methodHandle.invokeWithArguments();
            } else {
                return methodHandle.bindTo(src).invokeWithArguments();
            }
        } catch (Throwable t) {
            getUnsafe().throwException(t);
            return null;
        }
    }


    public static List<Field> getDeclaredFields(Class<?> clazz) {
        return getDeclaredFields(clazz, 0, true);
    }

    public static List<Field> getDeclaredFields(String clazz, int excludeModifiers, boolean cache) {
        try {
            return getDeclaredFields(Class.forName(clazz), excludeModifiers, cache);
        } catch (ClassNotFoundException e) {
            return Collections.emptyList();
        }
    }

    public static List<Field> getDeclaredFields(Class<?> clazz, int excludeModifiers, boolean cache) {
        try {
            List<Field> fields;
            if ((fields = CACHED_FIELDS.get(clazz.getName())) != null) {
                return fields;
            }
            ClassReader classReader = new ClassReader(clazz.getResourceAsStream("/" + clazz.getName().replace('.', '/') + ".class"));
            AsmAnalyser analyser = new AsmAnalyser(new ClassWriter(ClassWriter.COMPUTE_MAXS), excludeModifiers);
            classReader.accept(analyser, ClassReader.SKIP_DEBUG);
            fields = analyser.getFields().stream().map(name -> {
                try {
                    return clazz.getDeclaredField(name);
                } catch (Throwable ignored) {
                    return null;
                }
            }).filter(Objects::nonNull).collect(Collectors.toList());
            if (cache) {
                CACHED_FIELDS.putIfAbsent(clazz.getName(), fields);
            }
            return fields;
        } catch (Exception | Error e) {
            try {
                List<Field> list = Arrays.stream(clazz.getDeclaredFields())
                        .filter(field -> (field.getModifiers() & excludeModifiers) == 0).collect(Collectors.toList());
                CACHED_FIELDS.putIfAbsent(clazz.getName(), list);
                return list;
            } catch (Error err) {
                return Collections.emptyList();
            }
        }
    }

    public static List<Method> getDeclaredMethods(Class<?> clazz) {
        return getDeclaredMethods(clazz, 0, true);
    }

    public static List<Method> getDeclaredMethods(String clazz, int excludeModifiers, boolean cache) {
        try {
            return getDeclaredMethods(Class.forName(clazz), excludeModifiers, cache);
        } catch (ClassNotFoundException e) {
            return Collections.emptyList();
        }
    }

    public static List<Method> getDeclaredMethods(Class<?> clazz, int excludeModifiers, boolean cache) {
        try {
            List<Method> methods;
            if ((methods = CACHE_METHODS.get(clazz.getName())) != null) {
                return methods;
            }
            ClassReader classReader = new ClassReader(clazz.getResourceAsStream("/" + clazz.getName().replace('.', '/') + ".class"));
            AsmAnalyser analyser = new AsmAnalyser(new ClassWriter(ClassWriter.COMPUTE_MAXS), excludeModifiers);
            classReader.accept(analyser, ClassReader.SKIP_DEBUG);
            methods = analyser.getMethods().stream().map(name -> {
                try {
                    return clazz.getDeclaredMethod(name);
                } catch (Throwable ignored) {
                    return null;
                }
            }).filter(Objects::nonNull).collect(Collectors.toList());
            if (cache) {
                CACHE_METHODS.putIfAbsent(clazz.getName(), methods);
            }
            return methods;
        } catch (Exception | Error e) {
            try {
                List<Method> list = Arrays.stream(clazz.getDeclaredMethods())
                        .filter(field -> (field.getModifiers() & excludeModifiers) == 0).collect(Collectors.toList());
                CACHE_METHODS.putIfAbsent(clazz.getName(), list);
                return list;
            } catch (Error err) {
                return Collections.emptyList();
            }
        }
    }

    public static Optional<Class<?>> getCallerClass(int depth) {
        return Optional.ofNullable(CallerClass.impl.getCallerClass(depth + 1));
    }

    public static Class<?> getCallerClassNotOptional(int depth) {
        return CallerClass.impl.getCallerClass(depth);
    }

    public static String getSerializedName(Field field) {
        return field.isAnnotationPresent(SerializedName.class) ? field.getAnnotation(SerializedName.class).value() : field.getName();
    }

    public static Optional<Field> getFieldBySerializedName(Class<?> clazz, String name) {
        for (Field field : Reflect.getDeclaredFields(clazz, 0, false)) {
            if (field.isAnnotationPresent(SerializedName.class)) {
                if (field.getAnnotation(SerializedName.class).value().equals(name)) {
                    return Optional.of(field);
                } else if (field.getName().equals(name)) {
                    return Optional.of(field);
                }
            }
        }
        return Optional.empty();
    }

    public static Field setAccessible(Field field) throws ReflectiveOperationException {
        if (!field.isAccessible()) {
            field.setAccessible(true);
        }
        int modifiers = field.getModifiers();
        if (!Modifier.isFinal(modifiers)) {
            return field;
        }
        try {
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(field, modifiers & ~Modifier.FINAL);
        } catch (NoSuchFieldException e) {
            if ("modifiers".equals(e.getMessage()) || (e.getCause() != null && e.getCause().getMessage() != null &&  e.getCause().getMessage().equals("modifiers"))) {
                // https://github.com/ViaVersion/ViaVersion/blob/e07c994ddc50e00b53b728d08ab044e66c35c30f/bungee/src/main/java/us/myles/ViaVersion/bungee/platform/BungeeViaInjector.java
                // Java 12 compatibility *this is fine*
                Method getDeclaredFields0 = Class.class.getDeclaredMethod("getDeclaredFields0", boolean.class);
                getDeclaredFields0.setAccessible(true);
                Field[] fields = (Field[]) getDeclaredFields0.invoke(Field.class, false);
                for (Field classField : fields) {
                    if ("modifiers".equals(classField.getName())) {
                        classField.setAccessible(true);
                        classField.set(field, modifiers & ~Modifier.FINAL);
                        break;
                    }
                }
            } else {
                throw e;
            }
        }
        return field;
    }

    public static Method setAccessible(Method method) throws ReflectiveOperationException {
        if (method.isAccessible()) {
            return method;
        }

        method.setAccessible(true);
        return method;
    }

    public static Constructor setAccessible(Constructor constructor) throws ReflectiveOperationException {
        if (constructor.isAccessible()) {
            return constructor;
        }

        constructor.setAccessible(true);
        return constructor;
    }

    private static abstract class CallerClass {

        private static CallerClass impl;

        static {
//            try {
//                Class.forName("sun.reflect.Reflection");
//                impl = new ReflectionImpl();
//            } catch (ClassNotFoundException e) {
//                impl = new StackTraceImpl();
//            }
            impl = new StackTraceImpl();
        }

        abstract Class<?> getCallerClass(int i);

        /**
         * Removed on Java 11
         */
        private static class ReflectionImpl extends CallerClass {

            @SuppressWarnings({"deprecation", "restriction"})
            @Override
            Class<?> getCallerClass(int i) {
//                return Reflection.getCallerClass(i);
                return null;
            }
        }

        private static class StackTraceImpl extends CallerClass {

            @Override
            Class<?> getCallerClass(int i) {
                StackTraceElement[] elements = Thread.currentThread().getStackTrace();
                String className = elements[i].getClassName();
                try {
                    return Class.forName(className);
                } catch (ClassNotFoundException ignored) {
                }
                try {
                    return Class.forName(className);
                } catch (NullPointerException | ClassNotFoundException ignored) {
                }
                return null;
            }
        }
    }

}
