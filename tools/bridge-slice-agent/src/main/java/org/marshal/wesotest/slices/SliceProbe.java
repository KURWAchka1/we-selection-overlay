package org.marshal.wesotest.slices;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;

/** Test JVM only. No actor, frame, region or receiver survives a completed call. */
public final class SliceProbe {
    private static final com.sun.management.ThreadMXBean THREADS =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static final ThreadLocal<CallEvent> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<Long> IDS = ThreadLocal.withInitial(() -> 0L);
    private static final ClassValue<Field> HIGHWATER = new ClassValue<>() {
        @Override protected Field computeValue(Class<?> type) {
            try {
                Field field = type.getDeclaredField("maxSliceNanos");
                field.setAccessible(true);
                return field;
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
    };

    public static CallEvent begin(String operation, Object receiver) {
        CallEvent event = new CallEvent();
        event.operation = operation;
        event.parent = CURRENT.get();
        event.id = IDS.get() + 1; IDS.set(event.id);
        event.parentId = event.parent == null ? 0 : event.parent.id;
        event.coreMaxBefore = operation.equals("drain") ? highwater(receiver) : -1;
        event.cpuStarted = THREADS.getCurrentThreadCpuTime();
        event.allocatedStarted = THREADS.getThreadAllocatedBytes(Thread.currentThread().threadId());
        CURRENT.set(event);
        event.begin();
        event.started = System.nanoTime();
        return event;
    }

    public static void end(CallEvent event, Object receiver, boolean failed) {
        event.wallNanos = Math.max(0, System.nanoTime() - event.started);
        event.end();
        event.cpuNanos = Math.max(0, THREADS.getCurrentThreadCpuTime() - event.cpuStarted);
        event.allocatedBytes = Math.max(0, THREADS.getThreadAllocatedBytes(Thread.currentThread().threadId()) - event.allocatedStarted);
        event.coreMaxAfter = event.operation.equals("drain") ? highwater(receiver) : -1;
        event.failed = failed;
        if (CURRENT.get() != event) throw new IllegalStateException("Unbalanced diagnostic method interval");
        if (event.parent == null) CURRENT.remove(); else CURRENT.set(event.parent);
        event.parent = null;
        event.commit();
    }

    private static long highwater(Object receiver) {
        try { return HIGHWATER.get(receiver.getClass()).getLong(receiver); }
        catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }
}
