import assert from 'node:assert/strict';
import test from 'node:test';
import {movingEditKinds,movingEditTarget,verifyMovingEditTargets,verifyMovingEditWindow,reconcileMovingEditReplies,movingEditHostCounters} from './moving-edit-window.mjs';
import {channel} from './wire-codec.mjs';

function fixture(active=true) {
  const actors=Array.from({length:32},(_,i)=>({name:`WesoLoad${String(i).padStart(2,'0')}`,role:i<8?'editor':i<16?'reader':i<28?'flood':'silent',
    applied:active&&i<8?40:0,latencies:active&&i<8?Array(40).fill(150):[],reads:active&&i>=8&&i<16?40:0,
    sent:active&&i<28?40:0,frames:i<28?40:0,editKinds:Object.fromEntries(movingEditKinds.map(k=>[k,active&&i<8?5:0]))}));
  const before={reads:0,captures:8,commits:0,applied:0,replies:0,throttled:0};
  return {active,before,after:{...before,reads:active?320:0,commits:active?320:0,applied:active?320:0,replies:active?640:0,
    throttled:active?50:0,pending:0,tasks:0,peak:28},actors,durationMillis:20000,
    ticks:{samples:400,players:32,tickMillis:Array(400).fill(2),p50:2,p95:2,p99:2,max:2,generatedChunks:0,chunkLoads:0,
      ownerCpuMillis:500,ownerAllocatedBytes:500000,usedHeapBytes:10000000,
      gameplay:{version:1,ticks:400,editsEnabled:true,editBatches:20,changedBlocks:10240,entities:16,entityTickDeltas:Array(16).fill(400),invalidMoves:0,acceptedMoves:Array(32).fill(98)}},
    moving:{movementPackets:Array(32).fill(100),teleports:Array(32).fill(0),bridgeFrames:Array.from({length:32},(_,i)=>active&&i<28?40:0)}};
}

test('targets cover all three translation/expansion axes, contraction and restore without cumulative drift', () => {
  for (let index=0;index<8;index++) {
    const kinds=new Set(); let previous;
    for (let step=1;step<=160;step++) {
      const {kind,bounds}=movingEditTarget(index,step); kinds.add(kind);
      assert(bounds.every(Number.isSafeInteger)); assert(bounds.slice(0,3).every((n,i)=>n<=bounds[i+3]));
      assert(bounds[0]>=100+index*16 && bounds[3]<=107+index*16);
      if (previous) assert.notDeepEqual(bounds,previous); previous=bounds;
      assert.deepEqual(movingEditTarget(index,step+8),{kind,bounds});
    }
    assert.deepEqual([...kinds].sort(),[...movingEditKinds].sort());
  }
  for (const args of [[8,0],[-1,0],[0,-1],[0,NaN],[0,Number.MAX_SAFE_INTEGER+1]]) assert.throws(()=>movingEditTarget(...args));
});
test('simultaneous edit, accepted movement and fixed WorldEdit activity pass including quiet phases', () => {
  verifyMovingEditWindow(fixture()); verifyMovingEditWindow(fixture(false));
});
test('stopped movement, entities, world edits, corrections and non-consumer frames fail', () => {
  for (const change of [w=>delete w.moving,w=>w.ticks.gameplay.acceptedMoves[31]=0,w=>w.ticks.gameplay.entityTickDeltas[0]=0,
    w=>w.ticks.gameplay.editBatches--,w=>w.ticks.gameplay.editsEnabled=false,w=>w.ticks.chunkLoads++,
    w=>w.moving.teleports[0]++,w=>w.moving.bridgeFrames[31]++,w=>w.moving.movementPackets[0]=0,w=>w.moving.teleports.pop()]) {
    const w=fixture(); change(w); assert.throws(()=>verifyMovingEditWindow(w));
  }
  const w=fixture(false); w.moving.bridgeFrames[0]=1; assert.throws(()=>verifyMovingEditWindow(w));
});
test('incomplete operation coverage, swapped roles, inflated counters and hidden edit replay fail', () => {
  for (const change of [w=>w.actors[0].editKinds['move-y']=0,w=>w.actors[0].editKinds.extra=1,
    w=>w.actors[0].editKinds['restore']=NaN,w=>w.actors.reverse(),w=>w.after.commits++]) {
    const w=fixture(); change(w); assert.throws(()=>verifyMovingEditWindow(w));
  }
});

