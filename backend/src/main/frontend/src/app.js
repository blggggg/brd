import './login-motion.css';
import {setupLoginMedia} from './login-media.js';
import {api,upload,download} from './api.js';
import {esc,same,code,canWrite,size,newUploadKey} from './helpers.js';

const root=document.querySelector('#app'),modal=document.querySelector('#modal'),notice=document.querySelector('#notice');
const roles={ADMIN:'管理员',UPLOADER:'上传者',READER:'阅读者'};
const state={user:null,page:0,versionPage:0,search:'',view:'docs',doc:null,versions:[],users:[],load:0};
let projects=[],taskRows=[],spinTimer=null;
let noticeTimer;
const button=(label,action,id='',cls='')=>`<button type="button" class="${cls}" data-action="${action}" data-id="${esc(id)}">${label}</button>`;
const field=(label,name,value='',extra='')=>`<label>${label}<input name="${name}" value="${esc(value)}" ${extra}></label>`;
const date=v=>{if(!v)return '—';const d=new Date(v);return Number.isNaN(d.getTime())?esc(v):d.toLocaleString('zh-CN',{year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit',hour12:false});};
const badge=(text,cls='')=>`<span class="badge ${cls}">${esc(text)}</span>`;
const roleOptions=current=>Object.entries(roles).map(([k,v])=>`<option value="${k}" ${k===current?'selected':''}>${v}</option>`).join('');
function toast(text,error=false){clearTimeout(noticeTimer);notice.textContent=text;notice.className=error?'show error':'show';noticeTimer=setTimeout(()=>notice.className='',7000);}
function failure(e){
  if(e.status===401){modal.close();state.user=null;renderLogin();toast(e.message,true);return;}
  if(e.code==='PASSWORD_CHANGE_REQUIRED'){state.user.must_change=true;renderShell();passwordDialog();return;}
  toast(e.message||String(e),true);
}
function renderLogin(message = '') {
  root.innerHTML = `
    <main class="login">

      <video
        class="login-video"
        muted
        loop
        playsinline
        preload="metadata"
        aria-hidden="true"
      >
        <source src="/wallpaper.mp4" type="video/mp4">
      </video>
      <button type="button" class="login-motion-toggle" aria-label="播放背景动画">播放动画</button>

<div id="brd-login-motion" aria-label="BRD 登录页面">
  <div class="b-page">
    <section class="b-story" aria-label="文档版本管理">
      <div class="b-glow one" aria-hidden="true"></div><div class="b-glow two" aria-hidden="true"></div><div class="b-grid" aria-hidden="true"></div>
      <div class="b-brand"><span class="b-logo">B</span>BRD 文件库<small>WORKSPACE</small></div>
      <div class="b-copy"><p class="b-eyebrow">CLARITY IN EVERY VERSION</p><h1>每一次迭代，<br>都<em>清晰有序。</em></h1><p>让需求有迹可循，让协作始终同步。<br>从一份文档，走向同一个方向。</p></div>
      <div class="b-scene" role="img" aria-label="示意文档卡片轻轻浮动，V3 被明确标记为当前使用版">
        <div class="b-orbit" aria-hidden="true"><div class="b-orbit-dot"></div></div>
        <div class="b-stack"><div class="b-sheet back" aria-hidden="true"><div class="b-sheet-head">VERSION 01</div><div class="b-line"></div></div><div class="b-sheet middle" aria-hidden="true"><div class="b-sheet-head">VERSION 02</div><div class="b-line"></div></div><div class="b-sheet front"><div class="b-sheet-head"><span class="b-doc-icon"><i data-lucide="file-text" aria-hidden="true"></i></span><span>BRD-000128</span></div><h3>客户平台 · 业务需求</h3><div class="b-sheet-foot"><span class="b-status">V3 · 当前使用版</span><span>所有版本，清晰可溯</span></div></div></div>
        <div class="b-chip left"><i data-lucide="history" aria-hidden="true"></i><span>每次变更都有记录</span></div><div class="b-chip right"><i data-lucide="check-check" aria-hidden="true"></i><span>唯一当前版本</span></div>
      </div>
      <div class="b-story-bottom"><span><i data-lucide="layers" aria-hidden="true"></i>版本归档</span><span><i data-lucide="git-branch" aria-hidden="true"></i>变更追溯</span><span><i data-lucide="users" aria-hidden="true"></i>团队协作</span></div>
    </section>
    <section class="b-login" aria-label="登录">
      
      <div class="b-form-wrap"><p class="b-greeting">WELCOME BACK</p><h2>欢迎回到工作空间</h2><p class="b-sub">登录 BRD，继续推进你的下一次迭代。</p>
        ${message ? '<p class="hint">' + esc(message) + '</p>' : ''}<form class="b-form" data-form="login">
          <label class="b-label" for="brd-demo-user">账号</label><div class="b-field"><i data-lucide="user-round" aria-hidden="true"></i><input id="brd-demo-user" name="username" required maxlength="40" autocomplete="username" aria-label="用户名" placeholder="请输入用户名"></div>
          <label class="b-label" for="brd-demo-password">密码</label><div class="b-field"><i data-lucide="lock-keyhole" aria-hidden="true"></i><input id="brd-demo-password" name="password" type="password" required autocomplete="current-password" aria-label="密码" placeholder="请输入密码"><button type="button" class="b-eye cursor-interaction" aria-label="显示密码"><i data-lucide="eye" aria-hidden="true"></i></button></div>
          <button class="b-submit cursor-interaction" type="submit"><span>登录工作空间</span><i data-lucide="arrow-right" aria-hidden="true"></i></button>
          <p class="form-error b-form-error" role="alert"></p>
        </form>
        <button type="button" class="project-entry" data-action="project-picker"><span class="project-entry-icon">✦</span><span><strong>项目随机抽取</strong><small>无需登录 · 维护项目名单并随机选取</small></span><span class="project-entry-arrow">→</span></button>
      </div>
      <p class="b-bottom"><i data-lucide="shield-check" aria-hidden="true"></i>账号由管理员分配 · 不开放自助注册</p>
    </section>
  </div>
</div>
    </main>`;
  setupLoginMedia(root.querySelector('main.login'));
}
async function projectPicker(){
  state.load++;try{projects=await api('/projects');}catch(e){renderLogin();toast('项目名单暂不可用：'+e.message,true);return;}
  root.innerHTML=`<main class="picker-page"><header class="picker-header"><button type="button" class="picker-back" data-action="picker-back">← 返回登录</button><div class="picker-brand"><span class="logo">B</span><span>BRD 文件库<small>PROJECT DRAW</small></span></div><span class="picker-pill">无需登录</span></header><section class="picker-main"><div class="picker-intro"><p class="eyebrow">A LITTLE MOMENT OF SURPRISE</p><h1>今天，开启哪个项目？</h1><p>把项目放进名单，交给随机抽取决定下一站。</p></div><section class="draw-card"><div class="draw-orbit" aria-hidden="true"></div><span class="draw-label">随机选中的项目</span><div class="draw-result" aria-live="polite">准备好了吗？</div><p class="draw-hint">当前共有 <strong>${projects.length}</strong> 个项目</p><button class="draw-button" data-action="draw" ${projects.length?'':'disabled'}>✦ &nbsp; 开始抽取</button></section><div class="picker-grid"><section class="project-list-card"><div class="picker-section-head"><div><span class="eyebrow">PROJECT LIST</span><h2>项目名单 <span>${projects.length}</span></h2></div><button type="button" class="refresh-projects" data-action="project-refresh" aria-label="刷新名单">↻</button></div><div class="project-list">${projects.length?projects.map((p,i)=>`<div class="project-row"><span class="project-index">${String(i+1).padStart(2,'0')}</span><span class="project-name">${esc(p.name)}</span><span class="project-dot"></span></div>`).join(''):'<div class="project-empty">还没有项目，先添加一个吧。</div>'}</div></section><section class="add-project-card"><span class="eyebrow">ADD TO THE POOL</span><h2>添加一个项目</h2><p>项目会保存到 BRD 项目名单，之后都可以参与抽取。</p><form data-form="project-add"><label class="project-input-label" for="project-name">项目名称</label><div class="project-input-wrap"><span>✦</span><input id="project-name" name="name" maxlength="100" required placeholder="例如：客户平台升级"></div><p class="form-error" role="alert"></p><button class="add-project-button" type="submit">＋ &nbsp; 保存到名单</button></form><div class="privacy-note">✦ &nbsp; 名单保存于 BRD 的 misc_setup 表</div></section></div></section><footer class="picker-footer">让每一次选择，都有一点期待。</footer></main>`;
}
async function refreshProjects(){projects=await api('/projects');await projectPicker();}
function drawProject(){if(!projects.length){toast('请先添加项目');return;}const target=root.querySelector('.draw-result'),buttonEl=root.querySelector('[data-action="draw"]');if(spinTimer)clearInterval(spinTimer);buttonEl.disabled=true;buttonEl.textContent='正在抽取…';let count=0;spinTimer=setInterval(()=>{target.textContent=projects[Math.floor(Math.random()*projects.length)].name;count++;if(count>18){clearInterval(spinTimer);spinTimer=null;target.textContent=projects[Math.floor(Math.random()*projects.length)].name;target.classList.add('is-winner');buttonEl.disabled=false;buttonEl.innerHTML='↻ &nbsp; 再抽一次';}},85);}

function renderShell(){
  const viewNames={docs:'文档库',users:'账号管理',tasks:'任务配置'};
  root.innerHTML=`<div class="shell"><aside><div class="brand"><span class="logo">B</span> BRD 文件库</div><p class="nav-label">工作空间</p>${button('▤ &nbsp; 文档库','docs','','nav '+(state.view==='docs'?'active':''))}${state.user.role==='ADMIN'?button('⚙ &nbsp; 任务配置','tasks','','nav '+(state.view==='tasks'?'active':''))+button('♙ &nbsp; 账号管理','users','','nav '+(state.view==='users'?'active':'')):''}<div class="aside-note"><strong>版本不再混淆</strong><p>一个文档编号<br>完整版本历史<br>唯一当前使用版</p></div><div class="profile"><span class="avatar">${esc(state.user.display_name?.slice(0,1)||'U')}</span><div>${esc(state.user.display_name)}<small>${roles[state.user.role]}</small></div></div></aside><div class="workspace"><header><span>工作空间 <span class="muted"> / ${viewNames[state.view]||'文档库'}</span></span><div>${button('修改密码','password')}${button('退出','logout')}</div></header><main id="content"></main><footer>BRD 文件库 · 上传与版本管理 · 不含审批流程</footer></div></div>`;
  if(state.user.must_change)document.querySelector('#content').innerHTML=`<section class="panel empty"><h2>请先修改临时密码</h2><p>设置至少 12 位的新密码，重新登录后即可使用系统功能。</p>${button('修改密码','password','','primary')}</section>`;
}
async function docs(reset=false){
  if(reset)state.page=0;state.view='docs';state.doc=null;renderShell();if(state.user.must_change)return;
  const ticket=++state.load,content=document.querySelector('#content');content.innerHTML='<p class="loading">正在加载文档…</p>';
  try{const result=await api(`/brds?search=${encodeURIComponent(state.search)}&page=${state.page}`);if(ticket!==state.load)return;
    content.innerHTML=`<div class="page-head"><div><p class="eyebrow">DOCUMENT LIBRARY</p><h1>需求文档库</h1><p class="muted">每份需求一个固定编号，每次变更一个独立版本。</p></div>${canWrite(state.user)?button('＋ 新建文档','new-doc','','primary'):''}</div><div class="stats"><div><span>符合筛选的文档</span><strong>${esc(result.total)}</strong></div><div><span>版本管理规则</span><strong class="stat-text">上传不自动切换当前版</strong></div><div><span>我的权限</span><strong class="stat-text">${roles[state.user.role]}</strong></div></div><section class="panel"><div class="toolbar"><form data-form="search" class="search"><input name="search" aria-label="搜索文档" placeholder="搜索标题、项目或交付批次" value="${esc(state.search)}" maxlength="160"><button type="submit">搜索</button></form>${button('刷新','refresh')}</div>${result.items.length?`<div class="table-wrap"><table><thead><tr><th>文档名称 / 编号</th><th>项目 / 批次</th><th>当前使用版</th><th>最新上传版</th><th>负责人</th><th>更新时间</th><th></th></tr></thead><tbody>${result.items.map(d=>`<tr><td>${button(esc(d.title),'detail',d.id,'text-link')}<small>${code(d.id)}</small></td><td>${esc(d.project_name)}<small>${esc(d.delivery_batch)}</small></td><td>${badge('V'+d.current_sequence,'green')}</td><td>${badge('V'+d.latest_sequence)}${!same(d.current_version_id,d.latest_version_id)?'<small class="amber">有尚未设为当前的版本</small>':''}</td><td>${esc(d.owner_name)}</td><td class="muted">${date(d.updated_at)}</td><td>${button('查看 →','detail',d.id)}</td></tr>`).join('')}</tbody></table></div>`:`<div class="empty"><div class="empty-icon">▤</div><h2>${state.search?'没有匹配的文档':'从第一份 BRD 开始'}</h2><p>${state.search?'换个关键词试试。':'上传 PDF 或 DOCX，系统自动建立 V1，并设为当前使用版。'}</p>${canWrite(state.user)&&!state.search?button('上传第一份文档','new-doc','','primary'):''}</div>`}${pager(state.page,result.total,20,'doc')}</section>`;
  }catch(e){content.innerHTML=`<section class="panel empty"><h2>文档加载失败</h2><p>${esc(e.message)}</p>${button('重试','refresh')}</section>`;failure(e);}
}
function pager(page,total,limit,kind){return `<div class="pagination"><span>共 ${esc(total)} 条 · 第 ${page+1} 页</span><button data-action="${kind}-prev" ${page===0?'disabled':''}>上一页</button><button data-action="${kind}-next" ${(page+1)*limit>=Number(total)?'disabled':''}>下一页</button></div>`;}
async function detail(id,reset=true){
  if(reset)state.versionPage=0;state.view='docs';renderShell();if(state.user.must_change)return;
  const ticket=++state.load,content=document.querySelector('#content');content.innerHTML='<p class="loading">正在加载版本历史…</p>';
  try{const [d,result,logs]=await Promise.all([api('/brds/'+id),api(`/brds/${id}/versions?page=${state.versionPage}`),api(`/brds/${id}/operations`)]);if(ticket!==state.load)return;state.doc=d;state.versions=result.items;
    content.innerHTML=`${button('← 返回文档库','docs','','back')}<div class="page-head"><div><p class="eyebrow">${code(d.id)}</p><h1>${esc(d.title)}</h1><p class="muted">${esc(d.project_name)} / ${esc(d.delivery_batch)} · 负责人 ${esc(d.owner_name)}</p></div><div class="actions">${canWrite(state.user,d)?button('编辑信息','edit-doc')+button('＋ 上传新版本','new-version','','primary'):''}</div></div><section class="current-card"><div><span class="eyebrow">团队当前使用</span><h2>V${esc(d.current_sequence)} <span>当前使用版</span></h2><p>团队交付时请下载此版本。上传新版本不会自动替换它。</p></div>${button('↓ 下载当前版本','download-current','','primary')}</section><div class="info-strip">最新上传：<strong>V${esc(d.latest_sequence)}</strong>${same(d.current_version_id,d.latest_version_id)?' · 已是当前使用版':' · 与当前使用版不同，可在下方手动切换'}<span>历史文件不可覆盖，版本号自动递增</span></div><section class="panel"><div class="toolbar"><h2>版本历史 <span class="muted">${esc(result.total)}</span></h2>${button('刷新','refresh-detail',d.id)}</div><div class="table-wrap"><table><thead><tr><th>版本</th><th>文件 / 变更说明</th><th>上传人 / 时间</th><th>操作</th></tr></thead><tbody>${result.items.map(v=>`<tr class="${same(v.id,d.current_version_id)?'current-row':''}"><td><strong>V${esc(v.sequence_no)}</strong><div>${same(v.id,d.current_version_id)?badge('当前使用','green'):''}${same(v.id,d.latest_version_id)?badge('最新上传'):''}</div></td><td><strong>${esc(v.original_filename)}</strong><p class="note">${esc(v.change_note)}</p><small>${size(v.size_bytes)} · SHA256 ${esc(v.sha256.slice(0,12))}… ${v.base_version_id?'· 基于版本 ID '+esc(v.base_version_id):''}</small></td><td>${esc(v.uploaded_by_name)}<small>${date(v.uploaded_at)}</small></td><td><div class="actions">${button('下载','download',v.id)}${canWrite(state.user,d)&&!same(v.id,d.current_version_id)?button('设为当前','set-current',v.id):''}</div></td></tr>`).join('')}</tbody></table></div>${pager(state.versionPage,result.total,10,'version')}</section><section class="panel history"><h2>操作记录 <small>最近 100 条</small></h2>${logs.length?logs.map(l=>`<div class="log"><span class="dot"></span><div><strong>${esc(({UPLOAD:'上传版本',SET_CURRENT:'切换当前版本',EDIT_DOCUMENT:'编辑文档信息',DOWNLOAD:'下载文件'})[l.action]||l.action)}</strong> · ${esc(l.actor_name)}${l.version_id?' · 版本 ID '+esc(l.version_id):''}<p>${esc(l.reason||'')}</p></div><time>${date(l.created_at)}</time></div>`).join(''):'<p class="muted">暂无记录</p>'}</section>`;
  }catch(e){content.innerHTML=`<section class="panel empty"><h2>无法加载文档</h2><p>${esc(e.message)}</p>${button('返回文档库','docs')}</section>`;failure(e);}
}
async function users(){
  state.view='users';state.doc=null;renderShell();if(state.user.must_change)return;const ticket=++state.load,content=document.querySelector('#content');content.innerHTML='<p class="loading">正在加载账号…</p>';
  try{const rows=await api('/users');if(ticket!==state.load)return;state.users=rows;content.innerHTML=`<div class="page-head"><div><p class="eyebrow">ACCESS MANAGEMENT</p><h1>账号管理</h1><p class="muted">管理员管理全部文档，上传者维护自己的文档，阅读者仅查看和下载。</p></div>${button('＋ 新建账号','new-user','','primary')}</div><section class="panel"><div class="table-wrap"><table><thead><tr><th>姓名 / 用户名</th><th>角色</th><th>状态</th><th>密码</th><th>操作</th></tr></thead><tbody>${rows.map(u=>`<tr><td><strong>${esc(u.display_name)}</strong>${same(u.id,state.user.id)?badge('我'):''}<small>${esc(u.username)}</small></td><td>${roles[u.role]}</td><td>${badge(u.enabled?'正常':'已停用',u.enabled?'green':'gray')}</td><td>${u.must_change?'下次登录需修改':'已设置'}</td><td>${button('编辑','edit-user',u.id)}${button('重置密码','reset-user',u.id)}</td></tr>`).join('')}</tbody></table></div></section><p class="hint">角色或状态变更会使该账号的旧会话失效；系统会保护最后一个有效管理员。</p>`;}catch(e){content.innerHTML=`<p class="error">${esc(e.message)}</p>${button('重试','users')}`;failure(e);}
}
async function tasks(companyId=''){
  state.view='tasks';state.doc=null;renderShell();if(state.user.must_change)return;
  const content=document.querySelector('#content');
  content.innerHTML=`<div class="page-head"><div><p class="eyebrow">TASK PIPELINE</p><h1>任务配置</h1><p class="muted">按公司维护执行类名、顺序和扩展参数。</p></div>${button('＋ 新增任务','new-task','','primary')}</div><section class="panel"><div class="toolbar"><form data-form="task-search" class="search task-search"><input name="companyId" aria-label="公司 ID" placeholder="输入 Company ID" value="${esc(companyId)}" required inputmode="numeric"><button type="submit">查询</button></form>${button('刷新','tasks-refresh')}</div><div id="task-results" class="task-results"><div class="empty task-prompt"><div class="empty-icon">⚙</div><h2>输入公司 ID 查看配置</h2><p>不同公司可以维护各自的执行类和运行顺序。</p></div></div></section>`;
  if(companyId)await loadTasks(companyId);
}
async function loadTasks(companyId){
  const results=document.querySelector('#task-results');if(!results)return;
  try{
    const rows=await api(`/tasks?companyId=${encodeURIComponent(companyId)}`);taskRows=rows;
    results.innerHTML=rows.length?`<div class="task-company-summary"><span>公司 <strong>${esc(companyId)}</strong> 的任务</span><span>${rows.length} 项 · 按执行顺序排列</span></div><div class="table-wrap"><table><thead><tr><th>顺序</th><th>执行类名</th><th>状态</th><th>扩展字段</th><th>备注</th><th>操作</th></tr></thead><tbody>${rows.map(t=>`<tr><td><span class="task-order">${esc(t.order_no)}</span></td><td><strong class="task-class">${esc(t.class_name)}</strong><small>Company ID ${esc(t.company_id)}</small></td><td>${badge(t.enabled?'启用':'停用',t.enabled?'green':'gray')}</td><td><code class="task-json">${esc(t.other_fields||'{}')}</code></td><td>${esc(t.remark||'—')}</td><td>${button('编辑','edit-task',t.id)}${button('删除','delete-task',t.id)}</td></tr>`).join('')}</tbody></table></div>`:`<div class="empty"><div class="empty-icon">⚙</div><h2>此公司还没有任务配置</h2><p>添加执行类名和顺序后，这里会显示运行清单。</p>${button('＋ 新增第一项','new-task','','primary')}</div>`;
  }catch(e){results.innerHTML=`<div class="empty"><h2>配置加载失败</h2><p>${esc(e.message)}</p></div>`;failure(e);}
}
function taskDialog(id=''){
  const t=id?taskRows.find(x=>same(x.id,id))||{}:{};
  const enabled=!([false,0,'0','false'].includes(t.enabled));
  openDialog(id?'编辑任务配置':'新增任务配置',`<div class="grid-two">${field('Company ID','companyId',t.company_id||document.querySelector('[name="companyId"]')?.value||'','required min="1" type="number"')}${field('执行顺序 Order No','orderNo',t.order_no??0,'required min="0" type="number"')}</div>${field('执行类名 Class Name','className',t.class_name||'','required maxlength="255" placeholder="例如 com.example.jobs.SyncTask"')}<label>其他字段（JSON 对象）<textarea name="otherFields" maxlength="60000" spellcheck="false" placeholder='{"retry":3,"timeout":60}'>${esc(t.other_fields||'{}')}</textarea><small>用 JSON 对象保存此类的扩展参数。</small></label><label class="task-enabled"><input type="checkbox" name="enabled" ${enabled?'checked':''}>启用此任务</label><label>备注<textarea name="remark" maxlength="500" placeholder="可选">${esc(t.remark||'')}</textarea></label>`,'task','保存配置');
  const form=modal.querySelector('form');form.dataset.id=id;
}
function openDialog(title,body,form,submit='保存'){
  modal.innerHTML=`<form data-form="${form}"><div class="dialog-head"><h2>${title}</h2>${button('✕','close')}</div>${body}<p class="form-error" role="alert"></p><div class="dialog-actions">${button('取消','close')}<button class="primary" type="submit">${submit}</button></div></form>`;modal.querySelectorAll('label > select').forEach(s=>s.setAttribute('aria-label',s.parentElement.firstChild.textContent.trim()));modal.showModal();
}
function passwordDialog(){openDialog('修改密码',`<p class="muted">新密码至少 12 位，UTF-8 编码不超过 72 字节。保存后需重新登录。</p>${field('当前密码','oldPassword','','type="password" required autocomplete="current-password"')}${field('新密码','newPassword','','type="password" required minlength="12" autocomplete="new-password"')}${field('确认新密码','confirm','','type="password" required minlength="12" autocomplete="new-password"')}`,'password','保存并重新登录');}
function documentFields(d={}){return field('文档标题','title',d.title,'required maxlength="160"')+`<div class="grid-two">${field('所属项目','projectName',d.project_name,'required maxlength="120"')}${field('交付批次','deliveryBatch',d.delivery_batch,'required maxlength="80"')}</div>`;}
function uploadDialog(fresh){
  const d=state.doc;openDialog(fresh?'新建 BRD 文档':'上传新版本',`${fresh?documentFields():`<p class="hint">${esc(d.title)} · 当前使用 V${esc(d.current_sequence)}<br>上传完成后，当前使用版保持不变。</p><label>基于哪个版本<select name="baseVersionId"><option value="">未指定</option>${state.versions.map(v=>`<option value="${esc(v.id)}">V${esc(v.sequence_no)}</option>`).join('')}</select><small>这里列出当前历史页的版本。</small></label>`}<label class="file-drop">选择需求文档<input type="file" name="file" accept=".pdf,.docx" required><small>支持 PDF / DOCX，最大 20 MB</small></label><label>变更说明<textarea name="changeNote" required minlength="2" maxlength="500" placeholder="说明本次新增、修改或删除的业务需求"></textarea></label><p class="muted">本地开发版仅进行结构校验，未启用完整病毒扫描。</p><div id="upload-status" role="status"></div>`,'upload','上传并创建版本');
  const f=modal.querySelector('form');f.dataset.document=fresh?'':d.id;f.dataset.key=newUploadKey();f.addEventListener('change',()=>{f.dataset.key=newUploadKey();});
}
async function editDocument(){
  const d=state.doc;let owners='';if(state.user.role==='ADMIN'){const list=await api('/users');owners=`<label>负责人<select name="ownerId">${list.filter(u=>u.enabled&&u.role!=='READER').map(u=>`<option value="${esc(u.id)}" ${same(u.id,d.owner_id)?'selected':''}>${esc(u.display_name)} (${esc(u.username)})</option>`).join('')}</select></label>`;}
  openDialog('编辑文档信息',documentFields(d)+owners,'edit-doc');modal.querySelector('form').dataset.revision=d.revision;
}
function userDialog(id){const u=id?state.users.find(x=>same(x.id,id)):null;openDialog(u?'编辑账号':'新建账号',`${u?`<p class="hint">账号：${esc(u.username)}。保存后该用户需要重新登录。</p>`:field('用户名','username','','required minlength="3" maxlength="40" pattern="[a-zA-Z0-9_]{3,40}" autocomplete="off"')} ${field('显示名称','displayName',u?.display_name,'required maxlength="80"')}<label>角色<select name="role">${roleOptions(u?.role||'UPLOADER')}</select></label>${u?`<label>账号状态<select name="enabled"><option value="true" ${u.enabled?'selected':''}>启用</option><option value="false" ${!u.enabled?'selected':''}>停用</option></select></label>`:field('临时密码','password','','type="password" required minlength="12" autocomplete="new-password"')}<p class="muted">临时密码至少 12 位，首次登录必须修改。</p>`,'user');modal.querySelector('form').dataset.id=id||'';}
async function pollUpload(id,f){
  const status=f.querySelector('#upload-status');for(let attempt=0;attempt<90;attempt++){
    const j=await api('/uploads/'+id);if(j.state==='SUCCEEDED'||j.state==='DUPLICATE'){sessionStorage.removeItem('brd-pending-upload');modal.close();toast(j.state==='DUPLICATE'?'文件内容重复，已保留原版本，未生成新版本。':'上传成功，版本已保存。');await detail(j.document_id);return;}
    if(j.state==='FAILED'||j.state==='CANCELLED'){sessionStorage.removeItem('brd-pending-upload');delete f.dataset.job;f.dataset.key=newUploadKey();throw new Error('上传未完成：'+(j.error_code||j.state)+'。请检查文件或联系管理员。');}
    status.textContent='文件已传输，正在检查并保存版本…';await new Promise(r=>setTimeout(r,1000));
  }throw new Error('文件仍在处理中，请点击“继续检查”，不要重复上传。');
}
async function submit(f){
  const type=f.dataset.form,b=Object.fromEntries(new FormData(f));
  if(type==='login'){state.user=await api('/auth/login','POST',b);await docs(true);return;}
  if(type==='project-add'){const saved=await api('/projects','POST',{name:b.name.trim()});toast('已添加「'+saved.name+'」');await projectPicker();return;}
  if(type==='search'){state.search=b.search.trim();await docs(true);return;}
  if(type==='task-search'){if(!/^\d+$/.test(b.companyId.trim())||Number(b.companyId)<1)throw new Error('Company ID 必须是大于 0 的整数');await tasks(b.companyId.trim());return;}
  if(type==='password'){if(b.newPassword!==b.confirm)throw new Error('两次新密码不一致');await api('/auth/password','PUT',{oldPassword:b.oldPassword,newPassword:b.newPassword});modal.close();state.user=null;renderLogin('密码已更新，请使用新密码重新登录。');return;}
  if(type==='upload'){
    if(!f.dataset.job){const file=b.file;if(!file?.size)throw new Error('请选择非空文件');if(!/\.(pdf|docx)$/i.test(file.name)||file.size>20*1024*1024)throw new Error('仅支持不超过 20 MB 的 PDF / DOCX');const data=new FormData(f);f.querySelector('#upload-status').textContent='准备上传…';const result=await upload(f.dataset.document?`/brds/${f.dataset.document}/uploads`:'/brd-uploads',data,f.dataset.key,p=>{f.querySelector('#upload-status').textContent=`正在传输文件 ${p}%`;});f.dataset.job=result.uploadId;sessionStorage.setItem('brd-pending-upload',JSON.stringify({id:result.uploadId,user:state.user.id}));}
    await pollUpload(f.dataset.job,f);return;
  }
  if(type==='edit-doc'){await api('/brds/'+state.doc.id,'PATCH',{...b,expectedRevision:f.dataset.revision});modal.close();toast('文档信息已保存');await detail(state.doc.id);return;}
  if(type==='current'){await api(`/brds/${state.doc.id}/current-version`,'PUT',{versionId:f.dataset.id,expectedRevision:f.dataset.revision,reason:b.reason});modal.close();toast('当前使用版已切换，历史版本仍保留');await detail(state.doc.id);return;}
  if(type==='user'){const id=f.dataset.id;await api(id?'/users/'+id:'/users',id?'PATCH':'POST',id?{...b,enabled:b.enabled==='true'}:b);modal.close();toast('账号已保存');if(id&&same(id,state.user.id)){state.user=null;renderLogin('账号权限已更新，请重新登录。');}else await users();return;}
  if(type==='reset'){await api(`/users/${f.dataset.id}/password-reset`,'POST',b);modal.close();toast('密码已重置，下次登录需修改');if(same(f.dataset.id,state.user.id)){state.user=null;renderLogin('请用重置后的临时密码重新登录。');}else await users();}
  if(type==='task'){let fields={};try{fields=JSON.parse(b.otherFields||'{}');}catch{throw new Error('其他字段不是有效的 JSON');}if(!fields||Array.isArray(fields)||typeof fields!=='object')throw new Error('其他字段必须是 JSON 对象');const payload={companyId:Number(b.companyId),className:b.className.trim(),orderNo:Number(b.orderNo),enabled:f.elements.enabled.checked,otherFields:fields,remark:b.remark.trim()};if(!Number.isInteger(payload.companyId)||payload.companyId<1)throw new Error('Company ID 必须是大于 0 的整数');if(!Number.isInteger(payload.orderNo)||payload.orderNo<0)throw new Error('Order No 必须是大于等于 0 的整数');const id=f.dataset.id;await api(id?`/tasks/${id}`:'/tasks',id?'PUT':'POST',payload);modal.close();toast('任务配置已保存');await tasks(String(payload.companyId));return;}
}
document.addEventListener('submit',async e=>{const f=e.target;if(!f.matches('[data-form]'))return;e.preventDefault();if(f.dataset.busy)return;f.dataset.busy='true';const controls=[...f.querySelectorAll('button')];controls.forEach(b=>b.disabled=true);f.querySelector('.form-error')?.replaceChildren();
  try{await submit(f);}catch(err){const target=f.querySelector('.form-error');if(target)target.textContent=err.message;if(err.status===401||err.code==='PASSWORD_CHANGE_REQUIRED')failure(err);else if(!target)toast(err.message,true);if(f.dataset.job){const btn=f.querySelector('[type="submit"]');if(btn)btn.textContent='继续检查';}}finally{delete f.dataset.busy;controls.forEach(b=>b.disabled=false);}
});
document.addEventListener('click',async e=>{
  const pickerAction=e.target.closest('[data-action]')?.dataset.action;
  if(pickerAction==='project-picker'){await projectPicker();return;}
  if(pickerAction==='picker-back'){if(spinTimer){clearInterval(spinTimer);spinTimer=null;}renderLogin();return;}
  if(pickerAction==='project-refresh'){try{await refreshProjects();}catch(err){toast(err.message,true);}return;}
  if(pickerAction==='draw'){drawProject();return;}
  const eye=e.target.closest('#brd-login-motion .b-eye');
  if(eye){
    const input=document.querySelector('#brd-login-motion #brd-demo-password');
    if(input){const visible=input.type==='password';input.type=visible?'text':'password';eye.setAttribute('aria-label',visible?'隐藏密码':'显示密码');}
    return;
  }
  const b=e.target.closest('[data-action]');if(!b||b.disabled)return;const action=b.dataset.action,id=b.dataset.id;
  try{
    if(action==='close'){if(modal.querySelector('[data-busy]'))return;modal.close();return;}
    if(action==='password')return passwordDialog();
    if(action==='logout'){await api('/auth/logout','POST');state.load++;state.user=null;renderLogin();return;}
    if(action==='docs')return await docs(true);if(action==='refresh')return await docs();if(action==='users')return await users();if(action==='tasks')return await tasks();if(action==='tasks-refresh')return await tasks(document.querySelector('[name="companyId"]')?.value.trim()||'');if(action==='new-task')return taskDialog();if(action==='edit-task')return taskDialog(id);if(action==='delete-task'){const t=taskRows.find(x=>same(x.id,id));if(!t||!confirm(`确定删除执行类“${t.class_name}”吗？`))return;await api('/tasks/'+id,'DELETE');toast('任务配置已删除');return await tasks(String(t.company_id));}
    if(action==='detail'||action==='refresh-detail')return await detail(id);
    if(action==='doc-prev'||action==='doc-next'){state.page+=action.endsWith('next')?1:-1;return await docs();}
    if(action==='version-prev'||action==='version-next'){state.versionPage+=action.endsWith('next')?1:-1;return await detail(state.doc.id,false);}
    if(action==='new-doc')return uploadDialog(true);if(action==='new-version')return uploadDialog(false);if(action==='edit-doc')return await editDocument();
    if(action==='new-user'||action==='edit-user')return userDialog(id);
    if(action==='reset-user'){const u=state.users.find(x=>same(x.id,id));openDialog('重置账号密码',`<p class="hint">为 ${esc(u.display_name)} (${esc(u.username)}) 设置临时密码。旧会话会失效。</p>${field('新临时密码','password','','type="password" required minlength="12" autocomplete="new-password"')}`,'reset','确认重置');modal.querySelector('form').dataset.id=id;return;}
    if(action==='set-current'){const v=state.versions.find(v=>same(v.id,id)),d=state.doc;openDialog('切换当前使用版',`<p class="hint">V${esc(d.current_sequence)} → V${esc(v.sequence_no)}<br>历史文件不会删除。${Number(v.sequence_no)<Number(d.current_sequence)?'本次操作是回退到历史版本。':''}</p><label>切换原因<textarea name="reason" required minlength="2" maxlength="300" placeholder="说明为什么团队现在应使用这一版"></textarea></label>`,'current','确认切换');const f=modal.querySelector('form');f.dataset.id=id;f.dataset.revision=d.revision;return;}
    if(action==='download-current'||action==='download'){b.disabled=true;const d=state.doc,v=action==='download'?state.versions.find(v=>same(v.id,id)):await api(`/brds/${d.id}/versions/${d.current_version_id}`);await download(`/brds/${d.id}/versions/${v.id}/download`,`${code(d.id)}_V${v.sequence_no}_${d.title.replace(/[\\/:*?"<>|]/g,'_')}.${v.extension}`);toast('文件下载已开始');b.disabled=false;}
  }catch(err){b.disabled=false;failure(err);}
});
modal.addEventListener('cancel',e=>{if(modal.querySelector('[data-busy]'))e.preventDefault();});
async function init(){try{state.user=await api('/auth/me');await docs(true);const saved=sessionStorage.getItem('brd-pending-upload');if(saved&&!state.user.must_change){const job=JSON.parse(saved);if(same(job.user,state.user.id)){openDialog('恢复上传状态','<div id="upload-status" role="status">检查上次上传任务…</div>','upload','继续检查');const f=modal.querySelector('form');f.dataset.job=job.id;f.requestSubmit();}}}catch(e){renderLogin();if(e.status!==401)toast('无法连接后端：'+e.message,true);}}
init();

