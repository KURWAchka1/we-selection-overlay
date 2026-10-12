package org.marshal.weaddon_lightposition.bridge.fixture;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import jdk.jfr.Threshold;

/** Test-only public-event bracket; Paper's reported duration can begin earlier. */
@Name("weso.fixture.Tick")
@Label("WESO Fixture Tick")
@Category("WESO Test Fixture")
@StackTrace(false)
@Threshold("0 ns")
public final class FixtureTickEvent extends Event {
    public String phase;
    public int sample;
    public int paperTick;
    public double paperMillis;
    public long bracketCpuNanos;
    public long bracketAllocatedBytes;
}
