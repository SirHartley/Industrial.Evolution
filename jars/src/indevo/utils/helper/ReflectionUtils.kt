package indevo.utils.helper

import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

object ReflectionUtils {

    private val fieldClass = Class.forName("java.lang.reflect.Field", false, Class::class.java.classLoader)
    private val setFieldHandle = MethodHandles.lookup().findVirtual(fieldClass, "set", MethodType.methodType(Void.TYPE, Any::class.java, Any::class.java))
    private val getFieldHandle = MethodHandles.lookup().findVirtual(fieldClass, "get", MethodType.methodType(Any::class.java, Any::class.java))
    private val getFieldNameHandle = MethodHandles.lookup().findVirtual(fieldClass, "getName", MethodType.methodType(String::class.java))
    private val setFieldAccessibleHandle = MethodHandles.lookup().findVirtual(fieldClass,"setAccessible", MethodType.methodType(Void.TYPE, Boolean::class.javaPrimitiveType))

    private val methodClass = Class.forName("java.lang.reflect.Method", false, Class::class.java.classLoader)
    private val getMethodNameHandle = MethodHandles.lookup().findVirtual(methodClass, "getName", MethodType.methodType(String::class.java))
    private val invokeMethodHandle = MethodHandles.lookup().findVirtual(methodClass, "invoke", MethodType.methodType(Any::class.java, Any::class.java, Array<Any>::class.java))

    fun set(fieldName: String, instanceToModify: Any, newValue: Any?)
    {
        var field: Any? = null
        try {  field = instanceToModify.javaClass.getField(fieldName) } catch (e: Throwable) {
            try {  field = instanceToModify.javaClass.getDeclaredField(fieldName) } catch (e: Throwable) { }
        }

        setFieldAccessibleHandle.invoke(field, true)
        setFieldHandle.invoke(field, instanceToModify, newValue)
    }

    fun get(fieldName: String, instanceToGetFrom: Any): Any? {
        var field: Any? = null
        try {  field = instanceToGetFrom.javaClass.getField(fieldName) } catch (e: Throwable) {
            try {  field = instanceToGetFrom.javaClass.getDeclaredField(fieldName) } catch (e: Throwable) { }
        }

        setFieldAccessibleHandle.invoke(field, true)
        return getFieldHandle.invoke(field, instanceToGetFrom)
    }

    fun hasMethodOfName(name: String, instance: Any, contains: Boolean = false) : Boolean {
        val instancesOfMethods: Array<out Any> = instance.javaClass.getDeclaredMethods()

        if (!contains) {
            return instancesOfMethods.any { getMethodNameHandle.invoke(it) == name }
        }
        else  {
            return instancesOfMethods.any { (getMethodNameHandle.invoke(it) as String).contains(name) }
        }
    }

    fun hasVariableOfName(name: String, instance: Any) : Boolean {

        val instancesOfFields: Array<out Any> = instance.javaClass.getDeclaredFields() as Array<out Any>
        return instancesOfFields.any { getFieldNameHandle.invoke(it) == name }
    }

    fun instantiate(clazz: Class<*>, vararg arguments: Any?) : Any?
    {
        val args = arguments.map { it!!::class.javaPrimitiveType ?: it!!::class.java }
        val methodType = MethodType.methodType(Void.TYPE, args)

        val constructorHandle = MethodHandles.lookup().findConstructor(clazz, methodType)
        val instance = constructorHandle.invokeWithArguments(arguments.toList())

        return instance
    }

    fun invoke(methodName: String, instance: Any, vararg arguments: Any?, declared: Boolean = false) : Any?
    {
        var method: Any? = null

        val clazz = instance.javaClass
        val args = arguments.map { it!!::class.javaPrimitiveType ?: it::class.java }
        val methodType = MethodType.methodType(Void.TYPE, args)

        if (!declared) {
            method = clazz.getMethod(methodName, *methodType.parameterArray()) as Any?
        }
        else  {
            method = clazz.getDeclaredMethod(methodName, *methodType.parameterArray()) as Any?
        }

        return invokeMethodHandle.invoke(method, instance, arguments)
    }
}