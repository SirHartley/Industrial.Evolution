package indevo.utils.helper;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

// Copyright Starficz, Licensed under LGPL-3.0-only
// Credits to Lukas04 for his ReflectionUtils, Lyravega, Float, and Andylizi for the original idea.
// java port of the kotlin original, modified 2026-11-01

// the mod classloader blocks java.lang.reflect by name: members are held as Object and only touched through method handles
public final class StarReflectionUtils {

    private static final Class<?> fieldClass = loadClass("java.lang.reflect.Field");
    private static final MethodHandle setFieldHandle = getHandle(fieldClass, "set", void.class, Object.class, Object.class);
    private static final MethodHandle getFieldHandle = getHandle(fieldClass, "get", Object.class, Object.class);
    private static final MethodHandle getFieldTypeHandle = getHandle(fieldClass, "getType", Class.class);
    private static final MethodHandle getFieldNameHandle = getHandle(fieldClass, "getName", String.class);
    private static final MethodHandle setFieldAccessibleHandle = getHandle(fieldClass, "setAccessible", void.class, boolean.class);

    private static final Class<?> methodClass = loadClass("java.lang.reflect.Method");
    private static final MethodHandle getMethodNameHandle = getHandle(methodClass, "getName", String.class);
    private static final MethodHandle invokeMethodHandle = getHandle(methodClass, "invoke", Object.class, Object.class, Object[].class);
    private static final MethodHandle setMethodAccessibleHandle = getHandle(methodClass, "setAccessible", void.class, boolean.class);
    private static final MethodHandle getMethodReturnHandle = getHandle(methodClass, "getReturnType", Class.class);
    private static final MethodHandle getMethodParametersHandle = getHandle(methodClass, "getParameterTypes", Class[].class);

    private static final Class<?> constructorClass = loadClass("java.lang.reflect.Constructor");
    private static final MethodHandle getConstructorParametersHandle = getHandle(constructorClass, "getParameterTypes", Class[].class);
    private static final MethodHandle setConstructorAccessibleHandle = getHandle(constructorClass, "setAccessible", void.class, boolean.class);
    private static final MethodHandle invokeConstructorHandle = getHandle(constructorClass, "newInstance", Object.class, Object[].class);

    private static final Map<ReflectedFieldCacheKey, List<ReflectedField>> reflectedFieldsCache = new ConcurrentHashMap<>();
    private static final Map<ReflectedMethodCacheKey, List<ReflectedMethod>> reflectedMethodsCache = new ConcurrentHashMap<>();
    private static final Map<ReflectedFieldWithMethodCacheKey, List<ReflectedField>> reflectedFieldsWithMethodCache = new ConcurrentHashMap<>();
    private static final Map<ReflectedConstructorsCacheKey, List<ReflectedConstructor>> reflectedConstructorsCache = new ConcurrentHashMap<>();

    private static final Map<Class<?>, Class<?>> primitiveToWrapper = Map.of(
            boolean.class, Boolean.class,
            byte.class, Byte.class,
            char.class, Character.class,
            short.class, Short.class,
            int.class, Integer.class,
            long.class, Long.class,
            float.class, Float.class,
            double.class, Double.class,
            void.class, Void.class);

    private static final Map<Class<?>, Class<?>> wrapperToPrimitive = Map.of(
            Boolean.class, boolean.class,
            Byte.class, byte.class,
            Character.class, char.class,
            Short.class, short.class,
            Integer.class, int.class,
            Long.class, long.class,
            Float.class, float.class,
            Double.class, double.class,
            Void.class, void.class);

    // key: parameter type, value: primitive argument types that widen to it
    private static final Map<Class<?>, Set<Class<?>>> primitiveWidensFrom = Map.of(
            short.class, Set.of(byte.class),
            int.class, Set.of(byte.class, short.class, char.class),
            long.class, Set.of(byte.class, short.class, char.class, int.class),
            float.class, Set.of(byte.class, short.class, char.class, int.class, long.class),
            double.class, Set.of(byte.class, short.class, char.class, int.class, long.class, float.class));

    private StarReflectionUtils() {
    }