function replies() {
  const frame=(opcode,status=0,commitStatus=0)=>{
    const offset=opcode===9?31:30, bytes=Buffer.alloc(opcode===2?49:opcode===4?64:offset+17+(status===0?33:0));
    Buffer.from('5745534f01','hex').copy(bytes); bytes[5]=opcode;
    if (opcode===2) { bytes[38]=status; bytes.writeUInt16BE(1,39); bytes.writeUInt16BE(2,41); }
    else if (opcode===4) bytes[30]=status;
    else { if (opcode===9) bytes[30]=commitStatus; bytes[offset+16]=status; if (status===0) bytes[offset]=1; }
    return {type:'payload',channel,data:bytes.toString('base64')};
  };
  const events=[...Array.from({length:28},()=>frame(2)),frame(4),frame(4),...Array.from({length:10},()=>frame(7)),
    ...Array.from({length:9},()=>frame(9)),frame(9,1,1),frame(9,1,1),frame(9,3,2)];
  return {events,final:{admitted:52,replies:52,bytes:events.reduce((n,e)=>n+Buffer.from(e.data,'base64').length,0),reads:2,captures:10,commits:9,applied:8}};
}

test('independent reply reconciliation distinguishes host apply, pre-host rejection and cached replay', () => {
  const {events,final}=replies();
  assert.deepEqual(reconcileMovingEditReplies(events,final),{replies:52,bytes:final.bytes,applied:8,conflicts:2,denied:1,cachedReplay:1});
});
test('lost replies, inflated host counts, replayed mutation and noncanonical wire evidence fail', () => {
  for (const change of [f=>f.events.pop(),f=>f.final.bytes++,f=>f.final.applied++,f=>f.final.commits++,f=>f.final.captures++,
    f=>f.events[0].data+='!',f=>f.events[0].channel='other:channel',f=>f.events.push(f.events.at(-1))]) {
    const f=replies(); change(f); assert.throws(()=>reconcileMovingEditReplies(f.events,f.final));
  }
});

const metrics='connections=0, admitted=52, throttled=3, malformed=0\nSelection metadata reads=2, replies=52, reply bytes=3600\nCuboid captures=10, commits=9, applied=8\nQueue pending=0, peak=28, queued=55, expired=0, cancelled=0, max wait nanos=100, max slice nanos=200, dispatch tasks=0';
test('raw console metric reconciliation chooses the last complete host snapshot', () => {
  assert.deepEqual(movingEditHostCounters(metrics+'\n'+metrics.replace('admitted=52','admitted=53')),
    {connections:0,admitted:53,throttled:3,reads:2,replies:52,captures:10,commits:9,applied:8,pending:0,peak:28,tasks:0,bytes:3600,expired:0});
});
test('missing malformed and overflowing raw host metrics cannot validate a proof', () => {
  for (const log of ['',metrics.replace('Selection metadata reads=','other='),metrics.replace('applied=8','applied=-1'),
    metrics.replace('reply bytes=3600','reply bytes=9007199254740992'),metrics.replace('dispatch tasks=0','dispatch tasks=bad')])
    assert.throws(()=>movingEditHostCounters(log));
});

test('operation coverage is reconstructed from every wire target, not trusted client counters', () => {
  const targets=Array.from({length:40},(_,n)=>movingEditTarget(0,n+1).bounds),counts=Object.fromEntries(movingEditKinds.map(k=>[k,5]));
  verifyMovingEditTargets(0,targets,counts);
  for (const change of [f=>f.targets.reverse(),f=>f.targets[10][1]++,f=>f.counts['move-y']++,
    f=>f.targets.splice(0,20),f=>f.targets.push(...f.targets)]) {
    const f={targets:structuredClone(targets),counts:{...counts}}; change(f);
    assert.throws(()=>verifyMovingEditTargets(0,f.targets,f.counts));
  }
});
