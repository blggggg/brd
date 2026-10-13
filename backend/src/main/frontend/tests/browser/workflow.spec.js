import {test,expect} from '@playwright/test';
const adminInitialPassword=process.env.BRD_TEST_ADMIN_PASSWORD;
const adminChangedPassword=`test-admin-changed-${Date.now()}`;
const readerInitialPassword=`test-reader-${Date.now()}`;
test('account, upload, current-version switch and user management',async({page})=>{
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  const uploadKeys=[];page.on('request',r=>{if(r.method()==='POST'&&/\/api\/v1\/(brd-uploads|brds\/[^/]+\/uploads)$/.test(new URL(r.url()).pathname))uploadKeys.push(r.headers()['idempotency-key']);});
  await page.goto('/');
  if(new URL(page.url()).hostname==='brd-http.test'){
    expect(await page.evaluate(()=>window.isSecureContext)).toBe(false);
    expect(await page.evaluate(()=>typeof crypto.randomUUID)).toBe('undefined');
  }
  await page.goto('/');await page.getByLabel('用户名',{exact:true}).fill('admin');await page.getByLabel('密码',{exact:true}).fill(adminInitialPassword);await page.getByRole('button',{name:'登录工作空间',exact:true}).click();
  await expect(page.getByRole('heading',{name:'请先修改临时密码'})).toBeVisible();await page.locator('#content').getByRole('button',{name:'修改密码'}).click();
  await page.getByLabel('当前密码',{exact:true}).fill(adminInitialPassword);await page.getByLabel('新密码',{exact:true}).fill(adminChangedPassword);await page.getByLabel('确认新密码',{exact:true}).fill(adminChangedPassword);await page.getByRole('button',{name:'保存并重新登录'}).click();
  await page.getByLabel('用户名',{exact:true}).fill('admin');await page.getByLabel('密码',{exact:true}).fill(adminChangedPassword);await page.getByRole('button',{name:'登录工作空间',exact:true}).click();
  await expect(page.getByRole('heading',{name:'需求文档库'})).toBeVisible();await page.getByRole('button',{name:'＋ 新建文档',exact:true}).click();
  await page.getByLabel('文档标题',{exact:true}).fill('客户需求 BRD');await page.getByLabel('所属项目').fill('客户平台');await page.getByLabel('交付批次').fill('R1');
  const pdf=text=>Buffer.from('%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n% '+text+'\n%%EOF');
  await page.locator('input[type=file]').setInputFiles({name:'requirements.pdf',mimeType:'application/pdf',buffer:pdf('first')});await page.getByLabel('变更说明').fill('首版需求');await page.getByRole('button',{name:'上传并创建版本'}).click();
  await expect(page.getByRole('heading',{name:'客户需求 BRD',exact:true})).toBeVisible();await expect(page.locator('.current-card h2')).toContainText('V1');
  await page.getByRole('button',{name:'＋ 上传新版本'}).click();await page.locator('input[type=file]').setInputFiles({name:'requirements.pdf',mimeType:'application/pdf',buffer:pdf('second')});await page.getByLabel('变更说明').fill('增加客户搜索');await page.getByRole('button',{name:'上传并创建版本'}).click();await expect(page.locator('dialog')).not.toBeVisible();
  await expect(page.locator('.info-strip')).toContainText('V2');await expect(page.locator('.current-card h2')).toContainText('V1');
  await page.getByRole('button',{name:'设为当前'}).click();await page.getByLabel('切换原因').fill('确认使用第二版');await page.getByRole('button',{name:'确认切换'}).click();await expect(page.locator('.current-card h2')).toContainText('V2');
  const downloaded=page.waitForEvent('download');await page.getByRole('button',{name:'↓ 下载当前版本'}).click();expect((await downloaded).suggestedFilename()).toContain('_V2_');
  await page.screenshot({path:'test-results/version-history.png',fullPage:true});
  await page.getByRole('button',{name:'编辑信息'}).click();await page.getByLabel('文档标题',{exact:true}).fill('客户需求 BRD 更新');await page.getByRole('button',{name:'保存',exact:true}).click();await expect(page.getByRole('heading',{name:'客户需求 BRD 更新'})).toBeVisible();
  await page.getByRole('button',{name:'返回文档库'}).click();await page.getByLabel('搜索文档').fill('客户');await page.getByRole('button',{name:'搜索',exact:true}).click();await expect(page.getByRole('button',{name:'客户需求 BRD 更新',exact:true})).toBeVisible();await page.screenshot({path:'test-results/document-library.png',fullPage:true});
  await page.getByRole('button',{name:'账号管理'}).click();await page.getByRole('button',{name:'＋ 新建账号'}).click();await page.getByLabel('用户名',{exact:true}).fill('reader_ui');await page.getByLabel('显示名称').fill('测试阅读者');await page.getByLabel('角色',{exact:true}).selectOption('READER');await page.getByLabel('临时密码',{exact:true}).fill(readerInitialPassword);await page.getByRole('button',{name:'保存',exact:true}).click();await expect(page.getByText('测试阅读者',{exact:true})).toBeVisible();
  await page.screenshot({path:'test-results/accounts.png',fullPage:true});await page.setViewportSize({width:390,height:844});await page.getByRole('button',{name:'文档库',exact:false}).first().click();await expect(page.getByRole('heading',{name:'需求文档库'})).toBeVisible();await page.screenshot({path:'test-results/mobile-library.png',fullPage:true});expect(errors).toEqual([]);
  expect(uploadKeys).toHaveLength(2);for(const key of uploadKeys)expect(key).toMatch(/^[a-zA-Z0-9-]{16,80}$/);expect(uploadKeys[0]).not.toBe(uploadKeys[1]);
});
