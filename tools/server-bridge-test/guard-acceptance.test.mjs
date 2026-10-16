import assert from 'node:assert/strict';
import test from 'node:test';
import { guardAcceptance } from './guard-acceptance.mjs';

function fixture() {
  const results=['previous','candidate','candidate','previous'].map((variant,i)=>({round:i+1,variant,latencyP95:300,
    slices:{phases:[{phase:'active',totals:{groups:[{operation:'drain',p95Millis:.4}]}}]}}));
  const runs=results.map(()=>({active:{actors:Array.from({length:32},(_,i)=>({name:`actor${i}`,
    role:i<8?'editor':i<16?'reader':i<28?'flood':'silent',applied:i<8?100:0,reads:i>=8&&i<16?100:0}))}}));
  return {results,runs};
}
test('guard accepts both opposite-order pairs at inclusive boundaries',()=>{
  const {results,runs}=fixture();
  for(const i of [1,2]) {
    results[i].slices.phases[0].totals.groups[0].p95Millis=.5; results[i].latencyP95=400;
    for(const a of runs[i].active.actors) {a.applied=Math.floor(a.applied*.95);a.reads=Math.floor(a.reads*.95);}
  }
  const report=guardAcceptance(results,runs);
  assert(report.accepted); assert.equal(report.pairs.length,2);
  assert.deepEqual(report.pairs.map(p=>[p.previousRound,p.candidateRound]),[[1,2],[4,3]]);
  assert(report.pairs.every(p=>p.actors.length===16));
});
test('guard rejects p95 just above target without rounding',()=>{
  const {results,runs}=fixture(); results[2].slices.phases[0].totals.groups[0].p95Millis=.5000001;
  const r=guardAcceptance(results,runs); assert(!r.accepted); assert(!r.pairs[1].gates.drainP95);
});
test('guard rejects starvation of one editor or reader even if aggregate rises',()=>{
  for(const index of [0,15]) {
    const {results,runs}=fixture(); for(const a of runs[1].active.actors){a.applied*=2;a.reads*=2;}
    const a=runs[1].active.actors[index]; a.applied=94;a.reads=94;
    const r=guardAcceptance(results,runs); assert(!r.accepted); assert(!r.pairs[0].gates.actorProgress);
  }
});
test('guard rejects latency degradation in either pair',()=>{
  for(const i of [1,2]) {
    const {results,runs}=fixture(); results[i].latencyP95=400.001;
    const r=guardAcceptance(results,runs);assert(!r.accepted);
  }
});
test('guard fails closed on missing duplicate or mismatched actor evidence',()=>{
  for(const change of [r=>r.active.actors.splice(0,1),
    r=>r.active.actors[1].name=r.active.actors[0].name,
    r=>r.active.actors[1].name='foreign',r=>r.active.actors[0].applied=NaN]) {
    const {results,runs}=fixture(); change(runs[1]); assert.throws(()=>guardAcceptance(results,runs));
  }
});
test('guard rejects missing progress baseline or reversed variants',()=>{
  const {results,runs}=fixture();runs[0].active.actors[0].applied=0;
  assert.throws(()=>guardAcceptance(results,runs));
  results[0].variant='candidate'; assert.throws(()=>guardAcceptance(results,fixture().runs));
});
