import assert from 'node:assert/strict';

const tuples = Object.freeze({
  '1.21.11': Object.freeze({ family: '1.21.11', runtime: 'mc1_21_11', paperBuild: 132, worldedit: '7.4.2',
    paperSha256: '5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba',
    worldeditAlgorithm: 'sha256', worldeditHash: '0ee152b1be5dfb51500505e2bf5a8c9d66f09c7fa484bf9aea384d7e7b459b06' }),
  '26.1.2': Object.freeze({ family: '26.1', runtime: 'mc26_1', paperBuild: 74, worldedit: '7.4.3',
    paperSha256: '1d70b1dab9cf4a6de615209a536f3a45a2186240253c428213ce2188ab95e5f7',
    worldeditAlgorithm: 'sha512', worldeditHash: '43d2f8865a06d63c71d2b8bc0ded66c2c7f5e413db1a64bc2f665db3bf25ec28ae40789884af4bdaf40740a6632196494d2897fa77675f0babfaf79f18400853' }),
  '26.2': Object.freeze({ family: '26.2', runtime: 'mc26_2', paperBuild: 133, worldedit: '7.4.5',
    paperSha256: 'bf1dcf627364f8a631ab15250d240eb5a8e89faaac523b391e1c61367fce9ff1',
    worldeditAlgorithm: 'sha512', worldeditHash: 'a383492fac6bfb4d43a257dfa7b5fc076aae503a71151b463de4fe80e6f3d5fc11209eaf4097baa115f3febf0adc40ca0a1ecda227b8439b429d0a4ba3a63a4f' }),
});

export function gestureTuple(version) {
  assert(Object.hasOwn(tuples, version), 'Unsupported exact native/server tuple');
  return tuples[version];
}
