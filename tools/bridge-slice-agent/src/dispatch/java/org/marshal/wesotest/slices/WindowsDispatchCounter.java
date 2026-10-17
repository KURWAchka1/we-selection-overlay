package org.marshal.wesotest.slices;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import static java.lang.foreign.ValueLayout.*;

/** Current owned fixture thread only. No ETW session or hardware-counter configuration. */
public final class WindowsDispatchCounter implements AutoCloseable {
    private final long owner = Thread.currentThread().threadId();
    private final Arena arena;
    private final MethodHandle read, disable;
    private final MemorySegment handle, data;
    private boolean closed;
    public record Snapshot(long switches, long cycles, long reasons, int retries) { }

    public WindowsDispatchCounter() {
        if (!Boolean.getBoolean("weso.bridge.fixture") || !Boolean.getBoolean("weso.bridge.dispatchDiagnostics")
                || !System.getProperty("os.name").startsWith("Windows") || !System.getProperty("os.arch").equals("amd64"))
            throw new IllegalStateException("Explicit Windows x64 fixture required");
        arena = Arena.ofConfined();
        try {
            var lookup = SymbolLookup.libraryLookup("Kernel32.dll", arena);
            var linker = Linker.nativeLinker();
            var current = linker.downcallHandle(lookup.find("GetCurrentThread").orElseThrow(), FunctionDescriptor.of(ADDRESS));
            var enable = linker.downcallHandle(lookup.find("EnableThreadProfiling").orElseThrow(), FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_LONG, ADDRESS));
            read = linker.downcallHandle(lookup.find("ReadThreadProfilingData").orElseThrow(), FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
            disable = linker.downcallHandle(lookup.find("DisableThreadProfiling").orElseThrow(), FunctionDescriptor.of(JAVA_INT, ADDRESS));
            var out = arena.allocate(ADDRESS);
            data = arena.allocate(288, 8); // WinSDK PERFORMANCE_DATA v1: 32-byte header + 16 x 16-byte counters.
            int error = (int) enable.invokeExact((MemorySegment) current.invokeExact(), 1, 0L, out);
            if (error != 0) throw new IllegalStateException("EnableThreadProfiling error=" + error);
            handle = out.get(ADDRESS, 0);
        } catch (Throwable failure) { arena.close(); throw new IllegalStateException("Dispatch profiling unavailable", failure); }
    }
    private void check() {
        if (Thread.currentThread().threadId() != owner || closed)
            throw new IllegalStateException("Wrong or closed dispatch owner");
    }
    public Snapshot read() {
        check(); data.fill((byte) 0);
        data.set(JAVA_SHORT, 0, (short) 288); data.set(JAVA_BYTE, 2, (byte) 1);
        try {
            int error = (int) read.invokeExact(handle, 1, data);
            if (error != 0 || data.get(JAVA_SHORT, 0) != 288 || data.get(JAVA_BYTE, 2) != 1 || data.get(JAVA_BYTE, 3) != 0)
                throw new IllegalStateException("Invalid dispatch snapshot error=" + error);
            return new Snapshot(Integer.toUnsignedLong(data.get(JAVA_INT, 4)), data.get(JAVA_LONG, 16),
                    data.get(JAVA_LONG, 8), data.get(JAVA_INT, 24));
        } catch (Throwable failure) { throw new IllegalStateException("Dispatch read failed", failure); }
    }
    @Override public void close() {
        check();
        try {
            int error = (int) disable.invokeExact(handle);
            if (error != 0) throw new IllegalStateException("DisableThreadProfiling error=" + error);
            closed = true;
        } catch (Throwable failure) { throw new IllegalStateException("Dispatch close failed", failure); }
        finally { arena.close(); }
    }
}
