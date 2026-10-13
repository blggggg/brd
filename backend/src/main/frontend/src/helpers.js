export const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
export const same=(a,b)=>String(a)===String(b);
export const code=id=>'BRD-'+String(id).padStart(6,'0');
export const canWrite=(u,d)=>!!u&&(u.role==='ADMIN'||(u.role==='UPLOADER'&&(!d||same(u.id,d.owner_id))));
export const size=n=>Number(n)<1048576?(Number(n)/1024).toFixed(1)+' KB':(Number(n)/1048576).toFixed(1)+' MB';
// Unlike randomUUID(), getRandomValues() is available on non-secure HTTP origins.
// Keep this key on the form so retries of the same payload reuse the same key.
export function newUploadKey(source=globalThis.crypto){
  if(typeof source?.getRandomValues!=='function')throw new Error('浏览器无法生成上传标识，请使用新版浏览器或 HTTPS 访问。');
  const bytes=new Uint8Array(16);source.getRandomValues(bytes);
  return Array.from(bytes,b=>b.toString(16).padStart(2,'0')).join('');
}
