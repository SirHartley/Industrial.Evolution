import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Windows-only helper for run-native-smoke.ps1. Java keeps preferences in the registry there, so an
 * isolated run cannot redirect them with a home directory the way the Linux runner does.
 *
 * <p>Export (default Windows registry factory): {@code java SmokePrefs export out.xml} writes the
 * game's {@code com/fs/starfarer} node to XML.
 * <p>Run: {@code -Djava.util.prefs.PreferencesFactory=SmokePrefs -Dsmoke.prefs.xml=out.xml} serves
 * that XML from memory. Nothing is ever written back to the registry.
 */
public class SmokePrefs implements PreferencesFactory {
    private static Node root;

    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !args[0].equals("export")) {
            System.err.println("usage: SmokePrefs export OUT.xml");
            System.exit(2);
        }
        try (OutputStream out = new FileOutputStream(args[1])) {
            Preferences.userRoot().node("com/fs/starfarer").exportSubtree(out);
        }
    }

    @Override public synchronized Preferences userRoot() {
        if (root == null) {
            root = new Node(null, "");
            String file = System.getProperty("smoke.prefs.xml");
            if (file != null) load(root, new File(file));
        }
        return root;
    }

    @Override public Preferences systemRoot() { return new Node(null, ""); }

    /** exportSubtree writes the whole path from the root, so the file maps straight onto the root node. */
    private static void load(Preferences parent, File file) {
        try {
            Element top = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(file).getDocumentElement();
            Element xmlRoot = (Element) top.getElementsByTagName("root").item(0);
            fill(parent, xmlRoot);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read " + file, e);
        }
    }

    private static void fill(Preferences target, Element xml) {
        for (Element map : children(xml, "map"))
            for (Element entry : children(map, "entry"))
                target.put(entry.getAttribute("key"), entry.getAttribute("value"));
        for (Element child : children(xml, "node")) fill(target.node(child.getAttribute("name")), child);
    }

    private static java.util.List<Element> children(Element parent, String tag) {
        java.util.List<Element> found = new java.util.ArrayList<>();
        NodeList all = parent.getChildNodes();
        for (int i = 0; i < all.getLength(); i++)
            if (all.item(i) instanceof Element && ((Element) all.item(i)).getTagName().equals(tag))
                found.add((Element) all.item(i));
        return found;
    }

    private static final class Node extends AbstractPreferences {
        private final Map<String, String> values = new LinkedHashMap<>();
        private final Map<String, Node> kids = new LinkedHashMap<>();

        Node(Node parent, String name) { super(parent, name); }

        @Override protected void putSpi(String key, String value) { values.put(key, value); }
        @Override protected String getSpi(String key) { return values.get(key); }
        @Override protected void removeSpi(String key) { values.remove(key); }
        @Override protected void removeNodeSpi() { ((Node) parent()).kids.remove(name()); }
        @Override protected String[] keysSpi() { return values.keySet().toArray(new String[0]); }
        @Override protected String[] childrenNamesSpi() { return kids.keySet().toArray(new String[0]); }
        @Override protected AbstractPreferences childSpi(String name) {
            return kids.computeIfAbsent(name, n -> new Node(this, n));
        }
        @Override protected void syncSpi() { }
        @Override protected void flushSpi() { }
    }
}
