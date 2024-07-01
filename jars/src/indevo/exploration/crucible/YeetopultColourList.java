package indevo.exploration.crucible;

import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.awt.*;
import java.util.ArrayList;
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
}
