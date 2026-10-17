package org.marshal.wesotest.slices;

import jdk.jfr.*;

@Name("weso.diagnostic.Dispatch")
@StackTrace(false)
@Threshold("0 ns")
public final class DispatchEvent extends Event {
    public long id, cycles, switches, reasons, readBeforeNanos, readAfterNanos;
    public int retries;
}
