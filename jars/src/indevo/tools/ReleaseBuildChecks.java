package indevo.tools;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ReleaseBuildChecks {

    private static int checks;

    private ReleaseBuildChecks() {
    }

    public static void main(String[] args) {
        checkNotes();
        checkStamping();
        checkFiles();
        checkSources();
        checkAssets();
        System.out.println("Release build: " + checks + " checks passed");
    }

    private static void checkNotes() {
        String changelog = "----- Changelog -----\n4.2.a - UNRELEASED\nGeneral\nAdded - New thing\n\n"
                + "4.1.b - CURRENT PUBLIC VERSION\nSAVE COMPATIBLE WITH 4.1\n\nMuseum\nFixed - Old thing\n\n"
                + "2.2.a/b\nFixed - Odd heading\n";
        ReleaseBuild.Notes preview = ReleaseBuild.notes(changelog, null);
        require(preview.version().equals("4.2.a") && preview.unreleased(), "preview reads newest section");
        require(!preview.text().contains("Old thing"), "notes stop at the next section");
        fails(() -> ReleaseBuild.notes(changelog, "4.2.a"), "version rejects UNRELEASED");
        fails(() -> ReleaseBuild.notes(changelog, "4.1.b"), "version must match newest section");
        fails(() -> ReleaseBuild.notes(changelog, "v4.2.a"), "version has no v prefix");
        String released = changelog.replace("4.2.a - UNRELEASED", "4.2.a - CURRENT PUBLIC VERSION")
                .replace("4.1.b - CURRENT PUBLIC VERSION", "4.1.b");
        ReleaseBuild.Notes release = ReleaseBuild.notes(released, "4.2.a");
        require(release.version().equals("4.2.a") && !release.unreleased(), "version accepts released section");
        require(release.text().startsWith("4.2.a - CURRENT PUBLIC VERSION\nGeneral\n"), "notes keep heading");
        fails(() -> ReleaseBuild.notes("4.1.a\nNothing here\n", null), "section needs a change line");
        fails(() -> ReleaseBuild.notes("4.1.a\nAdded - x\n4.1.a\nAdded - y\n", null), "duplicate versions");
        fails(() -> ReleaseBuild.notes("4.1.a\nAdded - x\n```\n", null), "code fences rejected");
    }

    private static void checkStamping() {
        String file = "{\n\"modVersion\":\n{\n\"major\":4,\n\"minor\":1,\n\"patch\":\"b\"\n},\n"
                + "#Link towards a direct download\n\"directDownloadURL\":\"x\",\n}";
        String stamped = ReleaseBuild.stampVersion(file, "4.2.c");
        require(stamped.contains("\"major\":4") && stamped.contains("\"minor\":2")
                && stamped.contains("\"patch\":\"c\""), "version file stamped");
        require(stamped.contains("#Link towards a direct download"), "version file comments kept");
        require(stamped.contains("/releases/download/4.2.c/" + ReleaseBuild.FOLDER + "-4.2.c.zip"),
                "download url stamped");
        String info = "{\"version\":\"4.1.b\", \"gameVersion\":\"0.98a\"}";
        String stampedInfo = ReleaseBuild.stampInfo(info, "4.2.c");
        require(stampedInfo.contains("\"version\":\"4.2.c\"") && stampedInfo.contains("\"gameVersion\":\"0.98a\""),
                "mod_info stamped, game version kept");
        fails(() -> ReleaseBuild.stampInfo("{}", "4.2.c"), "missing field rejected");
    }

    private static void checkFiles() {
        require(ReleaseBuild.releaseFile("Changelog"), "changelog shipped");
        require(ReleaseBuild.releaseFile("_IndEvo.png"), "mod icon shipped");
        require(ReleaseBuild.releaseFile("jars/source.url"), "extras shipped");
        require(ReleaseBuild.releaseFile("data/campaign/rules.csv"), "data shipped");
        require(ReleaseBuild.releaseFile("data/characters/skills/indevo_Micromanagement.skill"), "skills shipped");
        require(ReleaseBuild.releaseFile("data/world/factions/IndEvo_derelict.faction"), "factions shipped");
        require(ReleaseBuild.releaseFile("graphics/icons/IndEvo_icon.png"), "graphics shipped");
        require(ReleaseBuild.releaseFile("graphics/clouds/clouds.csv"), "graphics lists shipped");
        require(ReleaseBuild.releaseFile("IndEvo/sounds/crucible_alarm.ogg"), "sounds shipped");
        require(!ReleaseBuild.releaseFile("docs/ARCHITECTURE.md"), "docs skipped");
        require(!ReleaseBuild.releaseFile("CLAUDE.md"), "agent files skipped");
        require(!ReleaseBuild.releaseFile("Changelog_todo"), "todo list skipped");
        require(!ReleaseBuild.releaseFile("IndEvo.iml"), "ide files skipped");
        require(!ReleaseBuild.releaseFile("lib/starfarer.api.jar"), "compile jars skipped");
        require(!ReleaseBuild.releaseFile("jars/src/indevo/utils/ModPlugin.java"), "source skipped");
        require(!ReleaseBuild.releaseFile("jars/release/IndEvo.jar"), "release jar mapped, not copied");
        require(!ReleaseBuild.releaseFile("graphics/asteroids/unused/IndEvo_ice_1.png"), "unused art skipped");
        require(!ReleaseBuild.releaseFile("graphics/maps/photothumb.db"), "thumbnail caches skipped");
        require(!ReleaseBuild.releaseFile("data/campaign/crucible/notes.docx"), "design notes skipped");
        require(!ReleaseBuild.releaseFile("data/.idea/x.json"), "hidden dirs skipped");
        require(ReleaseBuild.runtimeEntry("indevo/utils/ModPlugin.class"), "runtime classes kept");
        require(ReleaseBuild.runtimeEntry("data/scripts/weapons/ai/X.class"), "combat classes kept");
        require(!ReleaseBuild.runtimeEntry("indevo/tools/ReleaseBuild.class"), "tools left out of jar");
        require(!ReleaseBuild.runtimeEntry("kotlin/Unit.class"), "kotlin runtime left out of jar");
        require(!ReleaseBuild.runtimeEntry("indevo/exploration/crucible/dialogue/Todo"), "notes left out of jar");
    }

    private static void checkSources() {
        String module = "<excludeFolder url=\"file://$MODULE_DIR$/jars/src/indevo/WIP/converters\" />\n"
                + "<excludeFolder url=\"file://$MODULE_DIR$/jars/src/indevo/exploration/distress\" />";
        List<String> excluded = ReleaseBuild.excludes(module);
        require(excluded.equals(List.of("jars/src/indevo/WIP/converters/", "jars/src/indevo/exploration/distress/")),
                "module excludes read");
        require(ReleaseBuild.compiled("jars/src/indevo/utils/ModPlugin.java", excluded), "java compiled");
        require(!ReleaseBuild.compiled("jars/src/indevo/utils/helper/ReflectionUtils.kt", excluded), "kotlin not compiled");
        require(!ReleaseBuild.compiled("jars/src/indevo/exploration/distress/Distress.java", excluded),
                "excluded folder skipped");
        require(ReleaseBuild.compiled("jars/src/indevo/exploration/distressed/X.java", excluded),
                "exclude matches whole folders");
        require(!ReleaseBuild.compiled("jars/src/indevo/exploration/distress/hull_mods.csv", List.of()),
                "resources not compiled");
    }

    private static void checkAssets() {
        String folder = ReleaseBuild.FOLDER + "/";
        byte[] sounds = "{\"x\":\"IndEvo/sounds/a.ogg\",\"y\":\"sounds/vanilla.ogg\"}".getBytes();
        ReleaseBuild.checkAssets(Map.of(folder + "data/config/sounds.json", sounds,
                folder + "IndEvo/sounds/a.ogg", new byte[0]), Set.of());
        checks++;
        fails(() -> ReleaseBuild.checkAssets(Map.of(folder + "data/config/sounds.json", sounds), Set.of()),
                "missing ie asset rejected");
        byte[] icon = "graphics/icons/old.png".getBytes();
        fails(() -> ReleaseBuild.checkAssets(Map.of(folder + "data/campaign/x.csv", icon),
                Set.of("graphics/icons/old.png")), "tracked but unpackaged asset rejected");
        byte[] list = "\"IndEvo_cloudList\": \"graphics/clouds/clouds.csv\"".getBytes();
        fails(() -> ReleaseBuild.checkAssets(Map.of(folder + "data/config/settings.json", list),
                Set.of("graphics/clouds/clouds.csv")), "unpackaged csv list rejected");
    }

    private static void fails(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            checks++;
            return;
        }
        throw new AssertionError(message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
