package org.marshal.wesotest.slices;

/** Separate recorder: historical root-only bootstrap and its metrics remain unchanged. */
public final class DispatchProbe {
    private static WindowsDispatchCounter counter;
    private static WindowsDispatchCounter.Snapshot before;
    private static DispatchEvent current;
    private static long owner, completed;

    public static CallEvent begin(String operation, Object receiver) {
        if (!operation.equals("drain") || current != null) throw new IllegalStateException("Unexpected dispatch root");
        if (counter == null) { counter = new WindowsDispatchCounter(); owner = Thread.currentThread().threadId(); }
        if (owner != Thread.currentThread().threadId()) throw new IllegalStateException("Dispatch owner changed");
        DispatchEvent event = new DispatchEvent(); event.begin();
        long at = System.nanoTime(); before = counter.read(); event.readBeforeNanos = System.nanoTime() - at;
        current = event;
        CallEvent call = SliceProbe.begin(operation, receiver); event.id = call.id;
        return call;
    }
    public static void end(CallEvent call, Object receiver, boolean failed) {
        DispatchEvent event = current;
        if (event == null || event.id != call.id) throw new IllegalStateException("Unbalanced dispatch root");
        SliceProbe.end(call, receiver, failed);
        long at = System.nanoTime(); var after = counter.read(); event.readAfterNanos = System.nanoTime() - at;
        event.cycles = after.cycles() - before.cycles();
        event.switches = (after.switches() - before.switches()) & 0xffff_ffffL;
        event.reasons = after.reasons(); event.retries = after.retries();
        if (event.cycles < 0 || event.readBeforeNanos < 0 || event.readAfterNanos < 0 || event.retries < 0)
            throw new IllegalStateException("Invalid dispatch delta");
        event.end(); event.commit(); current = null; before = null; completed++;
    }
    public static void close() {
        if (counter == null) return;
        if (current != null || owner != Thread.currentThread().threadId()) throw new IllegalStateException("Unsafe dispatch cleanup");
        counter.close(); counter = null;
        System.out.println("WESO_DISPATCH_CLOSED completed=" + completed);
    }
}
