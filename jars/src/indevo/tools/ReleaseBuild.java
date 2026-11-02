package indevo.tools;

import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ReleaseBuild {

    static final String MOD = "IndEvo";
    static final String FOLDER = "Industrial.Evolution";
    static final String RELEASES = "https://github.com/SirHartley/Industrial.Evolution/releases";

    static final String VERSION_FILE = MOD + ".version";
    static final String JAR = "jars/release/" + MOD + ".jar";
    static final String MODULE = MOD + ".iml";
    static final String SOURCE = "jars/src/";
    static final String TOOLS = SOURCE + "indevo/tools/";
    static final String TOOL = TOOLS + "ReleaseBuild.java";
    static final String PLUGIN = "indevo/utils/ModPlugin.class";
    static final String SETTINGS_CHECKS = "indevo.tools.SettingsChecks";
    static final List<String> PACKAGES = List.of("indevo/", "data/scripts/");
    static final String VERSION = "[1-9][0-9]*\\.(?:0|[1-9][0-9]*)\\.[a-z]";
    static final Pattern HEADING = Pattern.compile(
            "^(" + VERSION + ")(?:[ \\t]*-[ \\t]*(UNRELEASED|CURRENT PUBLIC VERSION))?[ \\t]*$");

    static final String LIBS = "lib";
    static final String JSON = "json.jar";

    static final Set<String> ROOT_FILES = Set.of("mod_info.json", VERSION_FILE, "Changelog", "_IndEvo.png",
            "LICENSE", "LICENSE.txt", "LICENSE.md", "NOTICE", "CREDITS", "CREDITS.txt", "CREDITS.md");
    static final List<String> REQUIRED = List.of("Changelog", "mod_info.json", VERSION_FILE,
            "data/config/settings.json", "data/config/LunaSettings.csv", "data/config/version/version_files.csv");
    static final Set<String> EXTRAS = Set.of("jars/source.url",
            "graphics/pets/alt/if you wanna switch em out just replace the image in the pets folder.txt");
    static final Map<String, Set<String>> ASSETS = Map.of(
            "data", Set.of("csv", "json", "shader", "ship", "wpn", "proj", "system", "variant", "skin",
                    "skill", "faction"),
            "graphics", Set.of("png", "jpg", "jpeg", "csv"),
            MOD, Set.of("ogg", "wav"));
    static final Set<String> SKIPPED_DIRS = Set.of("unused", "unedited", "tools", "docs");
    static final LocalDateTime ZIP_TIME = LocalDateTime.of(2000, 1, 1, 0, 0);

    record Notes(String version, boolean unreleased, String text) {

    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        if (args.length == 1 && (args[0].equals("build") || args[0].equals("compile"))) {
            build(root, args[0].equals("build"));
        } else if ((args.length == 1 || args.length == 2) && args[0].equals("package")) {
            pack(root, args.length == 2 ? args[1] : null);
        } else {
            throw new IllegalArgumentException("From the mod root, with JDK 17:\n"
                    + "  java " + TOOL + " compile\n"
                    + "  java " + TOOL + " build\n"
                    + "  java " + TOOL + " package [version]");
        }
    }

    static void build(Path root, boolean replace) throws Exception {
        require(Runtime.version().feature() == 17, "Build with JDK 17.");
        Files.createDirectories(root.resolve("out"));
        Path work = Files.createTempDirectory(root.resolve("out"), "release-build-");
        Path classes = Files.createDirectory(work.resolve("classes"));
        List<Path> dependencies = files(root.resolve(LIBS)).stream()
                .filter(path -> path.toString().endsWith(".jar")).toList();
        require(!dependencies.isEmpty(), "No jars in " + LIBS + "/.");
        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{root.resolve(LIBS).resolve(JSON).toUri().toURL()}, null)) {
            require(!RuntimeException.class.isAssignableFrom(loader.loadClass("org.json.JSONException")),
                    "Use the game's json.jar, not a newer Maven JSON jar.");
        }
        String classpath = String.join(File.pathSeparator,
                dependencies.stream().map(path -> path.toAbsolutePath().toString()).toList());
        List<Path> sources = sources(root);
        var compiler = ToolProvider.getSystemJavaCompiler();
        require(compiler != null, "A full JDK 17 is required.");
        try (var manager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            boolean passed = compiler.getTask(null, manager, null,
                    List.of("--release", "17", "-encoding", "UTF-8",
                            "-classpath", classpath, "-d", classes.toString()),
                    null, manager.getJavaFileObjectsFromPaths(sources)).call();
            require(passed, "Compilation failed; the release jar was not replaced.");
        }
        System.out.println("Settings: " + checkSettings(root, classes, dependencies, sources) + " checks passed.");
        Map<String, byte[]> entries = new TreeMap<>();
        for (Path path : files(classes)) {
            String name = relative(classes, path);
            if (runtimeEntry(name)) entries.put(name, Files.readAllBytes(path));
        }
        String hash = sourceHash(root);
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.putValue("Manifest-Version", "1.0");
        attributes.putValue("Implementation-Title", FOLDER);
        attributes.putValue("Source-SHA256", hash);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        manifest.write(bytes);
        entries.put("META-INF/MANIFEST.MF", bytes.toByteArray());
        Path candidate = work.resolve(MOD + ".jar");
        writeZip(candidate, entries);
        checkJar(candidate, hash, true);
        System.out.println("Compiled " + sources.size() + " sources into " + (entries.size() - 1) + " jar entries.");
        if (!replace) {
            System.out.println("Checked jar: " + candidate + " (release jar left as is)");
            return;
        }
        Files.createDirectories(root.resolve(JAR).getParent());
        Files.move(candidate, root.resolve(JAR), StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Release jar: " + root.resolve(JAR));
    }

    static int checkSettings(Path root, Path classes, List<Path> dependencies, List<Path> sources) throws Exception {
        List<URL> urls = new ArrayList<>(List.of(classes.toUri().toURL()));
        for (Path path : dependencies) urls.add(path.toUri().toURL());
        // the checks use the compiled Setting enum, this file runs as a single source file and cannot link it
        try (URLClassLoader loader = new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
            return (int) loader.loadClass(SETTINGS_CHECKS).getMethod("run", Path.class, List.class)
                    .invoke(null, root, sources);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) throw cause;
            throw e;
        }
    }

    static void pack(Path root, String version) throws Exception {
        Notes notes = notes(Files.readString(root.resolve("Changelog")), version);
        // jar is rebuilt on request only, so only a release needs a fresh one
        boolean fresh = checkJar(root.resolve(JAR), sourceHash(root), version != null);
        if (!fresh) System.out.println("Preview packages a stale release jar; rebuild it before a release.");
        List<String> tracked = trackedFiles(root);
        require(tracked.contains(JAR), "Commit " + JAR + " before packaging.");
        Map<String, byte[]> entries = new TreeMap<>();
        for (String name : tracked) {
            if (releaseFile(name)) entries.put(FOLDER + "/" + name, Files.readAllBytes(root.resolve(name)));
        }
        for (String name : REQUIRED) {
            require(entries.containsKey(FOLDER + "/" + name), "Missing tracked release file: " + name);
        }
        checkAssets(entries, new HashSet<>(tracked));
        entries.put(FOLDER + "/jars/" + MOD + ".jar", Files.readAllBytes(root.resolve(JAR)));
        entries.put(FOLDER + "/mod_info.json", stampInfo(Files.readString(root.resolve("mod_info.json")),
                notes.version()).getBytes(StandardCharsets.UTF_8));
        String versionFile = stampVersion(Files.readString(root.resolve(VERSION_FILE)), notes.version());
        entries.put(FOLDER + "/" + VERSION_FILE, versionFile.getBytes(StandardCharsets.UTF_8));
        Path output = root.resolve("out/release");
        Files.createDirectories(output);
        Files.writeString(output.resolve(VERSION_FILE), versionFile);
        Path zip = output.resolve(FOLDER + "-" + notes.version() + ".zip");
        writeZip(zip, entries);
        // text block keeps the changelog's plain-text layout on github
        Files.writeString(output.resolve("release-notes.md"), "```text\n" + notes.text() + "\n```\n");
        Files.writeString(output.resolve("version.txt"), notes.version() + "\n");
        System.out.println("Packaged " + entries.size() + " files: " + zip + " (" + Files.size(zip) + " bytes)");
    }

    static Notes notes(String changelog, String version) {
        if (version != null) require(version.matches(VERSION), "Expected a version such as 4.1.c, without a v prefix.");
        List<String> lines = changelog.replace("\r\n", "\n").lines().toList();
        List<Integer> headings = new ArrayList<>();
        Set<String> versions = new HashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            Matcher match = HEADING.matcher(lines.get(i));
            if (!match.matches()) continue;
            require(versions.add(match.group(1)), "Duplicate changelog version: " + match.group(1));
            headings.add(i);
        }
        require(!headings.isEmpty(), "Changelog has no version heading.");
        int start = headings.get(0);
        int end = headings.size() > 1 ? headings.get(1) : lines.size();
        Matcher heading = HEADING.matcher(lines.get(start));
        require(heading.matches(), "Invalid changelog heading.");
        String newest = heading.group(1);
        boolean unreleased = "UNRELEASED".equals(heading.group(2));
        require(version == null || version.equals(newest), "Version must match the newest Changelog section: " + newest);
        require(version == null || !unreleased, "Remove UNRELEASED from " + newest + " before a release.");
        String body = String.join("\n", lines.subList(start + 1, end)).strip();
        require(Pattern.compile("(?m)^(Added|Adjusted|Fixed|Removed) - .+").matcher(body).find(),
                "Changelog section " + newest + " has no changes.");
        require(!body.contains("```"), "Changelog must be plain text, without code fences.");
        return new Notes(newest, unreleased, lines.get(start) + "\n" + body + "\n");
    }

    static void checkAssets(Map<String, byte[]> entries, Set<String> tracked) {
        Pattern asset = Pattern.compile("(?:graphics|sounds|data|" + MOD + "/sounds)/[A-Za-z0-9_./-]+"
                + "\\.(?:png|jpg|jpeg|ogg|wav|shader|csv|json)\\b");
        for (var file : entries.entrySet()) {
            if (!file.getKey().startsWith(FOLDER + "/data/")) continue;
            Matcher references = asset.matcher(new String(file.getValue(), StandardCharsets.UTF_8));
            while (references.find()) {
                String path = references.group();
                // vanilla paths are not in the repo, so only ie named or tracked assets are checked
                if (!path.toLowerCase(Locale.ROOT).contains("indevo") && !tracked.contains(path)) continue;
                require(entries.containsKey(FOLDER + "/" + path),
                        file.getKey() + " references an unpackaged asset: " + path);
            }
        }
    }

    static String stampInfo(String text, String version) {
        require(version.matches(VERSION), "Invalid release version.");
        return replaceField(text, "version", "\"" + version + "\"");
    }

    static String stampVersion(String text, String version) {
        require(version.matches(VERSION), "Invalid release version.");
        String[] parts = version.split("\\.");
        text = replaceField(text, "major", parts[0]);
        text = replaceField(text, "minor", parts[1]);
        text = replaceField(text, "patch", "\"" + parts[2] + "\"");
        return replaceField(text, "directDownloadURL",
                "\"" + RELEASES + "/download/" + version + "/" + FOLDER + "-" + version + ".zip\"");
    }

    static String replaceField(String text, String key, String value) {
        Pattern field = Pattern.compile("(\"" + key + "\"\\s*:\\s*)(\"[^\"\\r\\n]*\"|[0-9]+)");
        Matcher matcher = field.matcher(text);
        require(matcher.find(), "Missing metadata field: " + key);
        require(!matcher.find(), "Duplicate metadata field: " + key);
        return field.matcher(text).replaceFirst("$1" + Matcher.quoteReplacement(value));
    }

    static boolean releaseFile(String name) {
        if (ROOT_FILES.contains(name) || EXTRAS.contains(name)) return true;
        String[] parts = name.split("/");
        if (parts.length < 2 || !ASSETS.containsKey(parts[0])) return false;
        for (String part : parts) {
            if (part.startsWith(".") || SKIPPED_DIRS.contains(part)) return false;
        }
        int dot = name.lastIndexOf('.');
        return dot >= 0 && ASSETS.get(parts[0]).contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    static boolean runtimeEntry(String name) {
        if (!name.endsWith(".class") || name.contains("..") || name.contains("\\")) return false;
        if (name.startsWith("indevo/tools/")) return false;
        return PACKAGES.stream().anyMatch(name::startsWith);
    }

    static List<String> excludes(String module) {
        Matcher folders = Pattern.compile("<excludeFolder url=\"file://\\$MODULE_DIR\\$/([^\"]+)\"").matcher(module);
        List<String> excluded = new ArrayList<>();
        while (folders.find()) excluded.add(folders.group(1) + "/");
        return excluded;
    }

    static boolean compiled(String name, List<String> excluded) {
        return name.startsWith(SOURCE) && name.endsWith(".java") && excluded.stream().noneMatch(name::startsWith);
    }

    static List<Path> sources(Path root) throws IOException {
        // same excludes as the intellij module, so ide and release builds compile the same files
        List<String> excluded = excludes(Files.readString(root.resolve(MODULE)));
        return files(root.resolve(SOURCE)).stream()
                .filter(path -> compiled(relative(root, path), excluded)).toList();
    }

    static boolean checkJar(Path file, String hash, boolean requireFresh) throws IOException {
        require(Files.isRegularFile(file), "Missing " + file + "; run ReleaseBuild build.");
        boolean fresh;
        try (JarFile jar = new JarFile(file.toFile())) {
            Manifest manifest = jar.getManifest();
            fresh = manifest != null && hash.equals(manifest.getMainAttributes().getValue("Source-SHA256"));
            require(fresh || !requireFresh, "Release jar is stale. Run ReleaseBuild build, then commit " + JAR + ".");
            require(jar.getEntry(PLUGIN) != null, "Release jar has no " + PLUGIN + ".");
            for (ZipEntry entry : jar.stream().toList()) {
                if (entry.getName().equals("META-INF/MANIFEST.MF")) continue;
                require(runtimeEntry(entry.getName()), "Development/dependency file in jar: " + entry.getName());
                try (var input = new DataInputStream(jar.getInputStream(entry))) {
                    require(input.readInt() == 0xcafebabe, "Invalid class: " + entry.getName());
                    input.readUnsignedShort();
                    require(input.readUnsignedShort() == 61, "Class is not Java 17: " + entry.getName());
                }
            }
        }
        return fresh;
    }

    static String sourceHash(Path root) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        for (Path path : sources(root)) {
            String name = relative(root, path);
            if (name.startsWith(TOOLS) && !name.equals(TOOL)) continue;
            hash.update(name.getBytes(StandardCharsets.UTF_8));
            hash.update((byte) 0);
            hash.update(Files.readString(path).replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8));
            hash.update((byte) 0);
        }
        for (Path path : files(root.resolve(LIBS))) {
            if (!path.toString().endsWith(".jar")) continue;
            hash.update(relative(root, path).getBytes(StandardCharsets.UTF_8));
            hash.update((byte) 0);
            hash.update(Files.readAllBytes(path));
        }
        return HexFormat.of().formatHex(hash.digest());
    }

    static List<String> trackedFiles(Path root) throws Exception {
        Process git = new ProcessBuilder("git", "ls-files", "-z").directory(root.toFile())
                .redirectError(ProcessBuilder.Redirect.INHERIT).start();
        String output = new String(git.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        require(git.waitFor() == 0, "git ls-files failed.");
        return output.isEmpty() ? List.of() : List.of(output.split("\u0000"));
    }

    static List<Path> files(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> relative(directory, path))).toList();
        }
    }

    static String relative(Path root, Path path) {
        return root.relativize(path).toString().replace(File.separatorChar, '/');
    }

    static void writeZip(Path target, Map<String, byte[]> entries) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
            zip.setLevel(9);
            for (var item : new TreeMap<>(entries).entrySet()) {
                ZipEntry entry = new ZipEntry(item.getKey());
                entry.setTimeLocal(ZIP_TIME);
                zip.putNextEntry(entry);
                zip.write(item.getValue());
                zip.closeEntry();
            }
        }
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
