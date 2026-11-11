// Drives the Starsector window on Windows. Build with build-gamectl.sh.
// Coordinates are client-area pixels of the game window, origin at the top left (same as the bshot PNG).
//
// Background commands. They never move your mouse, take focus or touch other windows.
// The game must not idle while unfocused: see "idleWhileWindowNotVisible" in the live test mod.
// Set GAMECTL_PID to the test game's PID (launch-game.sh writes WORK_DIR/game.pid) so only that window is used.
//   gamectl bshot <out.png>           capture the game window, even when other windows cover it (not when minimized)
//   gamectl bmove <x> <y>             send a mouse move (send one before the first click so hover state updates)
//   gamectl bclick <x> <y>            send a left click
//   gamectl brclick <x> <y>           send a right click
//   gamectl bkey <name> [count]       send a key press (ESC, ENTER, SPACE, TAB, BACK, F1-F12, A-Z, 0-9, UP, ...)
//   gamectl btype <text>              type lowercase letters, digits, space, '-', '.', '/' (no Shift characters)
//   gamectl hide [seconds]            for the given time, push any window of the GAMECTL_PID game (a crash box
//                                     included) behind other windows whenever it comes to the front, and give
//                                     focus back to the window you were using; prints each change with a time
//
// Foreground commands. They bring the game to the front and use the real mouse and keyboard,
// and refuse to send input if the game is not in front. Avoid them while someone is using the PC.
//   gamectl shot <out.png>            capture the game window's client area from the screen
//   gamectl click <x> <y>             left click at client coordinates
//   gamectl rclick <x> <y>            right click
//   gamectl move <x> <y>              move the cursor
//   gamectl drag <x1> <y1> <x2> <y2>  left-drag
//   gamectl key <name> [count]        press a key
//   gamectl combo <mod> <name>        press a key while holding CTRL, SHIFT or ALT
//   gamectl type <text>               type text as unicode characters
//   gamectl info                      print window position and client size
using System;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;
using System.Threading;

