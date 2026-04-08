function escapeHtml(raw) {
  return String(raw ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

const STAGE_INDEX = {
  queued: 0,
  vision: 1,
  reasoning: 2,
  done: 3,
  failed: 3
};

function stageBadge(status) {
  if (status === 'done') {
    return { text: '已完成', klass: 'status-badge status-badge--success' };
  }
  if (status === 'failed') {
    return { text: '失败', klass: 'status-badge status-badge--danger' };
  }
  if (status === 'vision' || status === 'reasoning') {
    return { text: '处理中', klass: 'status-badge status-badge--running' };
  }
  return { text: '排队中', klass: 'status-badge status-badge--pending' };
}

export class DiagnosisProgressPanel {
  constructor({ container }) {
    this.container = container;
  }

  reset() {
    if (!this.container) {
      return;
    }

    this.container.innerHTML = `
      <div class="diagnosis-progress__empty">
        <p class="diagnosis-progress__empty-title">异步诊断流水线</p>
        <p class="diagnosis-progress__empty-copy">启动 AI 诊断后，这里会先展示视觉识别进度，再逐步显示推理分析状态，减少老师等待完整结果的空窗时间。</p>
      </div>
    `;
  }

  setFailed(message) {
    this.renderTask({
      status: 'failed',
      progress: 100,
      stageMessage: message || '诊断失败',
      errorMessage: message || '诊断失败',
      partialResult: {}
    });
  }

  renderTask(task) {
    if (!this.container) {
      return;
    }

    const status = task?.status || 'queued';
    const progress = Number.isFinite(Number(task?.progress)) ? Number(task.progress) : 0;
    const stageMessage = task?.stageMessage || '系统正在处理中';
    const partialResult = task?.partialResult || {};
    const badge = stageBadge(status);
    const currentIndex = STAGE_INDEX[status] ?? 0;
    const expressions = Array.isArray(partialResult.matrixExpressions) ? partialResult.matrixExpressions : [];
    const studentSteps = Array.isArray(partialResult.studentSteps) ? partialResult.studentSteps : [];
    const highlightCount = Array.isArray(partialResult.imageHighlights) ? partialResult.imageHighlights.length : 0;

    const steps = [
      {
        title: '任务已接收',
        desc: '图片已经上传，系统已生成 taskId 并进入处理队列。'
      },
      {
        title: '识别题目结构',
        desc: expressions.length
          ? `已识别 ${expressions.length} 个公式片段，准备进入推理阶段。`
          : '正在解析题目、公式与原图区域。'
      },
      {
        title: '推理诊断中',
        desc: studentSteps.length
          ? `已提取 ${studentSteps.length} 条步骤草稿，正在定位错误点。`
          : '正在组织步骤重构、错误定位与教学反馈。'
      },
      {
        title: status === 'done' ? '完整结果已生成' : status === 'failed' ? '诊断异常终止' : '等待生成最终结论',
        desc: status === 'done'
          ? '完整诊断结果已经返回，下方步骤区会自动更新。'
          : status === 'failed'
          ? (task?.errorMessage || '本次诊断失败，请重试。')
          : '系统会在推理完成后自动刷新完整结果。'
      }
    ];

    this.container.innerHTML = `
      <div class="diagnosis-progress__header">
        <div>
          <p class="diagnosis-progress__eyebrow">Pipeline Progress</p>
          <h3 class="diagnosis-progress__title">异步诊断进度</h3>
        </div>
        <span class="${badge.klass}">${badge.text}</span>
      </div>

      <div class="diagnosis-progress__bar">
        <span style="width:${Math.max(0, Math.min(progress, 100))}%"></span>
      </div>

      <p class="diagnosis-progress__message">${escapeHtml(stageMessage)}</p>

      <ol class="diagnosis-progress__timeline">
        ${steps.map((step, index) => {
          const isDone = status !== 'failed' && index < currentIndex;
          const isActive = index === currentIndex && status !== 'done' && status !== 'failed';
          const isFailed = status === 'failed' && index === steps.length - 1;
          const klass = isFailed
            ? 'diagnosis-progress__step diagnosis-progress__step--failed'
            : isActive
              ? 'diagnosis-progress__step diagnosis-progress__step--active'
              : isDone || status === 'done' && index <= currentIndex
                ? 'diagnosis-progress__step diagnosis-progress__step--done'
                : 'diagnosis-progress__step';

          return `
            <li class="${klass}">
              <span class="diagnosis-progress__dot">${isFailed ? '!' : isDone || status === 'done' && index <= currentIndex ? '✓' : index + 1}</span>
              <div class="diagnosis-progress__copy">
                <p class="diagnosis-progress__step-title">${escapeHtml(step.title)}</p>
                <p class="diagnosis-progress__step-desc">${escapeHtml(step.desc)}</p>
              </div>
            </li>
          `;
        }).join('')}
      </ol>

      ${partialResult.problemText || expressions.length || highlightCount ? `
        <div class="diagnosis-progress__insights">
          <div class="diagnosis-progress__chips">
            ${partialResult.cacheHit ? '<span class="tag-chip tag-chip--brand">复用视觉缓存</span>' : ''}
            ${expressions.length ? `<span class="tag-chip tag-chip--accent">公式片段 ${expressions.length}</span>` : ''}
            ${studentSteps.length ? `<span class="tag-chip">步骤草稿 ${studentSteps.length}</span>` : ''}
            ${highlightCount ? `<span class="tag-chip tag-chip--danger">识别线索 ${highlightCount}</span>` : ''}
          </div>
          ${partialResult.problemText ? `
            <div class="diagnosis-progress__preview">
              <p class="diagnosis-progress__preview-label">已识别题干摘要</p>
              <p class="diagnosis-progress__preview-copy">${escapeHtml(partialResult.problemText)}</p>
            </div>
          ` : ''}
        </div>
      ` : ''}
    `;
  }
}
