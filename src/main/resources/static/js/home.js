import { requireAuth, roleToLabel } from './common/auth-guard.js?v=20260407d';
import {
  downloadPdfReport,
  evaluateProblem,
  fetchDashboardSummary,
  fetchHistory,
  logEvent,
  logoutUser,
  submitAiFeedback
} from './common/storage.js?v=20260407d';
import { ProblemViewer } from './modules/problem-viewer.js?v=20260407d';
import { DiagnosisPanel } from './modules/diagnosis-panel.js?v=20260407d';
import { NotebookDrawer } from './modules/notebook-drawer.js?v=20260407d';
import { initWorkspaceTheme } from './modules/theme-controller.js?v=20260407d';

function setButtonBusy(button, busy, busyLabel) {
  if (!button) {
    return;
  }

  const label = button.querySelector('.action-button__label');
  if (label && !button.dataset.idleLabel) {
    button.dataset.idleLabel = label.textContent;
  }

  if (busy) {
    button.classList.add('is-loading');
    button.setAttribute('aria-busy', 'true');
    if (label && busyLabel) {
      label.textContent = busyLabel;
    }
    button.disabled = true;
    return;
  }

  button.classList.remove('is-loading');
  button.setAttribute('aria-busy', 'false');
  if (label && button.dataset.idleLabel) {
    label.textContent = button.dataset.idleLabel;
  }
  button.disabled = button.dataset.locked === 'true';
}

function setButtonLocked(button, locked, title = '') {
  if (!button) {
    return;
  }
  button.dataset.locked = locked ? 'true' : 'false';
  button.disabled = locked;
  button.title = title;
}

function setFeedbackMessage(element, message, type = '') {
  if (!element) {
    return;
  }
  element.textContent = message || '';
  element.classList.remove('is-error', 'is-success');
  if (type === 'error') {
    element.classList.add('is-error');
  }
  if (type === 'success') {
    element.classList.add('is-success');
  }
}