    public static Object get(Object instance) {
        return get(instance, null, null, false);
    }

    public static Object get(Object instance, String name) {
        return get(instance, name, null, false);
    }

    public static Object get(Object instance, String name, Class<?> type) {
        return get(instance, name, type, false);
    }

    // type: the field's type must be assignable to it, so an api interface finds a field holding its obfuscated implementation
    public static Object get(Object instance, String name, Class<?> type, boolean searchSuperclass) {
        List<ReflectedField> reflectedFields = fieldsMatching(instance.getClass(), name, null, type, null, searchSuperclass);
        String typeName = type != null ? type.getName() : "<any>";
        if (reflectedFields.isEmpty())
            throw new IllegalArgumentException("No field found for name: '" + anyName(name) + "' on class: " + instance.getClass().getName()
                    + " that is assignable to type: '" + typeName + "'.");
        if (reflectedFields.size() > 1)
            throw new IllegalArgumentException("Ambiguous fields with name: '" + anyName(name) + "' on class " + instance.getClass().getName()
                    + " assignable to type: '" + typeName + "'. Multiple fields match.");
        return reflectedFields.get(0).get(instance);
    }

    public static void set(Object instance, Object value) {
        set(instance, null, value, false);
    }

    public static void set(Object instance, String name, Object value) {
        set(instance, name, value, false);
    }

    // the field must accept the value's runtime type, with widening and boxing
    public static void set(Object instance, String name, Object value, boolean searchSuperclass) {
        Class<?> valueType = value != null ? primitiveOrClass(value) : null;
        List<ReflectedField> reflectedFields = fieldsMatching(instance.getClass(), name, null, null, valueType, searchSuperclass);
        String typeName = valueType != null ? valueType.getName() : "null";
        if (reflectedFields.isEmpty())
            throw new IllegalArgumentException("No field found for name: '" + anyName(name) + "' on class: " + instance.getClass().getName()
                    + " that accepts type: '" + typeName + "'.");
        if (reflectedFields.size() > 1)
            throw new IllegalArgumentException("Ambiguous fields with name: '" + anyName(name) + "' on class: " + instance.getClass().getName()
                    + " accepting type: '" + typeName + "'. Multiple fields match.");
        reflectedFields.get(0).set(instance, value);
    }

    public static List<ReflectedField> getFieldsMatching(Class<?> clazz) {
        return fieldsMatching(clazz, null, null, null, null, false);
    }

    public static List<ReflectedField> getFieldsMatching(Class<?> clazz, String name) {
        return fieldsMatching(clazz, name, null, null, null, false);
    }

    public static List<ReflectedField> getFieldsMatching(Class<?> clazz, String name, Class<?> type) {
        return fieldsMatching(clazz, name, type, null, null, false);
    }

    public static List<ReflectedField> getFieldsMatching(Class<?> clazz, String name, Class<?> type, Class<?> fieldAssignableTo) {
        return fieldsMatching(clazz, name, type, fieldAssignableTo, null, false);
    }

    public static List<ReflectedField> getFieldsMatching(Class<?> clazz, String name, Class<?> type, Class<?> fieldAssignableTo,
                                                         Class<?> fieldAccepts) {
        return fieldsMatching(clazz, name, type, fieldAssignableTo, fieldAccepts, false);
    }

    public static List<ReflectedField> getFieldsMatching(Class<?> clazz, String name, Class<?> type, Class<?> fieldAssignableTo,
                                                         Class<?> fieldAccepts, boolean searchSuperclass) {
        return fieldsMatching(clazz, name, type, fieldAssignableTo, fieldAccepts, searchSuperclass);
    }

    public static List<ReflectedField> getFieldsMatching(Object instance) {
        return fieldsMatching(instance.getClass(), null, null, null, null, false);
    }

    public static List<ReflectedField> getFieldsMatching(Object instance, String name) {
        return fieldsMatching(instance.getClass(), name, null, null, null, false);
    }

    public static List<ReflectedField> getFieldsMatching(Object instance, String name, Class<?> type) {
        return fieldsMatching(instance.getClass(), name, type, null, null, false);
    }

