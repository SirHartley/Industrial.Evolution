import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.input.InputEventClass;
import com.fs.starfarer.api.input.InputEventType;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Test-only helpers shared by native smoke probes. The runner puts this class on the game's classpath, outside
 * the mod classloader's security gate, so it does file access and reflection on the probe's behalf. The probe is
 * mod code behind the gate, so every public signature here uses only types the gate allows: no java.io.File, no
 * java.lang.reflect. Coordinates are UI units with the origin at the bottom left, like PositionAPI.
 * Nothing here catches an exception and carries on: every failure reaches run() and fails the smoke.
 */
public final class NativeSmokeSupport {
    private static final List<String> failures = new ArrayList<>();

    private NativeSmokeSupport() {
    }

    /** Output directory passed by the runner. */
    public static String output() {
        return System.getProperty("native.smoke.output");
    }

    public static float width() {
        return Global.getSettings().getScreenWidth();
    }

    public static float height() {
        return Global.getSettings().getScreenHeight();
    }

    /**
     * Runs the probe body and exits the isolated JVM. Prints NATIVE_SMOKE COMPLETE and exits 0 only when nothing
     * threw and every expect() held; otherwise prints NATIVE_SMOKE FAILED, saves failure.png and exits 1.
     */
    public static void run(ThrowingRunnable body) {
        int status = 1;
        try {
            body.run();
            if (failures.isEmpty()) {
                status = 0;
            } else {
                System.out.println("NATIVE_SMOKE FAILED " + failures.size() + " expectation(s): " + failures);
            }
        } catch (Throwable failure) {
            System.out.println("NATIVE_SMOKE FAILED " + failure);
            failure.printStackTrace();
            try {
                screenshot("failure.png");
            } catch (Throwable screenshot) {
                log("failure screenshot unavailable: " + screenshot);
            }
        }
        if (status == 0) System.out.println("NATIVE_SMOKE COMPLETE");
        System.out.flush();
        System.err.flush();
        System.exit(status);
    }

    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    public static void log(String message) {
        System.out.println("NATIVE_SMOKE " + message);
    }

