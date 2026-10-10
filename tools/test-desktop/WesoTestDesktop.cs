using System;
using System.ComponentModel;
using System.Runtime.InteropServices;
using System.Text;

// Test infrastructure only. There is deliberately no SwitchDesktop or input-injection API here.
public sealed class WesoTestDesktop : IDisposable
{
    private IntPtr desktop, job, process;
    public string Name { get; private set; }
    public uint ProcessId { get; private set; }

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct StartupInfo
    {
        public uint cb;
        public string reserved, desktop, title;
        public uint x, y, width, height, columns, rows, fill, flags;
        public ushort show, reservedSize;
        public IntPtr reservedBytes, stdin, stdout, stderr;
    }
    [StructLayout(LayoutKind.Sequential)]
    private struct ProcessInfo { public IntPtr process, thread; public uint id, threadId; }
    [StructLayout(LayoutKind.Sequential)]
    private struct BasicLimits
    {
        public long processTime, jobTime;
        public uint flags;
        public UIntPtr minWorkingSet, maxWorkingSet;
        public uint activeProcesses;
        public UIntPtr affinity;
        public uint priority, scheduling;
    }
    [StructLayout(LayoutKind.Sequential)]
    private struct IoCounters { public ulong a, b, c, d, e, f; }
    [StructLayout(LayoutKind.Sequential)]
    private struct ExtendedLimits
    {
        public BasicLimits basic;
        public IoCounters io;
        public UIntPtr processMemory, jobMemory, peakProcessMemory, peakJobMemory;
    }
    [StructLayout(LayoutKind.Sequential)]
    public struct Rect { public int left, top, right, bottom; }

