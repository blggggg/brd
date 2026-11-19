import {esc} from '../helpers.js';

export function reportsPage(companies,isAdmin=false){
  const options=companies.map(company=>`<option value="${esc(company.id)}">${esc(company.company_name)} · ${esc(company.id)}</option>`).join('');
  return `<div class="page-head"><div><p class="eyebrow">BRD · REPORTS</p><h1>生成报告</h1><p class="muted">选择公司、报告格式和编号，查看任务顺序并生成报告。</p></div><div class="actions">${isAdmin?'<button type="button" data-action="tasks">管理任务配置</button>':''}<span class="report-mark">公司专属报告</span></div></div>
    <section class="report-hero"><div class="report-hero-icon">R</div><div><h2>报告生成</h2><p>统一报告入口 · 公司和格式决定任务配置及执行顺序</p></div></section>
    <section class="panel report-form-panel"><form data-form="report-plan"><div class="grid-two"><label>公司<select name="companyId" required>${options}</select></label><label>报告格式 ID<input name="reportFormatId" type="number" min="0" required></label></div><label>报告编号 / 样品编号<input name="reportNo" maxlength="100" required placeholder="输入报告或主样品编号"></label><p class="form-error" role="alert"></p><button class="primary" type="submit">查看报告生成配置</button></form></section>
    <section id="report-plan" class="panel report-plan"><div class="empty"><div class="empty-icon">✦</div><h2>准备报告配置</h2><p>提交公司、格式和报告编号后，显示该公司启用的任务顺序。</p></div></section>
    <section class="report-engine-note"><strong>报告生成说明</strong><p>Walmart Canada 使用本地 Word 模板；协议、评级和失败摘要在业务数据源接入前显示为 N/A。其他公司暂使用模拟报告。</p></section>`;
}

export function reportPlanResult(plan){
  const tasks=plan.tasks||[];
  return `<div class="report-plan-head"><div><span class="eyebrow">REPORT TASK PLAN</span><h2>${esc(plan.reportNo)}</h2><p class="muted">公司 ${esc(plan.companyId)} · 报告格式 ${esc(plan.reportFormatId)}</p></div><span class="badge amber">${Number(plan.companyId)===2105259?'Walmart 模板':'模拟模式'}</span></div>${tasks.length?`<div class="report-task-list">${tasks.map((task,index)=>`<div class="report-task-row"><span class="task-order">${esc(task.order_no)}</span><div><strong>${esc(task.class_name)}</strong><small>执行顺序 ${index+1}${task.remark?` · ${esc(task.remark)}`:''}</small></div></div>`).join('')}</div>`:'<div class="empty"><h2>尚无启用的任务配置</h2><p>生成仍可使用对应公司模板或模拟模式。</p></div>'}<div class="report-engine-reason">${esc(plan.reason)}<p><button type="button" class="primary" data-action="report-generate" data-company-id="${esc(plan.companyId)}" data-format-id="${esc(plan.reportFormatId)}" data-report-no="${esc(plan.reportNo)}">生成并下载报告（DOCX）</button></p></div>`;
}
