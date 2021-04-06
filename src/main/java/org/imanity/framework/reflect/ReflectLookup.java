package org.imanity.framework.reflect;

import lombok.Getter;
import org.reflections.Reflections;
import org.reflections.scanners.FieldAnnotationsScanner;
import org.reflections.scanners.MethodAnnotationsScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ReflectLookup {

    private final Map<Class<? extends Annotation>, Set<Class<?>>> cacheAnnotatedClasses;
    private final Map<Class<? extends Annotation>, ReflectFieldLookupCache> cacheAnnotatedFields;
    private final Map<Class<? extends Annotation>, ReflectMethodLookupCache> cacheAnnotatedMethods;

    private final Reflections reflections;

    public ReflectLookup(Collection<ClassLoader> classLoaders, Collection<String> packages) {
        this.reflections = new Reflections(
                classLoaders,
                packages,
                new TypeAnnotationsScanner(),
                new FieldAnnotationsScanner(),
                new MethodAnnotationsScanner(),
                new SubTypesScanner(false)
        );

        this.cacheAnnotatedClasses = new ConcurrentHashMap<>();
        this.cacheAnnotatedFields = new ConcurrentHashMap<>();
        this.cacheAnnotatedMethods = new ConcurrentHashMap<>();
    }

    public Set<Class<?>> findAnnotatedClasses(Class<? extends Annotation> annotation) {
        return this.cacheAnnotatedClasses.computeIfAbsent(annotation, aClass -> this.reflections.getTypesAnnotatedWith(annotation));
    }

    public Collection<Method> findAnnotatedStaticMethods(Class<? extends Annotation> annotation) {
        return this.cacheAnnotatedMethods
                .computeIfAbsent(annotation, aClass -> new ReflectMethodLookupCache(this.reflections.getMethodsAnnotatedWith(annotation)))
                .getStaticMethods();
    }

    public Collection<Method> findAnnotatedInstanceMethods(Class<? extends Annotation> annotation, Class<?> instanceClass) {
        return this.cacheAnnotatedMethods
                .computeIfAbsent(annotation, aClass -> new ReflectMethodLookupCache(this.reflections.getMethodsAnnotatedWith(annotation)))
                .getMethod(instanceClass);
    }

    public Collection<Field> findAnnotatedInstanceFields(Class<? extends Annotation> annotation, Class<?> instanceClass) {
        return this.cacheAnnotatedFields
                .computeIfAbsent(annotation, aClass -> new ReflectFieldLookupCache(this.reflections.getFieldsAnnotatedWith(annotation)))
                .getFields(instanceClass);
    }

    public Collection<Field> findAnnotatedStaticFields(Class<? extends Annotation> annotation) {
        return this.cacheAnnotatedFields
                .computeIfAbsent(annotation, aClass -> new ReflectFieldLookupCache(this.reflections.getFieldsAnnotatedWith(annotation)))
                .getStaticFields();
    }

    private static class ReflectFieldLookupCache {

        @Getter
        private final Collection<Field> staticFields;
        private final Map<Class<?>, Collection<Field>> classFields;

        private ReflectFieldLookupCache(Set<Field> fields) {
            this.staticFields = new ArrayList<>();
            this.classFields = new HashMap<>();

            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers())) {
                    this.staticFields.add(field);
                } else {
                    Collection<Field> collection;
                    Class<?> type = field.getDeclaringClass();

                    if (classFields.containsKey(type)) {
                        collection = classFields.get(type);
                    } else {
                        collection = new ArrayList<>();
                        classFields.put(type, collection);
                    }

                    collection.add(field);
                }
            }
        }

        public Collection<Field> getFields(Class<?> type) {
            return this.classFields.getOrDefault(type, Collections.emptyList());
        }

    }

    private static class ReflectMethodLookupCache {

        @Getter
        private final Collection<Method> staticMethods;
        private final Map<Class<?>, Collection<Method>> classMethods;

        private ReflectMethodLookupCache(Set<Method> fields) {
            this.staticMethods = new ArrayList<>();
            this.classMethods = new HashMap<>();

            for (Method method : fields) {
                if (Modifier.isStatic(method.getModifiers())) {
                    this.staticMethods.add(method);
                } else {
                    Collection<Method> collection;
                    Class<?> type = method.getDeclaringClass();

                    if (classMethods.containsKey(type)) {
                        collection = classMethods.get(type);
                    } else {
                        collection = new ArrayList<>();
                        classMethods.put(type, collection);
                    }

                    collection.add(method);
                }
            }
        }

        public Collection<Method> getMethod(Class<?> type) {
            return this.classMethods.getOrDefault(type, Collections.emptyList());
        }

    }

}