    private delegate bool EnumWindow(IntPtr window, IntPtr parameter);
    [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern IntPtr CreateDesktopW(string name, IntPtr device, IntPtr mode, uint flags, uint access, IntPtr security);
    [DllImport("user32.dll", SetLastError = true)] private static extern bool CloseDesktop(IntPtr handle);
    [DllImport("user32.dll", SetLastError = true)] private static extern IntPtr OpenInputDesktop(uint flags, bool inherit, uint access);
    [DllImport("user32.dll")] private static extern IntPtr GetThreadDesktop(uint thread);
    [DllImport("kernel32.dll")] private static extern uint GetCurrentThreadId();
    [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern bool GetUserObjectInformationW(IntPtr handle, int index, StringBuilder value, uint length, out uint needed);
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern bool CreateProcessW(string application, StringBuilder command, IntPtr processSecurity, IntPtr threadSecurity,
        bool inherit, uint flags, IntPtr environment, string directory, ref StartupInfo startup, out ProcessInfo info);
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)] private static extern IntPtr CreateJobObjectW(IntPtr security, string name);
    [DllImport("kernel32.dll", SetLastError = true)] private static extern bool SetInformationJobObject(IntPtr job, int kind, ref ExtendedLimits limits, uint size);
    [DllImport("kernel32.dll", SetLastError = true)] private static extern bool AssignProcessToJobObject(IntPtr job, IntPtr process);
    [DllImport("kernel32.dll", SetLastError = true)] private static extern uint ResumeThread(IntPtr thread);
    [DllImport("kernel32.dll", SetLastError = true)] private static extern bool TerminateJobObject(IntPtr job, uint code);
    [DllImport("kernel32.dll", SetLastError = true)] private static extern bool TerminateProcess(IntPtr process, uint code);
    [DllImport("kernel32.dll", SetLastError = true)] private static extern uint WaitForSingleObject(IntPtr handle, uint milliseconds);
    [DllImport("kernel32.dll", SetLastError = true)] private static extern bool GetExitCodeProcess(IntPtr process, out uint code);
    [DllImport("kernel32.dll")] private static extern bool CloseHandle(IntPtr handle);
    [DllImport("user32.dll", SetLastError = true)] private static extern bool EnumDesktopWindows(IntPtr desktop, EnumWindow callback, IntPtr parameter);
    [DllImport("user32.dll")] private static extern uint GetWindowThreadProcessId(IntPtr window, out uint process);
    [DllImport("kernel32.dll", SetLastError = true)] private static extern IntPtr OpenProcess(uint access, bool inherit, uint id);
    [DllImport("kernel32.dll", SetLastError = true)] private static extern bool IsProcessInJob(IntPtr process, IntPtr job, out bool belongs);
    [DllImport("user32.dll", SetLastError = true)] public static extern bool GetClipCursor(out Rect rect);
    [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern IntPtr CreateWindowExW(uint exStyle, string className, string title, uint style, int x, int y,
        int width, int height, IntPtr parent, IntPtr menu, IntPtr instance, IntPtr parameter);
    [DllImport("user32.dll")] private static extern bool DestroyWindow(IntPtr window);
    [DllImport("user32.dll")] private static extern bool SetForegroundWindow(IntPtr window);

    private static void Check(bool success, string operation)
    {
        if (!success) throw new Win32Exception(Marshal.GetLastWin32Error(), operation);
    }
    private static string DesktopName(IntPtr handle)
    {
        var name = new StringBuilder(256);
        uint needed;
        Check(GetUserObjectInformationW(handle, 2, name, 512, out needed), "Read desktop name");
        return name.ToString();
    }
    public static string CurrentName() { return DesktopName(GetThreadDesktop(GetCurrentThreadId())); }
    public static void AssertIsolated(string expected)
    {
        if (String.IsNullOrEmpty(expected) || !expected.StartsWith("WesoTest-", StringComparison.Ordinal)
            || CurrentName() != expected) throw new InvalidOperationException("Test worker is not on its private desktop");
        IntPtr input = OpenInputDesktop(0, false, 1);
        // A locked/UAC desktop may be inaccessible. Never switch to it or ask for extra rights.
        if (input == IntPtr.Zero) return;
        try
        {
            if (DesktopName(input) == expected) throw new InvalidOperationException("Refusing to test on the user's input desktop");
        }
        finally { CloseDesktop(input); }
    }
    public static WesoTestDesktop Start(string name, string executable, string arguments, string directory)
    {
        if (!name.StartsWith("WesoTest-", StringComparison.Ordinal)) throw new ArgumentException("Invalid test desktop name");
        var session = new WesoTestDesktop { Name = name };
        ProcessInfo child = new ProcessInfo();
        try
        {
            // No DESKTOP_SWITCHDESKTOP right, no shared handle inheritance.
            session.desktop = CreateDesktopW(name, IntPtr.Zero, IntPtr.Zero, 0, 0x7f, IntPtr.Zero);
            Check(session.desktop != IntPtr.Zero, "Create private test desktop");
            session.job = CreateJobObjectW(IntPtr.Zero, null);
            Check(session.job != IntPtr.Zero, "Create test process job");
            var limits = new ExtendedLimits();
            limits.basic.flags = 0x2000 | 0x20; // KILL_ON_JOB_CLOSE | PRIORITY_CLASS
            limits.basic.priority = 0x4000; // BELOW_NORMAL for every test descendant, including Java.
            Check(SetInformationJobObject(session.job, 9, ref limits, (uint)Marshal.SizeOf(typeof(ExtendedLimits))), "Set test job limits");
            var startup = new StartupInfo();
            startup.cb = (uint)Marshal.SizeOf(typeof(StartupInfo));
            startup.desktop = @"WinSta0\" + name;
            startup.flags = 0x1 | 0x80; // Hidden helper; no launch feedback cursor.
            startup.show = 0;
            Check(CreateProcessW(executable, new StringBuilder("\"" + executable + "\" " + arguments), IntPtr.Zero, IntPtr.Zero,
                false, 0x4 | 0x08000000, IntPtr.Zero, directory, ref startup, out child), "Start isolated test worker");
            session.process = child.process;
            session.ProcessId = child.id;
            // Assign before resuming so no Gradle/JVM descendant can escape cleanup.
            Check(AssignProcessToJobObject(session.job, child.process), "Contain test process tree");
            Check(ResumeThread(child.thread) != UInt32.MaxValue, "Resume isolated test worker");
            return session;
        }
        catch
        {
            if (child.process != IntPtr.Zero) TerminateProcess(child.process, 1);
            session.Dispose();
            throw;
        }
        finally { if (child.thread != IntPtr.Zero) CloseHandle(child.thread); }
    }
    public bool Poll()
    {
        IntPtr input = OpenInputDesktop(0, false, 1);
        if (input != IntPtr.Zero)
        {
            try
            {
                if (DesktopName(input) == Name) throw new InvalidOperationException("Test desktop unexpectedly became active");
                bool leaked = false;
                EnumWindow inspect = delegate(IntPtr window, IntPtr unused)
                {
                    uint pid;
                    GetWindowThreadProcessId(window, out pid);
                    IntPtr owner = OpenProcess(0x1000, false, pid);
                    if (owner == IntPtr.Zero) return true;
                    try { bool belongs; if (IsProcessInJob(owner, job, out belongs) && belongs) leaked = true; }
                    finally { CloseHandle(owner); }
                    return !leaked;
                };
                EnumDesktopWindows(input, inspect, IntPtr.Zero);
                if (leaked) throw new InvalidOperationException("A test window escaped to the user's desktop");
            }
            finally { CloseDesktop(input); }
        }
        uint result = WaitForSingleObject(process, 0);
        if (result == UInt32.MaxValue) Check(false, "Wait for test worker");
        return result == 0;
    }
    public uint ExitCode()
    {
        uint code;
        Check(GetExitCodeProcess(process, out code), "Read test exit code");
        return code;
    }
    public static void Probe(string expected)
    {
        AssertIsolated(expected);
        IntPtr window = CreateWindowExW(0, "STATIC", "WESO private desktop probe", 0x10cf0000,
            20, 20, 240, 120, IntPtr.Zero, IntPtr.Zero, IntPtr.Zero, IntPtr.Zero);
        Check(window != IntPtr.Zero, "Create isolated probe window");
        try
        {
            SetForegroundWindow(window); // Must not activate anything on the user's desktop.
            System.Threading.Thread.Sleep(750);
            AssertIsolated(expected);
        }
        finally { DestroyWindow(window); }
    }
    public void Dispose()
    {
        if (job != IntPtr.Zero) { TerminateJobObject(job, 1); CloseHandle(job); job = IntPtr.Zero; }
        if (process != IntPtr.Zero) { WaitForSingleObject(process, 5000); CloseHandle(process); process = IntPtr.Zero; }
        if (desktop != IntPtr.Zero) { CloseDesktop(desktop); desktop = IntPtr.Zero; }
    }
}