    public static List<ReflectedField> getFieldsMatching(Object instance, String name, Class<?> type, Class<?> fieldAssignableTo) {
        return fieldsMatching(instance.getClass(), name, type, fieldAssignableTo, null, false);
    }

    public static List<ReflectedField> getFieldsMatching(Object instance, String name, Class<?> type, Class<?> fieldAssignableTo,
                                                         Class<?> fieldAccepts) {
        return fieldsMatching(instance.getClass(), name, type, fieldAssignableTo, fieldAccepts, false);
    }

    public static List<ReflectedField> getFieldsMatching(Object instance, String name, Class<?> type, Class<?> fieldAssignableTo,
                                                         Class<?> fieldAccepts, boolean searchSuperclass) {
        return fieldsMatching(instance.getClass(), name, type, fieldAssignableTo, fieldAccepts, searchSuperclass);
    }

    public static List<ReflectedField> getFieldsWithMethodsMatching(Class<?> clazz) {
        return fieldsWithMethodsMatching(clazz, null, null, null, null, false);
    }

    public static List<ReflectedField> getFieldsWithMethodsMatching(Class<?> clazz, String methodName) {
        return fieldsWithMethodsMatching(clazz, methodName, null, null, null, false);
    }

    public static List<ReflectedField> getFieldsWithMethodsMatching(Class<?> clazz, String methodName, Class<?> methodReturnType) {
        return fieldsWithMethodsMatching(clazz, methodName, methodReturnType, null, null, false);
    }

    public static List<ReflectedField> getFieldsWithMethodsMatching(Class<?> clazz, String methodName, Class<?> methodReturnType,
                                                                    Integer numOfMethodParams) {
        return fieldsWithMethodsMatching(clazz, methodName, methodReturnType, numOfMethodParams, null, false);
    }

    public static List<ReflectedField> getFieldsWithMethodsMatching(Class<?> clazz, String methodName, Class<?> methodReturnType,
                                                                    Integer numOfMethodParams, Class<?>[] methodParameterTypes) {
        return fieldsWithMethodsMatching(clazz, methodName, methodReturnType, numOfMethodParams, methodParameterTypes, false);
    }

    public static List<ReflectedField> getFieldsWithMethodsMatching(Class<?> clazz, String methodName, Class<?> methodReturnType,
                                                                    Integer numOfMethodParams, Class<?>[] methodParameterTypes,
                                                                    boolean searchSuperclass) {
        return fieldsWithMethodsMatching(clazz, methodName, methodReturnType, numOfMethodParams, methodParameterTypes, searchSuperclass);
    }

    public static List<ReflectedField> getFieldsWithMethodsMatching(Object instance) {
        return fieldsWithMethodsMatching(instance.getClass(), null, null, null, null, false);
    }

    public static List<ReflectedField> getFieldsWithMethodsMatching(Object instance, String methodName) {
        return fieldsWithMethodsMatching(instance.getClass(), methodName, null, null, null, false);
    }

    public static List<ReflectedField> getFieldsWithMethodsMatching(Object instance, String methodName, Class<?> methodReturnType) {
        return fieldsWithMethodsMatching(instance.getClass(), methodName, methodReturnType, null, null, false);
    }

    public static List<ReflectedField> getFieldsWithMethodsMatching(Object instance, String methodName, Class<?> methodReturnType,
                                                                    Integer numOfMethodParams) {
        return fieldsWithMethodsMatching(instance.getClass(), methodName, methodReturnType, numOfMethodParams, null, false);
    }

    public static List<ReflectedField> getFieldsWithMethodsMatching(Object instance, String methodName, Class<?> methodReturnType,
                                                                    Integer numOfMethodParams, Class<?>[] methodParameterTypes) {
        return fieldsWithMethodsMatching(instance.getClass(), methodName, methodReturnType, numOfMethodParams, methodParameterTypes, false);
    }

    public static List<ReflectedField> getFieldsWithMethodsMatching(Object instance, String methodName, Class<?> methodReturnType,
                                                                    Integer numOfMethodParams, Class<?>[] methodParameterTypes,
                                                                    boolean searchSuperclass) {
        return fieldsWithMethodsMatching(instance.getClass(), methodName, methodReturnType, numOfMethodParams, methodParameterTypes,
                searchSuperclass);
    }

