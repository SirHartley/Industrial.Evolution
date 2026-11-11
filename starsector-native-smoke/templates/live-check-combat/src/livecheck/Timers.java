package livecheck;

import patchlib.api.context.AfterContext;
import patchlib.api.context.BeforeContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.After;
import patchlib.api.patch.Before;
import patchlib.api.patch.Patch;

// PatchLib 1.0.3 patches. They time the instances the game really runs, which a copy built in test code would not.
public final class Timers {
    private Timers() {
    }

    // Replace the class and method with the code under test. Name the exact class: a packageName filter also matches
    // vanilla's loose scripts in the same package (data.scripts.plugins holds vanilla's TestCombatPlugin).
    @Patch(target = @ClassMatch(typeName = "com.example.MyCombatPlugin"))
    public static final class CodeUnderTest {
        @Before(target = @MethodMatch(methodName = "advance", parameterCount = 2))
        public static void before(BeforeContext ctx) {
            // the OFF windows skip the call; return a value here for a method that is not void
            if (!Perf.codeOn) {
                ctx.skipOriginal(null);
                return;
            }
            // a subclass calling super would be timed twice
            if (ctx.isMostDerivedCall()) ctx.setLocal("t0", System.nanoTime());
        }

        @After(target = @MethodMatch(methodName = "advance", parameterCount = 2))
        public static void after(AfterContext ctx) {
            Long t0 = ctx.getLocal("t0");
            if (t0 != null) Perf.add("MyCombatPlugin.advance", System.nanoTime() - t0);
        }
    }

    // Waiting for the driver (buffer swap, frame cap, vsync) happens inside these. BattleDriver subtracts it from the
    // frame's wall time, which leaves the game's work per frame whatever cap the user has set.
    @Patch(target = @ClassMatch(typeName = "org.lwjgl.opengl.Display"))
    public static final class DisplayWait {
        static void in() {
            if (Perf.depth++ == 0) Perf.displayStart = System.nanoTime();
        }

        static void out() {
            if (Perf.depth > 0 && --Perf.depth == 0) {
                Perf.displayNanos += System.nanoTime() - Perf.displayStart;
                Perf.displayCalls++;
            }
        }

        @Before(target = @MethodMatch(methodName = "update"))
        public static void b1(BeforeContext ctx) { in(); }

        @After(target = @MethodMatch(methodName = "update"))
        public static void a1(AfterContext ctx) { out(); }

        @Before(target = @MethodMatch(methodName = "sync"))
        public static void b2(BeforeContext ctx) { in(); }

        @After(target = @MethodMatch(methodName = "sync"))
        public static void a2(AfterContext ctx) { out(); }
    }
}
