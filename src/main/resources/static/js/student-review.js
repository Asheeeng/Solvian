// =================================================================
//  Solvian · student-review.js
//  学生作业AI诊断 - 完整交互逻辑
//  接入你现有的 API 只需修改 callAPI() 函数
// =================================================================

const App = (() => {

  // ──────────── 模拟数据 ────────────
  // 实际使用时这些数据从后端 API 获取

  const placeholder = (name, sub) => {
    return 'data:image/svg+xml,' + encodeURIComponent(
      `<svg xmlns="http://www.w3.org/2000/svg" width="400" height="300">
        <rect width="400" height="300" fill="#12122a"/>
        <text x="50%" y="42%" fill="#4a4a70" text-anchor="middle" font-size="16" font-family="sans-serif">${name}</text>
        <text x="50%" y="58%" fill="#2a2a50" text-anchor="middle" font-size="11" font-family="sans-serif">${sub}</text>
      </svg>`
    );
  };

  const STUDENTS = [
    {
      id: 'stu01', name: '张伟', cls: '数学A班',
      works: [
        { id: 'w1', file: 'matrix_001.jpg', time: '04-12 16:08', status: 'pending', url: placeholder('matrix_001.jpg', '矩阵行变换') },
        { id: 'w2', file: '截图_0410.png', time: '04-10 14:22', status: 'done', url: placeholder('截图_0410.png', '行列式计算') },
        { id: 'w3', file: 'hw_03.jpg', time: '04-08 09:30', status: 'done', url: placeholder('hw_03.jpg', '矩阵乘法') }
      ]
    },
    {
      id: 'stu02', name: '李娜', cls: '数学A班',
      works: [
        { id: 'w4', file: 'matrix_007.jpg', time: '04-11 19:45', status: 'pending', url: placeholder('matrix_007.jpg', '初等行变换') }
      ]
    },
    {
      id: 'stu03', name: '王强', cls: '数学A班',
      works: [
        { id: 'w5', file: 'linear_1.png', time: '04-12 08:10', status: 'pending', url: placeholder('linear_1.png', '特征值求解') },
        { id: 'w6', file: 'linear_2.png', time: '04-11 21:33', status: 'pending', url: placeholder('linear_2.png', '矩阵求逆') }
      ]
    },
    {
      id: 'stu04', name: '赵敏', cls: '数学A班',
      works: [
        { id: 'w7', file: 'ch3_hw.jpg', time: '04-09 10:15', status: 'done', url: placeholder('ch3_hw.jpg', '向量空间') }
      ]
    },
    {
      id: 'stu05', name: '陈浩', cls: '数学A班',
      works: [
        { id: 'w8', file: 'gauss.png', time: '04-12 11:00', status: 'pending', url: placeholder('gauss.png', '高斯消元') },
        { id: 'w9', file: 'echelon.png', time: '04-11 17:45', status: 'pending', url: placeholder('echelon.png', '阶梯形矩阵') }
      ]
    },
    { id: 'stu06', name: '刘洋', cls: '数学A班', works: [] },
    {
      id: 'stu07', name: '孙倩', cls: '数学A班',
      works: [
        { id: 'w10', file: 'eigen.png', time: '04-12 14:20', status: 'pending', url: placeholder('eigen.png', '特征值问题') }
      ]
    }
  ];

  // 模拟 AI 返回结果
  const MOCK_RESULT = {
    ocr: [
      { label: '题目矩阵', text: 'M = [3 -3 -3 3; -3 -1 0 -2; 2 3 -4 3; 2 5 0 -1]' },
      { label: '学生第3步', text: 'R1→2R1: [6 -6 -6 6; 0 -1 0 -2; -2 -3 4 -3; 2 5 0 -1]' }
    ],
    steps: [
      {
        n: 1, title: '写出原始矩阵 M', ok: true,
        formula: ' 3  -3  -3   3\n-3  -1   0  -2\n 2   3  -4   3\n 2   5   0  -1',
        note: '原始矩阵抄写正确，与题目一致。'
      },
      {
        n: 2, title: 'R3 → -1·R3（第三行乘以 -1）', ok: true,
        formula: ' 3  -3  -3   3\n-3  -1   0  -2\n-2  -3   4  -3\n 2   5   0  -1',
        note: '第三行每个元素正确地乘以了 -1。'
      },
      {
        n: 3, title: 'R1 → 2·R1（第一行乘以 2）', ok: false,
        formula: ' 6  -6  -6   6\n<ERR>0</ERR>  -1   0  -2\n-2  -3   4  -3\n 2   5   0  -1',
        formulaOk: ' 6  -6  -6   6\n-3  -1   0  -2\n-2  -3   4  -3\n 2   5   0  -1',
        note: '第一行乘以 2 正确，但第二行的值被意外修改了。',
        errors: [
          {
            loc: '第2行 · 第1列',
            wrote: '0',
            correct: '-3',
            explain: '执行 <span class="hl-err">R1 → 2·R1</span> 时，这个操作<strong>只对第一行生效</strong>。但你把第二行第一个元素从 <span class="hl-err">-3 改成了 0</span>，这属于误改。<span class="hl-ok">第二行应保持不变</span>。',
            suggest: '做行变换时只修改目标行。建议用笔盖住其他行，做完后逐行检查一遍。'
          }
        ],
        boxes: [{ x: 12, y: 38, w: 15, h: 10, label: '误改位置' }]
      }
    ]
  };

  // ──────────── 状态 ────────────
  let curStu = null;
  let curImg = null;

  // ──────────── DOM 引用 ────────────
  const $ = (id) => document.getElementById(id);

  // ──────────── 初始化 ────────────
  function init() {
    renderList(STUDENTS);
    $('searchInput').addEventListener('input', function () {
      const q = this.value.trim().toLowerCase();
      renderList(STUDENTS.filter((s) => s.name.includes(q) || s.id.includes(q)));
    });
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') {
        closeLightbox();
      }
    });
  }

  // ──────────── 学生列表 ────────────
  function renderList(list) {
    $('stuTotal').textContent = list.length;
    $('stuList').innerHTML = list.map((s) => {
      const pn = s.works.filter((w) => w.status === 'pending').length;
      return `<li class="stu-item" data-id="${s.id}" onclick="App.pickStu('${s.id}')">
        <div class="name">${s.name}</div>
        <div class="meta">
          <span>${s.works.length} 份</span>
          ${pn ? `<span class="badge-pending">${pn} 待批</span>` : ''}
        </div>
        ${pn ? '<div class="pending-dot"></div>' : ''}
      </li>`;
    }).join('');
  }

  // ──────────── 选择学生 ────────────
  function pickStu(id) {
    curStu = STUDENTS.find((s) => s.id === id);
    curImg = null;
    if (!curStu) {
      return;
    }

    document.querySelectorAll('.stu-item').forEach((el) => el.classList.toggle('active', el.dataset.id === id));

    $('emptyState').classList.add('hidden');
    $('homeworkView').classList.remove('hidden');
    $('viewStuName').textContent = curStu.name;
    $('viewMeta').textContent = `${curStu.cls} · 共 ${curStu.works.length} 份作业`;

    renderGrid(curStu.works);
    resetResult();
  }

  // ──────────── 图片网格 ────────────
  function renderGrid(works) {
    if (!works.length) {
      $('imgGrid').innerHTML = '<p class="no-work">暂无提交</p>';
      return;
    }
    $('imgGrid').innerHTML = works.map((w) => {
      const st = w.status === 'pending' ? '待检测' : '已批改';
      const sc = w.status === 'pending' ? 'pending' : 'done';
      return `<div class="img-card" data-wid="${w.id}" onclick="App.pickImg('${w.id}')">
        <img src="${w.url}" alt="${w.file}" loading="lazy">
        <div class="zoom-btn" onclick="event.stopPropagation();App.openLightbox('${w.url}')">🔍</div>
        <span class="status-tag ${sc}">${st}</span>
        <div class="overlay">
          <div class="fn">${w.file}</div>
          <div class="tm">${w.time}</div>
        </div>
      </div>`;
    }).join('');
  }

  // ──────────── 选择图片 ────────────
  function pickImg(wid) {
    curImg = curStu.works.find((w) => w.id === wid);
    if (!curImg) {
      return;
    }

    document.querySelectorAll('.img-card').forEach((el) => el.classList.toggle('selected', el.dataset.wid === wid));

    $('btnDetect').disabled = false;
    $('actionTip').textContent = `✅ 已选：${curImg.file}`;
    $('actionTip').className = 'action-tip ok';

    resetResult();
  }

  // ──────────── 重置结果区 ────────────
  function resetResult() {
    $('resultArea').classList.add('hidden');
    $('ocrBlock').classList.add('hidden');
    $('traceBlock').classList.add('hidden');
    $('explainBlock').classList.add('hidden');
  }

  // ──────────── 返回 ────────────
  function goBack() {
    curStu = null;
    curImg = null;
    $('homeworkView').classList.add('hidden');
    $('emptyState').classList.remove('hidden');
    document.querySelectorAll('.stu-item').forEach((el) => el.classList.remove('active'));
    $('btnDetect').disabled = true;
    $('actionTip').textContent = '请先选择一张作业图片';
    $('actionTip').className = 'action-tip';
  }

  // ──────────── API 占位 ────────────
  async function callAPI() {
    return JSON.parse(JSON.stringify(MOCK_RESULT));
  }

  // ──────────── AI 诊断 ────────────
  async function runDetection() {
    if (!curImg) {
      return;
    }

    const btn = $('btnDetect');
    btn.classList.add('loading');
    btn.disabled = true;

    $('resultArea').classList.remove('hidden');
    $('previewImg').src = curImg.url;
    $('errorBoxes').innerHTML = '';
    resetPipeline();

    $('resultArea').scrollIntoView({ behavior: 'smooth', block: 'start' });

    try {
      await wait(500);
      setPipe(1, 'done');
      tag('处理中');

      const data = await callAPI(curImg);

      await wait(1000);
      setPipe(2, 'done');
      showOCR(data.ocr);

      await wait(1200);
      setPipe(3, 'done');
      showTrace(data.steps);

      await wait(600);
      setPipe(4, 'done');
      tag('✅ 完成', true);

      showExplain(data.steps);
      showBoxes(data.steps);

      curImg.status = 'done';
      renderGrid(curStu.works);
      document.querySelector(`.img-card[data-wid="${curImg.id}"]`)?.classList.add('selected');
    } catch (e) {
      console.error(e);
      tag('❌ 失败', false, true);
    } finally {
      btn.classList.remove('loading');
      btn.disabled = false;
    }
  }

  // ──────────── Pipeline ────────────
  function resetPipeline() {
    document.querySelectorAll('.pipe-step').forEach((el) => {
      el.classList.remove('done', 'active', 'fail');
    });
    document.querySelector('.pipe-step[data-n="1"]').classList.add('active');
    tag('等待');
  }

  function setPipe(n, state) {
    document.querySelectorAll('.pipe-step').forEach((el) => {
      const num = Number(el.dataset.n);
      if (num < n) {
        el.classList.remove('active');
        el.classList.add('done');
      } else if (num === n) {
        el.classList.remove('active');
        el.classList.add(state);
        if (state === 'done' && num < 4) {
          document.querySelector(`.pipe-step[data-n="${num + 1}"]`)?.classList.add('active');
        }
      }
    });
  }

  function tag(text, success, fail) {
    const el = $('pipeTag');
    el.textContent = text;
    el.style.background = success ? 'var(--ok-bg)' : fail ? 'var(--err-bg)' : '';
    el.style.color = success ? 'var(--ok)' : fail ? 'var(--err)' : '';
  }

  // ──────────── OCR ────────────
  function showOCR(fragments) {
    const el = $('ocrBlock');
    $('ocrContent').innerHTML = fragments.map((f) => `
      <div class="ocr-item">
        <span class="ocr-tag">${esc(f.label)}</span>
        <div class="ocr-code">${esc(f.text)}</div>
      </div>
    `).join('');
    el.classList.remove('hidden');
  }

  // ──────────── 步骤追踪 ────────────
  function showTrace(steps) {
    const el = $('traceBlock');
    $('traceCards').innerHTML = steps.map((s) => {
      const cls = s.ok ? 'correct' : 'wrong';
      const icon = s.ok ? '✅' : '❌';
      const open = !s.ok ? ' open' : '';

      const fHtml = esc(s.formula).replace(/&lt;ERR&gt;(.*?)&lt;\/ERR&gt;/g, '<span class="mark-err">$1</span>');

      let body = `<div class="formula-box">${fHtml}</div>`;
      if (s.note) {
        body += `<div class="step-note"><strong>📝</strong> ${s.note}</div>`;
      }
      if (!s.ok && s.formulaOk) {
        body += `<div class="correct-answer-box">
          <div class="ca-label">✅ 正确结果：</div>
          <div class="formula-box"><span class="mark-ok">${esc(s.formulaOk)}</span></div>
        </div>`;
      }

      return `<div class="step-card ${cls}${open}">
        <div class="step-top" onclick="this.parentElement.classList.toggle('open')">
          <div class="step-left">
            <div class="step-num">${s.n}</div>
            <div class="step-name">${s.title}</div>
          </div>
          <div class="step-icon">${icon}</div>
        </div>
        <div class="step-body">${body}</div>
      </div>`;
    }).join('');
    el.classList.remove('hidden');
  }

  // ──────────── 错误详解 ────────────
  function showExplain(steps) {
    const el = $('explainBlock');
    const errSteps = steps.filter((s) => !s.ok && s.errors);

    if (!errSteps.length) {
      $('explainCards').innerHTML = `
        <div class="all-correct">
          <div class="big">🎉</div>
          <div class="title">全部步骤正确！</div>
          <div class="sub">这位同学的解题过程没有发现错误</div>
        </div>`;
      el.classList.remove('hidden');
      return;
    }

    $('explainCards').innerHTML = errSteps.flatMap((s) =>
      s.errors.map((e) => `
        <div class="err-card">
          <div class="err-card-title">
            <div class="err-icon-circle">⚠️</div>
            步骤 ${s.n} ·「${s.title}」发现错误
          </div>

          <div class="err-grid">
            <div class="err-cell loc">
              <div class="cell-label">📍 错误位置</div>
              <div class="cell-value">${e.loc}</div>
            </div>
            <div class="err-cell">
              <div class="cell-label">📐 所在步骤</div>
              <div class="cell-value">${s.title}</div>
            </div>
            <div class="err-cell wrong">
              <div class="cell-label">❌ 学生写的</div>
              <div class="cell-value">${e.wrote}</div>
            </div>
            <div class="err-cell right">
              <div class="cell-label">✅ 正确答案</div>
              <div class="cell-value">${e.correct}</div>
            </div>
          </div>

          <div class="err-explain">
            <div class="ex-label">💡 错误分析</div>
            <p>${e.explain}</p>
          </div>

          <div class="err-suggest">
            <div class="sug-icon">🎯</div>
            <div class="sug-text">${e.suggest}</div>
          </div>
        </div>`)
    ).join('');
    el.classList.remove('hidden');
  }

  // ──────────── 错误框叠加 ────────────
  function showBoxes(steps) {
    const wrap = $('errorBoxes');
    wrap.innerHTML = '';
    steps.forEach((s) => {
      if (!s.ok && s.boxes) {
        s.boxes.forEach((b) => {
          const d = document.createElement('div');
          d.className = 'err-box';
          d.style.cssText = `left:${b.x}%;top:${b.y}%;width:${b.w}%;height:${b.h}%;`;
          d.innerHTML = `<div class="err-label">⚠ ${b.label}</div>`;
          d.onclick = (ev) => {
            ev.stopPropagation();
            $('explainBlock')?.scrollIntoView({ behavior: 'smooth' });
          };
          wrap.appendChild(d);
        });
      }
    });
  }

  // ──────────── Lightbox ────────────
  function openLightbox(src) {
    const s = src || $('previewImg')?.src;
    if (!s) {
      return;
    }
    $('lbImg').src = s;
    $('lightbox').classList.add('open');
    document.body.style.overflow = 'hidden';
  }

  function closeLightbox(ev) {
    if (ev && ev.target.tagName === 'IMG') {
      return;
    }
    $('lightbox').classList.remove('open');
    document.body.style.overflow = '';
  }

  // ──────────── 工具 ────────────
  function wait(ms) {
    return new Promise((r) => setTimeout(r, ms));
  }

  function esc(s) {
    const d = document.createElement('div');
    d.textContent = s ?? '';
    return d.innerHTML;
  }

  // ──────────── 启动 ────────────
  document.addEventListener('DOMContentLoaded', init);

  // 公共 API
  return { pickStu, pickImg, goBack, runDetection, openLightbox, closeLightbox };
})();

window.App = App;