    public static Object invoke(Object instance, Object... args) {
        return invoke(instance, (String) null, args);
    }

    // the overload is picked from the runtime types of args, exactly one method must match
    public static Object invoke(Object instance, String name, Object... args) {
        Class<?>[] paramTypes = argumentTypes(args);
        List<ReflectedMethod> reflectedMethods = methodsMatching(instance.getClass(), name, null, null, paramTypes);
        if (reflectedMethods.isEmpty())
            throw new IllegalArgumentException("No method found for name: '" + name + "' on class: " + instance.getClass().getName()
                    + " with compatible parameter types derived from arguments: " + Arrays.toString(paramTypes));
        if (reflectedMethods.size() > 1)
            throw new IllegalArgumentException("Ambiguous method call for name: '" + name + "' on class: " + instance.getClass().getName()
                    + ". Multiple methods match parameter types derived from arguments: " + Arrays.toString(paramTypes));
        return reflectedMethods.get(0).invoke(instance, args);
    }

    public static Object invoke(Class<?> clazz, Object... args) {
        return invoke(clazz, (String) null, args);
    }

    public static Object invoke(Class<?> clazz, String name, Object... args) {
        Class<?>[] paramTypes = argumentTypes(args);
        List<ReflectedMethod> reflectedMethods = methodsMatching(clazz, name, null, null, paramTypes);
        if (reflectedMethods.isEmpty())
            throw new IllegalArgumentException("No method found for name '" + name + "' on class " + clazz.getName()
                    + " with compatible parameter types derived from arguments: " + Arrays.toString(paramTypes));
        if (reflectedMethods.size() > 1)
            throw new IllegalArgumentException("Ambiguous method call for name '" + name + "' on class " + clazz.getName()
                    + ". Multiple methods match parameter types derived from arguments: " + Arrays.toString(paramTypes));
        return reflectedMethods.get(0).invoke(null, args);
    }

    public static List<ReflectedMethod> getMethodsMatching(Class<?> clazz) {
        return methodsMatching(clazz, null, null, null, null);
    }

    public static List<ReflectedMethod> getMethodsMatching(Class<?> clazz, String name) {
        return methodsMatching(clazz, name, null, null, null);
    }

    public static List<ReflectedMethod> getMethodsMatching(Class<?> clazz, String name, Class<?> returnType) {
        return methodsMatching(clazz, name, returnType, null, null);
    }

    public static List<ReflectedMethod> getMethodsMatching(Class<?> clazz, String name, Class<?> returnType, Integer numOfParams) {
        return methodsMatching(clazz, name, returnType, numOfParams, null);
    }

    public static List<ReflectedMethod> getMethodsMatching(Class<?> clazz, String name, Class<?> returnType, Integer numOfParams,
                                                           Class<?>[] parameterTypes) {
        return methodsMatching(clazz, name, returnType, numOfParams, parameterTypes);
    }

    public static List<ReflectedMethod> getMethodsMatching(Object instance) {
        return methodsMatching(instance.getClass(), null, null, null, null);
    }

    public static List<ReflectedMethod> getMethodsMatching(Object instance, String name) {
        return methodsMatching(instance.getClass(), name, null, null, null);
    }

    public static List<ReflectedMethod> getMethodsMatching(Object instance, String name, Class<?> returnType) {
        return methodsMatching(instance.getClass(), name, returnType, null, null);
    }

    public static List<ReflectedMethod> getMethodsMatching(Object instance, String name, Class<?> returnType, Integer numOfParams) {
        return methodsMatching(instance.getClass(), name, returnType, numOfParams, null);
    }

    public static List<ReflectedMethod> getMethodsMatching(Object instance, String name, Class<?> returnType, Integer numOfParams,
                                                           Class<?>[] parameterTypes) {
        return methodsMatching(instance.getClass(), name, returnType, numOfParams, parameterTypes);
    }