const currentSession = requireAuth();
if (currentSession) {
  const currentUser = currentSession.user;

  document.getElementById('currentRoleLabel').textContent = roleToLabel(currentUser.role);
  document.getElementById('currentUserLabel').textContent = currentUser.username;

  initWorkspaceTheme({
    root: document.body,
    toggle: document.getElementById('themeToggle')
  });

  const problemViewer = new ProblemViewer({
    fileInput: document.getElementById('problemFileInput'),
    previewImage: document.getElementById('problemPreviewImage'),
    placeholder: document.getElementById('problemImagePlaceholder'),
    modal: document.getElementById('imageModal'),
    modalImage: document.getElementById('modalImage'),
    closeModalBtn: document.getElementById('closeImageModalBtn'),
    fileMetaText: document.getElementById('problemFileMeta')
  });
  problemViewer.init();

  const diagnosisPanel = new DiagnosisPanel({
    statusBadge: document.getElementById('diagnosisStatusBadge'),
    statusText: document.getElementById('diagnosisStatusText'),
    stepsContainer: document.getElementById('diagnosisSteps'),
    feedbackText: document.getElementById('diagnosisFeedback'),
    errorIndexText: document.getElementById('diagnosisErrorIndexText'),
    errorReasonText: document.getElementById('diagnosisErrorReasonText'),
    tagsContainer: document.getElementById('diagnosisTags'),
    subjectScopeText: document.getElementById('subjectScopeText'),
    recordIdText: document.getElementById('currentRecordIdText')
  });

  const drawer = new NotebookDrawer({
    drawer: document.getElementById('drawer'),
    drawerMask: document.getElementById('drawerMask'),
    drawerTitle: document.getElementById('drawerTitle'),
    drawerSubtitle: document.getElementById('drawerSubtitle'),
    drawerEyebrow: document.getElementById('drawerEyebrow'),
    drawerBody: document.getElementById('drawerBody'),
    closeBtn: document.getElementById('closeDrawerBtn')
  });

  const socraticModeToggle = document.getElementById('socraticModeToggle');
  const triggerDiagnosisBtn = document.getElementById('triggerDiagnosisBtn');
  const openNotebookBtn = document.getElementById('openNotebookBtn');
  const openStatsBtn = document.getElementById('openStatsBtn');
  const logoutBtn = document.getElementById('logoutBtn');
  const aiFeedbackForm = document.getElementById('aiFeedbackForm');
  const saveFeedbackBtn = document.getElementById('saveFeedbackBtn');
  const aiFeedbackSelect = document.getElementById('aiFeedbackSelect');
  const errorTypeWrap = document.getElementById('errorTypeWrap');
  const errorTypeSelect = document.getElementById('errorTypeSelect');
  const feedbackNote = document.getElementById('feedbackNote');
  const aiFeedbackMessage = document.getElementById('aiFeedbackMessage');
  const downloadPdfBtn = document.getElementById('downloadPdfBtn');
  const teacherOnlyHint = document.getElementById('teacherOnlyHint');

  if (currentUser.role !== 'TEACHER') {
    teacherOnlyHint.classList.remove('hidden');
    setButtonLocked(triggerDiagnosisBtn, true, '当前阶段仅老师角色可用');
    diagnosisPanel.setError('当前阶段仅支持老师端发起 AI 检测。');
  }

  let currentRecordId = '';

  document.getElementById('problemFileInput').addEventListener('change', () => {
    const meta = problemViewer.getImageMeta();
    logEvent({
      eventType: 'UPLOAD',
      page: 'HOME',
      action: 'UPLOAD_IMAGE',
      payload: { imageName: meta.imageName || '' },
      ts: Date.now()
    });
  });

  document.getElementById('problemPreviewImage').addEventListener('click', () => {
    const meta = problemViewer.getImageMeta();
    logEvent({
      eventType: 'PREVIEW',
      page: 'HOME',
      action: 'OPEN_IMAGE_MODAL',
      payload: { imageName: meta.imageName || '' },
      ts: Date.now()
    });
  });

  triggerDiagnosisBtn.addEventListener('click', async () => {
    if (currentUser.role !== 'TEACHER') {
      teacherOnlyHint.classList.remove('hidden');
      diagnosisPanel.setError('当前阶段仅支持老师端发起 AI 检测。');
      return;
    }

    if (!problemViewer.hasImage()) {
      diagnosisPanel.setError('请先上传题目图片，再启动 AI 诊断。');
      document.getElementById('problemFileMeta').textContent = '请先选择题目图片，然后再发起本次诊断。';
      return;
    }

    const selectedFile = problemViewer.getSelectedFile();
    const isSocratic = socraticModeToggle.checked;

    diagnosisPanel.setRunning();
    setFeedbackMessage(aiFeedbackMessage, '');
    setButtonBusy(triggerDiagnosisBtn, true, '正在诊断');

    await logEvent({
      eventType: 'DIAGNOSIS',
      page: 'HOME',
      action: 'START_DIAGNOSIS',
      payload: { isSocratic },
      ts: Date.now()
    });

    try {
      const result = await evaluateProblem({
        file: selectedFile,
        isSocratic,
        problemType: 'matrix'
      });

      diagnosisPanel.renderResult(result);
      currentRecordId = result.recordId || '';

      aiFeedbackForm.reset();
      errorTypeWrap.classList.add('hidden');
      setFeedbackMessage(aiFeedbackMessage, '');

      await logEvent({
        eventType: 'DIAGNOSIS',
        page: 'HOME',
        action: 'DIAGNOSIS_FINISHED',
        payload: {
          recordId: currentRecordId,
          status: result.status,
          errorIndex: result.errorIndex ?? result.error_index ?? null
        },
        recordId: currentRecordId,
        ts: Date.now()
      });
    } catch (error) {
      diagnosisPanel.setError(error.message || '诊断失败，请稍后重试。');
    } finally {
      setButtonBusy(triggerDiagnosisBtn, false);
    }
  });

  aiFeedbackSelect.addEventListener('change', () => {
    if (aiFeedbackSelect.value === 'INACCURATE') {
      errorTypeWrap.classList.remove('hidden');
    } else {
      errorTypeWrap.classList.add('hidden');
      errorTypeSelect.value = '';
    }
  });

  aiFeedbackForm.addEventListener('submit', async (event) => {
    event.preventDefault();

    if (!currentRecordId) {
      setFeedbackMessage(aiFeedbackMessage, '请先完成一次诊断，再提交反馈。', 'error');
      return;
    }

    const aiFeedback = aiFeedbackSelect.value;
    const errorType = errorTypeSelect.value;
    const note = feedbackNote.value.trim();

    if (aiFeedback === 'INACCURATE' && !errorType) {
      setFeedbackMessage(aiFeedbackMessage, '识别不准确时请先选择错误类型。', 'error');
      return;
    }

    try {
      setButtonBusy(saveFeedbackBtn, true, '正在保存');
      const response = await submitAiFeedback({
        recordId: currentRecordId,
        aiFeedback,
        errorType,
        note
      });

      setFeedbackMessage(aiFeedbackMessage, response.message || '反馈已保存', 'success');
      await logEvent({
        eventType: 'FEEDBACK',
        page: 'HOME',
        action: 'SUBMIT_AI_FEEDBACK',
        payload: { aiFeedback, errorType },
        recordId: currentRecordId,
        ts: Date.now()
      });
    } catch (error) {
      setFeedbackMessage(aiFeedbackMessage, error.message || '反馈保存失败', 'error');
    } finally {
      setButtonBusy(saveFeedbackBtn, false);
    }
  });

  downloadPdfBtn.addEventListener('click', async () => {
    if (!currentRecordId) {
      setFeedbackMessage(aiFeedbackMessage, '暂无可下载报告，请先完成诊断。', 'error');
      return;
    }

    try {
      setButtonBusy(downloadPdfBtn, true, '正在下载');
      await downloadPdfReport(currentRecordId);
      setFeedbackMessage(aiFeedbackMessage, 'PDF 报告已开始下载。', 'success');
    } catch (error) {
      setFeedbackMessage(aiFeedbackMessage, error.message || '下载失败', 'error');
    } finally {
      setButtonBusy(downloadPdfBtn, false);
    }
  });

  openNotebookBtn.addEventListener('click', async () => {
    try {
      setButtonBusy(openNotebookBtn, true, '正在加载');
      const historyResponse = await fetchHistory();
      drawer.openNotebook(historyResponse);
      await logEvent({
        eventType: 'VIEW',
        page: 'HOME',
        action: 'OPEN_NOTEBOOK',
        payload: {},
        ts: Date.now()
      });
    } catch (error) {
      diagnosisPanel.setError(error.message || '加载错题本失败');
    } finally {
      setButtonBusy(openNotebookBtn, false);
    }
  });

  openStatsBtn.addEventListener('click', async () => {
    try {
      setButtonBusy(openStatsBtn, true, '正在加载');
      const summaryResponse = await fetchDashboardSummary();
      drawer.openStats(summaryResponse);
      await logEvent({
        eventType: 'VIEW',
        page: 'HOME',
        action: 'OPEN_DASHBOARD',
        payload: {},
        ts: Date.now()
      });
    } catch (error) {
      diagnosisPanel.setError(error.message || '加载统计失败');
    } finally {
      setButtonBusy(openStatsBtn, false);
    }
  });

  logoutBtn.addEventListener('click', async () => {
    try {
      setButtonBusy(logoutBtn, true, '正在退出');
      await logoutUser();
      window.location.href = '/login.html';
    } finally {
      setButtonBusy(logoutBtn, false);
    }
  });
}
