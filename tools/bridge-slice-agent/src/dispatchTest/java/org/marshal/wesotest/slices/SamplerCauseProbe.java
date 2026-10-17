package org.marshal.wesotest.slices;

import java.nio.file.*;
import java.time.Duration;
import jdk.jfr.*;

/** Fixed CPU work; sampler off/default/dense interventions, not a server benchmark. */
public final class SamplerCauseProbe {
    @Name("weso.diagnostic.SamplerCause") @StackTrace(false)
    static class Work extends Event { long id, cycles, switches, reasons, iterations, wallNanos; }
    private static volatile long sink;
    private static long work(long seed) {
        for (int i=0;i<250_000;i++) { seed ^= seed >>> 12; seed ^= seed << 25; seed ^= seed >>> 27; seed *= 2685821657736338717L; }
        return seed;
    }
    public static void main(String[] args) throws Exception {
        Path directory=Path.of(args[0]); Files.createDirectory(directory);
        try (var counter=new WindowsDispatchCounter()) {
            for(int i=0;i<500;i++) sink=work(sink+i+1);
            int round=0;
            for(int period:args.length==2 ? new int[]{Integer.parseInt(args[1])} : new int[]{0,20,1,1,20,0}) {
                long switches=0,suspended=0,totalCycles=0,maxWall=0;
                long[] reasonCounts=new long[64];
                try(var recording=new Recording()) {
                    recording.enable(Work.class); recording.enable("jdk.GCPhasePause").withoutThreshold();
                    if(period==0) {recording.disable("jdk.ExecutionSample");recording.disable("jdk.NativeMethodSample");}
                    else {recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(period));recording.enable("jdk.NativeMethodSample").withPeriod(Duration.ofMillis(period));}
                    recording.start();
                    for(int i=0;i<500;i++) {
                        var event=new Work();event.id=i;event.iterations=250_000;
                        var before=counter.read();event.begin();long at=System.nanoTime();sink=work(sink+i+1);
                        event.wallNanos=System.nanoTime()-at;event.end();var after=counter.read();
                        event.cycles=after.cycles()-before.cycles();event.switches=(after.switches()-before.switches())&0xffffffffL;
                        event.reasons=after.reasons();event.commit();
                        totalCycles+=event.cycles;switches+=event.switches;maxWall=Math.max(maxWall,event.wallNanos);
                        if((event.reasons&((1L<<5)|(1L<<12)))!=0)suspended++;
                        for(int bit=0;bit<64;bit++)if((event.reasons&(1L<<bit))!=0)reasonCounts[bit]++;
                    }
                    recording.stop();recording.dump(directory.resolve("sampler-"+(++round)+".jfr"));
                }
                System.out.printf("{\"round\":%d,\"periodMillis\":%d,\"calls\":500,\"iterationsPerCall\":250000,\"switches\":%d,\"suspendedIntervals\":%d,\"cycles\":%d,\"maxWallNanos\":%d,\"reasonCounts\":[",
                    round,period,switches,suspended,totalCycles,maxWall);
                for(int bit=0;bit<64;bit++){if(bit>0)System.out.print(',');System.out.print(reasonCounts[bit]);}System.out.println("]}");
            }
        }
    }
}
