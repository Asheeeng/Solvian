import { renderLatexBatch } from '../common/latex-renderer.js?v=20260407g';

function escapeHtml(raw) {
  return String(raw ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function latexDisplay(latex) {
  if (!latex) {
    return '';
  }
  return `\\[${escapeHtml(latex)}\\]`;
}

function renderMatrixDiffList(matrixCellDiffs) {
  if (!Array.isArray(matrixCellDiffs) || !matrixCellDiffs.length) {
    return '';
  }

  return `
    <div class="timeline-step__diff-list">
      ${matrixCellDiffs.map((item) => `
        <article class="timeline-step__diff-card">
          <p class="timeline-step__diff-title">矩阵差异定位 · 第 ${escapeHtml(item.row)} 行第 ${escapeHtml(item.col)} 列</p>
          <div class="timeline-step__diff-meta">
            <span class="tag-chip tag-chip--danger">学生写法：${escapeHtml(item.actual || '-')}</span>
            <span class="tag-chip tag-chip--success">应为：${escapeHtml(item.expected || '-')}</span>
          </div>
          <p class="timeline-step__diff-line"><strong>说明：</strong>${escapeHtml(item.reason || '该元素存在差异，请重点复核。')}</p>
        </article>
      `).join('')}
    </div>
  `;
}

function renderNarrative(content, explanation, isWrong) {
  const normalizedContent = String(content || '').trim();
  const normalizedExplanation = String(explanation || '').trim();

  if (normalizedContent) {
    return `<p>${escapeHtml(normalizedContent)}</p>`;
  }
  if (normalizedExplanation) {
    return `<p class="${isWrong ? 'timeline-step__narrative timeline-step__narrative--wrong' : ''}">${escapeHtml(normalizedExplanation)}</p>`;
  }
  return '<p><span class="muted">该步骤暂无可展示的文字说明。</span></p>';
}

export class DiagnosisPanel {
  constructor({
    statusBadge,
    statusText,
    stepsContainer,
    feedbackText,
    errorIndexText,
    errorReasonText,
    tagsContainer,
    subjectScopeText,
    recordIdText
  }) {
    this.statusBadge = statusBadge;
    this.statusText = statusText;
    this.stepsContainer = stepsContainer;
    this.feedbackText = feedbackText;
    this.errorIndexText = errorIndexText;
    this.errorReasonText = errorReasonText;
    this.tagsContainer = tagsContainer;
    this.subjectScopeText = subjectScopeText;
    this.recordIdText = recordIdText;
    this.latexRenderFrame = 0;
    this.latexRenderToken = 0;
  }

  updateStatusBadge(text, type) {
    if (!this.statusBadge) {
      return;
    }
    this.statusBadge.textContent = text;
    this.statusBadge.className = `status-badge status-badge--${type}`;
  }

  setRunning() {
    this.updateStatusBadge('AI 正在检测', 'running');
    this.statusText.textContent = '系统正在提取题目特征、生成步骤重构并汇总错误反馈，请稍候。';
    this.statusText.className = 'result-summary__note';
    this.stepsContainer.innerHTML = `
      <li class="timeline-step">
        <div class="timeline-step__header">
          <span class="timeline-step__stepno">1</span>
          <div class="timeline-step__title">
            <span class="timeline-step__name">正在提取题目特征</span>
          </div>
          <span class="status-badge status-badge--running">处理中</span>
        </div>
      </li>
      <li class="timeline-step">
        <div class="timeline-step__header">
          <span class="timeline-step__stepno">2</span>
          <div class="timeline-step__title">
            <span class="timeline-step__name">正在生成详细步骤与错误定位</span>
          </div>
          <span class="status-badge status-badge--pending">排队中</span>
        </div>
      </li>
    `;
    this.feedbackText.textContent = '请稍候，系统正在生成诊断结论。';
    this.recordIdText.textContent = '-';
    this.errorIndexText.textContent = '-';
    this.errorReasonText.textContent = '等待模型返回错误说明。';
    this.tagsContainer.innerHTML = '<span class="tag-chip tag-chip--accent">#检测中</span>';
  }

  setError(message) {
    this.updateStatusBadge('诊断失败', 'danger');
    this.statusText.textContent = message;
    this.statusText.className = 'result-summary__note';
  }

  renderStreamingResult(partialResult = {}) {
    const steps = Array.isArray(partialResult?.steps) ? partialResult.steps : [];
    if (!steps.length) {
      return;
    }

    this.updateStatusBadge('AI 正在检测', 'running');
    if (partialResult.stageMessage) {
      this.statusText.textContent = partialResult.stageMessage;
      this.statusText.className = 'result-summary__note';
    }

    this.renderSteps(steps, '步骤处理中');

    if (partialResult.feedback) {
      this.feedbackText.textContent = partialResult.feedback;
    }
    if (partialResult.subjectScope) {
      this.subjectScopeText.textContent = partialResult.subjectScope;
    }
    if (partialResult.errorIndex != null) {
      this.errorIndexText.textContent = `第 ${partialResult.errorIndex} 步`;
    }
  }

  renderResult(result) {
    const status = result?.status || 'unable_to_judge';
    const errorIndex = result?.errorIndex ?? result?.error_index ?? null;
    const diffSummary = result?.diffInfo?.summary || '';

    if (status === 'correct') {
      this.updateStatusBadge('步骤正确', 'success');
      this.statusText.textContent = 'AI 已确认当前步骤链路整体正确，未发现明确错误。';
      this.statusText.className = 'result-summary__note';
    } else if (status === 'error_found') {
      this.updateStatusBadge('发现错误', 'danger');
      this.statusText.textContent = `系统已发现错误，当前定位到第 ${errorIndex ?? '-'} 步，请优先查看下方步骤列表与错误说明。`;
      this.statusText.className = 'result-summary__note';
    } else {
      this.updateStatusBadge('暂时无法判断', 'pending');
      this.statusText.textContent = '系统暂时无法形成稳定结论，建议结合步骤内容再次检查。';
      this.statusText.className = 'result-summary__note';
    }

    const steps = Array.isArray(result?.steps) && result.steps.length
      ? result.steps
      : [{
        stepNo: 1,
        title: '未返回步骤',
        content: '模型未返回可解析步骤。',
        latex: '',
        highlightedLatex: '',
        isWrong: false,
        explanation: '',
        matrixCellDiffs: []
      }];

    this.renderSteps(steps);

    this.feedbackText.textContent = result?.feedback || '未返回反馈信息';
    this.recordIdText.textContent = result?.recordId || '-';
    this.subjectScopeText.textContent = result?.subjectScope || 'matrix';
    this.errorIndexText.textContent = errorIndex == null ? '-' : `第 ${errorIndex} 步`;
    this.errorReasonText.textContent = diffSummary || this.pickErrorReason(steps, status);

    const tags = Array.isArray(result?.tags) && result.tags.length ? result.tags : ['#未分类'];
    this.tagsContainer.innerHTML = tags
      .map((tag, index) => `<span class="tag-chip ${index % 2 === 0 ? 'tag-chip--brand' : 'tag-chip--accent'}">${escapeHtml(tag)}</span>`)
      .join('');
  }

  renderSteps(steps, fallbackLabel = '步骤通过') {
    this.stepsContainer.innerHTML = steps.map((step, idx) => {
      const stepNo = step?.stepNo ?? (idx + 1);
      const title = escapeHtml(step?.title || `步骤 ${stepNo}`);
      const content = String(step?.content || '').trim();
      const explanation = String(step?.explanation || '').trim();
      const errorMessage = String(step?.errorMessage || step?.explanation || '').trim();
      const renderedLatex = String(step?.highlightedLatex || step?.latex || '').trim();
      const hasRenderableLatex = Boolean(renderedLatex);
      const isWrong = Boolean(step?.isWrong);
      const matrixCellDiffs = Array.isArray(step?.matrixCellDiffs) ? step.matrixCellDiffs : [];
      const primaryText = content || explanation;
      const secondaryExplanation = isWrong
        ? (errorMessage && errorMessage !== primaryText ? `错误说明：${escapeHtml(errorMessage)}` : '')
        : (explanation && explanation !== primaryText ? escapeHtml(explanation) : '');

      return `
        <li class="timeline-step ${isWrong ? 'timeline-step--wrong' : ''}">
          <div class="timeline-step__header">
            <span class="timeline-step__stepno">${stepNo}</span>
            <div class="timeline-step__title">
              <span class="timeline-step__name">${title}</span>
              <span class="muted">${isWrong ? '该步骤存在错误，请优先查看本步说明。' : '步骤内容已完成结构化整理。'}</span>
            </div>
            <span class="status-badge ${isWrong ? 'status-badge--danger' : 'status-badge--success'}">${isWrong ? '错误步骤' : fallbackLabel}</span>
          </div>
          <div class="timeline-step__body">
            ${isWrong ? `
              <div class="timeline-step__alert">
                <span class="timeline-step__alert-icon">!</span>
                <span>请优先查看该步骤的文字说明与公式内容。</span>
              </div>
            ` : ''}
            ${renderNarrative(content, explanation, isWrong)}
            ${hasRenderableLatex ? `
              <div class="timeline-step__latex ${isWrong ? 'timeline-step__latex--error' : ''}">
                <div class="timeline-step__latex-head">
                  <span class="timeline-step__latex-label">${isWrong ? '步骤公式' : '公式展示'}</span>
                </div>
                <div class="timeline-step__latex-body" data-has-latex="true">${latexDisplay(renderedLatex)}</div>
              </div>
            ` : ''}
            ${renderMatrixDiffList(matrixCellDiffs)}
            ${secondaryExplanation ? `<p class="timeline-step__explanation">${secondaryExplanation}</p>` : ''}
          </div>
        </li>
      `;
    }).join('');

    this.scheduleLatexRender(Array.from(this.stepsContainer.querySelectorAll('.timeline-step')));
  }

  pickErrorReason(steps, status) {
    if (status !== 'error_found') {
      return '未检测到明确错误';
    }
    const wrong = steps.find((step) => step?.isWrong);
    if (wrong?.explanation) {
      return wrong.explanation;
    }
    return '模型未返回明确错误原因，请结合步骤内容复核。';
  }

  scheduleLatexRender(stepElements = []) {
    this.latexRenderToken += 1;
    const renderToken = this.latexRenderToken;
    if (this.latexRenderFrame) {
      window.cancelAnimationFrame(this.latexRenderFrame);
    }

    this.latexRenderFrame = window.requestAnimationFrame(() => {
      if (renderToken !== this.latexRenderToken) {
        return;
      }
      renderLatexBatch(stepElements).catch(() => {
        // 公式渲染失败时保留清洗后的普通文本，不阻塞整体结果展示。
      });
    });
  }
}
