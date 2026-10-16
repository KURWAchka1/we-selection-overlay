import assert from 'node:assert/strict';

// Fixed before the guard-band trial; these are local acceptance gates, not hard deadlines.
export function guardAcceptance(results, runs) {
  assert.equal(results.length,4);
  assert.equal(runs.length,4);
  const thresholds={drainP95Millis:.5,minimumActorRatio:.95,maximumLatencyP95DeltaMillis:100};
  const pairs=[[0,1],[3,2]].map(([previous,candidate])=>{
    const before=results[previous],after=results[candidate];
    assert.equal(before.variant,'previous'); assert.equal(after.variant,'candidate');
    const ordinary=run=>{
      const actors=run.active.actors.filter(a=>a.role==='editor' || a.role==='reader');
      assert.equal(actors.length,16);
      assert.equal(actors.filter(a=>a.role==='editor').length,8);
      assert.equal(new Set(actors.map(a=>a.name)).size,16);
      return new Map(actors.map(a=>[a.name,a]));
    };
    const oldActors=ordinary(runs[previous]),newActors=ordinary(runs[candidate]);
    const actors=[...oldActors.values()].map(old=>{
      const next=newActors.get(old.name); assert(next); assert.equal(next.role,old.role);
      const key=old.role==='editor' ? 'applied' : 'reads',oldCount=old[key],newCount=next[key];
      assert(Number.isSafeInteger(oldCount) && oldCount>0);
      assert(Number.isSafeInteger(newCount) && newCount>=0);
      const ratio=newCount/oldCount;
      return {name:old.name,role:old.role,previous:oldCount,candidate:newCount,ratio,accepted:ratio>=thresholds.minimumActorRatio};
    });
    const drainP95Millis=after.slices.phases.find(p=>p.phase==='active').totals.groups.find(g=>g.operation==='drain').p95Millis;
    const latencyP95DeltaMillis=after.latencyP95-before.latencyP95;
    assert(Number.isFinite(drainP95Millis) && drainP95Millis>=0);
    assert(Number.isFinite(latencyP95DeltaMillis));
    const gates={drainP95:drainP95Millis<=thresholds.drainP95Millis,
      actorProgress:actors.every(a=>a.accepted),latency:latencyP95DeltaMillis<=thresholds.maximumLatencyP95DeltaMillis};
    return {previousRound:before.round,candidateRound:after.round,drainP95Millis,latencyP95DeltaMillis,
      actors,gates,accepted:Object.values(gates).every(Boolean)};
  });
  return {thresholds,pairs,accepted:pairs.every(p=>p.accepted)};
}
