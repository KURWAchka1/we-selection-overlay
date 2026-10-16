import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, rmSync } from 'node:fs';
import { delimiter, join, relative, resolve, sep } from 'node:path';
import test from 'node:test';

const root = resolve(import.meta.dirname, '../..'), java = process.env.WESO_TEST_JAVA;
assert(java, 'Explicit WESO_TEST_JAVA required');
function invoke(args) {
  return spawnSync(java, ['-Xmx256m','-XX:ActiveProcessorCount=2',...args],
    {windowsHide:true, encoding:'utf8', timeout:30000, maxBuffer:1024*1024});
}
function recording(t, kind) {
  const build = join(root,'build'); mkdirSync(build,{recursive:true});
  const directory = mkdtempSync(join(build,'edit-slice-probe-'));
  t.after(() => {
    const inside = relative(build,resolve(directory)); assert(inside.startsWith('edit-slice-probe-') && !inside.includes(sep));
    rmSync(directory,{recursive:true,force:true});
  });
  const path = join(directory,'probe.jfr');
  const result = invoke(['--class-path',[
    join(root,'server-bridge/paper-test-fixture/build/libs/weso-paper-test-fixture.jar'),
    join(root,'tools/bridge-slice-agent/build/libs/bridge-slice-bootstrap.jar')].join(delimiter),
    join(import.meta.dirname,'JfrEditSliceProbe.java'),path,...(kind?[kind]:[])]);
  assert.equal(result.status,0,result.stderr || result.error?.message); return path;
}
function read(path,...args) { return invoke([join(import.meta.dirname,'JfrBridgeSlices.java'),path,'edit',...args]); }
test('edit reader separates setup, active and zero-work windows by exact JFR markers', t => {
  const path = recording(t);
  for (const [args,count] of [[[],15],[['active','2'],4],[['idle','2'],0]]) {
    const result = read(path,...args); assert.equal(result.status,0,result.stderr);
    const [totals,...intervals] = result.stdout.trim().split(/\r?\n/).map(JSON.parse);
    assert.equal(totals.calls,count); assert.equal(totals.groups.reduce((n,g)=>n+g.count,0),count);
    if (count === 4) {
      assert.equal(totals.groups.find(g=>g.operation==='apply').count,2);
      assert.equal(intervals.length,2); assert(intervals.every(i=>i.children.length===1 && i.children[0].operation==='apply'));
    }
  }
});
test('missing or duplicated phase markers and missing edit operations fail before summaries', t => {
  const path = recording(t);
  for (const result of [read(path,'active','3'),read(path,'wrong','2'),
    read(recording(t,'duplicate'),'active','2'),read(recording(t,'missing'))]) {
    assert.notEqual(result.status,0); assert.equal(result.stdout,'');
  }
});
test('tick attribution counts root drains, not nested apply calls', t => {
  const path = recording(t);
  const result = invoke([join(import.meta.dirname,'JfrTickTimeline.java'),path,'active','2',
    '2026-10-14T00:00:00Z','2026-10-14T00:00:01Z']);
  assert.equal(result.status,0,result.stderr);
  const rows = result.stdout.trim().split(/\r?\n/).map(line=>JSON.parse(line));
  assert.equal(rows.length,2);
  assert(rows.every(row=>row.inferredDrainCount>=1 && row.inferredDrainOverlapMillis>0
    && row.inferredDrainOverlapMillis<=row.paperMillis && row.inferredSafepointBeginMillis>=0));
});

test('drain-only reader requires exact roots and cannot silently accept nested or incomplete full diagnostics', t => {
  const path=recording(t,'drain');
  const drainRead=(file,...args)=>invoke([join(import.meta.dirname,'JfrBridgeSlices.java'),file,'drain',...args]);
  for (const [args,count] of [[[],3],[['active','2'],2],[['idle','2'],0]]) {
    const result=drainRead(path,...args); assert.equal(result.status,0,result.stderr);
    const [totals,...intervals]=result.stdout.trim().split(/\r?\n/).map(JSON.parse);
    assert.equal(totals.calls,count); assert(totals.groups.every(g=>g.operation==='drain'));
    assert(intervals.every(i=>i.children.length===0));
  }
  for (const result of [read(path),drainRead(recording(t)),drainRead(path,'active','3'),
    drainRead(recording(t,'drain-parent')),drainRead(recording(t,'drain-failed'))]) {
    assert.notEqual(result.status,0); assert.equal(result.stdout,'');
  }
});
