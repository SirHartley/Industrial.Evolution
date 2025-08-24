package indevo.utils.helper

import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

/**
 * Stolen from Lukas04 and multiple methods provided by starficz - thank you!
 */

object ReflectionUtils {

    private val fieldClass = Class.forName("java.lang.reflect.Field", false, Class::class.java.classLoader)
    private val setFieldHandle = MethodHandles.lookup().findVirtual(fieldClass, "set", MethodType.methodType(Void.TYPE, Any::class.java, Any::class.java))
    private val getFieldHandle = MethodHandles.lookup().findVirtual(fieldClass, "get", MethodType.methodType(Any::class.java, Any::class.java))
    private val getFieldNameHandle = MethodHandles.lookup().findVirtual(fieldClass, "getName", MethodType.methodType(String::class.java))
    private val setFieldAccessibleHandle = MethodHandles.lookup().findVirtual(fieldClass,"setAccessible", MethodType.methodType(Void.TYPE, Boolean::class.javaPrimitiveType))

    private val methodClass = Class.forName("java.lang.reflect.Method", false, Class::class.java.classLoader)
    private val getMethodNameHandle = MethodHandles.lookup().findVirtual(methodClass, "getName", MethodType.methodType(String::class.java))
    private val invokeMethodHandle = MethodHandles.lookup().findVirtual(methodClass, "invoke", MethodType.methodType(Any::class.java, Any::class.java, Array<Any>::class.java))
    private val getFieldTypeHandle = MethodHandles.lookup().findVirtual(fieldClass, "getType", MethodType.methodType(Class::class.java))

    /**
     * Any.set(name, value) -> ReflectionUtils.set(Any, name, value) with jvmstatic
     */
    @JvmStatic
    internal fun Any.set(name: String? = null, value: Any?) {
        val valueType = value?.let{ it::class.javaPrimitiveType ?: it::class.java }
        val reflectedFields = this.getFieldsMatching(name, assignableToType=valueType)
        if (reflectedFields.isEmpty())
            throw IllegalArgumentException("Field: $name of type: $valueType not exist for class: $this")
        else if (reflectedFields.size > 1)
            throw IllegalArgumentException("Field: $name of type: $valueType is ambiguous for class: $this")
        else return reflectedFields[0].set(this, value)
    }

    @JvmStatic
    internal fun Any.getFieldsMatching(
        name: String? = null,
        type: Class<*>? = null,
        assignableFromType: Class<*>? = null,
        assignableToType: Class<*>? = null
    ): List<ReflectedField> {
        return this::class.java.getFieldsMatching(name, type, assignableFromType, assignableToType)
    }

    @JvmStatic
    internal fun Class<*>.getFieldsMatching(
        name: String? = null,
        type: Class<*>? = null,
        assignableFromType: Class<*>? = null,
        assignableToType: Class<*>? = null
    ): List<ReflectedField> {
        val fieldInstances = mutableSetOf<Any>() // Use a Set to avoid duplicates if fields are somehow redefined identically
        var currentClass: Class<*>? = this // Start with the class itself

        // Iterate up the superclass hierarchy
        while (currentClass != null && currentClass != Object::class.java) { // Stop before Object class
            fieldInstances.addAll(currentClass.declaredFields) // Add all fields declared in the current level
            currentClass = currentClass.superclass // Move up to the parent
        }

        return fieldInstances.filter { field ->
            val fieldType = if (type != null || assignableToType != null || assignableFromType != null){
                getFieldTypeHandle.invoke(field) as Class<*>
            } else null

            val nameMatches = name?.let { getFieldNameHandle.invoke(field) == it } != false
            val typeMatches = type?.let { fieldType!! == it } != false
            val assignableToTypeMatches = assignableToType?.let { fieldType!!.isAssignableFrom(it) } != false
            val assignableFromTypeMatches = assignableFromType?.isAssignableFrom(fieldType!!) != false

            // filter out object fields that arnt specifically matched for
            val objectFilter = fieldType?.let { it != Object::class.java || name != null || type != null } != false

            nameMatches && typeMatches && assignableToTypeMatches && assignableFromTypeMatches && objectFilter
        }.map { ReflectedField(it) }
    }

    /**
     * includes superclasses, 5 levels deep.
     */
    fun setWithSuper(fieldName: String, instanceToModify: Any, newValue: Any?, limit: Int, clazz: Class<*>? = null)
    {
        if (limit < 0) return

        var field: Any? = null
        var claz = clazz
        if (claz == null) claz = instanceToModify.javaClass

        try {  field = claz.getField(fieldName) } catch (e: Throwable) {
            try {  field = claz.getDeclaredField(fieldName) } catch (e: Throwable) { }
        }
        if (field == null) {
            setWithSuper(fieldName, instanceToModify, newValue, limit-1, claz.superclass)
            return
        }

        setFieldAccessibleHandle.invoke(field, true)
        setFieldHandle.invoke(field, instanceToModify, newValue)
    }

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

    fun invokeProtectedNoArgs(methodName: String, instance: Any, clazz: Class<*>? = null): Any? {
        var method: Any? = null
        var claz = clazz ?: instance.javaClass

        try {
            val methods = claz.declaredMethods
            for (m in methods) {
                if (getMethodNameHandle.invoke(m) == methodName) {
                    method = m
                    break
                }
            }
        } catch (e: Throwable) { }

        if (method == null) return null

        setFieldAccessibleHandle.invoke(method, true)
        return invokeMethodHandle.invoke(method, instance, emptyArray<Any>())
    }

    class ReflectedMethod(private val method: Any) {
        fun invoke(instance: Any?, vararg arguments: Any?): Any? = invokeMethodHandle.invoke(method, instance, arguments)
    }

    fun getFieldsOfType(instance: Any, clazz: Class<*>): List<String> {
        val instancesOfMethods: Array<out Any> = instance.javaClass.getDeclaredFields()

        return instancesOfMethods.filter { getFieldTypeHandle.invoke(it) == clazz }
            .map { getFieldNameHandle.invoke(it) as String }
    }

    fun findFieldsOfType(instance: Any, clazz: Class<*>): List<ReflectedField> {
        val instancesOfFields: Array<out Any> = instance.javaClass.declaredFields

        return instancesOfFields.map { fieldObj -> fieldObj to getFieldTypeHandle.invoke(fieldObj) }
            .filter { (fieldObj, fieldClass) ->
                fieldClass == clazz
            }.map { (fieldObj, fieldClass) -> ReflectedField(fieldObj) }
    }

    class ReflectedField(val field: Any) {
        fun get(instance: Any?): Any? {
            setFieldAccessibleHandle.invoke(field, true)
            return getFieldHandle.invoke(field, instance)
        }

        fun set(instance: Any?, value: Any?) {
            setFieldHandle.invoke(field, instance, value)
        }
    }
}