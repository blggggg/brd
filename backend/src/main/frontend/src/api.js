export class ApiError extends Error { constructor(status, code, message) { super(message); this.status=status; this.code=code; } }
export async function parse(r) {
  if (!r.ok) { let b={}; try { b=await r.json(); } catch {} throw new ApiError(r.status,b.code||'REQUEST_FAILED',b.message||`请求失败（${r.status}）`); }
  const text=await r.text(); return text ? JSON.parse(text) : null;
}
export async function csrf() { return parse(await fetch('/api/v1/auth/csrf',{credentials:'same-origin'})); }
export async function api(path,method='GET',body) {
  const headers={}; if(method!=='GET'){const c=await csrf(); headers[c.headerName]=c.token;}
  if(body!==undefined) headers['Content-Type']='application/json';
  return parse(await fetch('/api/v1'+path,{method,headers,credentials:'same-origin',body:body===undefined?undefined:JSON.stringify(body)}));
}
export async function upload(path,data,key,onProgress) {
  if(typeof key!=='string'||! /^[a-zA-Z0-9-]{16,80}$/.test(key))throw new Error('上传标识无效，请关闭上传窗口后重试；若仍失败，请刷新页面。');
  const c=await csrf();
  return new Promise((resolve,reject)=>{
    const xhr=new XMLHttpRequest(); xhr.open('POST','/api/v1'+path); xhr.timeout=120000;
    xhr.setRequestHeader(c.headerName,c.token); xhr.setRequestHeader('Idempotency-Key',key);
    xhr.upload.onprogress=e=>{if(e.lengthComputable)onProgress(Math.round(e.loaded/e.total*100));};
    xhr.onload=()=>{let b={};try{b=JSON.parse(xhr.responseText);}catch{} if(xhr.status>=200&&xhr.status<300)resolve(b);else reject(new ApiError(xhr.status,b.code,b.message||'上传失败'));};
    xhr.onerror=()=>reject(new Error('网络中断，重试不会重复生成版本。')); xhr.ontimeout=()=>reject(new Error('上传超时，请重试。')); xhr.send(data);
  });
}
export async function download(path,name) {
  const r=await fetch('/api/v1'+path,{credentials:'same-origin'});if(!r.ok){await parse(r);return;}
  const url=URL.createObjectURL(await r.blob()),a=document.createElement('a');a.href=url;a.download=name;document.body.append(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),30000);
}
