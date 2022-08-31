package com.fs.starfarer.api.plugins;

import java.lang.reflect.Field;

public class ReflectionUtils {

    //https://github.com/qcwxezda/Starsector-Officer-Extension/blob/16f38dadffa27220e62137508af2142c04dec380/src/officerextension/ui/OfficerUIElement.java#L366

    public static Object getField(Object o, String fieldName) {
        if (o == null) return null;
        try {
            Field field = o.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(o);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void setField(Object o, String fieldName, Object to) {
        if (o == null) return;
        try {
            Field field = o.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(o, to);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