    public static List<ReflectedConstructor> getConstructorsMatching(Class<?> clazz) {
        return constructorsMatching(clazz, null, null);
    }

    public static List<ReflectedConstructor> getConstructorsMatching(Class<?> clazz, Integer numOfParams) {
        return constructorsMatching(clazz, numOfParams, null);
    }

    public static List<ReflectedConstructor> getConstructorsMatching(Class<?> clazz, Integer numOfParams, Class<?>[] parameterTypes) {
        return constructorsMatching(clazz, numOfParams, parameterTypes);
    }

    private static List<ReflectedField> fieldsMatching(Class<?> clazz, String name, Class<?> type, Class<?> fieldAssignableTo,
                                                       Class<?> fieldAccepts, boolean searchSuperclass) {
        ReflectedFieldCacheKey cacheKey = new ReflectedFieldCacheKey(clazz, name, type, fieldAssignableTo, fieldAccepts, searchSuperclass);
        return getOrPut(reflectedFieldsCache, cacheKey, () -> {
            List<ReflectedField> result = new ArrayList<>();
            for (Object field : searchSuperclass ? getAllFields(clazz) : getDeclaredFields(clazz)) {
                if (name != null && !name.equals(fieldName(field))) continue;

                if (type != null || fieldAccepts != null || fieldAssignableTo != null) {
                    Class<?> fieldType = fieldType(field);
                    if (type != null && type != fieldType) continue;
                    if (fieldAccepts != null && !isParameterCompatible(fieldType, fieldAccepts)) continue;
                    if (fieldAssignableTo != null && !fieldAssignableTo.isAssignableFrom(fieldType)) continue;

                    // object fields only match when asked for by name or as object
                    if (fieldType == Object.class && name == null && type != Object.class
                            && fieldAssignableTo != Object.class && fieldAccepts != Object.class) continue;
                }

                result.add(new ReflectedField(field));
            }
            return result;
        });
    }

    // fields whose type has at least one method matching the signature
    private static List<ReflectedField> fieldsWithMethodsMatching(Class<?> clazz, String methodName, Class<?> methodReturnType,
                                                                  Integer numOfMethodParams, Class<?>[] methodParameterTypes,
                                                                  boolean searchSuperclass) {
        ReflectedFieldWithMethodCacheKey cacheKey = new ReflectedFieldWithMethodCacheKey(clazz, methodName, methodReturnType,
                numOfMethodParams, methodParameterTypes, searchSuperclass);
        return getOrPut(reflectedFieldsWithMethodCache, cacheKey, () -> {
            List<ReflectedField> result = new ArrayList<>();
            for (Object field : searchSuperclass ? getAllFields(clazz) : getDeclaredFields(clazz)) {
                Class<?> fieldType = fieldType(field);
                if (fieldType != null && !methodsMatching(fieldType, methodName, methodReturnType, numOfMethodParams,
                        methodParameterTypes).isEmpty()) result.add(new ReflectedField(field));
            }
            return result;
        });
    }

    // declared and public inherited methods; returnType: the method's return type must be assignable to it
    // a null in parameterTypes matches any non-primitive parameter
    private static List<ReflectedMethod> methodsMatching(Class<?> clazz, String name, Class<?> returnType, Integer numOfParams,
                                                         Class<?>[] parameterTypes) {
        ReflectedMethodCacheKey cacheKey = new ReflectedMethodCacheKey(clazz, name, returnType, numOfParams, parameterTypes);
        return getOrPut(reflectedMethodsCache, cacheKey, () -> {
            Object[] declaredMethods = clazz.getDeclaredMethods();
            Object[] publicMethods = clazz.getMethods();
            Set<Object> accessibleMethods = new LinkedHashSet<>();
            Collections.addAll(accessibleMethods, declaredMethods);
            Collections.addAll(accessibleMethods, publicMethods);

            List<ReflectedMethod> result = new ArrayList<>();
            for (Object method : accessibleMethods) {
                if (name != null && !name.equals(methodName(method))) continue;
                if (returnType != null && !returnType.isAssignableFrom(methodReturnType(method))) continue;

                if (numOfParams != null || parameterTypes != null) {
                    Class<?>[] actualParamTypes = methodParameterTypes(method);
                    if (numOfParams != null && numOfParams != actualParamTypes.length) continue;
                    if (parameterTypes != null && !parametersCompatible(actualParamTypes, parameterTypes)) continue;
                }

                result.add(new ReflectedMethod(method));
            }
            return result;
        });
    }

