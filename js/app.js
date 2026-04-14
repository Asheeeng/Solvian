const App = (() => {
  'use strict';

  // ━━ 配置 ━━
  const API = '/api';
  const MOCK = false;

  // ━━ 状态 ━━
  let students = [];
  let curStu = null;
  let curW = null;

  const $ = id => document.getElementById(id);

  // ================================================================
  //  INIT
  // ================================================================
  function init() {
    syncSessionUi();
    if (MOCK) { students = mockStudents(); }
    else { loadStudents(); }
    renderList(students);
    $('sbSearch').addEventListener('input', function () {
      const q = this.value.trim().toLowerCase();
      renderList(students.filter(s => s.name.includes(q) || s.sid.includes(q)));
    });
    document.addEventListener('keydown', e => { if (e.key === 'Escape') closeLb(); });
  }

  async function loadStudents() {
    try {
      const r = await fetch(`${API}/students`);
      students = await r.json();
      renderList(students);
    } catch (e) { console.error('加载失败:', e); }
  }

  // ================================================================
  //  学生列表
  // ================================================================
  function renderList(list) {
    $('sbCount').textContent = list.length;
    $('sbList').innerHTML = list.map(s => {
      const pn = s.works.filter(w => w.status === 'pending').length;
      return `<li class="sb-item${curStu && curStu.id === s.id ? ' active' : ''}"
        data-id="${s.id}" onclick="App.pickStu('${s.id}')">
        <div class="s-name">${s.name}</div>
        <div class="s-info"><span>${s.works.length}份</span>${pn ? `<span class="s-badge">${pn}待批</span>` : ''}</div>
        ${pn ? '<div class="s-dot"></div>' : ''}
      </li>`;
    }).join('');
  }

  // ================================================================
  //  添加学生
  // ================================================================
  function showAddStudent() {
    $('modalOv').classList.remove('hidden');
    $('addName').value = '';
    $('addSid').value = '';
    setTimeout(() => $('addName').focus(), 100);
  }
  function closeModal(ev) {
    if (ev && ev.target !== $('modalOv')) return;
    $('modalOv').classList.add('hidden');
  }
  async function addStudent() {
    const name = $('addName').value.trim();
    const cls = $('addClass').value.trim() || '数学A班';
    const sid = $('addSid').value.trim() || ('s' + Date.now().toString(36));
    if (!name) { alert('请输入姓名'); return; }
    const stu = { id: sid, sid, name, cls, works: [] };
    if (!MOCK) {
      try {
        const r = await fetch(`${API}/students`, {
          method: 'POST', headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(stu)
        });
        const d = await r.json(); stu.id = d.id || stu.id;
      } catch (e) { console.error(e); }
    }
    students.push(stu);
    renderList(students);
    closeModal();
  }

  // ================================================================
  //  选择学生
  // ================================================================
  function pickStu(id) {
    curStu = students.find(s => s.id === id);
    curW = null;
    if (!curStu) return;
    document.querySelectorAll('.sb-item').forEach(el => el.classList.toggle('active', el.dataset.id === id));
    $('wsEmpty').classList.add('hidden');
    $('wsHw').classList.remove('hidden');
    $('wsName').textContent = curStu.name;
    $('wsMeta').textContent = `${curStu.cls} · ${curStu.works.length} 份作业`;
    renderGrid(curStu.works);
    resetDiag();
    resetBtn();
  }

  // ================================================================
  //  图片网格
  // ================================================================
  function renderGrid(works) {
    const g = $('imgGrid');
    if (!works.length) {
      g.innerHTML = '<p style="color:var(--t3);font-size:.74rem;padding:10px">暂无提交，点击下方上传</p>';
      return;
    }
    g.innerHTML = works.map(w => {
      const sl = w.status === 'pending' ? '待检测' : '已批改';
      const sc = w.status === 'pending' ? 'pending' : 'done';
      return `<div class="img-th${curW && curW.id === w.id ? ' sel' : ''}"
        data-wid="${w.id}" onclick="App.pickW('${w.id}')">
        <img src="${w.url}" alt="${w.file}" loading="lazy">
        <div class="zm" onclick="event.stopPropagation();App.openLb('${w.url}')">🔍</div>
        <span class="st ${sc}">${sl}</span>
        <div class="ov"><div class="fn">${w.file}</div><div class="tm">${w.time}</div></div>
      </div>`;
    }).join('');
  }

  // ================================================================
  //  上传
  // ================================================================
  async function handleUpload(input) {
    if (!curStu || !input.files.length) return;
    for (const file of input.files) {
      const url = await toDataURL(file);
      const now = new Date();
      const time = `${p2(now.getMonth() + 1)}-${p2(now.getDate())} ${p2(now.getHours())}:${p2(now.getMinutes())}`;
      const w = { id: 'w' + Date.now().toString(36) + rnd(), file: file.name, time, status: 'pending', url };
      if (!MOCK) {
        try {
          const fd = new FormData(); fd.append('image', file); fd.append('studentId', curStu.id);
          const r = await fetch(`${API}/upload`, { method: 'POST', body: fd });
          const d = await r.json(); w.id = d.id || w.id; w.url = d.url || w.url;
        } catch (e) { console.error(e); }
      }
      curStu.works.unshift(w);
    }
    renderGrid(curStu.works);
    renderList(students);
    input.value = '';
  }

  // ================================================================
  //  选择图片
  // ================================================================
  function pickW(wid) {
    curW = curStu.works.find(w => w.id === wid);
    if (!curW) return;
    document.querySelectorAll('.img-th').forEach(el => el.classList.toggle('sel', el.dataset.wid === wid));
    $('btnRun').disabled = false;
    $('wsTip').textContent = `已选：${curW.file}`;
    $('wsTip').className = 'ws-tip ok';
    resetDiag();
  }

  function resetBtn() {
    $('btnRun').disabled = true;
    $('wsTip').textContent = '';
    $('wsTip').className = 'ws-tip';
  }

  function goBack() {
    curStu = null; curW = null;
    $('wsHw').classList.add('hidden');
    $('wsEmpty').classList.remove('hidden');
    document.querySelectorAll('.sb-item').forEach(el => el.classList.remove('active'));
    resetBtn();
  }

  // ================================================================
  //  AI 诊断
  // ================================================================
  async function runAI() {
    if (!curW) return;
    const btn = $('btnRun');
    btn.classList.add('loading'); btn.disabled = true;
    $('diagWait').classList.add('hidden');
    $('diagMain').classList.remove('hidden');
    $('pvImg').src = curW.url;
    $('errBoxes').innerHTML = '';
    hideSec(); resetPipe();
    $('diagMain').scrollIntoView({ behavior: 'smooth', block: 'start' });

    let result;
    try {
      await wait(400); setPipe(1, 'done'); pTag('处理中…');

      if (MOCK) {
        await wait(900); setPipe(2, 'done');
        result = mockResult();
        showOCR(result.ocr);
        await wait(1100); setPipe(3, 'done');
        showTrace(result.steps);
        await wait(500); setPipe(4, 'done'); pTag('✅ 完成', 'ok');
      } else {
        setPipe(2, 'on'); pTag('OCR中…');
        const fd = new FormData();
        fd.append('image_url', curW.url);
        fd.append('student_id', curStu.id);
        fd.append('work_id', curW.id);
        const r = await fetch(`${API}/diagnose`, { method: 'POST', body: fd });
        result = await r.json();
        setPipe(2, 'done'); showOCR(result.ocr || []);
        setPipe(3, 'done'); showTrace(result.steps || []);
        setPipe(4, 'done'); pTag('✅ 完成', 'ok');
      }

      showExplain(result.steps);
      showBoxes(result.steps);

      curW.status = 'done';
      renderGrid(curStu.works);
      document.querySelector(`.img-th[data-wid="${curW.id}"]`)?.classList.add('sel');
      renderList(students);
    } catch (e) {
      console.error('诊断失败:', e);
      pTag('❌ 失败', 'err');
    } finally {
      btn.classList.remove('loading'); btn.disabled = false;
    }
  }

  // ── Pipeline ──
  function resetPipe() {
    document.querySelectorAll('.pp').forEach(el => { el.classList.remove('done', 'on', 'fail'); });
    document.querySelector('.pp[data-n="1"]').classList.add('on');
    pTag('等待');
  }
  function setPipe(n, state) {
    document.querySelectorAll('.pp').forEach(el => {
      const num = +el.dataset.n;
      if (num < n) { el.classList.remove('on'); el.classList.add('done'); }
      else if (num === n) {
        el.classList.remove('on'); el.classList.add(state);
        if (state === 'done' && num < 4) document.querySelector(`.pp[data-n="${num + 1}"]`)?.classList.add('on');
      }
    });
  }
  function pTag(text, type) {
    const el = $('pipeStatus');
    el.textContent = text;
    el.style.background = type === 'ok' ? 'var(--okbg)' : type === 'err' ? 'var(--errbg)' : '';
    el.style.color = type === 'ok' ? 'var(--ok)' : type === 'err' ? 'var(--err)' : '';
  }
  function hideSec() {
    $('ocrSec').classList.add('hidden');
    $('traceSec').classList.add('hidden');
    $('explSec').classList.add('hidden');
  }
  function resetDiag() {
    $('diagWait').classList.remove('hidden');
    $('diagMain').classList.add('hidden');
    hideSec();
  }

  // ── OCR ──
  function showOCR(frags) {
    if (!frags.length) return;
    $('ocrOut').innerHTML = frags.map(f => `
      <div style="margin-bottom:6px">
        <span class="ocr-lbl">${esc(f.label)}</span>
        <div class="ocr-pre">${esc(f.text)}</div>
      </div>`).join('');
    $('ocrSec').classList.remove('hidden');
  }

  // ── 步骤追踪 ──
  function showTrace(steps) {
    if (!steps.length) return;
    $('traceOut').innerHTML = steps.map(s => {
      const cls = s.ok ? 'ok' : 'bad';
      const icon = s.ok ? '✅' : '❌';
      const open = !s.ok ? ' open' : '';
      let fH = esc(s.formula).replace(/&lt;ERR&gt;(.*?)&lt;\/ERR&gt;/g, '<span class="me">$1</span>');
      let body = `<div class="fb">${fH}</div>`;
      if (s.note) body += `<div class="tc-note"><strong>📝</strong> ${s.note}</div>`;
      if (!s.ok && s.formulaOk) {
        body += `<div class="tc-ok"><div class="cl">✅ 正确结果：</div><div class="fb"><span class="mo">${esc(s.formulaOk)}</span></div></div>`;
      }
      return `<div class="tc ${cls}${open}">
        <div class="tc-top" onclick="this.parentElement.classList.toggle('open')">
          <div class="tc-l"><div class="tc-n">${s.n}</div><div class="tc-nm">${s.title}</div></div>
          <div class="tc-ic">${icon}</div>
        </div>
        <div class="tc-body">${body}</div>
      </div>`;
    }).join('');
    $('traceSec').classList.remove('hidden');
  }

  // ── 错误详解 ──
  function showExplain(steps) {
    const errs = steps.filter(s => !s.ok && s.errors);
    if (!errs.length) {
      $('explOut').innerHTML = `<div class="all-ok"><div class="big">🎉</div><div class="t">全部步骤正确！</div><div class="s">没有发现错误</div></div>`;
      $('explSec').classList.remove('hidden');
      return;
    }
    $('explOut').innerHTML = errs.flatMap(s => s.errors.map(e => `
      <div class="ex-card">
        <div class="ex-title"><div class="ex-ic">⚠️</div>步骤 ${s.n} ·「${s.title}」发现错误</div>
        <div class="ex-grid">
          <div class="ex-cell loc"><div class="cl">📍 错误位置</div><div class="cv">${e.loc}</div></div>
          <div class="ex-cell"><div class="cl">📐 所在步骤</div><div class="cv" style="color:var(--t1)">${s.title}</div></div>
          <div class="ex-cell wrong"><div class="cl">❌ 学生写的</div><div class="cv">${e.wrote}</div></div>
          <div class="ex-cell right"><div class="cl">✅ 正确答案</div><div class="cv">${e.correct}</div></div>
        </div>
        <div class="ex-exp"><div class="etag">💡 错误分析</div><p>${e.explain}</p></div>
        <div class="ex-tip"><div class="ti">🎯</div><div class="tt">${e.suggest}</div></div>
      </div>`)).join('');
    $('explSec').classList.remove('hidden');
  }

  // ── 错误框叠加 ──
  function showBoxes(steps) {
    const wrap = $('errBoxes'); wrap.innerHTML = '';
    steps.forEach(s => {
      if (!s.ok && s.boxes) {
        s.boxes.forEach(b => {
          const d = document.createElement('div');
          d.className = 'ebox';
          d.style.cssText = `left:${b.x}%;top:${b.y}%;width:${b.w}%;height:${b.h}%`;
          d.innerHTML = `<div class="elbl">⚠ ${b.label}</div>`;
          d.onclick = ev => { ev.stopPropagation(); $('explSec')?.scrollIntoView({ behavior: 'smooth' }); };
          wrap.appendChild(d);
        });
      }
    });
  }

  // ── Lightbox ──
  function openLb(src) {
    const s = src || $('pvImg')?.src; if (!s) return;
    $('lbImg').src = s;
    $('lightbox').classList.add('open');
    document.body.style.overflow = 'hidden';
  }
  function closeLb(ev) {
    if (ev && ev.target.tagName === 'IMG') return;
    $('lightbox').classList.remove('open');
    document.body.style.overflow = '';
  }

  function switchMainTab(name) {
    document.querySelectorAll('.topbar-tab').forEach(function (btn) {
      btn.classList.toggle('active', btn.dataset.tab === name);
    });
    ['viewDiagnosis', 'viewMistakes', 'viewStats'].forEach(function (id) {
      const el = $(id);
      if (el) el.classList.add('hidden');
    });
    const map = { diagnosis: 'viewDiagnosis', mistakes: 'viewMistakes', stats: 'viewStats' };
    const target = $(map[name] || 'viewDiagnosis');
    if (target) target.classList.remove('hidden');
  }

  function doLogout() {
    if (!confirm('确认退出登录？')) return;
    localStorage.removeItem('solvian_logged');
    localStorage.removeItem('copilot_auth_session');
    localStorage.removeItem('solvian_token');
    localStorage.removeItem('solvian_user');
    localStorage.removeItem('solvian_role');
    window.location.href = '/login.html';
  }

  function syncSessionUi() {
    const roleTag = document.querySelector('.role-tag');
    if (!roleTag) return;
    const role = (localStorage.getItem('solvian_role') || '').toLowerCase();
    const map = { teacher: '教师', student: '学生', admin: '管理员' };
    roleTag.textContent = map[role] || '教师';
  }

  function showStats() { alert('错题统计开发中'); }
  function showSettings() { alert('系统设置开发中'); }

  // ================================================================
  //  模拟数据
  // ================================================================
  function svg(name, sub) {
    return 'data:image/svg+xml,' + encodeURIComponent(
      `<svg xmlns="http://www.w3.org/2000/svg" width="400" height="300"><rect width="400" height="300" fill="#111128"/><text x="50%" y="42%" fill="#3a3a66" text-anchor="middle" font-size="15" font-family="sans-serif">${name}</text><text x="50%" y="58%" fill="#28284e" text-anchor="middle" font-size="11" font-family="sans-serif">${sub}</text></svg>`);
  }

  function mockStudents() {
    return [
      { id:'s1',sid:'2024001',name:'张伟',cls:'数学A班',works:[
        {id:'w1',file:'matrix_001.jpg',time:'04-12 16:08',status:'pending',url:svg('matrix_001.jpg','矩阵行变换')},
        {id:'w2',file:'截图_0410.png',time:'04-10 14:22',status:'done',url:svg('截图_0410.png','行列式计算')},
        {id:'w3',file:'hw_03.jpg',time:'04-08 09:30',status:'done',url:svg('hw_03.jpg','矩阵乘法')}
      ]},
      { id:'s2',sid:'2024002',name:'李娜',cls:'数学A班',works:[
        {id:'w4',file:'matrix_007.jpg',time:'04-11 19:45',status:'pending',url:svg('matrix_007.jpg','初等行变换')}
      ]},
      { id:'s3',sid:'2024003',name:'王强',cls:'数学A班',works:[
        {id:'w5',file:'linear_1.png',time:'04-12 08:10',status:'pending',url:svg('linear_1.png','特征值求解')},
        {id:'w6',file:'linear_2.png',time:'04-11 21:33',status:'pending',url:svg('linear_2.png','矩阵求逆')}
      ]},
      { id:'s4',sid:'2024004',name:'赵敏',cls:'数学A班',works:[
        {id:'w7',file:'ch3_hw.jpg',time:'04-09 10:15',status:'done',url:svg('ch3_hw.jpg','向量空间')}
      ]},
      { id:'s5',sid:'2024005',name:'陈浩',cls:'数学A班',works:[
        {id:'w8',file:'gauss.png',time:'04-12 11:00',status:'pending',url:svg('gauss.png','高斯消元')},
        {id:'w9',file:'echelon.png',time:'04-11 17:45',status:'pending',url:svg('echelon.png','阶梯形矩阵')}
      ]},
      { id:'s6',sid:'2024006',name:'刘洋',cls:'数学A班',works:[] },
      { id:'s7',sid:'2024007',name:'孙倩',cls:'数学A班',works:[
        {id:'w10',file:'eigen.png',time:'04-12 14:20',status:'pending',url:svg('eigen.png','特征值')}
      ]}
    ];
  }

  function mockResult() {
    return {
      ocr: [
        { label: '题目矩阵', text: 'M = [3 -3 -3 3; -3 -1 0 -2; 2 3 -4 3; 2 5 0 -1]' },
        { label: '学生第3步', text: 'R1→2R1: [6 -6 -6 6; 0 -1 0 -2; -2 -3 4 -3; 2 5 0 -1]' }
      ],
      steps: [
        { n:1, title:'写出原始矩阵 M', ok:true,
          formula:' 3  -3  -3   3\n-3  -1   0  -2\n 2   3  -4   3\n 2   5   0  -1',
          note:'原始矩阵抄写正确。' },
        { n:2, title:'R3 → -1·R3（第三行乘 -1）', ok:true,
          formula:' 3  -3  -3   3\n-3  -1   0  -2\n-2  -3   4  -3\n 2   5   0  -1',
          note:'第三行每个元素正确乘以 -1。' },
        { n:3, title:'R1 → 2·R1（第一行乘 2）', ok:false,
          formula:' 6  -6  -6   6\n<ERR>0</ERR>  -1   0  -2\n-2  -3   4  -3\n 2   5   0  -1',
          formulaOk:' 6  -6  -6   6\n-3  -1   0  -2\n-2  -3   4  -3\n 2   5   0  -1',
          note:'第一行乘2正确，但第二行被误改。',
          errors: [{
            loc: '第2行 · 第1列',
            wrote: '0',
            correct: '-3',
            explain: '执行 <span class="he">R1 → 2·R1</span> 时，这个操作<strong>只对第一行生效</strong>。但你把第二行第一个元素从 <span class="he">-3 改成了 0</span>，这属于误改。<span class="ho">第二行应保持不变，仍为 -3</span>。',
            suggest: '做行变换时<strong>只修改目标行</strong>。建议用笔盖住其他行，做完后逐行核对一遍。'
          }],
          boxes: [{ x:12, y:38, w:15, h:10, label:'误改位置' }]
        }
      ]
    };
  }

  // ── 工具 ──
  function wait(ms) { return new Promise(r => setTimeout(r, ms)); }
  function esc(s) { const d = document.createElement('div'); d.textContent = s; return d.innerHTML; }
  function p2(n) { return String(n).padStart(2, '0'); }
  function rnd() { return Math.random().toString(36).slice(2, 5); }
  function toDataURL(file) { return new Promise(r => { const fr = new FileReader(); fr.onload = () => r(fr.result); fr.readAsDataURL(file); }); }

  document.addEventListener('DOMContentLoaded', init);

  return {
    pickStu, pickW, goBack, runAI, handleUpload,
    showAddStudent, addStudent, closeModal,
    openLb, closeLb, switchMainTab, doLogout,
    showStats, showSettings
  };
})();

window.App = App;
