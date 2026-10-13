import {test} from 'node:test';import assert from 'node:assert/strict';import {webcrypto} from 'node:crypto';import {esc,same,code,canWrite,size,newUploadKey} from '../src/helpers.js';import {upload} from '../src/api.js';
test('dynamic HTML is escaped',()=>assert.equal(esc('<img onerror="x">&\''),'&lt;img onerror=&quot;x&quot;&gt;&amp;&#39;'));
test('long identifiers are not coerced to unsafe numbers',()=>{assert.equal(same('9007199254740993','9007199254740992'),false);assert.equal(code('12'),'BRD-000012');});
test('only admin or uploader-owner can change a document',()=>{assert.equal(canWrite({id:'1',role:'ADMIN'},{owner_id:'2'}),true);assert.equal(canWrite({id:'1',role:'READER'}),false);assert.equal(canWrite({id:'1',role:'UPLOADER'},{owner_id:'2'}),false);assert.equal(canWrite({id:'1',role:'UPLOADER'},{owner_id:'1'}),true);});
test('file size formatting',()=>assert.equal(size(1048576),'1.0 MB'));
test('upload key works without secure-context randomUUID',()=>{
  const httpCrypto={getRandomValues:a=>webcrypto.getRandomValues(a)};
  const keys=Array.from({length:1000},()=>newUploadKey(httpCrypto));
  for(const key of keys)assert.match(key,/^[0-9a-f]{32}$/);
  assert.equal(new Set(keys).size,1000);
});
test('missing crypto fails explicitly rather than generating a broken key',()=>assert.throws(()=>newUploadKey({}),/浏览器无法生成上传标识/));
test('invalid upload keys are rejected before any network request',async()=>{
  for(const key of [undefined,null,'undefined','short','x'.repeat(81)])await assert.rejects(upload('/brd-uploads',null,key,()=>{}),/上传标识无效/);
});
