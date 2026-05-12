const fs = require('fs');
const { WASI } = require('wasi');

const wasi = new WASI({
  version: 'preview1',
  args: [],
  env: { CDD_INPUT: 'does-not-exist.json' },
  preopens: {
    '/': '/'
  }
});

(async () => {
  const wasmBuffer = fs.readFileSync('build/compileSync/wasmWasi/main/productionExecutable/optimized/cdd-kotlin.wasm');
  const wasmModule = await WebAssembly.compile(wasmBuffer);
  
  const instance = await WebAssembly.instantiate(wasmModule, {
    wasi_snapshot_preview1: wasi.wasiImport
  });
  
  wasi.initialize(instance);
  
  try {
      console.log('Calling to_docs_json...');
      const res = instance.exports.to_docs_json();
      console.log('Result:', res);
  } catch (e) {
      console.error('Execution failed:', e);
  }
})();
