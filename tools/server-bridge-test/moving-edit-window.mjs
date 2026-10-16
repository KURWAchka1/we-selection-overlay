import assert from 'node:assert/strict';
import { validateGameplayActivity } from './gameplay-workload.mjs';
import { verifyEditLoadWindow } from './edit-load-window.mjs';
import { channel, decode } from './wire-codec.mjs';

export const movingEditKinds = Object.freeze(['move-x','move-y','move-z','expand-x','expand-y','expand-z','contract-x','restore']);

export function movingEditTarget(index, step) {
  assert(Number.isInteger(index) && index >= 0 && index < 8);
  assert(Number.isSafeInteger(step) && step >= 0);
  const x=100+index*16+(index===0 ? 1 : 0), bounds=[x,64,20,x+5,70,27], phase=step%movingEditKinds.length;
  if (phase<3) { bounds[phase]++; bounds[phase+3]++; }
  else if (phase<6) bounds[phase]++;
  else if (phase===6) bounds[3]--;
  return {kind:movingEditKinds[phase],bounds};
}

export function verifyMovingEditTargets(index, targets, expectedCounts) {
  assert(Array.isArray(targets) && targets.length>=30 && targets.length<=256,'Invalid ordinary edit sequence length');
  const counts=Object.fromEntries(movingEditKinds.map(k=>[k,0]));
  for (const [step,bounds] of targets.entries()) {
    const expected=movingEditTarget(index,step+1);
    assert.deepEqual(bounds,expected.bounds,'Wire acknowledgment differs from planned move/resize');
    counts[expected.kind]++;
  }
  assert.deepEqual(counts,expectedCounts,'Operation coverage differs from actual wire targets');
}

export function verifyMovingEditWindow(window) {
  verifyEditLoadWindow(window);
  const {moving,ticks,actors,active}=window;
  assert(moving,'Missing moving edit evidence');
  validateGameplayActivity(ticks,{players:32,samples:400,movementPackets:moving.movementPackets});
  assert.equal(ticks.gameplay.editsEnabled,true,'WorldEdit activity paused');
  for (const values of [moving.teleports,moving.bridgeFrames]) {
    assert.equal(values?.length,32); assert(values.every(n=>Number.isSafeInteger(n)&&n>=0));
  }
  assert(moving.teleports.every(n=>n===0),'Measured movement corrected by server');
  assert(moving.bridgeFrames.slice(28).every(n=>n===0),'CUI-only actor received bridge data');
  if (!active) assert(moving.bridgeFrames.every(n=>n===0),'Unsolicited idle bridge frames');
  for (let i=0;i<32;i++) {
    const actor=actors[i]; assert.equal(actor.name,`WesoLoad${String(i).padStart(2,'0')}`);
    assert.equal(actor.role,i<8 ? 'editor' : i<16 ? 'reader' : i<28 ? 'flood' : 'silent');
    if (i<8) {
      assert.deepEqual(Object.keys(actor.editKinds).sort(),[...movingEditKinds].sort());
      assert(Object.values(actor.editKinds).every(n=>Number.isSafeInteger(n)&&n>=0));
      assert.equal(Object.values(actor.editKinds).reduce((a,b)=>a+b,0),actor.applied);
      if (active) assert(Object.values(actor.editKinds).every(n=>n>=3),'An editor missed a move/resize kind');
    }
  }
}

export function reconcileMovingEditReplies(events, final) {
  const frames=events.filter(e=>e.type==='payload' && e.channel===channel).map(event=>{
    const bytes=Buffer.from(event.data,'base64'); assert.equal(bytes.toString('base64'),event.data);
    return {bytes,value:decode(bytes)};
  });
  const count=predicate=>frames.filter(f=>predicate(f.value)).length;
  assert.equal(frames.length,final.replies,'Host/wire lifetime replies differ');
  assert.equal(frames.reduce((n,f)=>n+f.bytes.length,0),final.bytes,'Host/wire lifetime bytes differ');
  assert.equal(final.admitted,final.replies,'Admitted fixture request lost');
  assert.equal(count(v=>v.opcode===2 && v.status===0),28,'Missing negotiations');
  assert.equal(count(v=>v.opcode===4 && v.status===0),final.reads,'Metadata host/wire reads differ');
  assert.equal(count(v=>v.opcode===7 && v.status===0),final.captures,'Capture host/wire counts differ');
  // Host commits exclude core-rejected foreign revisions, cached replay and early denial.
  assert.equal(count(v=>v.opcode===9),final.commits+3,'Unexpected host/wire commit boundary');
  assert.equal(final.commits,final.applied+1,'Expected one host-side stale conflict');
  assert.equal(count(v=>v.opcode===9 && v.commitStatus===0 && v.status===0),final.applied+1,'Mutation replay or missing success');
  assert.equal(count(v=>v.opcode===9 && v.commitStatus===1),2,'Missing stale/foreign conflicts');
  assert.equal(count(v=>v.opcode===9 && v.commitStatus===2 && v.status===3 && !v.bounds),1,'Missing nondisclosing revocation');
  assert.equal(28+final.reads+final.captures+final.commits+3,final.replies,'Unexpected reply kind');
  return {replies:frames.length,bytes:final.bytes,applied:final.applied,conflicts:2,denied:1,cachedReplay:1};
}

export function movingEditHostCounters(log) {
  assert.equal(typeof log,'string'); assert(log.length<=16*1024*1024,'Fixture log too large');
  const lines=log.split(/\r?\n/);
  const number=(prefix,key)=>{
    const line=lines.findLast(s=>s.includes(prefix)); assert(line,`Missing raw host metric: ${prefix}`);
    const match=line.match(new RegExp(`${key}=(\\d+)`)); assert(match,`Missing raw host field: ${key}`);
    const value=Number(match[1]); assert(Number.isSafeInteger(value),'Invalid raw host counter'); return value;
  };
  const n=key=>number('connections=',key),s=key=>number('Selection metadata reads=',key),e=key=>number('Cuboid captures=',key),q=key=>number('Queue pending=',key);
  return {connections:n('connections'),admitted:n('admitted'),throttled:n('throttled'),reads:s('reads'),replies:s('replies'),
    captures:e('captures'),commits:e('commits'),applied:e('applied'),pending:q('pending'),peak:q('peak'),tasks:q('tasks'),bytes:s('reply bytes'),expired:q('expired')};
}