    private static List<ReflectedConstructor> constructorsMatching(Class<?> clazz, Integer numOfParams, Class<?>[] parameterTypes) {
        ReflectedConstructorsCacheKey cacheKey = new ReflectedConstructorsCacheKey(clazz, numOfParams, parameterTypes);
        return getOrPut(reflectedConstructorsCache, cacheKey, () -> {
            Object[] constructors = clazz.getDeclaredConstructors();
            List<ReflectedConstructor> result = new ArrayList<>();
            for (Object constructor : constructors) {
                Class<?>[] actualParamTypes = constructorParameterTypes(constructor);
                if (numOfParams != null && numOfParams != actualParamTypes.length) continue;
                if (parameterTypes != null && !parametersCompatible(actualParamTypes, parameterTypes)) continue;
                result.add(new ReflectedConstructor(constructor));
            }
            return result;
        });
    }

    private static boolean parametersCompatible(Class<?>[] actualParamTypes, Class<?>[] parameterTypes) {
        if (parameterTypes.length != actualParamTypes.length) return false;
        for (int i = 0; i < parameterTypes.length; i++) {
            if (!isParameterCompatible(actualParamTypes[i], parameterTypes[i])) return false;
        }
        return true;
    }

    // jls 5.3 invocation conversion: widening, boxing and unboxing; a null callerArgType matches any non-primitive
    private static boolean isParameterCompatible(Class<?> methodParamType, Class<?> callerArgType) {
        if (callerArgType == null) return !methodParamType.isPrimitive();
        if (methodParamType == callerArgType) return true;

        if (!callerArgType.isPrimitive() && !methodParamType.isPrimitive()) return methodParamType.isAssignableFrom(callerArgType);
        if (callerArgType.isPrimitive() && methodParamType.isPrimitive()) return widensFrom(methodParamType, callerArgType);

        if (callerArgType.isPrimitive()) {
            Class<?> boxedCallerType = primitiveToWrapper.get(callerArgType);
            return boxedCallerType != null && methodParamType.isAssignableFrom(boxedCallerType);
        }

        Class<?> unboxedCallerType = wrapperToPrimitive.get(callerArgType);
        return unboxedCallerType != null && (unboxedCallerType == methodParamType || widensFrom(methodParamType, unboxedCallerType));
    }

    private static boolean widensFrom(Class<?> methodParamType, Class<?> callerArgType) {
        Set<Class<?>> widensFrom = primitiveWidensFrom.get(methodParamType);
        return widensFrom != null && widensFrom.contains(callerArgType);
    }

    private static Set<Object> getDeclaredFields(Class<?> clazz) {
        Object[] fields = clazz.getDeclaredFields();
        return new LinkedHashSet<>(Arrays.asList(fields));
    }

    private static Set<Object> getAllFields(Class<?> clazz) {
        Set<Object> fields = new LinkedHashSet<>();
        Class<?> currentClazz = clazz;
        while (currentClazz != Object.class && currentClazz != null) {
            fields.addAll(getDeclaredFields(currentClazz));
            currentClazz = currentClazz.getSuperclass();
        }
        return fields;
    }

