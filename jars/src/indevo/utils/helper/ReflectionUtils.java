package indevo.utils.helper;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static indevo.utils.helper.StarReflectionUtils.fieldName;
import static indevo.utils.helper.StarReflectionUtils.fieldType;
import static indevo.utils.helper.StarReflectionUtils.getFieldValue;
import static indevo.utils.helper.StarReflectionUtils.invokeMethod;
import static indevo.utils.helper.StarReflectionUtils.methodName;
import static indevo.utils.helper.StarReflectionUtils.primitiveOrClass;
import static indevo.utils.helper.StarReflectionUtils.rethrow;
import static indevo.utils.helper.StarReflectionUtils.setFieldAccessible;
import static indevo.utils.helper.StarReflectionUtils.setFieldValue;

// from Lukas04's ReflectionUtils, several methods by Starficz
// reflection objects stay Object typed, the mod classloader blocks java.lang.reflect by name
public final class ReflectionUtils {

    private ReflectionUtils() {
    }

    // walks up to limit superclasses; past Object it restarts at the instance class until limit runs out
    public static void setWithSuper(String fieldName, Object instanceToModify, Object newValue, int limit, Class<?> clazz) {
        if (limit < 0) return;

        Class<?> claz = clazz != null ? clazz : instanceToModify.getClass();
        Object field = findField(claz, fieldName);
        if (field == null) {
            setWithSuper(fieldName, instanceToModify, newValue, limit - 1, claz.getSuperclass());
            return;
        }

        setFieldAccessible(field);
        setFieldValue(field, instanceToModify, newValue);
    }

    public static void set(String fieldName, Object instanceToModify, Object newValue) {
        Object field = findField(instanceToModify.getClass(), fieldName);
        setFieldAccessible(field);
        setFieldValue(field, instanceToModify, newValue);
    }

    public static Object get(String fieldName, Object instanceToGetFrom) {
        Object field = findField(instanceToGetFrom.getClass(), fieldName);
        setFieldAccessible(field);
        return getFieldValue(field, instanceToGetFrom);
    }

    public static boolean hasMethodOfName(String name, Object instance, boolean contains) {
        Object[] methods = instance.getClass().getDeclaredMethods();
        for (Object method : methods) {
            String methodName = methodName(method);
            if (contains ? methodName.contains(name) : methodName.equals(name)) return true;
        }
        return false;
    }

    public static boolean hasVariableOfName(String name, Object instance) {
        Object[] fields = instance.getClass().getDeclaredFields();
        for (Object field : fields) {
            if (fieldName(field).equals(name)) return true;
        }
        return false;
    }

    // constructor is matched by the exact runtime types of the arguments, null arguments throw
    public static Object instantiate(Class<?> clazz, Object... arguments) {
        MethodType methodType = MethodType.methodType(void.class, argumentTypes(arguments));
        try {
            MethodHandle constructorHandle = MethodHandles.lookup().findConstructor(clazz, methodType);
            return constructorHandle.invokeWithArguments(Arrays.asList(arguments));
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static Object invoke(String methodName, Object instance, Object[] arguments, boolean declared) {
        Class<?> clazz = instance.getClass();
        Class<?>[] parameterTypes = argumentTypes(arguments).toArray(new Class<?>[0]);

        Object method;
        try {
            if (declared) method = clazz.getDeclaredMethod(methodName, parameterTypes);
            else method = clazz.getMethod(methodName, parameterTypes);
        } catch (Throwable t) {
            throw rethrow(t);
        }

        return invokeMethod(method, instance, arguments);
    }

    public static List<String> getFieldsOfType(Object instance, Class<?> clazz) {
        Object[] fields = instance.getClass().getDeclaredFields();
        List<String> names = new ArrayList<>();
        for (Object field : fields) {
            if (fieldType(field) == clazz) names.add(fieldName(field));
        }
        return names;
    }

    public static List<ReflectedField> findFieldsOfType(Object instance, Class<?> clazz) {
        Object[] fields = instance.getClass().getDeclaredFields();
        List<ReflectedField> result = new ArrayList<>();
        for (Object field : fields) {
            if (fieldType(field) == clazz) result.add(new ReflectedField(field));
        }
        return result;
    }

    private static Object findField(Class<?> clazz, String fieldName) {
        try {
            return clazz.getField(fieldName);
        } catch (Throwable e) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (Throwable e2) {
                return null;
            }
        }
    }

    private static List<Class<?>> argumentTypes(Object[] arguments) {
        List<Class<?>> types = new ArrayList<>();
        for (Object argument : arguments) types.add(primitiveOrClass(argument));
        return types;
    }

    public static final class ReflectedMethod {

        private final Object method;

        public ReflectedMethod(Object method) {
            this.method = method;
        }

        public Object invoke(Object instance, Object... arguments) {
            return invokeMethod(method, instance, arguments);
        }
    }

    public static final class ReflectedField {

        private final Object field;

        public ReflectedField(Object field) {
            this.field = field;
        }

        public Object getField() {
            return field;
        }

        public Object get(Object instance) {
            setFieldAccessible(field);
            return getFieldValue(field, instance);
        }

        public void set(Object instance, Object value) {
            setFieldValue(field, instance, value);
        }
    }
}
