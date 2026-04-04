import { requireAuth, roleToLabel } from './common/auth-guard.js';
import {
  downloadPdfReport,
  evaluateProblem,
  fetchDashboardSummary,
  fetchHistory,
  logEvent,
  logoutUser,
  submitAiFeedback
} from './common/storage.js';
import { ProblemViewer } from './modules/problem-viewer.js';
import { DiagnosisPanel } from './modules/diagnosis-panel.js';
import { NotebookDrawer } from './modules/notebook-drawer.js';

const currentSession = requireAuth();
if (currentSession) {
  const currentUser = currentSession.user;

  document.getElementById('currentRoleLabel').textContent = roleToLabel(currentUser.role);
  document.getElementById('currentUserLabel').textContent = currentUser.username;

  const problemViewer = new ProblemViewer({
    fileInput: document.getElementById('problemFileInput'),
    previewImage: document.getElementById('problemPreviewImage'),
    placeholder: document.getElementById('problemImagePlaceholder'),
    modal: document.getElementById('imageModal'),
    modalImage: document.getElementById('modalImage'),
    closeModalBtn: document.getElementById('closeImageModalBtn')
  });
  problemViewer.init();

  const diagnosisPanel = new DiagnosisPanel({
    statusText: document.getElementById('diagnosisStatusText'),
    stepsContainer: document.getElementById('diagnosisSteps'),
    feedbackText: document.getElementById('diagnosisFeedback'),
    recordIdText: document.getElementById('currentRecordIdText')
  });

  const drawer = new NotebookDrawer({
    drawer: document.getElementById('drawer'),
    drawerMask: document.getElementById('drawerMask'),
    drawerTitle: document.getElementById('drawerTitle'),
    drawerBody: document.getElementById('drawerBody'),
    closeBtn: document.getElementById('closeDrawerBtn')
  });

  const socraticModeToggle = document.getElementById('socraticModeToggle');
  const triggerDiagnosisBtn = document.getElementById('triggerDiagnosisBtn');
  const aiFeedbackForm = document.getElementById('aiFeedbackForm');
  const aiFeedbackSelect = document.getElementById('aiFeedbackSelect');
  const errorTypeWrap = document.getElementById('errorTypeWrap');
  const errorTypeSelect = document.getElementById('errorTypeSelect');
  const feedbackNote = document.getElementById('feedbackNote');
  const aiFeedbackMessage = document.getElementById('aiFeedbackMessage');
  const downloadPdfBtn = document.getElementById('downloadPdfBtn');

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
    if (!problemViewer.hasImage()) {
      window.alert('请先上传题目图片。');
      return;
    }

    const selectedFile = problemViewer.getSelectedFile();
    const isSocratic = socraticModeToggle.checked;

    diagnosisPanel.setRunning();
    aiFeedbackMessage.textContent = '';

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
        isSocratic
      });

      diagnosisPanel.renderResult(result);
      currentRecordId = result.recordId || '';

      aiFeedbackForm.reset();
      errorTypeWrap.classList.add('hidden');

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
      aiFeedbackMessage.textContent = '请先完成一次诊断，再提交反馈。';
      return;
    }

    const aiFeedback = aiFeedbackSelect.value;
    const errorType = errorTypeSelect.value;
    const note = feedbackNote.value.trim();

    if (aiFeedback === 'INACCURATE' && !errorType) {
      aiFeedbackMessage.textContent = '识别不准确时请先选择错误类型。';
      return;
    }

    try {
      const response = await submitAiFeedback({
        recordId: currentRecordId,
        aiFeedback,
        errorType,
        note
      });

      aiFeedbackMessage.textContent = response.message || '反馈已保存';
      await logEvent({
        eventType: 'FEEDBACK',
        page: 'HOME',
        action: 'SUBMIT_AI_FEEDBACK',
        payload: { aiFeedback, errorType },
        recordId: currentRecordId,
        ts: Date.now()
      });
    } catch (error) {
      aiFeedbackMessage.textContent = error.message || '反馈保存失败';
    }
  });

  downloadPdfBtn.addEventListener('click', async () => {
    if (!currentRecordId) {
      aiFeedbackMessage.textContent = '暂无可下载报告，请先完成诊断。';
      return;
    }

    try {
      await downloadPdfReport(currentRecordId);
    } catch (error) {
      aiFeedbackMessage.textContent = error.message || '下载失败';
    }
  });

  document.getElementById('openNotebookBtn').addEventListener('click', async () => {
    try {
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
      window.alert(error.message || '加载错题本失败');
    }
  });

  document.getElementById('openStatsBtn').addEventListener('click', async () => {
    try {
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
      window.alert(error.message || '加载统计失败');
    }
  });

  document.getElementById('logoutBtn').addEventListener('click', async () => {
    await logoutUser();
    window.location.href = '/login.html';
  });
}