    /** Hard assertion: stops the probe at the first failure. Use it when later steps depend on this one. */
    public static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        log("ok: " + message);
    }

    /** Soft assertion: records the failure and carries on, so one game boot reports every failure. */
    public static boolean expect(boolean condition, String message) {
        if (condition) {
            log("ok: " + message);
        } else {
            failures.add(message);
            log("FAIL: " + message);
        }
        return condition;
    }

    /** Clears the back buffer and sets a UI-unit orthographic projection. */
    public static void beginFrame() {
        GL11.glViewport(0, 0, (int) (Display.getWidth() * Display.getPixelScaleFactor()),
                (int) (Display.getHeight() * Display.getPixelScaleFactor()));
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glColorMask(true, true, true, true);
        GL11.glClearColor(0.02f, 0.04f, 0.06f, 1f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0, width(), 0, height(), -4000, 4000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    /** Advances a native panel for the given number of 60 fps frames. */
    public static void advance(UIComponentAPI component, int frames) {
        for (int i = 0; i < frames; i++) component.advance(1f / 60f);
    }

    /** Renders one frame of the given components in order. */
    public static void render(UIComponentAPI... components) {
        beginFrame();
        for (UIComponentAPI component : components) component.render(1f);
    }

    /** RGBA bytes of a UI-unit rectangle in the back buffer. */
    public static byte[] region(float x, float y, float w, float h) {
        float scaleX = Display.getWidth() * Display.getPixelScaleFactor() / width();
        float scaleY = Display.getHeight() * Display.getPixelScaleFactor() / height();
        int pw = Math.max(1, Math.round(w * scaleX)), ph = Math.max(1, Math.round(h * scaleY));
        ByteBuffer pixels = BufferUtils.createByteBuffer(pw * ph * 4);
        GL11.glReadBuffer(GL11.GL_BACK);
        GL11.glReadPixels(Math.round(x * scaleX), Math.round(y * scaleY), pw, ph,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        byte[] bytes = new byte[pixels.remaining()];
        pixels.get(bytes);
        return bytes;
    }

    /** Pixels of a component's on-screen rectangle. */
    public static byte[] region(UIComponentAPI component) {
        return region(component.getPosition().getX(), component.getPosition().getY(),
                component.getPosition().getWidth(), component.getPosition().getHeight());
    }

    public static int changedPixels(byte[] first, byte[] second) {
        check(first.length == second.length, "framebuffer regions have equal size");
        int changed = 0;
        for (int i = 0; i < first.length; i += 4) {
            if (first[i] != second[i] || first[i + 1] != second[i + 1] || first[i + 2] != second[i + 2]) changed++;
        }
        return changed;
    }

    /** Pixels that differ from the clear colour; a cheap "something rendered here" measure. */
    public static int litPixels(byte[] rgba) {
        int lit = 0;
        for (int i = 0; i < rgba.length; i += 4) {
            if ((rgba[i] & 255) > 20 || (rgba[i + 1] & 255) > 25 || (rgba[i + 2] & 255) > 30) lit++;
        }
        return lit;
    }

    /** Writes the whole back buffer to OUTPUT/name; ".png" is added when missing. */
    public static void screenshot(String name) throws Exception {
        if (!name.endsWith(".png")) name += ".png";
        int pw = Math.round(Display.getWidth() * Display.getPixelScaleFactor());
        int ph = Math.round(Display.getHeight() * Display.getPixelScaleFactor());
        byte[] pixels = region(0, 0, width(), height());
        BufferedImage image = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < ph; y++) {
            for (int x = 0; x < pw; x++) {
                int offset = (x + y * pw) * 4;
                int rgb = ((pixels[offset] & 255) << 16) | ((pixels[offset + 1] & 255) << 8)
                        | (pixels[offset + 2] & 255);
                image.setRGB(x, ph - 1 - y, rgb);
            }
        }
        check(ImageIO.write(image, "png", new File(output(), name)), "PNG written: " + name);
        log("screenshot=" + name);
    }

    /** Key-down event usable with processInput-style plugin entry points. */
    public static InputEventAPI key(int code) {
        return event(InputEventClass.KEYBOARD_EVENT, InputEventType.KEY_DOWN, code, 0, 0);
    }

    /**
     * Mouse event at (x, y) in UI units: a left-button down when click, else a wheel event with this value
     * (positive = up). Move the OS cursor first with Mouse.setCursorPosition(x * getScreenScaleMult(), ...),
     * because plugins usually read the position from SettingsAPI.getMouseX/Y.
     */
    public static InputEventAPI mouse(float x, float y, boolean click, int wheel) {
        return event(InputEventClass.MOUSE_EVENT, click ? InputEventType.MOUSE_DOWN : InputEventType.MOUSE_SCROLL,
                click ? 0 : wheel, (int) x, (int) y);
    }

    // anything not answered here gets false, 0 or null, never an exception a plugin guard could hide
    private static InputEventAPI event(InputEventClass kind, InputEventType type, int value, int x, int y) {
        boolean[] consumed = {false};
        boolean keyboard = kind == InputEventClass.KEYBOARD_EVENT;
        boolean click = type == InputEventType.MOUSE_DOWN;
        return (InputEventAPI) Proxy.newProxyInstance(InputEventAPI.class.getClassLoader(),
                new Class<?>[] {InputEventAPI.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "isConsumed": return consumed[0];
                        case "consume": consumed[0] = true; return null;
                        case "getEventClass": return kind;
                        case "getEventType": return type;
                        case "getEventValue": return value;
                        case "getX": return x;
                        case "getY": return y;
                        case "isKeyboardEvent": return keyboard;
                        case "isKeyDownEvent": return type == InputEventType.KEY_DOWN;
                        case "isMouseEvent": return !keyboard;
                        case "isLMBDownEvent": case "isMouseDownEvent": case "isLMBEvent": return click;
                        case "isMouseScrollEvent": return type == InputEventType.MOUSE_SCROLL;
                        case "isUnmodified": return true;
                        case "getEventChar": return '\0';
                        case "toString": return "NativeSmoke " + type + " value=" + value + " at " + x + "," + y;
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == args[0];
                        default:
                            if (method.getReturnType() == boolean.class) return false;
                            if (method.getReturnType() == int.class) return 0;
                            return null;
                    }
                });
    }

    /** True when the type or a superclass declares a method with this name and arity. */
    public static boolean hasMethod(Class<?> type, String name, int count) {
        return method(type, name, count) != null;
    }

    /**
     * Calls the method with this name whose parameters fit the arguments, searching superclasses; private ones too.
     * Among overloads with the same argument count the fitting one wins; when none fits by type, the first one is tried.
     */
    public static Object invoke(Object receiver, String name, Object... args) throws Exception {
        Method method = method(receiver.getClass(), name, args);
        if (method == null) throw new NoSuchMethodException(receiver.getClass().getName() + "." + name);
        return method.invoke(receiver, args);
    }

    public static Object invokeStatic(Class<?> type, String name, Object... args) throws Exception {
        Method method = method(type, name, args);
        if (method == null) throw new NoSuchMethodException(type.getName() + "." + name);
        return method.invoke(null, args);
    }

    public static Object getField(Object receiver, String name) throws Exception {
        return field(receiver.getClass(), name).get(receiver);
    }

    public static void setField(Object receiver, String name, Object value) throws Exception {
        field(receiver.getClass(), name).set(receiver, value);
    }

    /** Static field value, such as a readable size constant on an obfuscated class. */
    public static Object getStatic(Class<?> type, String name) throws Exception {
        return field(type, name).get(null);
    }

    /** Constructs a class by argument shape instead of by obfuscated parameter types. */
    public static Object construct(Class<?> type, Object... args) throws Exception {
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            if (!fits(parameters, args)) continue;
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        }
        throw new NoSuchMethodException("No constructor of " + type.getName() + " fits " + args.length + " arguments");
    }

    /** Parameter types of the type's only constructor with this arity; derive obfuscated types from it. */
    public static Class<?>[] constructorParameters(Class<?> type, int count) throws Exception {
        Constructor<?> found = null;
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (constructor.getParameterCount() != count) continue;
            if (found != null) throw new IllegalStateException(type.getName() + " has several " + count + "-argument constructors");
            found = constructor;
        }
        if (found == null) throw new NoSuchMethodException("No " + count + "-argument constructor on " + type.getName());
        return found.getParameterTypes();
    }

    /**
     * The only class directly in the package (for example "com.fs.starfarer.coreui") of the jar that holds anchor
     * that matches. Finds obfuscated classes by shape; fails when none or several match.
     */
    public static Class<?> findClass(Class<?> anchor, String packageName, Predicate<Class<?>> match) throws Exception {
        String prefix = packageName.replace('.', '/') + "/";
        Class<?> found = null;
        File jar = new File(anchor.getProtectionDomain().getCodeSource().getLocation().toURI());
        try (JarFile file = new JarFile(jar)) {
            for (Enumeration<JarEntry> entries = file.entries(); entries.hasMoreElements(); ) {
                String name = entries.nextElement().getName();
                if (!name.startsWith(prefix) || !name.endsWith(".class") || name.indexOf('/', prefix.length()) >= 0) continue;
                Class<?> type = Class.forName(name.substring(0, name.length() - 6).replace('/', '.'), false,
                        anchor.getClassLoader());
                if (!match.test(type)) continue;
                if (found != null) throw new IllegalStateException("Several classes match in " + packageName
                        + ": " + found.getName() + ", " + type.getName());
                found = type;
            }
        }
        if (found == null) throw new ClassNotFoundException("No class in " + packageName + " matches");
        return found;
    }

    /** Depth-first search through native panels using their getChildrenCopy accessor. */
    public static Object findComponent(Object root, Predicate<Object> match) throws Exception {
        if (root == null) return null;
        if (match.test(root)) return root;
        Method children = method(root.getClass(), "getChildrenCopy", 0);
        if (children == null) return null;
        for (Object child : (List<?>) children.invoke(root)) {
            Object found = findComponent(child, match);
            if (found != null) return found;
        }
        return null;
    }

    private static Method method(Class<?> type, String name, int count) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == count) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        return null;
    }

    private static Method method(Class<?> type, String name, Object[] args) {
        Method first = null;
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
                if (first == null) first = method;
                if (fits(method.getParameterTypes(), args)) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        if (first != null) first.setAccessible(true);
        return first;
    }

    private static boolean fits(Class<?>[] parameters, Object[] args) {
        if (parameters.length != args.length) return false;
        for (int i = 0; i < args.length; i++) {
            if (args[i] == null ? parameters[i].isPrimitive() : !wrap(parameters[i]).isInstance(args[i])) return false;
        }
        return true;
    }

    private static Field field(Class<?> type, String name) throws Exception {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getName().equals(name)) {
                    field.setAccessible(true);
                    return field;
                }
            }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == float.class) return Float.class;
        if (type == boolean.class) return Boolean.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        return Character.class;
    }
}
