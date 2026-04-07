function escapeHtml(raw) {
  return String(raw ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function formatTime(timestamp) {
  if (!timestamp) {
    return '-';
  }
  return new Date(timestamp).toLocaleString('zh-CN', { hour12: false });
}

function roleLabel(role) {
  const labelMap = {
    STUDENT: '学生',
    TEACHER: '老师',
    ADMIN: '管理员'
  };
  return labelMap[role] || role || '-';
}

function statusMeta(status) {
  if (status === 'error_found') {
    return { label: '发现错误', className: 'status-badge status-badge--danger' };
  }
  if (status === 'correct') {
    return { label: '步骤正确', className: 'status-badge status-badge--success' };
  }
  return { label: status || '待判断', className: 'status-badge status-badge--pending' };
}

function renderEmptyState(title, description) {
  return `
    <div class="empty-state">
      <div class="empty-state__icon">⌁</div>
      <p class="empty-state__title">${escapeHtml(title)}</p>
      <p class="empty-state__description">${escapeHtml(description)}</p>
    </div>
  `;
}

function renderRecordChip(text, modifier = '') {
  const modifierClass = modifier ? ` ${modifier.trim()}` : '';
  return `<span class="tag-chip${modifierClass}">${escapeHtml(text)}</span>`;
}

export class NotebookDrawer {
  constructor({
    drawer,
    drawerMask,
    drawerTitle,
    drawerSubtitle,
    drawerEyebrow,
    drawerBody,
    closeBtn
  }) {
    this.drawer = drawer;
    this.drawerMask = drawerMask;
    this.drawerTitle = drawerTitle;
    this.drawerSubtitle = drawerSubtitle;
    this.drawerEyebrow = drawerEyebrow;
    this.drawerBody = drawerBody;
    this.closeBtn = closeBtn;

    this.currentHistoryList = [];
    this.activeFilter = 'ALL';

    this.closeBtn.addEventListener('click', () => this.close());
    this.drawerMask.addEventListener('click', () => this.close());
    this.drawerBody.addEventListener('click', (event) => this.handleBodyClick(event));
  }

  handleBodyClick(event) {
    const filterButton = event.target.closest('[data-history-filter]');
    if (!filterButton) {
      return;
    }

    const nextFilter = filterButton.dataset.historyFilter || 'ALL';
    this.activeFilter = nextFilter;
    this.renderNotebook();
  }

  openNotebook(historyResponse) {
    this.currentHistoryList = historyResponse?.list || [];
    this.activeFilter = 'ALL';

    this.drawerEyebrow.textContent = 'Notebook';
    this.drawerTitle.textContent = '错题本';
    this.drawerSubtitle.textContent = '按结构化卡片查看历史诊断记录，并用轻量筛选快速定位关键问题。';

    this.renderNotebook();
    this.show();
  }

  renderNotebook() {
    const list = this.currentHistoryList;
    const filteredList = this.filterHistory(list, this.activeFilter);

    if (!list.length) {
      this.drawerBody.innerHTML = renderEmptyState(
        '暂时还没有错题归档',
        '先完成一次诊断，系统就会把记录自动整理到这里。'
      );
      return;
    }

    const totalCount = list.length;
    const errorCount = list.filter((record) => record.status === 'error_found').length;
    const feedbackCount = list.filter((record) => Boolean(record.aiFeedback)).length;
    const socraticCount = list.filter((record) => Boolean(record.isSocratic)).length;

    const cards = filteredList.length
      ? filteredList.map((record) => this.renderNotebookCard(record)).join('')
      : renderEmptyState(
        '当前筛选下暂无记录',
        '换一个筛选条件试试看，或者先完成新的诊断流程。'
      );

    this.drawerBody.innerHTML = `
      <div class="drawer-stack">
        <div class="summary-grid">
          <article class="summary-card">
            <p class="stat-card__label">累计记录</p>
            <p class="summary-card__value">${totalCount}</p>
            <p class="summary-card__desc">已归档的历史诊断条目</p>
          </article>
          <article class="summary-card">
            <p class="stat-card__label">错误定位</p>
            <p class="summary-card__value">${errorCount}</p>
            <p class="summary-card__desc">被系统标记为发现错误的记录</p>
          </article>
          <article class="summary-card">
            <p class="stat-card__label">教师反馈</p>
            <p class="summary-card__value">${feedbackCount}</p>
            <p class="summary-card__desc">已经补充 AI 反馈的记录</p>
          </article>
        </div>

        <div class="drawer-toolbar" role="tablist" aria-label="错题本筛选">
          ${this.renderFilterChip('ALL', `全部 ${totalCount}`)}
          ${this.renderFilterChip('ERROR', `发现错误 ${errorCount}`)}
          ${this.renderFilterChip('FEEDBACK', `已反馈 ${feedbackCount}`)}
          ${this.renderFilterChip('SOCRATIC', `启发式 ${socraticCount}`)}
        </div>

        <div class="record-list">${cards}</div>
      </div>
    `;
  }

  renderFilterChip(key, label) {
    const activeClass = this.activeFilter === key ? ' is-active' : '';
    return `
      <button
        type="button"
        class="filter-chip${activeClass}"
        data-history-filter="${key}"
        aria-pressed="${this.activeFilter === key}"
      >${escapeHtml(label)}</button>
    `;
  }

  filterHistory(list, filterKey) {
    if (filterKey === 'ERROR') {
      return list.filter((record) => record.status === 'error_found');
    }
    if (filterKey === 'FEEDBACK') {
      return list.filter((record) => Boolean(record.aiFeedback));
    }
    if (filterKey === 'SOCRATIC') {
      return list.filter((record) => Boolean(record.isSocratic));
    }
    return list;
  }

  renderNotebookCard(record) {
    const status = statusMeta(record.status);
    const feedbackText = record.aiFeedback || '未提交';
    const errorTypeText = record.errorType || '未标记';
    const feedbackSummary = record.feedback || record.note || '当前暂无附加说明。';
    const tags = Array.isArray(record.tags) && record.tags.length ? record.tags : [];

    return `
      <article class="record-card">
        <div class="record-card__header">
          <div>
            <p class="record-card__title">${escapeHtml(record.fileName || '未命名题目')}</p>
            <p class="record-card__subtitle">${escapeHtml(formatTime(record.createdAt))} · ${escapeHtml(roleLabel(record.role))}</p>
          </div>
          <span class="${status.className}">${escapeHtml(status.label)}</span>
        </div>

        <div class="record-card__meta">
          ${renderRecordChip(`记录 ${record.recordId || '-'}`)}
          ${record.isSocratic ? renderRecordChip('启发式模式', 'tag-chip--brand') : ''}
          ${record.aiFeedback ? renderRecordChip(`反馈 ${feedbackText}`, 'tag-chip--accent') : ''}
          ${record.errorType ? renderRecordChip(`类型 ${errorTypeText}`, 'tag-chip--danger') : ''}
        </div>

        <div class="record-kv-grid">
          <div class="record-kv">
            <p class="record-kv__label">错误定位</p>
            <p class="record-kv__value">${record.errorIndex == null ? '未明确定位' : `第 ${record.errorIndex} 步`}</p>
          </div>
          <div class="record-kv">
            <p class="record-kv__label">教师反馈</p>
            <p class="record-kv__value">${escapeHtml(feedbackText)}</p>
          </div>
        </div>

        <div class="record-card__body">
          <div class="record-kv">
            <p class="record-kv__label">诊断摘要</p>
            <p class="record-kv__value">${escapeHtml(feedbackSummary)}</p>
          </div>
          ${tags.length ? `<div class="record-card__chips">${tags.map((tag, index) => renderRecordChip(tag, index % 2 === 0 ? 'tag-chip--brand' : 'tag-chip--accent')).join('')}</div>` : ''}
        </div>
      </article>
    `;
  }

  openStats(summaryResponse) {
    const summary = summaryResponse?.summary || {};
    const total = summary.totalEvaluations || 0;
    const errorTypeCount = summary.errorTypeCount || {};
    const aiFeedbackCount = summary.aiFeedbackCount || {};
    const recentRecords = summary.recentRecords || [];

    this.drawerEyebrow.textContent = 'Insight Panel';
    this.drawerTitle.textContent = '错题统计';
    this.drawerSubtitle.textContent = '把当前老师端的诊断数据整理成可读的概览、分布和近期轨迹。';

    this.drawerBody.innerHTML = `
      <div class="drawer-stack">
        <div class="summary-grid">
          <article class="summary-card">
            <p class="stat-card__label">总诊断次数</p>
            <p class="summary-card__value">${total}</p>
            <p class="summary-card__desc">当前可见记录范围内的累计诊断量</p>
          </article>
          <article class="summary-card">
            <p class="stat-card__label">错误类型数</p>
            <p class="summary-card__value">${Object.keys(errorTypeCount).length}</p>
            <p class="summary-card__desc">已出现过的错误分类数量</p>
          </article>
          <article class="summary-card">
            <p class="stat-card__label">反馈类型数</p>
            <p class="summary-card__value">${Object.keys(aiFeedbackCount).length}</p>
            <p class="summary-card__desc">老师提交过的反馈类型覆盖范围</p>
          </article>
        </div>

        <div class="stat-list">
          <article class="distribution-card">
            <p class="distribution-card__title">错误类型分布</p>
            <p class="distribution-card__hint">帮助快速识别近期最常出现的问题类别。</p>
            ${this.renderDistribution(errorTypeCount, total, '当前还没有错误类型统计。')}
          </article>

          <article class="distribution-card">
            <p class="distribution-card__title">AI 反馈分布</p>
            <p class="distribution-card__hint">查看识别准确与不准确等反馈的当前分布情况。</p>
            ${this.renderDistribution(aiFeedbackCount, total, '当前还没有 AI 反馈统计。')}
          </article>

          <article class="distribution-card">
            <p class="distribution-card__title">近期诊断记录</p>
            <p class="distribution-card__hint">保留最近 5 条记录，方便老师快速回看最近轨迹。</p>
            ${this.renderRecentRecords(recentRecords)}
          </article>
        </div>
      </div>
    `;

    this.show();
  }

  renderDistribution(entriesMap, total, emptyText) {
    const entries = Object.entries(entriesMap || {});
    if (!entries.length) {
      return renderEmptyState('暂无统计数据', emptyText);
    }

    const safeTotal = Math.max(total, 1);

    return `
      <div class="distribution-list">
        ${entries.map(([key, count]) => {
          const percentage = Math.max(8, Math.round((count / safeTotal) * 100));
          return `
            <div class="distribution-item">
              <div class="distribution-item__header">
                <span>${escapeHtml(key)}</span>
                <strong>${count}</strong>
              </div>
              <div class="distribution-item__track">
                <div class="distribution-item__fill" style="width: ${percentage}%;"></div>
              </div>
            </div>
          `;
        }).join('')}
      </div>
    `;
  }

  renderRecentRecords(records) {
    if (!records.length) {
      return renderEmptyState('暂无近期记录', '完成新的诊断后，这里会展示最近的活动轨迹。');
    }

    return `
      <div class="recent-list">
        ${records.map((record) => {
          const status = statusMeta(record.status);
          return `
            <article class="recent-card">
              <p class="recent-card__title">${escapeHtml(record.fileName || '未命名题目')}</p>
              <div class="recent-card__meta">
                <span class="${status.className}">${escapeHtml(status.label)}</span>
                <span class="tag-chip">${escapeHtml(formatTime(record.createdAt))}</span>
                ${record.errorType ? renderRecordChip(record.errorType, 'tag-chip--danger') : ''}
              </div>
              <p class="recent-card__desc">${escapeHtml(record.feedback || record.note || '暂无额外说明，保留基础诊断状态。')}</p>
            </article>
          `;
        }).join('')}
      </div>
    `;
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
