package indevo.exploration.crucible;

import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class YeetopultColourList {
    public static List<Color> colorList = new ArrayList<Color>(){{
        add(Color.decode("#FF0000")); // Red
        add(Color.decode("#00FF00")); // Lime
        add(Color.decode("#0000FF")); // Blue
        add(Color.decode("#FFFF00")); // Yellow
        add(Color.decode("#FF00FF")); // Magenta
        add(Color.decode("#00FFFF")); // Cyan
        add(Color.decode("#800000")); // Maroon
        add(Color.decode("#808000")); // Olive
        add(Color.decode("#008000")); // Green
        add(Color.decode("#800080")); // Purple
        add(Color.decode("#008080")); // Teal
        add(Color.decode("#000080")); // Navy
        add(Color.decode("#FFA500")); // Orange
        add(Color.decode("#A52A2A")); // Brown
        add(Color.decode("#DEB887")); // Burlywood
        add(Color.decode("#5F9EA0")); // Cadet Blue
        add(Color.decode("#7FFF00")); // Chartreuse
        add(Color.decode("#D2691E")); // Chocolate
        add(Color.decode("#FF7F50")); // Coral
        add(Color.decode("#6495ED")); // Cornflower Blue
        add(Color.decode("#DC143C")); // Crimson
        add(Color.decode("#00CED1")); // Dark Turquoise
        add(Color.decode("#9400D3")); // Dark Violet
        add(Color.decode("#FF1493")); // Deep Pink
        add(Color.decode("#00BFFF")); // Deep Sky Blue
        add(Color.decode("#696969")); // Dim Gray
        add(Color.decode("#1E90FF")); // Dodger Blue
        add(Color.decode("#B22222")); // Firebrick
        add(Color.decode("#FFFAF0")); // Floral White
        add(Color.decode("#228B22")); // Forest Green
        add(Color.decode("#FF00FF")); // Fuchsia
        add(Color.decode("#DCDCDC")); // Gainsboro
        add(Color.decode("#FFD700")); // Gold
        add(Color.decode("#DAA520")); // Goldenrod
        add(Color.decode("#ADFF2F")); // Green Yellow
        add(Color.decode("#F0FFF0")); // Honeydew
        add(Color.decode("#FF69B4")); // Hot Pink
        add(Color.decode("#CD5C5C")); // Indian Red
        add(Color.decode("#4B0082")); // Indigo
        add(Color.decode("#FFFFF0")); // Ivory
    }};

    public static WeightedRandomPicker<Color> getWeightedRandomPicker(){
        WeightedRandomPicker<Color> picker = new WeightedRandomPicker<>();
        picker.addAll(colorList);
        return picker;
    }

    public static List<Color> getSortedColourList() {
        return sortColorsByRainbow(colorList);
    }

    public static List<Color> sortColorsByRainbow(List<Color> colorList) {
        Collections.sort(colorList, new Comparator<Color>() {
            @Override
            public int compare(Color c1, Color c2) {
                float[] hsbValues1 = Color.RGBtoHSB(c1.getRed(), c1.getGreen(), c1.getBlue(), null);
                float[] hsbValues2 = Color.RGBtoHSB(c2.getRed(), c2.getGreen(), c2.getBlue(), null);

                float hue1 = hsbValues1[0];
                float hue2 = hsbValues2[0];

                // Compare hue
                if (hue1 < hue2) {
                    return -1;
                } else if (hue1 > hue2) {
                    return 1;
                } else {
                    // If hues are the same, compare by saturation
                    float saturation1 = hsbValues1[1];
                    float saturation2 = hsbValues2[1];
                    if (saturation1 < saturation2) {
                        return -1;
                    } else if (saturation1 > saturation2) {
                        return 1;
                    } else {
                        // If saturation is also the same, compare by brightness
                        float brightness1 = hsbValues1[2];
                        float brightness2 = hsbValues2[2];
                        if (brightness1 < brightness2) {
                            return -1;
                        } else if (brightness1 > brightness2) {
                            return 1;
                        } else {
                            // If all HSB values are the same, compare by alpha
                            int alpha1 = c1.getAlpha();
                            int alpha2 = c2.getAlpha();
                            if (alpha1 < alpha2) {
                                return -1;
                            } else if (alpha1 > alpha2) {
                                return 1;
                            } else {
                                return 0;
                            }
                        }
                    }
                }
            }
        });

        return colorList;
    }
}
