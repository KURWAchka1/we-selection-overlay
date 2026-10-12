package org.marshal.wesotest.slices;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import jdk.jfr.Threshold;

@Name("weso.diagnostic.Call")
@Label("WESO Test Method Interval")
@Category("WESO Test Diagnostics")
@StackTrace(false)
@Threshold("0 ns")
public final class CallEvent extends Event {
    public String operation;
    public long id, parentId, wallNanos, cpuNanos, allocatedBytes;
    public long coreMaxBefore, coreMaxAfter;
    public boolean failed;
    transient CallEvent parent;
    transient long started, cpuStarted, allocatedStarted;
}
