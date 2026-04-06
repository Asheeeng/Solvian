function escapeHtml(raw) {
  return String(raw ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function latexInline(latex) {
  if (!latex) {
    return '<span class="muted">LaTeX：未提供</span>';
  }
  return `\\(${escapeHtml(latex)}\\)`;
}

export class DiagnosisPanel {
  constructor({
    statusText,
    stepsContainer,
    feedbackText,
    errorIndexText,
    errorReasonText,
    tagsContainer,
    subjectScopeText,
    recordIdText
  }) {
    this.statusText = statusText;
    this.stepsContainer = stepsContainer;
    this.feedbackText = feedbackText;
    this.errorIndexText = errorIndexText;
    this.errorReasonText = errorReasonText;
    this.tagsContainer = tagsContainer;
    this.subjectScopeText = subjectScopeText;
    this.recordIdText = recordIdText;
  }

  setRunning() {
    this.statusText.textContent = 'AI 正在检测中...';
    this.statusText.className = 'status-running';
    this.stepsContainer.innerHTML = '<li>正在提取题目特征...</li><li>正在生成详细步骤...</li>';
    this.feedbackText.textContent = '请稍候，系统正在生成诊断结论。';
    this.errorIndexText.textContent = '错误定位：-';
    this.errorReasonText.textContent = '错误说明：-';
    this.tagsContainer.innerHTML = '<span class="tag-chip">#检测中</span>';
  }

  setError(message) {
    this.statusText.textContent = message;
    this.statusText.className = 'status-error';
  }

  renderResult(result) {
    const status = result?.status || 'unable_to_judge';
    const errorIndex = result?.errorIndex ?? result?.error_index ?? null;

    if (status === 'correct') {
      this.statusText.textContent = '诊断结论：步骤正确';
      this.statusText.className = 'status-finished';
    } else if (status === 'error_found') {
      this.statusText.textContent = `诊断结论：发现错误（定位步骤 ${errorIndex ?? '-'}）`;
      this.statusText.className = 'status-error';
    } else {
      this.statusText.textContent = '诊断结论：暂时无法判断';
      this.statusText.className = 'status-pending';
    }

    const steps = Array.isArray(result?.steps) && result.steps.length
      ? result.steps
      : [{
        stepNo: 1,
        title: '未返回步骤',
        content: '模型未返回可解析步骤。',
        latex: '',
        isWrong: false,
        explanation: ''
      }];

    this.stepsContainer.innerHTML = steps.map((step, idx) => {
      const stepNo = step?.stepNo ?? (idx + 1);
      const title = escapeHtml(step?.title || `步骤 ${stepNo}`);
      const content = escapeHtml(step?.content || '');
      const latex = step?.latex || '';
      const isWrong = Boolean(step?.isWrong);
      const explanation = escapeHtml(step?.explanation || '');

      return `
        <li class="step-card ${isWrong ? 'step-wrong' : ''}">
          <p><strong>Step ${stepNo}：</strong>${title}</p>
          <p>${content || '<span class="muted">无自然语言说明</span>'}</p>
          <p class="latex-line">${latexInline(latex)}</p>
          <p class="muted">${isWrong ? `错误说明：${explanation || '该步骤存在错误，请复核。'}` : (explanation || ' ')}</p>
        </li>
      `;
    }).join('');

    this.feedbackText.textContent = result?.feedback || '未返回反馈信息';
    this.recordIdText.textContent = `记录ID：${result?.recordId || '-'}`;
    this.subjectScopeText.textContent = `题型范围：${result?.subjectScope || 'matrix'}`;
    this.errorIndexText.textContent = `错误定位：${errorIndex == null ? '-' : `第 ${errorIndex} 步`}`;
    this.errorReasonText.textContent = `错误说明：${this.pickErrorReason(steps, status)}`;

    const tags = Array.isArray(result?.tags) && result.tags.length ? result.tags : ['#未分类'];
    this.tagsContainer.innerHTML = tags
      .map((tag) => `<span class="tag-chip">${escapeHtml(tag)}</span>`)
      .join('');

    this.typesetLatex();
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

  typesetLatex() {
    if (window.MathJax?.typesetPromise) {
      window.MathJax.typesetPromise([this.stepsContainer]).catch(() => {
        // 渲染失败时保留原始 LaTeX 文本，不中断页面。
      });
    }
  }
}

