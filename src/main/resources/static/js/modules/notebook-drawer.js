function formatTime(timestamp) {
  if (!timestamp) {
    return '-';
  }
  return new Date(timestamp).toLocaleString('zh-CN', { hour12: false });
}

export class NotebookDrawer {
  constructor({ drawer, drawerMask, drawerTitle, drawerBody, closeBtn }) {
    this.drawer = drawer;
    this.drawerMask = drawerMask;
    this.drawerTitle = drawerTitle;
    this.drawerBody = drawerBody;
    this.closeBtn = closeBtn;

    this.closeBtn.addEventListener('click', () => this.close());
    this.drawerMask.addEventListener('click', () => this.close());
  }

  openNotebook(historyResponse) {
    this.drawerTitle.textContent = '错题本';

    const list = historyResponse?.list || [];
    if (!list.length) {
      this.drawerBody.innerHTML = '<p class="muted">暂无错题记录，请先完成一次诊断。</p>';
      this.show();
      return;
    }

    this.drawerBody.innerHTML = list
      .map(
        (r) => `
        <article class="record-card">
          <p><strong>题目：</strong>${r.fileName || '未命名题目'}</p>
          <p><strong>诊断状态：</strong>${r.status}</p>
          <p><strong>错误定位：</strong>${r.errorIndex ?? '-'}</p>
          <p><strong>AI反馈：</strong>${r.aiFeedback || '-'}</p>
          <p><strong>错误类型：</strong>${r.errorType || '-'}</p>
          <p><strong>时间：</strong>${formatTime(r.createdAt)}</p>
        </article>
      `
      )
      .join('');

    this.show();
  }

  openStats(summaryResponse) {
    this.drawerTitle.textContent = '统计面板';

    const summary = summaryResponse?.summary || {};
    const total = summary.totalEvaluations || 0;
    const errorTypeCount = summary.errorTypeCount || {};
    const aiFeedbackCount = summary.aiFeedbackCount || {};

    const errorCards = Object.keys(errorTypeCount).length
      ? Object.entries(errorTypeCount)
        .map(([key, count]) => `
          <article class="record-card">
            <p><strong>${key}</strong></p>
            <p>出现次数：${count}</p>
          </article>
        `)
        .join('')
      : '<p class="muted">暂无错误类型统计。</p>';

    const feedbackCards = Object.keys(aiFeedbackCount).length
      ? Object.entries(aiFeedbackCount)
        .map(([key, count]) => `
          <article class="record-card">
            <p><strong>${key}</strong></p>
            <p>反馈次数：${count}</p>
          </article>
        `)
        .join('')
      : '<p class="muted">暂无反馈统计。</p>';

    this.drawerBody.innerHTML = `
      <article class="record-card">
        <p><strong>累计诊断次数：</strong>${total}</p>
      </article>
      <h4>错误类型统计</h4>
      ${errorCards}
      <h4>AI反馈统计</h4>
      ${feedbackCards}
    `;

    this.show();
  }

  show() {
    this.drawer.classList.remove('hidden');
    this.drawerMask.classList.remove('hidden');
    this.drawer.setAttribute('aria-hidden', 'false');
  }

  close() {
    this.drawer.classList.add('hidden');
    this.drawerMask.classList.add('hidden');
    this.drawer.setAttribute('aria-hidden', 'true');
  }
}
