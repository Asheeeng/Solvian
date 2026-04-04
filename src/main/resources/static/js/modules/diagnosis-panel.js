export class DiagnosisPanel {
  constructor({ statusText, stepsContainer, feedbackText, recordIdText }) {
    this.statusText = statusText;
    this.stepsContainer = stepsContainer;
    this.feedbackText = feedbackText;
    this.recordIdText = recordIdText;
  }

  setRunning() {
    this.statusText.textContent = 'AI 正在诊断中...';
    this.statusText.className = 'status-running';
    this.stepsContainer.innerHTML = '<li>正在提取题目特征...</li><li>正在重构步骤...</li>';
    this.feedbackText.textContent = '请稍候，系统正在生成诊断结论。';
  }

  setError(message) {
    this.statusText.textContent = message;
    this.statusText.className = 'status-error';
  }

  renderResult(result) {
    const status = result?.status || 'error_found';
    if (status === 'correct') {
      this.statusText.textContent = '诊断结论：步骤正确';
      this.statusText.className = 'status-finished';
    } else {
      const errorIndex = result?.errorIndex ?? result?.error_index ?? '-';
      this.statusText.textContent = `诊断结论：发现错误（定位步骤 ${errorIndex}）`;
      this.statusText.className = 'status-finished';
    }

    const steps = Array.isArray(result?.steps) && result.steps.length
      ? result.steps
      : ['未返回步骤信息'];
    this.stepsContainer.innerHTML = steps.map((step) => `<li>${step}</li>`).join('');

    this.feedbackText.textContent = result?.feedback || '未返回反馈信息';
    this.recordIdText.textContent = `记录ID：${result?.recordId || '-'}`;
  }
}
