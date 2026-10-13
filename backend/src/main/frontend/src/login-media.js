const paths={
  'user-round':'<circle cx="12" cy="8" r="4"/><path d="M4 21v-2a8 8 0 0 1 16 0v2"/>',
  'lock-keyhole':'<rect x="4" y="10" width="16" height="11" rx="2"/><path d="M8 10V6a4 4 0 0 1 8 0v4M12 14v3"/>',
  'eye':'<path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12Z"/><circle cx="12" cy="12" r="3"/>',
  'arrow-right':'<path d="M4 12h16m-6-6 6 6-6 6"/>',
  'shield-check':'<path d="m12 2 8 4v6c0 5-8 10-8 10S4 17 4 12V6l8-4Z"/><path d="m8 12 3 3 5-6"/>'
};
const reduced=matchMedia('(prefers-reduced-motion: reduce)');
let current=null;
function play(video){video.play().catch(()=>{});}
reduced.addEventListener('change',()=>{if(!current?.isConnected)return;const video=current.querySelector('video');if(reduced.matches)video.pause();else if(!current.dataset.motionPaused)play(video);});
export function setupLoginMedia(root){
  current=root;
  root.querySelectorAll('[data-lucide]').forEach(icon=>{
    const path=paths[icon.dataset.lucide];if(!path)return;
    const svg=document.createElementNS('http://www.w3.org/2000/svg','svg');
    for(const [key,value] of Object.entries({viewBox:'0 0 24 24',fill:'none',stroke:'currentColor','stroke-width':'1.8','stroke-linecap':'round','stroke-linejoin':'round','aria-hidden':'true'}))svg.setAttribute(key,value);
    svg.innerHTML=path;icon.replaceWith(svg);
  });
  const video=root.querySelector('video'),toggle=root.querySelector('.login-motion-toggle');
  video.muted=true;
  const update=()=>{toggle.textContent=video.paused?'播放动画':'暂停动画';toggle.setAttribute('aria-label',video.paused?'播放背景动画':'暂停背景动画');toggle.setAttribute('aria-pressed',String(!video.paused));};
  video.addEventListener('play',update);video.addEventListener('pause',update);
  toggle.addEventListener('click',()=>{if(video.paused){delete root.dataset.motionPaused;play(video);}else{root.dataset.motionPaused='true';video.pause();}});
  video.addEventListener('error',()=>{toggle.textContent='动画暂不可用';toggle.disabled=true;});
  update();if(!reduced.matches)play(video);
}
