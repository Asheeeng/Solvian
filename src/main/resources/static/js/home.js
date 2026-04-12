import { requireAuth, roleToLabel } from './common/auth-guard.js?v=20260407e';
import {
  buildSubmissionImageUrl,
  createDiagnosisTask,
  createSubmission,
  createSubmissionDiagnosisTask,
  downloadPdfReport,
  fetchCurrentProfile,
  fetchDashboardSummary,
  fetchDiagnosisTask,
  fetchHistory,
  fetchMySubmissions,
  fetchTeacherSubmissions,
  logEvent,
  logoutUser,
  setCurrentUser,
  submitAiFeedback
} from './common/storage.js?v=20260407g';
import { normalizeDiagnosisPreview, normalizeDiagnosisResult } from './common/diagnosis-normalizer.js?v=20260408a';
import { ProblemViewer } from './modules/problem-viewer.js?v=20260408b';
import { DiagnosisPanel } from './modules/diagnosis-panel.js?v=20260408b';
import { DiagnosisProgressPanel } from './modules/diagnosis-progress-panel.js?v=20260408b';
import { NotebookDrawer } from './modules/notebook-drawer.js?v=20260407e';
import { initWorkspaceTheme } from './modules/theme-controller.js?v=20260407e';

function escapeHtml(raw) {
  return String(raw ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

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

function logEventInBackground(payload) {
  logEvent(payload);
}

function formatTime(timestamp) {
  if (!timestamp) {
    return '-';
  }
  return new Date(timestamp).toLocaleString('zh-CN', { hour12: false });
}

function formatTeacherCardTime(timestamp) {
  if (!timestamp) {
    return '-';
  }
  const date = new Date(timestamp);
  if (Number.isNaN(date.getTime())) {
    return '-';
  }
  const month = date.getMonth() + 1;
  const day = date.getDate();
  const hour = String(date.getHours()).padStart(2, '0');
  const minute = String(date.getMinutes()).padStart(2, '0');
  return `${month}月${day}日 ${hour}:${minute}`;
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

function submissionStatusMeta(status) {
  if (status === 'CHECKED') {
    return { label: '已批改', className: 'status-badge status-badge--success' };
  }
  if (status === 'CHECKING') {
    return { label: '检测中', className: 'status-badge status-badge--running' };
  }
  if (status === 'FAILED') {
    return { label: '检测失败', className: 'status-badge status-badge--danger' };
  }
  return { label: '待批改', className: 'status-badge status-badge--pending' };
}

function normalizeSubmissionList(list = []) {
  if (!Array.isArray(list)) {
    return [];
  }
  return [...list].sort((left, right) => {
    const leftTimeRaw = new Date(left?.submitTime || 0).getTime();
    const rightTimeRaw = new Date(right?.submitTime || 0).getTime();
    const leftTime = Number.isFinite(leftTimeRaw) ? leftTimeRaw : 0;
    const rightTime = Number.isFinite(rightTimeRaw) ? rightTimeRaw : 0;
    return rightTime - leftTime;
  });
}

function getTeacherSubmissionDisplayName(item = {}) {
  return item.studentName || item.studentUserId || '未知学生';
}

function getTeacherSubmissionInitial(item = {}) {
  const source = String(getTeacherSubmissionDisplayName(item)).trim();
  return source ? source.slice(0, 1).toUpperCase() : 'S';
}

function mapDiagnosisStatusLabel(status) {
  switch (status) {
    case 'error_found':
      return '已定位错误';
    case 'correct':
      return '未发现明显错误';
    case 'reasoning':
      return '分析中';
    case 'queued':
      return '等待诊断';
    case 'failed':
      return '诊断失败';
    case 'unable_to_judge':
      return '暂未形成稳定结论';
    default:
      return status ? String(status).replaceAll('_', ' ') : '暂无诊断结果';
  }
}

function extractDiagnosisFeedback(result = {}) {
  const wrongStep = Array.isArray(result.steps)
    ? result.steps.find((step) => step?.isWrong || step?.errorMessage || step?.explanation || step?.content)
    : null;
  const diffInfo = result?.diffInfo && typeof result.diffInfo === 'object'
    ? ['summary', 'reason', 'message', 'detail', 'explanation']
      .map((key) => String(result.diffInfo[key] ?? '').trim())
      .find(Boolean)
    : '';
  const wrongStepText = wrongStep
    ? [wrongStep.title, wrongStep.errorMessage || wrongStep.explanation || wrongStep.content]
      .filter(Boolean)
      .join('：')
    : '';

  return [
    String(result.feedback ?? '').trim(),
    wrongStepText,
    diffInfo
  ].find(Boolean) || '暂无诊断结果';
}

function resolveSubmissionDiagnosis(item = {}) {
  const rawResult = String(item.checkResultJson ?? '').trim();
  if (!rawResult) {
    return {
      hasDiagnosis: false,
      indexText: '-',
      statusText: '暂无诊断结果',
      feedbackText: '暂无诊断结果'
    };
  }

  const normalized = normalizeDiagnosisResult(rawResult);
  const wrongStep = Array.isArray(normalized.steps)
    ? normalized.steps.find((step) => step?.isWrong || step?.errorMessage || step?.explanation || step?.content)
    : null;
  const errorIndex = normalized.errorIndex ?? wrongStep?.stepNo ?? null;

  return {
    hasDiagnosis: true,
    indexText: errorIndex ? `第 ${errorIndex} 步` : (wrongStep?.title || '未定位到具体步骤'),
    statusText: mapDiagnosisStatusLabel(normalized.status),
    feedbackText: extractDiagnosisFeedback(normalized)
  };
}

function buildDefaultClassLabel(role, className) {
  if (className) {
    return className;
  }
  if (role === 'STUDENT') {
    return '默认测试班级';
  }
  if (role === 'TEACHER') {
    return '默认测试班级';
  }
  return '未绑定班级';
}

function buildInitialTimeline() {
  return `
    <li class="timeline-step">
      <div class="timeline-step__header">
        <span class="timeline-step__stepno">1</span>
        <div class="timeline-step__title">
          <span class="timeline-step__name">尚未生成诊断步骤</span>
        </div>
      </div>
      <div class="timeline-step__body">
        <p>完成上传并启动诊断后，这里会展示 AI 返回的分步分析。</p>
      </div>
    </li>
  `;
}

function renderRecentSubmissions(list = []) {
  if (!Array.isArray(list) || !list.length) {
    return renderEmptyState('暂无提交记录', '还没有提交过作业，先上传一张图片试试吧。');
  }

  return list.map((item) => {
    const status = submissionStatusMeta(item.checkStatus);
    return `
      <article class="recent-card">
        <div class="record-card__header">
          <div>
            <p class="recent-card__title">${escapeHtml(item.fileName || '未命名作业')}</p>
            <p class="record-card__subtitle">${escapeHtml(formatTime(item.submitTime))}</p>
          </div>
          <span class="${status.className}">${escapeHtml(status.label)}</span>
        </div>
      </article>
    `;
  }).join('');
}

function renderTeacherSubmissionList(list = []) {
  if (!Array.isArray(list) || !list.length) {
    return renderEmptyState('暂时没有学生提交', '学生提交作业后，这里会自动显示最近的作业列表。');
  }

  return list.map((item) => {
    const status = submissionStatusMeta(item.checkStatus);
    const displayName = getTeacherSubmissionDisplayName(item);
    const detectLabel = item.checkStatus === 'CHECKING'
      ? '检测中'
      : (item.checkStatus === 'CHECKED' || item.checkStatus === 'FAILED' ? '重新AI检测' : 'AI检测');
    const detectDisabled = item.checkStatus === 'CHECKING' ? ' disabled' : '';
    const resultAction = item.checkStatus === 'CHECKED'
      ? `
          <button type="button" class="action-button action-button--ghost" data-submission-action="result" data-submission-id="${item.id}">
            <span class="action-button__icon">📋</span>
            <span class="action-button__label">查看诊断结果</span>
          </button>
        `
      : '';
    return `
      <article class="record-card" data-submission-card-id="${item.id}" style="gap: 16px;">
        <div class="record-card__header" style="align-items: center;">
          <div style="display: flex; align-items: center; gap: 12px; min-width: 0; flex: 1;">
            <div class="app-brand__badge" style="width: 40px; height: 40px; min-width: 40px; border-radius: 14px; font-size: 16px; box-shadow: var(--shadow-sm);">
              ${escapeHtml(getTeacherSubmissionInitial(item))}
            </div>
            <div style="min-width: 0;">
              <p class="record-card__title">${escapeHtml(displayName)}</p>
              <p class="record-card__subtitle">${escapeHtml(item.fileName || '未命名作业')}</p>
            </div>
          </div>
          <p class="record-card__subtitle" style="margin-top: 0; white-space: nowrap;">${escapeHtml(formatTeacherCardTime(item.submitTime))}</p>
        </div>
        <div class="record-card__body">
          <div class="record-kv" style="padding: 12px; overflow: hidden;">
            <img
              class="problem-preview"
              data-submission-action="preview"
              data-submission-image="true"
              data-submission-id="${item.id}"
              src="${escapeHtml(buildSubmissionImageUrl(item.id))}"
              alt="${escapeHtml(item.fileName || '学生作业图片')}"
              loading="lazy"
              style="width: 100%; max-height: 300px; object-fit: contain; cursor: zoom-in;"
            />
            <div class="empty-state hidden" data-image-fallback="${item.id}" style="min-height: 180px; padding: 24px;">
              <p class="empty-state__title">图片加载失败</p>
              <p class="empty-state__description">请稍后重试或重新打开。</p>
            </div>
          </div>
        </div>
        <div class="feedback-actions" style="align-items: center; justify-content: space-between;">
          <span class="${status.className}">${escapeHtml(status.label)}</span>
          <div class="feedback-actions" style="justify-content: flex-end;">
            ${resultAction}
          <button type="button" class="action-button action-button--primary" data-submission-action="diagnose" data-submission-id="${item.id}"${detectDisabled}>
            <span class="action-button__icon">▶</span>
            <span class="action-button__label">${escapeHtml(detectLabel)}</span>
          </button>
          </div>
        </div>
      </article>
    `;
  }).join('');
}

function bindTeacherSubmissionImageFallbacks(container) {
  if (!container) {
    return;
  }

  container.querySelectorAll('[data-submission-image="true"]').forEach((image) => {
    const submissionId = image.dataset.submissionId;
    const fallback = container.querySelector(`[data-image-fallback="${submissionId}"]`);
    if (!fallback) {
      return;
    }

    image.addEventListener('load', () => {
      image.classList.remove('hidden');
      fallback.classList.add('hidden');
    });

    image.addEventListener('error', () => {
      image.classList.add('hidden');
      fallback.classList.remove('hidden');
    });

    if (image.complete && !image.naturalWidth) {
      image.classList.add('hidden');
      fallback.classList.remove('hidden');
    }
  });
}

const currentSession = requireAuth();
if (currentSession) {
  let currentUser = currentSession.user;
  const isTeacher = currentUser?.role === 'TEACHER';
  const isStudent = currentUser?.role === 'STUDENT';

  const currentRoleLabel = document.getElementById('currentRoleLabel');
  const currentUserLabel = document.getElementById('currentUserLabel');
  const currentClassLabel = document.getElementById('currentClassLabel');

  const workspaceGrid = document.getElementById('workspaceGrid');
  const workspaceHeroCard = document.getElementById('workspaceHeroCard');
  const workspaceSecondaryColumn = document.getElementById('workspaceSecondaryColumn');
  const teacherSubmissionBoard = document.getElementById('teacherSubmissionBoard');
  const teacherSubmissionList = document.getElementById('teacherSubmissionList');
  const teacherWorkspacePanels = document.getElementById('teacherWorkspacePanels');
  const studentWorkspacePanels = document.getElementById('studentWorkspacePanels');
  const studentSubmissionMessage = document.getElementById('studentSubmissionMessage');
  const recentSubmissionsList = document.getElementById('recentSubmissionsList');

  const heroTitleText = document.getElementById('heroTitleText');
  const heroDescriptionText = document.getElementById('heroDescriptionText');
  const heroMetaText = document.getElementById('heroMetaText');
  const heroPrimaryChip = document.getElementById('heroPrimaryChip');
  const teacherFlowChip = document.getElementById('teacherFlowChip');
  const problemFileMeta = document.getElementById('problemFileMeta');
  const problemFileButtonLabel = document.getElementById('problemFileButtonLabel');
  const placeholderTitleText = document.getElementById('placeholderTitleText');
  const placeholderDescText = document.getElementById('placeholderDescText');
  const socraticModeCard = document.getElementById('socraticModeCard');
  const triggerDiagnosisBtn = document.getElementById('triggerDiagnosisBtn');
  const submitHomeworkBtn = document.getElementById('submitHomeworkBtn');
  const openNotebookBtn = document.getElementById('openNotebookBtn');
  const openStatsBtn = document.getElementById('openStatsBtn');
  const openNotebookCardBtn = document.getElementById('openNotebookCardBtn');
  const openStatsCardBtn = document.getElementById('openStatsCardBtn');
  const logoutBtn = document.getElementById('logoutBtn');
  const imageModal = document.getElementById('imageModal');
  const modalTitleText = document.getElementById('modalTitleText');
  const modalSubtitleText = document.getElementById('modalSubtitleText');
  const modalStatusBadge = document.getElementById('modalStatusBadge');
  const modalImage = document.getElementById('modalImage');
  const modalImageShell = document.getElementById('modalImageShell');
  const closeImageModalBtn = document.getElementById('closeImageModalBtn');
  const modalDiagnosisSection = document.getElementById('modalDiagnosisSection');
  const modalDiagnosisIndexText = document.getElementById('modalDiagnosisIndexText');
  const modalDiagnosisStatusText = document.getElementById('modalDiagnosisStatusText');
  const modalDiagnosisFeedbackText = document.getElementById('modalDiagnosisFeedbackText');

  initWorkspaceTheme({
    root: document.body,
    toggle: document.getElementById('themeToggle')
  });

  const problemViewer = new ProblemViewer({
    fileInput: document.getElementById('problemFileInput'),
    previewImage: document.getElementById('problemPreviewImage'),
    imageCanvas: document.getElementById('problemImageCanvas'),
    placeholder: document.getElementById('problemImagePlaceholder'),
    modal: document.getElementById('imageModal'),
    modalImage: document.getElementById('modalImage'),
    closeModalBtn: document.getElementById('closeImageModalBtn'),
    fileMetaText: problemFileMeta
  });
  problemViewer.init();

  const drawer = new NotebookDrawer({
    drawer: document.getElementById('drawer'),
    drawerMask: document.getElementById('drawerMask'),
    drawerTitle: document.getElementById('drawerTitle'),
    drawerSubtitle: document.getElementById('drawerSubtitle'),
    drawerEyebrow: document.getElementById('drawerEyebrow'),
    drawerBody: document.getElementById('drawerBody'),
    closeBtn: document.getElementById('closeDrawerBtn')
  });

  const diagnosisPanel = isTeacher
    ? new DiagnosisPanel({
      statusBadge: document.getElementById('diagnosisStatusBadge'),
      statusText: document.getElementById('diagnosisStatusText'),
      stepsContainer: document.getElementById('diagnosisSteps'),
      feedbackText: document.getElementById('diagnosisFeedback'),
      errorIndexText: document.getElementById('diagnosisErrorIndexText'),
      errorReasonText: document.getElementById('diagnosisErrorReasonText'),
      tagsContainer: document.getElementById('diagnosisTags'),
      subjectScopeText: document.getElementById('subjectScopeText'),
      recordIdText: document.getElementById('currentRecordIdText')
    })
    : null;
  const diagnosisProgressPanel = isTeacher
    ? new DiagnosisProgressPanel({
      container: document.getElementById('diagnosisProgressPanel')
    })
    : null;

  if (diagnosisProgressPanel) {
    diagnosisProgressPanel.reset();
  }

  const socraticModeToggle = document.getElementById('socraticModeToggle');
  const aiFeedbackForm = document.getElementById('aiFeedbackForm');
  const saveFeedbackBtn = document.getElementById('saveFeedbackBtn');
  const aiFeedbackSelect = document.getElementById('aiFeedbackSelect');
  const errorTypeWrap = document.getElementById('errorTypeWrap');
  const errorTypeSelect = document.getElementById('errorTypeSelect');
  const feedbackNote = document.getElementById('feedbackNote');
  const aiFeedbackMessage = document.getElementById('aiFeedbackMessage');
  const downloadPdfBtn = document.getElementById('downloadPdfBtn');
  const teacherOnlyHint = document.getElementById('teacherOnlyHint');

  let currentRecordId = '';
  let currentTaskId = '';
  let diagnosisPollTimer = 0;
  let diagnosisPollToken = 0;
  let activeDiagnosisButton = null;
  let teacherSubmissionMap = new Map();
  let modalImageZoomed = false;

  function renderHeaderMeta() {
    currentRoleLabel.textContent = roleToLabel(currentUser.role);
    currentUserLabel.textContent = currentUser.username || '';
    currentClassLabel.textContent = buildDefaultClassLabel(currentUser.role, currentUser.className);
  }

  function resetModalImageZoom() {
    modalImageZoomed = false;
    if (modalImage) {
      modalImage.style.transform = '';
      modalImage.style.cursor = 'zoom-in';
      modalImage.style.transition = 'transform 180ms ease';
    }
    if (modalImageShell) {
      modalImageShell.style.overflow = '';
    }
  }

  function resetImageModalContent() {
    if (modalTitleText) {
      modalTitleText.textContent = '图片预览';
    }
    if (modalSubtitleText) {
      modalSubtitleText.textContent = '点击图片可放大查看';
    }
    if (modalStatusBadge) {
      modalStatusBadge.className = 'status-badge hidden';
      modalStatusBadge.textContent = '-';
    }
    if (modalDiagnosisSection) {
      modalDiagnosisSection.classList.add('hidden');
    }
    if (modalDiagnosisIndexText) {
      modalDiagnosisIndexText.textContent = '-';
    }
    if (modalDiagnosisStatusText) {
      modalDiagnosisStatusText.textContent = '暂无诊断结果';
    }
    if (modalDiagnosisFeedbackText) {
      modalDiagnosisFeedbackText.textContent = '暂无诊断结果';
    }
    resetModalImageZoom();
  }

  function populateImageModal({
    title = '图片预览',
    subtitle = '点击图片可放大查看',
    status = null,
    diagnosis = null
  } = {}) {
    if (modalTitleText) {
      modalTitleText.textContent = title;
    }
    if (modalSubtitleText) {
      modalSubtitleText.textContent = subtitle;
    }

    if (modalStatusBadge) {
      if (status?.label && status?.className) {
        modalStatusBadge.className = status.className;
        modalStatusBadge.textContent = status.label;
      } else {
        modalStatusBadge.className = 'status-badge hidden';
        modalStatusBadge.textContent = '-';
      }
    }

    if (diagnosis?.hasDiagnosis) {
      modalDiagnosisSection?.classList.remove('hidden');
      if (modalDiagnosisIndexText) {
        modalDiagnosisIndexText.textContent = diagnosis.indexText || '-';
      }
      if (modalDiagnosisStatusText) {
        modalDiagnosisStatusText.textContent = diagnosis.statusText || '暂无诊断结果';
      }
      if (modalDiagnosisFeedbackText) {
        modalDiagnosisFeedbackText.textContent = diagnosis.feedbackText || '暂无诊断结果';
      }
    } else {
      modalDiagnosisSection?.classList.add('hidden');
      if (modalDiagnosisIndexText) {
        modalDiagnosisIndexText.textContent = '-';
      }
      if (modalDiagnosisStatusText) {
        modalDiagnosisStatusText.textContent = '暂无诊断结果';
      }
      if (modalDiagnosisFeedbackText) {
        modalDiagnosisFeedbackText.textContent = '暂无诊断结果';
      }
    }

    resetModalImageZoom();
  }

  function openSubmissionImageModal(item, { showDiagnosis = false } = {}) {
    if (!item?.id) {
      return;
    }

    const displayName = getTeacherSubmissionDisplayName(item);
    const fileName = item.fileName || '学生作业图片';
    const status = submissionStatusMeta(item.checkStatus);

    populateImageModal({
      title: displayName,
      subtitle: `${formatTeacherCardTime(item.submitTime)} · ${fileName}`,
      status,
      diagnosis: showDiagnosis ? resolveSubmissionDiagnosis(item) : null
    });

    problemViewer.showModalWithSource({
      imageUrl: buildSubmissionImageUrl(item.id),
      imageName: fileName
    });
  }

  async function refreshProfile() {
    try {
      const profile = await fetchCurrentProfile();
      currentUser = { ...currentUser, ...profile };
      currentSession.user = currentUser;
      setCurrentUser(currentSession);
    } catch (error) {
      // 旧登录态没有班级信息时保持回退文案，不阻塞页面渲染。
    } finally {
      renderHeaderMeta();
    }
  }

  function setActiveDiagnosisButton(button, busyLabel = '正在诊断') {
    if (activeDiagnosisButton && activeDiagnosisButton !== button) {
      setButtonBusy(activeDiagnosisButton, false);
    }
    activeDiagnosisButton = button || null;
    if (activeDiagnosisButton) {
      setButtonBusy(activeDiagnosisButton, true, busyLabel);
    }
  }

  function clearActiveDiagnosisButton() {
    if (activeDiagnosisButton) {
      setButtonBusy(activeDiagnosisButton, false);
      activeDiagnosisButton = null;
    }
  }

  function stopDiagnosisPolling() {
    if (diagnosisPollTimer) {
      window.clearTimeout(diagnosisPollTimer);
      diagnosisPollTimer = 0;
    }
  }

  function resetFeedbackForm() {
    if (!aiFeedbackForm) {
      return;
    }
    aiFeedbackForm.reset();
    errorTypeWrap?.classList.add('hidden');
    setFeedbackMessage(aiFeedbackMessage, '');
  }

  function resetTeacherPanels() {
    if (!diagnosisPanel || !diagnosisProgressPanel) {
      return;
    }
    const stepsContainer = document.getElementById('diagnosisSteps');
    const feedbackText = document.getElementById('diagnosisFeedback');
    const errorReasonText = document.getElementById('diagnosisErrorReasonText');
    const tagsContainer = document.getElementById('diagnosisTags');
    const statusBadge = document.getElementById('diagnosisStatusBadge');
    const statusText = document.getElementById('diagnosisStatusText');
    const recordIdText = document.getElementById('currentRecordIdText');
    const subjectScopeText = document.getElementById('subjectScopeText');
    const errorIndexText = document.getElementById('diagnosisErrorIndexText');

    if (statusBadge) {
      statusBadge.textContent = '等待诊断';
      statusBadge.className = 'status-badge status-badge--pending';
    }
    if (statusText) {
      statusText.textContent = '请先上传题目并启动诊断，系统会在这里返回完整结论。';
      statusText.className = 'result-summary__note';
    }
    if (stepsContainer) {
      stepsContainer.innerHTML = buildInitialTimeline();
    }
    if (feedbackText) {
      feedbackText.textContent = '请先上传题目并启动诊断。';
    }
    if (errorReasonText) {
      errorReasonText.textContent = '-';
    }
    if (tagsContainer) {
      tagsContainer.innerHTML = '<span class="tag-chip tag-chip--accent">#待检测</span>';
    }
    if (recordIdText) {
      recordIdText.textContent = '-';
    }
    if (subjectScopeText) {
      subjectScopeText.textContent = 'matrix';
    }
    if (errorIndexText) {
      errorIndexText.textContent = '-';
    }
    teacherOnlyHint?.classList.add('hidden');
    diagnosisProgressPanel.reset();
    resetFeedbackForm();
  }

  async function refreshStudentSubmissions() {
    if (!isStudent) {
      return;
    }
    try {
      const response = await fetchMySubmissions(3);
      recentSubmissionsList.innerHTML = renderRecentSubmissions(response?.list || []);
    } catch (error) {
      recentSubmissionsList.innerHTML = renderEmptyState('加载失败', error.message || '提交记录暂时无法加载。');
    }
  }

  async function refreshTeacherSubmissions() {
    if (!isTeacher) {
      return;
    }
    try {
      const response = await fetchTeacherSubmissions(20);
      const list = normalizeSubmissionList(response?.list || []);
      teacherSubmissionMap = new Map(list.map((item) => [String(item.id), item]));
      teacherSubmissionList.innerHTML = renderTeacherSubmissionList(list);
      teacherSubmissionList.style.gap = '16px';
      bindTeacherSubmissionImageFallbacks(teacherSubmissionList);
    } catch (error) {
      teacherSubmissionMap = new Map();
      teacherSubmissionList.innerHTML = renderEmptyState('加载失败', error.message || '提交列表暂时无法加载。');
      if (diagnosisPanel) {
        diagnosisPanel.setError(error.message || '学生提交列表加载失败');
      }
    }
  }

  async function handleDiagnosisTaskSnapshot(task) {
    if (!task) {
      return false;
    }

    currentTaskId = task.taskId || currentTaskId;
    const partialResult = normalizeDiagnosisPreview(task.partialResult || {}, task);
    diagnosisProgressPanel?.renderTask({
      ...task,
      partialResult
    });
    const partialHighlights = Array.isArray(partialResult.imageHighlights) ? partialResult.imageHighlights : [];
    const partialSteps = Array.isArray(partialResult.steps) ? partialResult.steps : [];
    if (partialHighlights.length) {
      problemViewer.setHighlights(partialHighlights);
    }
    if (partialSteps.length && task.status !== 'done') {
      diagnosisPanel?.renderStreamingResult({
        ...partialResult,
        stageMessage: task.stageMessage
      });
    }

    if (task.status === 'done' && task.finalResult) {
      const finalResult = normalizeDiagnosisResult(task.finalResult);
      diagnosisPanel?.renderResult(finalResult);
      const finalHighlights = Array.isArray(finalResult.imageHighlights) ? finalResult.imageHighlights : [];
      problemViewer.setHighlights(finalHighlights.length ? finalHighlights : partialHighlights);
      currentRecordId = finalResult.recordId || task.recordId || '';
      resetFeedbackForm();

      logEventInBackground({
        eventType: 'DIAGNOSIS',
        page: 'HOME',
        action: 'DIAGNOSIS_FINISHED',
        payload: {
          recordId: currentRecordId,
          status: finalResult.status,
          errorIndex: finalResult.errorIndex ?? finalResult.error_index ?? null
        },
        recordId: currentRecordId,
        ts: Date.now()
      });
      if (isTeacher) {
        await refreshTeacherSubmissions();
      }
      return true;
    }

    if (task.status === 'failed') {
      diagnosisPanel?.setError(task.errorMessage || '诊断失败，请稍后重试。');
      if (!partialHighlights.length) {
        problemViewer.clearHighlights();
      }
      if (isTeacher) {
        await refreshTeacherSubmissions();
      }
      return true;
    }

    return false;
  }

  async function pollDiagnosisTask(taskId, pollToken) {
    try {
      const task = await fetchDiagnosisTask(taskId);
      if (pollToken !== diagnosisPollToken) {
        return;
      }

      const finished = await handleDiagnosisTaskSnapshot(task);
      if (finished) {
        stopDiagnosisPolling();
        clearActiveDiagnosisButton();
        return;
      }

      diagnosisPollTimer = window.setTimeout(() => pollDiagnosisTask(taskId, pollToken), 1100);
    } catch (error) {
      if (pollToken !== diagnosisPollToken) {
        return;
      }
      stopDiagnosisPolling();
      diagnosisProgressPanel?.setFailed(error.message || '诊断进度获取失败');
      diagnosisPanel?.setError(error.message || '诊断进度获取失败');
      clearActiveDiagnosisButton();
    }
  }

  function startDiagnosisPolling(taskId) {
    stopDiagnosisPolling();
    diagnosisPollToken += 1;
    const pollToken = diagnosisPollToken;
    diagnosisPollTimer = window.setTimeout(() => pollDiagnosisTask(taskId, pollToken), 800);
  }

  async function openNotebook() {
    try {
      setButtonBusy(openNotebookBtn, true, '正在加载');
      if (openNotebookCardBtn) {
        setButtonBusy(openNotebookCardBtn, true, '正在加载');
      }
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
      const message = error.message || '加载错题本失败';
      if (diagnosisPanel) {
        diagnosisPanel.setError(message);
      } else {
        setFeedbackMessage(studentSubmissionMessage, message, 'error');
      }
    } finally {
      setButtonBusy(openNotebookBtn, false);
      if (openNotebookCardBtn) {
        setButtonBusy(openNotebookCardBtn, false);
      }
    }
  }

  async function openStats() {
    try {
      setButtonBusy(openStatsBtn, true, '正在加载');
      if (openStatsCardBtn) {
        setButtonBusy(openStatsCardBtn, true, '正在加载');
      }
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
      const message = error.message || '加载统计失败';
      if (diagnosisPanel) {
        diagnosisPanel.setError(message);
      } else {
        setFeedbackMessage(studentSubmissionMessage, message, 'error');
      }
    } finally {
      setButtonBusy(openStatsBtn, false);
      if (openStatsCardBtn) {
        setButtonBusy(openStatsCardBtn, false);
      }
    }
  }

  function configureStudentView() {
    teacherSubmissionBoard.classList.add('hidden');
    teacherWorkspacePanels.classList.add('hidden');
    workspaceHeroCard.classList.remove('hidden');
    workspaceSecondaryColumn.classList.remove('hidden');
    workspaceGrid.style.gridTemplateColumns = '';
    studentWorkspacePanels.classList.remove('hidden');
    socraticModeCard.classList.add('hidden');
    triggerDiagnosisBtn.classList.add('hidden');
    submitHomeworkBtn.classList.remove('hidden');
    studentSubmissionMessage.classList.remove('hidden');
    teacherFlowChip.classList.add('hidden');

    heroTitleText.textContent = '上传作业';
    heroDescriptionText.textContent = '选择作业图片后，点击提交按钮发送给老师批改。';
    heroMetaText.textContent = '上传作业图片，提交给老师批改';
    heroPrimaryChip.textContent = '上传作业';
    problemFileButtonLabel.textContent = '选择作业图片';
    problemFileMeta.textContent = '请选择要提交的作业图片';
    placeholderTitleText.textContent = '等待上传作业';
    placeholderDescText.textContent = '选择作业图片后，点击提交按钮发送给老师批改。';
  }

  function configureTeacherView() {
    teacherSubmissionBoard.classList.remove('hidden');
    teacherWorkspacePanels.classList.add('hidden');
    workspaceHeroCard.classList.add('hidden');
    workspaceSecondaryColumn.classList.add('hidden');
    workspaceGrid.style.gridTemplateColumns = 'minmax(0, 1fr)';
    studentWorkspacePanels.classList.add('hidden');
    socraticModeCard.classList.add('hidden');
    triggerDiagnosisBtn.classList.add('hidden');
    submitHomeworkBtn.classList.add('hidden');
    teacherFlowChip.classList.add('hidden');
    resetTeacherPanels();
  }

  renderHeaderMeta();
  refreshProfile();
  resetImageModalContent();

  closeImageModalBtn?.addEventListener('click', resetImageModalContent);
  imageModal?.addEventListener('click', (event) => {
    if (event.target?.dataset?.closeModal === 'true') {
      resetImageModalContent();
    }
  });
  modalImage?.addEventListener('click', () => {
    if (!modalImage.getAttribute('src')) {
      return;
    }
    modalImageZoomed = !modalImageZoomed;
    modalImage.style.transform = modalImageZoomed ? 'scale(1.5)' : '';
    modalImage.style.cursor = modalImageZoomed ? 'zoom-out' : 'zoom-in';
    if (modalImageShell) {
      modalImageShell.style.overflow = modalImageZoomed ? 'auto' : '';
    }
  });

  document.getElementById('problemFileInput').addEventListener('change', () => {
    const meta = problemViewer.getImageMeta();
    if (isStudent) {
      setFeedbackMessage(studentSubmissionMessage, '');
      setButtonLocked(submitHomeworkBtn, !problemViewer.getSelectedFile());
      submitHomeworkBtn.disabled = !problemViewer.getSelectedFile();
    }
    if (isTeacher) {
      currentTaskId = '';
      currentRecordId = '';
      stopDiagnosisPolling();
      clearActiveDiagnosisButton();
      problemViewer.clearHighlights();
      resetTeacherPanels();
      logEventInBackground({
        eventType: 'UPLOAD',
        page: 'HOME',
        action: 'UPLOAD_IMAGE',
        payload: { imageName: meta.imageName || '' },
        ts: Date.now()
      });
    }
  });

  document.getElementById('problemPreviewImage').addEventListener('click', () => {
    const meta = problemViewer.getImageMeta();
    populateImageModal({
      title: meta.imageName || '图片预览',
      subtitle: meta.imageName ? `${meta.imageName} · 点击图片可放大查看` : '点击图片可放大查看'
    });
    logEventInBackground({
      eventType: 'PREVIEW',
      page: 'HOME',
      action: 'OPEN_IMAGE_MODAL',
      payload: { imageName: meta.imageName || '' },
      ts: Date.now()
    });
  });

  if (isStudent) {
    configureStudentView();
    submitHomeworkBtn.disabled = true;

    submitHomeworkBtn.addEventListener('click', async () => {
      const selectedFile = problemViewer.getSelectedFile();
      if (!selectedFile) {
        setFeedbackMessage(studentSubmissionMessage, '请先选择要提交的作业图片。', 'error');
        return;
      }

      try {
        setButtonBusy(submitHomeworkBtn, true, '提交中');
        const response = await createSubmission({ file: selectedFile });
        setFeedbackMessage(studentSubmissionMessage, response.message || '提交成功', 'success');
        await refreshStudentSubmissions();
        problemViewer.reset({ metaText: '请选择要提交的作业图片' });
        submitHomeworkBtn.disabled = true;
        logEventInBackground({
          eventType: 'SUBMISSION',
          page: 'HOME',
          action: 'CREATE_SUBMISSION',
          payload: { fileName: selectedFile.name },
          ts: Date.now()
        });
      } catch (error) {
        setFeedbackMessage(studentSubmissionMessage, error.message || '提交失败，请稍后重试。', 'error');
      } finally {
        setButtonBusy(submitHomeworkBtn, false);
        if (!problemViewer.getSelectedFile()) {
          submitHomeworkBtn.disabled = true;
        }
      }
    });

    refreshStudentSubmissions();
  }

  if (isTeacher) {
    configureTeacherView();

    teacherSubmissionList.addEventListener('click', async (event) => {
      const actionButton = event.target.closest('[data-submission-action]');
      if (!actionButton) {
        return;
      }

      const submissionId = String(actionButton.dataset.submissionId || '');
      const submission = teacherSubmissionMap.get(submissionId);
      if (!submission) {
        return;
      }

      const imageUrl = buildSubmissionImageUrl(submissionId);
      const fileName = submission.fileName || '作业图片';

      if (actionButton.dataset.submissionAction === 'preview') {
        openSubmissionImageModal(submission, { showDiagnosis: false });
        return;
      }

      if (actionButton.dataset.submissionAction === 'result') {
        openSubmissionImageModal(submission, { showDiagnosis: true });
        return;
      }

      try {
        problemViewer.setPreviewSource({
          imageUrl,
          imageName: fileName,
          metaText: `${getTeacherSubmissionDisplayName(submission)} · ${fileName} · 已从学生提交列表载入`
        });
        currentTaskId = '';
        currentRecordId = '';
        stopDiagnosisPolling();
        resetTeacherPanels();
        diagnosisPanel?.setRunning();
        diagnosisProgressPanel?.renderTask({
          status: 'queued',
          progress: 2,
          stageMessage: '正在创建诊断任务',
          partialResult: {}
        });
        setActiveDiagnosisButton(actionButton, '正在诊断');
        const task = await createSubmissionDiagnosisTask(submissionId, {
          isSocratic: false,
          problemType: 'matrix'
        });
        const finished = await handleDiagnosisTaskSnapshot(task);
        if (finished) {
          clearActiveDiagnosisButton();
          return;
        }
        startDiagnosisPolling(task.taskId);
      } catch (error) {
        diagnosisProgressPanel?.setFailed(error.message || '诊断失败，请稍后重试。');
        diagnosisPanel?.setError(error.message || '诊断失败，请稍后重试。');
        clearActiveDiagnosisButton();
        await refreshTeacherSubmissions();
      }
    });

    triggerDiagnosisBtn.addEventListener('click', async () => {
      if (!problemViewer.hasImage()) {
        diagnosisPanel?.setError('请先上传题目图片，再启动 AI 诊断。');
        problemFileMeta.textContent = '请先选择题目图片，然后再发起本次诊断。';
        return;
      }

      const selectedFile = problemViewer.getSelectedFile();
      const isSocratic = socraticModeToggle.checked;

      diagnosisPanel?.setRunning();
      diagnosisProgressPanel?.renderTask({
        status: 'queued',
        progress: 2,
        stageMessage: '正在创建诊断任务',
        partialResult: {}
      });
      problemViewer.clearHighlights();
      setFeedbackMessage(aiFeedbackMessage, '');
      currentRecordId = '';
      currentTaskId = '';
      setActiveDiagnosisButton(triggerDiagnosisBtn, '正在诊断');

      logEventInBackground({
        eventType: 'DIAGNOSIS',
        page: 'HOME',
        action: 'START_DIAGNOSIS',
        payload: { isSocratic },
        ts: Date.now()
      });

      try {
        const task = await createDiagnosisTask({
          file: selectedFile,
          isSocratic,
          problemType: 'matrix'
        });

        const finished = await handleDiagnosisTaskSnapshot(task);
        if (finished) {
          clearActiveDiagnosisButton();
          return;
        }

        startDiagnosisPolling(task.taskId);
      } catch (error) {
        problemViewer.clearHighlights();
        diagnosisProgressPanel?.setFailed(error.message || '诊断失败，请稍后重试。');
        diagnosisPanel?.setError(error.message || '诊断失败，请稍后重试。');
        clearActiveDiagnosisButton();
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

    refreshTeacherSubmissions();
  }

  openNotebookBtn.addEventListener('click', openNotebook);
  openStatsBtn.addEventListener('click', openStats);
  openNotebookCardBtn?.addEventListener('click', openNotebook);
  openStatsCardBtn?.addEventListener('click', openStats);

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