    private static Class<?>[] argumentTypes(Object[] args) {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) types[i] = args[i] != null ? primitiveOrClass(args[i]) : null;
        return types;
    }

    private static String anyName(String name) {
        return name != null ? name : "<any>";
    }

    private static <K, V> V getOrPut(Map<K, V> cache, K key, Supplier<V> value) {
        V cached = cache.get(key);
        if (cached != null) return cached;
        V computed = value.get();
        V previous = cache.putIfAbsent(key, computed);
        return previous != null ? previous : computed;
    }

    static Class<?> primitiveOrClass(Object value) {
        Class<?> clazz = value.getClass();
        Class<?> primitive = wrapperToPrimitive.get(clazz);
        return primitive != null ? primitive : clazz;
    }

    static String fieldName(Object field) {
        try {
            return (String) getFieldNameHandle.invoke(field);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static Class<?> fieldType(Object field) {
        try {
            return (Class<?>) getFieldTypeHandle.invoke(field);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static void setFieldAccessible(Object field) {
        try {
            setFieldAccessibleHandle.invoke(field, true);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static Object getFieldValue(Object field, Object instance) {
        try {
            return getFieldHandle.invoke(field, instance);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static void setFieldValue(Object field, Object instance, Object value) {
        try {
            setFieldHandle.invoke(field, instance, value);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static String methodName(Object method) {
        try {
            return (String) getMethodNameHandle.invoke(method);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static Object invokeMethod(Object method, Object instance, Object[] arguments) {
        try {
            return invokeMethodHandle.invoke(method, instance, arguments);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    private static Class<?> methodReturnType(Object method) {
        try {
            return (Class<?>) getMethodReturnHandle.invoke(method);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    private static Class<?>[] methodParameterTypes(Object method) {
        try {
            return (Class<?>[]) getMethodParametersHandle.invoke(method);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    private static Class<?>[] constructorParameterTypes(Object constructor) {
        try {
            return (Class<?>[]) getConstructorParametersHandle.invoke(constructor);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    // bootstrap loader, so the blocked name never goes through the mod classloader
    private static Class<?> loadClass(String name) {
        try {
            return Class.forName(name, false, Class.class.getClassLoader());
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    private static MethodHandle getHandle(Class<?> clazz, String name, Class<?> returnType, Class<?>... paramTypes) {
        try {
            return MethodHandles.lookup().findVirtual(clazz, name, MethodType.methodType(returnType, paramTypes));
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    // throws t unchanged without declaring it, reflection errors reach callers as thrown
    @SuppressWarnings("unchecked")
    static <T extends Throwable> RuntimeException rethrow(Throwable t) throws T {
        throw (T) t;
    }

    public static final class ReflectedField {

        private final Object field;
        private final Class<?> type;
        private final String name;

        public ReflectedField(Object field) {
            this.field = field;
            this.type = fieldType(field);
            this.name = fieldName(field);
        }

        public Class<?> getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        public Object get(Object instance) {
            setFieldAccessible(field);
            return getFieldValue(field, instance);
        }

        public void set(Object instance, Object value) {
            setFieldAccessible(field);
            setFieldValue(field, instance, value);
        }
    }

    public static final class ReflectedMethod {

        private final Object method;
        private final Class<?>[] parameterTypes;
        private final Class<?> returnType;
        private final String name;

        public ReflectedMethod(Object method) {
            this.method = method;
            this.parameterTypes = methodParameterTypes(method);
            this.returnType = methodReturnType(method);
            this.name = methodName(method);
        }

        public Class<?>[] getParameterTypes() {
            return parameterTypes;
        }

        public Class<?> getReturnType() {
            return returnType;
        }

        public String getName() {
            return name;
        }

        public Object invoke(Object instance, Object... arguments) {
            try {
                setMethodAccessibleHandle.invoke(method, true);
            } catch (Throwable t) {
                throw rethrow(t);
            }
            return invokeMethod(method, instance, arguments);
        }
    }

    public static final class ReflectedConstructor {

        private final Object constructor;
        private final Class<?>[] parameterTypes;

        public ReflectedConstructor(Object constructor) {
            this.constructor = constructor;
            this.parameterTypes = constructorParameterTypes(constructor);
        }

        public Class<?>[] getParameterTypes() {
            return parameterTypes;
        }

        public Object newInstance(Object... arguments) {
            try {
                setConstructorAccessibleHandle.invoke(constructor, true);
                return invokeConstructorHandle.invoke(constructor, arguments);
            } catch (Throwable t) {
                throw rethrow(t);
            }
        }
    }

    private static final class ReflectedFieldCacheKey {

        private final Class<?> targetClass;
        private final String fieldName;
        private final Class<?> fieldType;
        private final Class<?> fieldAssignableTo;
        private final Class<?> fieldAccepts;
        private final boolean searchSuperclass;

        ReflectedFieldCacheKey(Class<?> targetClass, String fieldName, Class<?> fieldType, Class<?> fieldAssignableTo,
                               Class<?> fieldAccepts, boolean searchSuperclass) {
            this.targetClass = targetClass;
            this.fieldName = fieldName;
            this.fieldType = fieldType;
            this.fieldAssignableTo = fieldAssignableTo;
            this.fieldAccepts = fieldAccepts;
            this.searchSuperclass = searchSuperclass;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ReflectedFieldCacheKey key)) return false;
            return targetClass == key.targetClass && Objects.equals(fieldName, key.fieldName) && fieldType == key.fieldType
                    && fieldAssignableTo == key.fieldAssignableTo && fieldAccepts == key.fieldAccepts
                    && searchSuperclass == key.searchSuperclass;
        }

        @Override
        public int hashCode() {
            return Objects.hash(targetClass, fieldName, fieldType, fieldAssignableTo, fieldAccepts, searchSuperclass);
        }
    }

    private static final class ReflectedMethodCacheKey {

        private final Class<?> targetClass;
        private final String fieldName;
        private final Class<?> returnType;
        private final Integer numOfParams;
        private final Class<?>[] parameterTypes;

        ReflectedMethodCacheKey(Class<?> targetClass, String fieldName, Class<?> returnType, Integer numOfParams,
                                Class<?>[] parameterTypes) {
            this.targetClass = targetClass;
            this.fieldName = fieldName;
            this.returnType = returnType;
            this.numOfParams = numOfParams;
            this.parameterTypes = parameterTypes;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ReflectedMethodCacheKey key)) return false;
            return Objects.equals(numOfParams, key.numOfParams) && targetClass == key.targetClass
                    && Objects.equals(fieldName, key.fieldName) && returnType == key.returnType
                    && Arrays.equals(parameterTypes, key.parameterTypes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(numOfParams, targetClass, fieldName, returnType, Arrays.hashCode(parameterTypes));
        }
    }

    private static final class ReflectedFieldWithMethodCacheKey {

        private final Class<?> targetClass;
        private final String methodName;
        private final Class<?> methodReturnType;
        private final Integer numOfMethodParams;
        private final Class<?>[] methodParameterTypes;
        private final boolean searchSuperclass;

        ReflectedFieldWithMethodCacheKey(Class<?> targetClass, String methodName, Class<?> methodReturnType,
                                         Integer numOfMethodParams, Class<?>[] methodParameterTypes, boolean searchSuperclass) {
            this.targetClass = targetClass;
            this.methodName = methodName;
            this.methodReturnType = methodReturnType;
            this.numOfMethodParams = numOfMethodParams;
            this.methodParameterTypes = methodParameterTypes;
            this.searchSuperclass = searchSuperclass;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ReflectedFieldWithMethodCacheKey key)) return false;
            return Objects.equals(numOfMethodParams, key.numOfMethodParams) && searchSuperclass == key.searchSuperclass
                    && targetClass == key.targetClass && Objects.equals(methodName, key.methodName)
                    && methodReturnType == key.methodReturnType && Arrays.equals(methodParameterTypes, key.methodParameterTypes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(numOfMethodParams, searchSuperclass, targetClass, methodName, methodReturnType,
                    Arrays.hashCode(methodParameterTypes));
        }
    }

    private static final class ReflectedConstructorsCacheKey {

        private final Class<?> targetClass;
        private final Integer numOfParams;
        private final Class<?>[] parameterTypes;

        ReflectedConstructorsCacheKey(Class<?> targetClass, Integer numOfParams, Class<?>[] parameterTypes) {
            this.targetClass = targetClass;
            this.numOfParams = numOfParams;
            this.parameterTypes = parameterTypes;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ReflectedConstructorsCacheKey key)) return false;
            return Objects.equals(numOfParams, key.numOfParams) && targetClass == key.targetClass
                    && Arrays.equals(parameterTypes, key.parameterTypes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(numOfParams, targetClass, Arrays.hashCode(parameterTypes));
        }
    }
}
