import {esc} from '../helpers.js';

export function walmartReportsPage(isAdmin=false){
  return `<div class="page-head"><div><p class="eyebrow">WALMART CANADA · REPORTS</p><h1>生成 Walmart 报告</h1><p class="muted">填写报告信息，查看该公司和格式对应的任务配置。</p></div><div class="actions">${isAdmin?'<button type="button" data-action="tasks">管理任务配置</button>':''}<span class="walmart-mark">Walmart Canada</span></div></div>
    <section class="walmart-hero"><div class="walmart-hero-icon">W</div><div><h2>Walmart Canada 报告</h2><p>报告专属模块 · 格式 60 · 任务按配置顺序读取</p></div></section>
    <section class="panel walmart-form-panel"><form data-form="walmart-plan"><div class="grid-two"><label>Company ID<input name="companyId" type="number" min="1" required value="2105259"></label><label>报告格式 ID<input name="reportFormatId" type="number" min="0" required value="60"></label></div><label>报告编号 / 样品编号<input name="reportNo" maxlength="100" required placeholder="输入报告或主样品编号"></label><p class="form-error" role="alert"></p><button class="primary" type="submit">查看报告生成配置</button></form></section>
    <section id="walmart-plan" class="panel walmart-plan"><div class="empty"><div class="empty-icon">✦</div><h2>准备报告配置</h2><p>提交公司、格式和报告编号后，显示启用中的任务顺序。</p></div></section>
    <section class="walmart-engine-note"><strong>报告引擎尚未接入</strong><p>完整生成仍需 WMCA 任务类、Word 模板、报告数据 Mapper 和对应数据库。本页面会显示 BRD 中维护的任务配置；目前不会生成示例或不完整文件。</p></section>`;
}

export function walmartPlanResult(plan){
  const tasks=plan.tasks||[];
  return `<div class="walmart-plan-head"><div><span class="eyebrow">REPORT TASK PLAN</span><h2>${esc(plan.reportNo)}</h2><p class="muted">公司 ${esc(plan.companyId)} · 报告格式 ${esc(plan.reportFormatId)}</p></div><span class="badge amber">引擎未接入</span></div>${tasks.length?`<div class="walmart-task-list">${tasks.map((task,index)=>`<div class="walmart-task-row"><span class="task-order">${esc(task.order_no)}</span><div><strong>${esc(task.class_name)}</strong><small>执行顺序 ${index+1}${task.remark?` · ${esc(task.remark)}`:''}</small></div></div>`).join('')}</div>`:'<div class="empty"><h2>尚无启用的任务</h2><p>请管理员在任务配置中添加并启用报告格式任务。</p></div>'}<p class="walmart-engine-reason">${esc(plan.reason)}</p>`;
}
