import {test,expect} from '@playwright/test';
test('mobile video stays complete and controls stay inside form',async({page})=>{
  await page.setViewportSize({width:390,height:844});await page.goto('/');await page.locator('.b-submit').waitFor();
  await expect.poll(()=>page.locator('video').evaluate(v=>v.videoWidth)).toBeGreaterThan(0);
  for(const width of [320,390,768]){
    await page.setViewportSize({width,height:844});
    const result=await page.evaluate(()=>{const v=document.querySelector('video'),vr=v.getBoundingClientRect(),c=document.querySelector('.b-login').getBoundingClientRect(),p=document.querySelector('[name=password]').getBoundingClientRect(),eye=document.querySelector('.b-eye').getBoundingClientRect();return {fit:getComputedStyle(v).objectFit,ratio:vr.width/vr.height,native:v.videoWidth/v.videoHeight,cardTop:c.top,videoBottom:vr.bottom,overflow:document.body.scrollWidth>innerWidth,eyeInside:eye.left>=p.left&&eye.right<=p.right&&eye.top>=p.top&&eye.bottom<=p.bottom};});
    expect(result.fit).toBe('contain');expect(result.ratio).toBeCloseTo(result.native,1);expect(result.cardTop).toBeGreaterThanOrEqual(result.videoBottom);expect(result.overflow).toBe(false);expect(result.eyeInside).toBe(true);
  }
  await page.setViewportSize({width:390,height:844});await page.screenshot({path:'test-results/login-mobile-fixed.png',fullPage:true});
  await page.getByLabel('密码',{exact:true}).fill('not-submitted');await page.getByRole('button',{name:'显示密码'}).click();await expect(page.getByLabel('密码',{exact:true})).toHaveAttribute('type','text');
  await page.emulateMedia({reducedMotion:'reduce'});await expect.poll(()=>page.locator('video').evaluate(v=>v.paused)).toBe(true);
  await page.setViewportSize({width:1440,height:900});expect(await page.getByLabel('密码',{exact:true}).evaluate(e=>getComputedStyle(e).backgroundColor)).toBe('rgba(0, 0, 0, 0)');await expect(page.locator('.b-eye svg')).toHaveCount(1);
  await page.screenshot({path:'test-results/login-desktop-fixed.png',fullPage:true});
});