static class GameCtl
{
    [DllImport("user32.dll")] static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] static extern bool ShowWindow(IntPtr h, int cmd);
    [DllImport("user32.dll")] static extern bool GetClientRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] static extern bool ClientToScreen(IntPtr h, ref POINT p);
    [DllImport("user32.dll")] static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] static extern uint SendInput(uint n, INPUT[] inputs, int size);
    [DllImport("user32.dll")] static extern short VkKeyScan(char c);
    [DllImport("user32.dll")] static extern uint MapVirtualKey(uint code, uint mapType);
    [DllImport("user32.dll")] static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] static extern bool IsIconic(IntPtr h);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] static extern int GetWindowText(IntPtr h, System.Text.StringBuilder s, int max);

    [StructLayout(LayoutKind.Sequential)] struct RECT { public int L, T, R, B; }
    [StructLayout(LayoutKind.Sequential)] struct POINT { public int X, Y; }
    [StructLayout(LayoutKind.Sequential)] struct MOUSEINPUT { public int dx, dy; public uint data, flags, time; public IntPtr extra; }
    [StructLayout(LayoutKind.Sequential)] struct KEYBDINPUT { public ushort vk, scan; public uint flags, time; public IntPtr extra; }
    [StructLayout(LayoutKind.Explicit)] struct UNION { [FieldOffset(0)] public MOUSEINPUT mi; [FieldOffset(0)] public KEYBDINPUT ki; }
    [StructLayout(LayoutKind.Sequential)] struct INPUT { public uint type; public UNION u; }

    const uint MOUSE = 0, KEYBOARD = 1;
    const uint LEFTDOWN = 0x2, LEFTUP = 0x4, RIGHTDOWN = 0x8, RIGHTUP = 0x10;
    const uint KEYUP = 0x2, UNICODE = 0x4, SCANCODE = 0x8;

    // With GAMECTL_PID set, only that process's window is used, whatever its title (a crash box included).
    // Without it, exactly one Starsector window must exist, so input never lands in a game the user has open.
    static IntPtr FindGame()
    {
        string pid = Environment.GetEnvironmentVariable("GAMECTL_PID");
        IntPtr found = IntPtr.Zero;
        int count = 0;
        foreach (var p in Process.GetProcesses())
        {
            try
            {
                if (p.MainWindowHandle == IntPtr.Zero) continue;
                bool match = string.IsNullOrEmpty(pid) ? p.MainWindowTitle.StartsWith("Starsector") : p.Id.ToString() == pid;
                if (match) { found = p.MainWindowHandle; count++; }
            }
            catch { }
        }
        if (count == 1) return found;
        if (count > 1) throw new Exception("More than one Starsector window; set GAMECTL_PID to the test game's PID (WORK_DIR/game.pid)");
        throw new Exception(string.IsNullOrEmpty(pid) ? "Starsector window not found" : "No window for GAMECTL_PID " + pid);
    }

    [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr h, IntPtr pid);
    [DllImport("kernel32.dll")] static extern uint GetCurrentThreadId();
    [DllImport("user32.dll")] static extern bool AttachThreadInput(uint a, uint b, bool attach);
    [DllImport("user32.dll")] static extern bool BringWindowToTop(IntPtr h);

    static IntPtr game = IntPtr.Zero;

    // Brings the game to the front. Throws if it cannot, so input never goes to another window.
    static IntPtr Focus()
    {
        var h = FindGame();
        game = h;
        if (IsIconic(h)) ShowWindow(h, 9);
        for (int attempt = 0; attempt < 3 && GetForegroundWindow() != h; attempt++)
        {
            uint fgThread = GetWindowThreadProcessId(GetForegroundWindow(), IntPtr.Zero);
            uint me = GetCurrentThreadId();
            AttachThreadInput(me, fgThread, true);
            BringWindowToTop(h);
            SetForegroundWindow(h);
            AttachThreadInput(me, fgThread, false);
            Thread.Sleep(200);
        }
        if (GetForegroundWindow() != h)
            throw new Exception("Starsector is not the foreground window; refusing to send input or capture");
        return h;
    }

    static void CheckFront()
    {
        if (game == IntPtr.Zero || GetForegroundWindow() != game)
            throw new Exception("Starsector lost focus; stopped sending input");
    }

    static POINT ToScreen(IntPtr h, int x, int y)
    {
        var p = new POINT { X = x, Y = y };
        ClientToScreen(h, ref p);
        return p;
    }

    static void Send(params INPUT[] inputs)
    {
        CheckFront();
        SendInput((uint)inputs.Length, inputs, Marshal.SizeOf(typeof(INPUT)));
    }

    static INPUT Mouse(uint flags) { var i = new INPUT { type = MOUSE }; i.u.mi.flags = flags; return i; }

    static INPUT Key(ushort vk, bool up)
    {
        var i = new INPUT { type = KEYBOARD };
        i.u.ki.vk = vk;
        i.u.ki.scan = (ushort)MapVirtualKey(vk, 0);
        i.u.ki.flags = up ? KEYUP : 0;
        return i;
    }

    static INPUT Char(char c, bool up)
    {
        var i = new INPUT { type = KEYBOARD };
        i.u.ki.scan = c;
        i.u.ki.flags = UNICODE | (up ? KEYUP : 0);
        return i;
    }

    static void MoveTo(IntPtr h, int x, int y)
    {
        var p = ToScreen(h, x, y);
        SetCursorPos(p.X, p.Y);
        Thread.Sleep(60);
    }

    static bool TryInt(string s, out int v) { return int.TryParse(s, out v); }

    static ushort Vk(string name)
    {
        int f;
        name = name.ToUpperInvariant();
        switch (name)
        {
            case "ESC": case "ESCAPE": return 0x1B;
            case "ENTER": case "RETURN": return 0x0D;
            case "SPACE": return 0x20;
            case "TAB": return 0x09;
            case "BACK": case "BACKSPACE": return 0x08;
            case "DELETE": return 0x2E;
            case "UP": return 0x26;
            case "DOWN": return 0x28;
            case "LEFT": return 0x25;
            case "RIGHT": return 0x27;
            case "CTRL": return 0x11;
            case "SHIFT": return 0x10;
            case "ALT": return 0x12;
            case "HOME": return 0x24;
            case "END": return 0x23;
        }
        if (name.Length >= 2 && name[0] == 'F' && TryInt(name.Substring(1), out f)) return (ushort)(0x70 + f - 1);
        if (name.Length == 1) return (ushort)(VkKeyScan(name[0]) & 0xFF);
        throw new Exception("Unknown key " + name);
    }

    // Background mode: posts messages to the game window and captures it with PrintWindow.
    // Never moves the real cursor, never changes focus.
    [DllImport("user32.dll")] static extern bool PostMessage(IntPtr h, uint msg, IntPtr w, IntPtr l);
    [DllImport("user32.dll")] static extern bool PrintWindow(IntPtr h, IntPtr hdc, uint flags);

    const uint WM_KEYDOWN = 0x100, WM_KEYUP = 0x101, WM_CHAR = 0x102, WM_MOUSEMOVE = 0x200;
    const uint WM_LBUTTONDOWN = 0x201, WM_LBUTTONUP = 0x202, WM_RBUTTONDOWN = 0x204, WM_RBUTTONUP = 0x205;

    static IntPtr XY(int x, int y) { return (IntPtr)((y << 16) | (x & 0xFFFF)); }

    static void Post(IntPtr h, uint msg, int w, IntPtr l)
    {
        PostMessage(h, msg, (IntPtr)w, l);
    }

    static IntPtr KeyParam(ushort vk, bool up)
    {
        uint scan = MapVirtualKey(vk, 0);
        uint l = 1 | (scan << 16);
        if (up) l |= 0xC0000000;
        return (IntPtr)unchecked((int)l);
    }

    static void BgKey(IntPtr h, ushort vk, bool up)
    {
        Post(h, up ? WM_KEYUP : WM_KEYDOWN, vk, KeyParam(vk, up));
    }

    static int Background(string[] a)
    {
        var h = FindGame();
        switch (a[0])
        {
            case "bshot":
            {
                RECT r; GetClientRect(h, out r);
                using (var bmp = new Bitmap(r.R, r.B))
                {
                    using (var g = Graphics.FromImage(bmp))
                    {
                        IntPtr hdc = g.GetHdc();
                        PrintWindow(h, hdc, 3); // client area only, full content
                        g.ReleaseHdc(hdc);
                    }
                    bmp.Save(a[1], ImageFormat.Png);
                }
                Console.WriteLine("saved " + a[1] + " " + r.R + "x" + r.B);
                return 0;
            }
            case "bmove":
                Post(h, WM_MOUSEMOVE, 0, XY(int.Parse(a[1]), int.Parse(a[2])));
                return 0;
            case "bclick":
            case "brclick":
            {
                int x = int.Parse(a[1]), y = int.Parse(a[2]);
                bool right = a[0] == "brclick";
                Post(h, WM_MOUSEMOVE, 0, XY(x, y));
                Thread.Sleep(80);
                Post(h, right ? WM_RBUTTONDOWN : WM_LBUTTONDOWN, right ? 2 : 1, XY(x, y));
                Thread.Sleep(80);
                Post(h, right ? WM_RBUTTONUP : WM_LBUTTONUP, 0, XY(x, y));
                Thread.Sleep(120);
                return 0;
            }
            case "bkey":
            {
                ushort vk = Vk(a[1]);
                int n = a.Length > 2 ? int.Parse(a[2]) : 1;
                for (int i = 0; i < n; i++)
                {
                    BgKey(h, vk, false);
                    Thread.Sleep(60);
                    BgKey(h, vk, true);
                    Thread.Sleep(90);
                }
                return 0;
            }
            case "bcombo":
            {
                ushort mod = Vk(a[1]), vk = Vk(a[2]);
                BgKey(h, mod, false);
                Thread.Sleep(60);
                BgKey(h, vk, false);
                Thread.Sleep(60);
                BgKey(h, vk, true);
                Thread.Sleep(60);
                BgKey(h, mod, true);
                Thread.Sleep(90);
                return 0;
            }
            case "btype":
            {
                // The game builds characters from key-down messages and the real Shift state,
                // so only unshifted characters can be typed: lowercase letters, digits, space, '-', '.', '/'.
                string text = string.Join(" ", a, 1, a.Length - 1).ToLowerInvariant();
                foreach (char c in text)
                {
                    ushort vk;
                    if (c == ' ') vk = 0x20;
                    else if (c == '-') vk = 0xBD;
                    else if (c == '.') vk = 0xBE;
                    else if (c == '/') vk = 0xBF;
                    else if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) vk = (ushort)char.ToUpperInvariant(c);
                    else throw new Exception("btype cannot type '" + c + "'");
                    BgKey(h, vk, false);
                    Thread.Sleep(70);
                    BgKey(h, vk, true);
                    Thread.Sleep(70);
                }
                return 0;
            }
        }
        return -1;
    }

    [DllImport("user32.dll")] static extern bool SetWindowPos(IntPtr h, IntPtr after, int x, int y, int cx, int cy, uint flags);

    [DllImport("user32.dll", EntryPoint = "GetWindowThreadProcessId")] static extern uint GetWindowPid(IntPtr h, out uint pid);
    [DllImport("user32.dll")] static extern bool IsWindow(IntPtr h);
    [DllImport("user32.dll")] static extern bool IsWindowVisible(IntPtr h);
    [DllImport("user32.dll")] static extern int GetWindowLong(IntPtr h, int index);
    delegate bool EnumProc(IntPtr h, IntPtr l);
    [DllImport("user32.dll")] static extern bool EnumWindows(EnumProc f, IntPtr l);

    static string Title(IntPtr h)
    {
        var title = new System.Text.StringBuilder(64);
        GetWindowText(h, title, title.Capacity);
        return title.ToString();
    }

    // Another test game is never the window to give focus back to: with several games starting, two hide loops
    // would hand focus to each other's game instead of to the user's window.
    static bool IsGameWindow(IntPtr h)
    {
        return Title(h).StartsWith("Starsector");
    }

    static uint PidOf(IntPtr h)
    {
        uint pid;
        GetWindowPid(h, out pid);
        return pid;
    }

    static bool Usable(IntPtr h, uint game)
    {
        // GWL_EXSTYLE = -20, WS_EX_TOOLWINDOW = 0x80
        return h != IntPtr.Zero && IsWindow(h) && IsWindowVisible(h) && !IsIconic(h) && PidOf(h) != game
            && !IsGameWindow(h) && Title(h).Length > 0 && (GetWindowLong(h, -20) & 0x80) == 0;
    }

    // The topmost window the user could have been using, for when the remembered one has closed.
    static IntPtr TopUserWindow(uint game)
    {
        IntPtr found = IntPtr.Zero;
        EnumWindows(delegate(IntPtr h, IntPtr l)
        {
            if (!Usable(h, game)) return true;
            found = h;
            return false;
        }, IntPtr.Zero);
        return found;
    }

    // Watches the foreground for the given number of seconds. Whenever any window of the game process takes it (the
    // game, a crash box), that window is pushed behind every other window and the user's window gets focus back.
    // Each change is printed with a time, so a run's hide.log shows what took the front and whether it went back.
    static int Hide(int seconds)
    {
        uint game;
        if (!uint.TryParse(Environment.GetEnvironmentVariable("GAMECTL_PID"), out game))
        {
            Console.Error.WriteLine("hide needs GAMECTL_PID");
            return 2;
        }
        IntPtr prev = GetForegroundWindow();
        if (!Usable(prev, game)) prev = IntPtr.Zero;
        Console.WriteLine(DateTime.Now.ToString("HH:mm:ss.f") + " hide " + seconds + " s, game " + game + ", user window '" + Title(prev) + "'");
        var end = DateTime.Now.AddSeconds(seconds);
        int pushed = 0, failed = 0;
        string last = "";
        while (DateTime.Now < end)
        {
            IntPtr fg = GetForegroundWindow();
            if (fg == IntPtr.Zero || PidOf(fg) != game)
            {
                if (Usable(fg, game)) prev = fg;
                last = "";
            }
            else
            {
                // SWP_NOSIZE | SWP_NOMOVE | SWP_NOACTIVATE, HWND_BOTTOM = 1
                SetWindowPos(fg, new IntPtr(1), 0, 0, 0, 0, 0x1 | 0x2 | 0x10);
                if (!Usable(prev, game)) prev = TopUserWindow(game);
                bool ok = false;
                if (prev != IntPtr.Zero)
                {
                    uint me = GetCurrentThreadId();
                    uint them = GetWindowThreadProcessId(fg, IntPtr.Zero);
                    AttachThreadInput(me, them, true);
                    ok = SetForegroundWindow(prev);
                    AttachThreadInput(me, them, false);
                }
                pushed++;
                if (!ok) failed++;
                string now = "'" + Title(fg) + "' in front, focus to '" + Title(prev) + "' " + (ok ? "ok" : "FAILED");
                if (now != last) Console.WriteLine(DateTime.Now.ToString("HH:mm:ss.f") + " " + now);
                last = now;
            }
            System.Threading.Thread.Sleep(150);
        }
        Console.WriteLine(DateTime.Now.ToString("HH:mm:ss.f") + " hide finished, pushed back " + pushed + " times, " + failed + " failed");
        return 0;
    }

    static int Main(string[] a)
    {
        try
        {
            if (a[0] == "hide") return Hide(a.Length > 1 ? int.Parse(a[1]) : 120);
            if (a[0].StartsWith("b"))
            {
                int r = Background(a);
                if (r >= 0) return r;
            }
            switch (a[0])
            {
                case "info":
                {
                    var h = FindGame();
                    RECT r; GetClientRect(h, out r);
                    var p = ToScreen(h, 0, 0);
                    Console.WriteLine("client " + r.R + "x" + r.B + " at screen " + p.X + "," + p.Y);
                    return 0;
                }
                case "shot":
                {
                    var h = Focus();
                    Thread.Sleep(250);
                    RECT r; GetClientRect(h, out r);
                    var p = ToScreen(h, 0, 0);
                    using (var bmp = new Bitmap(r.R, r.B))
                    {
                        using (var g = Graphics.FromImage(bmp)) g.CopyFromScreen(p.X, p.Y, 0, 0, new Size(r.R, r.B));
                        bmp.Save(a[1], ImageFormat.Png);
                    }
                    Console.WriteLine("saved " + a[1] + " " + r.R + "x" + r.B);
                    return 0;
                }
                case "click":
                case "rclick":
                {
                    var h = Focus();
                    MoveTo(h, int.Parse(a[1]), int.Parse(a[2]));
                    bool right = a[0] == "rclick";
                    Send(Mouse(right ? RIGHTDOWN : LEFTDOWN));
                    Thread.Sleep(80);
                    Send(Mouse(right ? RIGHTUP : LEFTUP));
                    Thread.Sleep(120);
                    return 0;
                }
                case "move":
                {
                    var h = Focus();
                    MoveTo(h, int.Parse(a[1]), int.Parse(a[2]));
                    return 0;
                }
                case "drag":
                {
                    var h = Focus();
                    MoveTo(h, int.Parse(a[1]), int.Parse(a[2]));
                    Send(Mouse(LEFTDOWN));
                    Thread.Sleep(100);
                    MoveTo(h, int.Parse(a[3]), int.Parse(a[4]));
                    Thread.Sleep(100);
                    Send(Mouse(LEFTUP));
                    return 0;
                }
                case "key":
                {
                    Focus();
                    ushort vk = Vk(a[1]);
                    int n = a.Length > 2 ? int.Parse(a[2]) : 1;
                    for (int i = 0; i < n; i++)
                    {
                        Send(Key(vk, false));
                        Thread.Sleep(60);
                        Send(Key(vk, true));
                        Thread.Sleep(90);
                    }
                    return 0;
                }
                case "combo":
                {
                    Focus();
                    ushort mod = Vk(a[1]), vk = Vk(a[2]);
                    Send(Key(mod, false));
                    Thread.Sleep(60);
                    Send(Key(vk, false));
                    Thread.Sleep(60);
                    Send(Key(vk, true));
                    Thread.Sleep(60);
                    Send(Key(mod, true));
                    Thread.Sleep(90);
                    return 0;
                }
                case "type":
                {
                    Focus();
                    string text = string.Join(" ", a, 1, a.Length - 1);
                    foreach (char c in text)
                    {
                        Send(Char(c, false));
                        Thread.Sleep(15);
                        Send(Char(c, true));
                        Thread.Sleep(15);
                    }
                    return 0;
                }
            }
            Console.Error.WriteLine("unknown command " + a[0]);
            return 2;
        }
        catch (Exception e)
        {
            Console.Error.WriteLine(e.Message);
            return 1;
        }
    }
}
