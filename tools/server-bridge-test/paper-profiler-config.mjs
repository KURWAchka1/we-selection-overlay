import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {join} from 'node:path';

export function profilerConfigLibraries(directory) {
  return [
    {path:'libraries/org/yaml/snakeyaml/2.6/snakeyaml-2.6.jar',sha256:'c8f7a98e7394adda02f6317249710e4d1b4c7a25aa8c7eace0c2eea52eb8bf85'},
    {path:'libraries/com/google/code/gson/gson/2.14.0/gson-2.14.0.jar',sha256:'2cbd119bf1961c28788310963dc80ba65f58cdeec1dd139c8bdb1240faa2c36f'}
  ].map(artifact => {
    const file = join(directory,artifact.path);
    assert.equal(createHash('sha256').update(readFileSync(file)).digest('hex'),artifact.sha256,'Unexpected fixture configuration reader library');
    return {...artifact,file};
  });
}

export function verifyEffectiveProfilerConfigurations(configurations, enabled) {
  assert.equal(configurations.length,4); assert.equal(enabled.length,4);
  const normalized = configurations.map((configuration,i) => {
    const copy = structuredClone(configuration);
    assert.equal(copy._version,31); assert.equal(copy.spark.enabled,enabled[i]);
    assert.equal(copy.spark['enable-immediately'],false);
    delete copy.spark.enabled;
    return copy;
  });
  for (const configuration of normalized) assert.deepEqual(configuration,normalized[0],'Non-profiler Paper configuration differs across matched arms');
  return createHash('sha256').update(JSON.stringify(normalized[0])).digest('hex');
}
