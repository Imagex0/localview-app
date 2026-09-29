const PROJECTS = [
  {name:"Vite React", port:5173, color:"#4ed58a", desc:"HMR / http://localhost:5173/", live:true},
  {name:"Next.js Shop", port:3000, color:"#56c2bb", desc:"SSR / localhost:3000", live:true},
  {name:"Python Docs", port:8000, color:"#a9c46a", desc:"mkdocs / :8000", live:true},
  {name:"Flutter Web", port:8080, color:"#e2a84e", desc:"debug / :8080", live:false},
  {name:"Go API + UI", port:9000, color:"#d48473", desc:"net/http / 127.0.0.1:9000", live:true},
  {name:"Astro Site", port:4321, color:"#8fa3d9", desc:"static / :4321", live:false},
];
const PORTS = [3000,5173,8000,8080,9000,4321];

const SVG_PIN = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 4h6l1 7 3 3v2H5v-2l3-3zM12 16v5"/></svg>';
const SVG_X = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 6l12 12M18 6L6 18"/></svg>';

function normalizeLocalInput(v){
  v=(v||"").trim();
  if(/^\d{2,5}$/.test(v)) return {url:`http://localhost:${v}/`,port:+v};
  if(/^:\d{2,5}$/.test(v)) return {url:`http://localhost${v}/`,port:+v.slice(1)};
  if(/^localhost:\d+/.test(v)) return {url:`http://${v}/`,port:+v.split(":")[1]};
  if(/^127\.0\.0\.1:\d+/.test(v)) return {url:`http://${v}/`,port:+v.split(":")[1]};
  if(/^https?:\/\//.test(v)) return {url:v,port:(v.match(/:(\d+)/)||[])[1]};
  return {url:`http://localhost:${v}/`,port:v};
}
function quickOpen(){
  const v=document.getElementById('quickInput').value||"5173";
  const {url}=normalizeLocalInput(v);
  location.href='./browser.html?url='+encodeURIComponent(url);
}
function fakeAdd(){
  const n=document.getElementById('fName').value||"Untitled";
  const {port}=normalizeLocalInput(document.getElementById('fUrl').value||"3000");
  PROJECTS.unshift({name:n,port:port,color:"#56c2bb",desc:`custom / :${port}`,live:true});
  document.getElementById('dlg').close(); renderGrid();
  alert(`[mock] Probed :${port} > 200 OK, 11ms. Project saved.`);
}
function renderGrid(){
  const g=document.getElementById('grid'); if(!g) return;
  document.getElementById('count').textContent=PROJECTS.length;
  g.innerHTML=PROJECTS.map((p,i)=>`
    <div class="card" style="--p:${p.color}" onclick="location.href='./browser.html?url=${encodeURIComponent('http://localhost:'+p.port+'/')}'">
      <div class="avatar">${p.name[0]}</div>
      <div class="meta"><b><span class="dot ${p.live?'live':'off'}"></span>${p.name}</b>
        <div style="margin-top:4px"><code>:${p.port}</code></div><span>${p.desc} ${p.live?'[LIVE/mock]':'[OFF/mock]'}</span></div>
      <div class="actions"><button class="open">Open</button><button class="ghost" title="Pin" onclick="event.stopPropagation();alert('[mock] Pinned ${p.name} to Home')">${SVG_PIN}</button></div>
    </div>`).join('')+`<button class="add" onclick="document.getElementById('dlg').showModal()">+ Add localhost project — 3000 / 5173 / 8000</button>`;
  initResizers();
}

// --- browser mock ---
let tabs=[5173,3000,8000], active=0;
function initBrowser(){
  const q=new URLSearchParams(location.search);
  const startUrl=q.get('url')||'http://localhost:5173/';
  const m=startUrl.match(/:(\d+)/); const startPort=m?+m[1]:5173;
  if(!tabs.includes(startPort)) tabs.unshift(startPort);
  active=tabs.indexOf(startPort);
  renderTabs(); renderPorts(); renderView();
  initResizers();
  document.addEventListener('keydown',e=>{if(e.key==='F12'){e.preventDefault();toggleDevtools()}});
  const u=document.getElementById('url'); u.value=startUrl;
  u.addEventListener('keydown',e=>{if(e.key==='Enter'){const n=normalizeLocalInput(u.value);switchPort(n.port);}});
  const rb=document.getElementById('reloadBtn');
  let t; rb.addEventListener('pointerdown',()=>{t=setTimeout(()=>hardReload(),550)});
  rb.addEventListener('pointerup',()=>{clearTimeout(t);softReload()});
}
function renderTabs(){
  const el=document.getElementById('tabs'); if(!el) return;
  document.getElementById('tabN').textContent=tabs.length;
  el.innerHTML=tabs.map((p,i)=>`<div class="tab ${i===active?'active':''} ${i===3?'off':''}" onclick="switchTab(${i})"><i></i>:${p} <em>${i===active?'active':'tap'}</em><span onclick="event.stopPropagation();closeTab(${i})">${SVG_X}</span></div>`).join('')
   +(tabs.length<5?`<div class="tab" onclick="newTab()">+ new</div>`:`<div class="tab off"><i></i>max 5</div>`);
}
function renderPorts(){
  const el=document.getElementById('ports'); if(!el) return;
  el.innerHTML=PORTS.map(p=>`<button class="${tabs[active]===p?'on':''}" onclick="switchPort(${p})">:${p}</button>`).join('');
}
function renderView(){
  const v=document.getElementById('view'); const port=tabs[active];
  document.getElementById('portPill').textContent=':'+port;
  document.getElementById('url').value=`http://localhost:${port}/`;
  const live = PROJECTS.find(x=>x.port===port)?.live ?? (port===5173||port===3000);
  document.getElementById('stat').textContent = live?`[OK] :${port} live — 200 [mock]`:`[OFF] :${port} offline [mock]`;
  document.getElementById('ms').textContent = live?`${8+port%17}ms / HMR WS OK`:`retry / start server`;
  v.innerHTML = live?`
    <div class="fake-page"><header><div class="winbar"><i class="fill"></i><i></i><i></i><span style="margin-left:6px">localhost:${port} — root/index [mock render]</span></div><h3>&gt; localhost:${port} loads instantly_</h3><p>Vite HMR connected / no tracking / DOM storage + SW on</p></header>
    <div class="body"><div class="skel" style="width:60%"></div><div class="skel"></div><div class="skel" style="width:80%"></div>
    <div class="btnrow"><span>Count is 0</span><span class="alt">HMR update 40ms [mock]</span></div>
    <span class="hint">YOUR APP RENDERS HERE — SINGLE TUNED WEBVIEW.</span></div></div>`
   :`<div class="offline"><h3>> nothing on :${port}</h3><div>Start your dev server, then retry.<br/><code>npm run dev -- --port ${port}</code></div><br/><button class="open" onclick="switchPort(${port})">Retry probe [mock]</button></div>`;
  initResizers();
}
// --- DevTools panel (F12 mock) ---
let dtOpen=false, dtTab='html';
function toggleDevtools(){
  dtOpen=!dtOpen;
  document.getElementById('dtpanel').hidden=!dtOpen;
  document.getElementById('dtBtn').classList.toggle('open',dtOpen);
  if(dtOpen) renderDevtools();
}
function setDtTab(t){dtTab=t;renderDevtools()}
function dtSource(port){
  const html=`<span class="c">&lt;!-- http://localhost:${port}/ — served locally [mock] --&gt;</span>
<span class="k">&lt;div</span> <span class="t">id</span>=<span class="s">"root"</span> <span class="t">data-port</span>=<span class="s">"${port}"</span><span class="k">&gt;</span>
  <span class="k">&lt;h1&gt;</span>Hello :${port}<span class="k">&lt;/h1&gt;</span>
  <span class="k">&lt;script</span> <span class="t">src</span>=<span class="s">"/src/main.jsx"</span> <span class="t">type</span>=<span class="s">"module"</span><span class="k">&gt;&lt;/script&gt;</span>
<span class="k">&lt;/div&gt;</span>`;
  const js=`<span class="c">// console — localhost:${port} [mock]</span>
<span class="k">import</span> { createRoot } <span class="k">from</span> <span class="s">"react-dom/client"</span>;
<span class="k">const</span> port = <span class="t">${port}</span>;
fetch(<span class="s">\`http://localhost:${port}/api/health\`</span>)
  .then(r =&gt; r.json()).then(d =&gt; console.log(<span class="s">"[OK] :${port}"</span>, d));
<span class="c">// HMR WS connected — 40ms reload [mock]</span>
<span class="k">if</span> (<span class="k">import</span>.meta.hot) <span class="k">import</span>.meta.hot.accept();`;
  const log=`<span class="t">[OK]</span> GET http://localhost:${port}/ — 200 — ${8+port%17}ms
<span class="t">[OK]</span> HMR WS ws://localhost:${port}/ — connected
<span class="c">[SYS]</span> DOM storage + ServiceWorker ON — no tracking`;
  return {html,js,log};
}
function renderDevtools(){
  const port=tabs[active];
  document.getElementById('dtPort').textContent=':'+port;
  ['Html','Js','Log'].forEach(k=>document.getElementById('dtTab'+k).classList.toggle('on',dtTab===k.toLowerCase()));
  document.getElementById('dtCode').innerHTML=dtSource(port)[dtTab];
}
function switchTab(i){active=i;renderTabs();renderPorts();renderView()}
function switchPort(p){if(!tabs.includes(p)){if(tabs.length>=5)tabs.pop();tabs.unshift(p)}active=tabs.indexOf(p);renderTabs();renderPorts();renderView();if(dtOpen)renderDevtools()}
function closeTab(i){if(tabs.length===1)return;tabs.splice(i,1);active=Math.max(0,i-1);renderTabs();renderPorts();renderView()}
function newTab(){if(tabs.length>=5){alert('[mock] Max 5 tabs — LocalView stays light on purpose.');return}tabs.push(4321);active=tabs.length-1;renderTabs();renderPorts();renderView()}
function toggleTabs(){const el=document.getElementById('tabs');el.scrollIntoView({behavior:'smooth'})}
function softReload(){renderView();fakeToast('Reloaded [mock, cache kept]')}
function hardReload(){closeMenu();renderView();fakeToast('Hard reload — cache bypassed [mock]')}
function openMenu(){document.getElementById('menu').classList.add('open');document.getElementById('scrim').classList.add('open')}
function closeMenu(){document.getElementById('menu')?.classList.remove('open');document.getElementById('scrim')?.classList.remove('open')}
function fakeToast(m){document.getElementById('stat').textContent='[SYS] '+m;setTimeout(renderView,1400)}
// --- resize dots: one dedicated drag area per window ---
function attachResizer(el, axis){
  if(!el||el.dataset.rz) return; el.dataset.rz='1'; el.classList.add('rz');
  axis=axis||'both';
  const h=document.createElement('div'); h.className='rz-dot'; h.title='Drag to resize';
  h.innerHTML='<i></i>'; el.appendChild(h);
  h.addEventListener('dblclick',e=>{e.stopPropagation();el.style.width='';el.style.height='';if(el.classList.contains('dtpanel'))el.style.maxHeight='';});
  h.addEventListener('pointerdown',e=>{
    e.preventDefault(); e.stopPropagation();
    try{h.setPointerCapture(e.pointerId)}catch(_){}
    const sx=e.clientX, sy=e.clientY, sw=el.offsetWidth, sh=el.offsetHeight;
    const maxW=el.parentElement?el.parentElement.clientWidth:sw;
    const move=ev=>{
      const dx=ev.clientX-sx, dy=ev.clientY-sy;
      if(axis==='both'||axis==='x'){el.style.width=Math.min(maxW,Math.max(220,sw+dx))+'px'}
      if(axis==='both'||axis==='y'){el.style.height=Math.max(90,sh+dy)+'px';if(el.classList.contains('dtpanel'))el.style.maxHeight='none'}
    };
    const up=()=>{h.removeEventListener('pointermove',move);h.removeEventListener('pointerup',up);h.removeEventListener('pointercancel',up)};
    h.addEventListener('pointermove',move); h.addEventListener('pointerup',up); h.addEventListener('pointercancel',up);
  });
}
function initResizers(){
  document.querySelectorAll('.fake-page').forEach(el=>attachResizer(el,'both'));
  document.querySelectorAll('.offline').forEach(el=>attachResizer(el,'both'));
  const dt=document.getElementById('dtpanel'); if(dt) attachResizer(dt,'y');
  document.querySelectorAll('.feat').forEach(el=>attachResizer(el,'y'));
  const hero=document.querySelector('.hero-mini'); if(hero) attachResizer(hero,'x');
}
