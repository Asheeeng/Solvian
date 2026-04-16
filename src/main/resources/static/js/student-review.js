import { requireAuth, roleToLabel } from './common/auth-guard.js?v=20260407e';
import {
  buildSubmissionImageUrl,
  createSubmission,
  createSubmissionDiagnosisTask,
  fetchCurrentProfile,
  fetchDashboardSummary,
  fetchDiagnosisTask,
  fetchHistory,
  fetchMySubmissions,
  fetchTeacherSubmissions,
  logoutUser
} from './common/storage.js?v=20260407g';
import { normalizeDiagnosisPreview, normalizeDiagnosisResult } from './common/diagnosis-normalizer.js?v=20260408a';
import { NotebookDrawer } from './modules/notebook-drawer.js?v=20260407e';

const App = (() => {
  const state = {
    session: null,
    profile: null,
    students: [],
    selectedStudentId: '',
    selectedWorkId: '',
    drawer: null,
    pollTimer: 0,
    pollToken: 0,
    activeTaskId: '',
    activeSubmissionId: ''
  };

  const $ = (id) => document.getElementById(id);

  function init() {
    const session = requireAuth();
    if (!session) {
      return;
    }

    state.session = session;
    initDrawer();
    bindEvents();
    setHeaderActionState('diagnosis');
    setActionTip('正在加载作业列表...');
    loadBootstrapData();
  }

  function bindEvents() {
    $('searchInput')?.addEventListener('input', () => {
      renderList(getFilteredStudents());
    });

    $('uploadInput')?.addEventListener('change', async (event) => {
      const files = Array.from(event.target?.files || []);
      event.target.value = '';
      if (!files.length) {
        return;
      }
      await uploadFiles(files);
    });

    document.addEventListener('keydown', (event) => {
      if (event.key !== 'Escape') {
        return;
      }
      if (state.drawer && !state.drawer.drawer.classList.contains('hidden')) {
        state.drawer.close();
        return;
      }
      closeLightbox(event);
    });

    window.addEventListener('beforeunload', stopDiagnosisPolling);
  }

  function initDrawer() {
    const drawer = new NotebookDrawer({
      drawer: $('drawer'),
      drawerMask: $('drawerMask'),
      drawerTitle: $('drawerTitle'),
      drawerSubtitle: $('drawerSubtitle'),
      drawerEyebrow: $('drawerEyebrow'),
      drawerBody: $('drawerBody'),
      closeBtn: $('closeDrawerBtn')
    });

    const close = drawer.close.bind(drawer);
    drawer.close = () => {
      close();
      setHeaderActionState('diagnosis');
    };

    state.drawer = drawer;
  }

  async function loadBootstrapData() {
    try {
      state.profile = await fetchCurrentProfile();
    } catch (_) {
      state.profile = state.session?.user || null;
    }

    updateShellCopy();
    syncRoleLayout();
    await refreshSubmissionData();
  }

  function getRole() {
    return String(state.profile?.role || state.session?.user?.role || '').toUpperCase();
  }

  function isTeacherView() {
    const role = getRole();
    return role === 'TEACHER' || role === 'ADMIN';
  }

  function isStudentView() {
    return getRole() === 'STUDENT';
  }

  function syncRoleLayout() {
    const shell = document.querySelector('.review-shell');
    const sidebar = document.querySelector('.review-sidebar');
    const headerUploadBtn = $('headerUploadBtn');
    const emptyUploadActions = $('emptyUploadActions');
    const backToListBtn = $('btnBackToList');
    const searchInput = $('searchInput');

    if (isStudentView()) {
      shell?.setAttribute('data-role-view', 'student');
      sidebar?.classList.add('hidden');
      headerUploadBtn?.classList.remove('hidden');
      emptyUploadActions?.classList.remove('hidden');
      backToListBtn?.classList.add('hidden');
      if (searchInput) {
        searchInput.value = '';
      }
      return;
    }

    shell?.setAttribute('data-role-view', 'teacher');
    sidebar?.classList.remove('hidden');
    headerUploadBtn?.classList.add('hidden');
    emptyUploadActions?.classList.add('hidden');
    backToListBtn?.classList.remove('hidden');
  }

  function updateShellCopy() {
    const eyebrow = document.querySelector('.review-brand__eyebrow');
    const title = document.querySelector('.review-brand__title');
    const desc = document.querySelector('.review-brand__desc');
    const sidebarEyebrow = document.querySelector('.sidebar-head .eyebrow');
    const sidebarTitle = document.querySelector('.sidebar-head .title');
    const sidebarDesc = document.querySelector('.sidebar-head .desc');

    if (!eyebrow || !title || !desc || !sidebarEyebrow || !sidebarTitle || !sidebarDesc) {
      return;
    }

    if (isTeacherView()) {
      const userName = state.profile?.username || state.profile?.userId || '当前老师';
      eyebrow.textContent = 'Student Review';
      title.textContent = 'Solvian 学生作业诊断台';
      desc.textContent = `当前登录：${roleToLabel(getRole())} ${userName}。按学生快速筛选最近提交的作业，查看图片、运行 AI 诊断，并把错题本与统计集中在同一个工作区中。`;
      sidebarEyebrow.textContent = 'Students';
      sidebarTitle.textContent = '学生列表';
      sidebarDesc.textContent = '按姓名或学号筛选，优先处理有待批作业的同学。';
      return;
    }

    eyebrow.textContent = 'My Review';
    title.textContent = 'Solvian 我的作业诊断';
    desc.textContent = '直接上传自己的作业图片，查看历史提交，以及同步回来的错题归档与统计信息。';
    sidebarEyebrow.textContent = 'My Submissions';
    sidebarTitle.textContent = '我的作业';
    sidebarDesc.textContent = '按提交时间查看自己的作业，选择一张图片即可查看详情。';
  }

  async function fetchSubmissionList() {
    if (isTeacherView()) {
      const response = await fetchTeacherSubmissions(60);
      return normalizeSubmissionList(response?.list || []);
    }

    const response = await fetchMySubmissions(60);
    return normalizeSubmissionList(response?.list || []);
  }

  async function refreshSubmissionData({ preserveSelection = false } = {}) {
    const preservedStudentId = preserveSelection ? state.selectedStudentId : '';
    const preservedWorkId = preserveSelection ? state.selectedWorkId : '';
    const searchInput = $('searchInput');

    if (searchInput) {
      searchInput.disabled = true;
    }

    try {
      const submissionList = await fetchSubmissionList();
      state.students = groupSubmissionsByStudent(submissionList);
      renderList(getFilteredStudents());

      if (!state.students.length) {
        goBack({
          emptyTitle: isTeacherView() ? '还没有学生提交作业' : '还没有提交作业',
          emptyDesc: isTeacherView()
            ? '等学生上传作业后，这里会自动出现作业卡片和诊断入口。'
            : '点击右上角“上传作业”按钮，提交图片后这里会自动出现你的作业列表。'
        });
      } else if (isStudentView()) {
        const student = state.students[0];
        const autoSelectWorkId = preservedWorkId || student.works[0]?.id || '';

        pickStu(student.id, {
          preserveWork: Boolean(preservedWorkId),
          autoSelectWorkId,
          silentScroll: true
        });
      } else if (preservedStudentId) {
        restoreSelection(preservedStudentId, preservedWorkId);
      } else {
        setActionTip('请先选择一张作业图片');
      }
    } catch (error) {
      renderList([], { message: error.message || '作业列表暂时无法加载。' });
      goBack({
        emptyTitle: '加载失败',
        emptyDesc: error.message || '当前无法读取作业列表，请稍后刷新重试。'
      });
      setActionTip(error.message || '作业列表暂时无法加载。', 'error');
    } finally {
      if (searchInput) {
        searchInput.disabled = false;
      }
    }
  }

  function normalizeSubmissionList(list = []) {
    if (!Array.isArray(list)) {
      return [];
    }

    return [...list].sort((left, right) => parseTime(right?.submitTime) - parseTime(left?.submitTime));
  }

  function groupSubmissionsByStudent(list = []) {
    const grouped = new Map();
    const teacherView = isTeacherView();

    list.forEach((item, index) => {
      const groupId = teacherView
        ? String(item.studentUserId || item.studentId || item.studentName || `student-${index}`)
        : String(state.profile?.userId || state.profile?.username || item.studentUserId || item.studentId || 'me');
      const groupName = teacherView
        ? (item.studentName || item.studentUserId || `学生 ${index + 1}`)
        : (state.profile?.username || state.profile?.userId || item.studentName || '我');

      if (!grouped.has(groupId)) {
        grouped.set(groupId, {
          id: groupId,
          name: groupName,
          cls: buildClassText(item),
          works: [],
          latestAt: 0,
          pendingCount: 0
        });
      }

      const group = grouped.get(groupId);
      const work = mapSubmissionToWork(item, groupName);
      group.works.push(work);
      group.latestAt = Math.max(group.latestAt, work.timeValue);
      if (work.status === 'pending' || work.status === 'running') {
        group.pendingCount += 1;
      }
    });

    return Array.from(grouped.values())
      .map((group) => ({
        ...group,
        works: group.works.sort((left, right) => right.timeValue - left.timeValue)
      }))
      .sort((left, right) => {
        if (right.pendingCount !== left.pendingCount) {
          return right.pendingCount - left.pendingCount;
        }
        return right.latestAt - left.latestAt;
      });
  }

  function buildClassText(item = {}) {
    if (state.profile?.className) {
      return state.profile.className;
    }
    if (item.classId) {
      return `班级 ${item.classId}`;
    }
    return isTeacherView() ? '学生作业队列' : '个人作业记录';
  }

  function mapSubmissionToWork(item = {}, studentName = '') {
    const diagnosis = parseStoredDiagnosis(item.checkResultJson);
    return {
      id: String(item.id),
      submissionId: item.id,
      studentName,
      file: item.fileName || `作业-${item.id}`,
      time: formatTime(item.submitTime || item.checkedAt),
      timeValue: parseTime(item.submitTime || item.checkedAt),
      status: mapWorkStatus(item.checkStatus, diagnosis),
      rawStatus: String(item.checkStatus || 'PENDING').toUpperCase(),
      url: buildSubmissionImageUrl(item.id),
      diagnosis,
      raw: item
    };
  }

  function parseStoredDiagnosis(rawResult) {
    const text = String(rawResult || '').trim();
    if (!text) {
      return {
        hasResult: false,
        result: null,
        hasError: false
      };
    }

    try {
      const result = normalizeDiagnosisResult(text);
      return {
        hasResult: true,
        result,
        hasError: hasErrorInResult(result)
      };
    } catch (_) {
      return {
        hasResult: false,
        result: null,
        hasError: false
      };
    }
  }

  function hasErrorInResult(result) {
    if (!result) {
      return false;
    }
    if (result.status === 'error_found') {
      return true;
    }
    return Array.isArray(result.steps) && result.steps.some((step) => {
      const diffs = Array.isArray(step.matrixCellDiffs) ? step.matrixCellDiffs : [];
      return Boolean(step.isWrong || step.errorMessage || step.explanation || diffs.length);
    });
  }

  function mapWorkStatus(checkStatus, diagnosis) {
    if (diagnosis.hasResult) {
      return diagnosis.hasError ? 'done-error' : 'done';
    }

    const status = String(checkStatus || '').toUpperCase();
    if (status === 'CHECKED') {
      return diagnosis.hasError ? 'done-error' : 'done';
    }
    if (status === 'CHECKING') {
      return 'running';
    }
    if (status === 'FAILED') {
      return 'failed';
    }
    return 'pending';
  }

  function getFilteredStudents() {
    if (isStudentView()) {
      return state.students;
    }

    const keyword = String($('searchInput')?.value || '').trim().toLowerCase();
    if (!keyword) {
      return state.students;
    }

    return state.students.filter((student) => {
      if (student.name.toLowerCase().includes(keyword) || student.id.toLowerCase().includes(keyword)) {
        return true;
      }
      return student.works.some((work) => work.file.toLowerCase().includes(keyword));
    });
  }

  function renderList(list, { message = '当前没有符合条件的学生。' } = {}) {
    const container = $('stuList');
    if (!container) {
      return;
    }

    $('stuTotal').textContent = String(list.length);

    if (!list.length) {
      container.innerHTML = `<li class="student-list__empty">${esc(message)}</li>`;
      return;
    }

    container.innerHTML = list.map((student) => `
      <li
        class="stu-item${student.id === state.selectedStudentId ? ' active' : ''}"
        data-id="${esc(student.id)}"
        onclick="App.pickStu(this.dataset.id)"
      >
        <div class="name">${esc(student.name)}</div>
        <div class="meta">
          <span>${esc(student.cls)}</span>
          <span>${student.works.length} 份</span>
          ${student.pendingCount ? `<span class="badge-pending">${student.pendingCount} 待处理</span>` : ''}
        </div>
        ${student.pendingCount ? '<div class="pending-dot"></div>' : ''}
      </li>
    `).join('');
  }

  function restoreSelection(studentId, workId) {
    const targetStudent = state.students.find((student) => student.id === studentId);
    if (!targetStudent) {
      goBack();
      return;
    }

    pickStu(targetStudent.id, {
      preserveWork: Boolean(workId),
      autoSelectWorkId: workId,
      silentScroll: true
    });
  }

  function getSelectedStudent() {
    return state.students.find((student) => student.id === state.selectedStudentId) || null;
  }

  function getSelectedWork() {
    const student = getSelectedStudent();
    if (!student) {
      return null;
    }
    return student.works.find((work) => work.id === state.selectedWorkId) || null;
  }

  function pickStu(id, options = {}) {
    const student = state.students.find((item) => item.id === String(id));
    if (!student) {
      return;
    }

    state.selectedStudentId = student.id;
    if (!options.preserveWork) {
      state.selectedWorkId = '';
    }

    document.querySelectorAll('.stu-item').forEach((item) => {
      item.classList.toggle('active', item.dataset.id === student.id);
    });

    $('emptyState').classList.add('hidden');
    $('homeworkView').classList.remove('hidden');

    const pendingText = student.pendingCount ? ` · ${student.pendingCount} 份待处理` : '';
    if (isTeacherView()) {
      $('viewStuName').textContent = student.name;
      $('viewMeta').textContent = `${student.cls} · 共 ${student.works.length} 份作业${pendingText}`;
    } else {
      $('viewStuName').textContent = '我的作业';
      $('viewMeta').textContent = `共 ${student.works.length} 份作业${pendingText}`;
    }

    renderGrid(student.works);

    if (options.autoSelectWorkId && student.works.some((work) => work.id === String(options.autoSelectWorkId))) {
      pickImg(options.autoSelectWorkId, { silentScroll: true });
      return;
    }

    state.selectedWorkId = '';
    resetResult();
    updateDetectButton(null);
    setActionTip('请先选择一张作业图片');
  }

  function renderGrid(works = []) {
    const container = $('imgGrid');
    if (!container) {
      return;
    }

    if (!works.length) {
      container.innerHTML = '<p class="no-work">当前学生暂无提交作业。</p>';
      return;
    }

    container.innerHTML = works.map((work) => {
      const status = getStatusMeta(work.status);
      return `
        <div class="img-card${work.id === state.selectedWorkId ? ' selected' : ''}" data-wid="${esc(work.id)}" onclick="App.pickImg(this.dataset.wid)">
          <img src="${esc(work.url)}" alt="${esc(work.file)}" loading="lazy">
          <div class="zoom-btn" data-src="${esc(work.url)}" onclick="event.stopPropagation();App.openLightbox(this.dataset.src)">🔍</div>
          <span class="status-tag ${status.className}">${status.label}</span>
          <div class="overlay">
            <div class="fn">${esc(work.file)}</div>
            <div class="tm">${esc(work.time)}</div>
          </div>
        </div>
      `;
    }).join('');
  }

  function getStatusMeta(status) {
    if (status === 'running') {
      return { label: '检测中', className: 'running' };
    }
    if (status === 'failed') {
      return { label: '失败', className: 'failed' };
    }
    if (status === 'done' || status === 'done-error') {
      return { label: '已批改', className: 'done' };
    }
    return { label: '待检测', className: 'pending' };
  }

  function pickImg(id, { silentScroll = false } = {}) {
    const student = getSelectedStudent();
    if (!student) {
      return;
    }

    const work = student.works.find((item) => item.id === String(id));
    if (!work) {
      return;
    }

    state.selectedWorkId = work.id;

    document.querySelectorAll('.img-card').forEach((item) => {
      item.classList.toggle('selected', item.dataset.wid === work.id);
    });

    updateDetectButton(work);

    if (isStudentView()) {
      resetResult();
      setActionTip(`✅ 已选：${work.file}，可点击图片右上角放大查看。`, 'ok');
      return;
    }

    if (work.diagnosis.hasResult && work.diagnosis.result) {
      renderDiagnosisResult(work.diagnosis.result, {
        imageUrl: work.url,
        tagText: '已从数据库载入诊断结果'
      });
      setActionTip(`✅ 已载入 ${work.file} 的历史诊断，可再次运行 AI 诊断。`, 'ok');
    } else if (work.status === 'running') {
      showTaskWaiting(work);
      setActionTip(`这张作业正在诊断中：${work.file}`, 'ok');
    } else {
      resetResult();
      setActionTip(`✅ 已选：${work.file}`, 'ok');
    }

    if (!silentScroll && !$('resultArea').classList.contains('hidden')) {
      $('resultArea')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }

  function updateDetectButton(work) {
    const button = $('btnDetect');
    if (!button) {
      return;
    }

    const label = button.querySelector('.action-button__label');
    button.classList.remove('is-loading');

    if (isStudentView()) {
      button.disabled = true;
      if (label) {
        label.textContent = '老师端可用';
      }
      return;
    }

    button.disabled = !work || work.status === 'running';

    if (!label) {
      return;
    }

    if (!work) {
      label.textContent = '启动 AI 诊断';
      return;
    }

    if (work.status === 'running') {
      label.textContent = '诊断进行中';
      return;
    }

    label.textContent = work.diagnosis.hasResult ? '重新运行 AI 诊断' : '启动 AI 诊断';
  }

  function setActionTip(message, type = '') {
    const element = $('actionTip');
    if (!element) {
      return;
    }
    element.textContent = message || '';
    element.className = 'action-tip';
    if (type === 'ok') {
      element.classList.add('ok');
    }
    if (type === 'error') {
      element.classList.add('error');
    }
  }

  function showEmptyPanel(title, desc) {
    const titleEl = document.querySelector('.empty-panel__title');
    const descEl = document.querySelector('#emptyState .desc');
    if (titleEl) {
      titleEl.textContent = title;
    }
    if (descEl) {
      descEl.textContent = desc;
    }
  }

  function goBack({ emptyTitle, emptyDesc } = {}) {
    state.selectedStudentId = '';
    state.selectedWorkId = '';
    state.activeSubmissionId = '';
    stopDiagnosisPolling();

    $('homeworkView').classList.add('hidden');
    $('emptyState').classList.remove('hidden');
    document.querySelectorAll('.stu-item').forEach((item) => item.classList.remove('active'));

    updateDetectButton(null);
    resetResult();
    showEmptyPanel(
      emptyTitle || (isTeacherView() ? '先选择左侧的一位学生' : '还没有提交作业'),
      emptyDesc || (isTeacherView()
        ? '进入后可以浏览该学生的作业图片，选择一张作业执行诊断，并查看 OCR、步骤追踪与错误讲解。'
        : '点击右上角“上传作业”按钮，提交图片后这里会自动出现你的作业列表。')
    );
  }

  function triggerUpload() {
    if (!isStudentView()) {
      return;
    }
    $('uploadInput')?.click();
  }

  async function uploadFiles(files = []) {
    if (!isStudentView()) {
      return;
    }

    const imageFiles = files.filter(isImageFile);
    if (!imageFiles.length) {
      if (state.students.length) {
        setActionTip('请选择图片格式的作业文件，例如 JPG、PNG、WEBP。', 'error');
      } else {
        showEmptyPanel('请选择图片文件', '目前仅支持上传图片格式的作业文件，例如 JPG、PNG、WEBP。');
      }
      return;
    }

    const headerUploadBtn = $('headerUploadBtn');
    const emptyUploadBtn = $('emptyUploadBtn');
    setButtonBusy(headerUploadBtn, true, '上传中');
    setButtonBusy(emptyUploadBtn, true, '上传中');

    if (state.students.length) {
      setActionTip(`正在上传 ${imageFiles.length} 张作业图片...`);
    } else {
      showEmptyPanel('正在上传作业...', `共 ${imageFiles.length} 张图片，上传完成后会自动出现在你的作业列表中。`);
    }

    let successCount = 0;
    const failures = [];

    try {
      for (const file of imageFiles) {
        try {
          await createSubmission({ file });
          successCount += 1;
        } catch (error) {
          failures.push(`${file.name}：${error.message || '上传失败'}`);
        }
      }

      if (successCount > 0) {
        await refreshSubmissionData();
      }

      if (successCount > 0 && failures.length === 0) {
        setActionTip(`✅ 已上传 ${successCount} 张作业图片。`, 'ok');
        return;
      }

      if (successCount > 0) {
        setActionTip(`✅ 已上传 ${successCount} 张，另有 ${failures.length} 张失败。`, 'error');
        return;
      }

      if (state.students.length) {
        setActionTip(failures[0] || '当前无法完成上传，请稍后重试。', 'error');
      } else {
        showEmptyPanel('上传失败', failures[0] || '当前无法完成上传，请稍后重试。');
      }
    } finally {
      setButtonBusy(headerUploadBtn, false);
      setButtonBusy(emptyUploadBtn, false);
    }
  }

  function isImageFile(file) {
    if (!file) {
      return false;
    }

    if (String(file.type || '').startsWith('image/')) {
      return true;
    }

    return /\.(png|jpe?g|webp|bmp|gif)$/i.test(String(file.name || ''));
  }

  function resetResult() {
    $('resultArea').classList.add('hidden');
    $('ocrBlock').classList.add('hidden');
    $('traceBlock').classList.add('hidden');
    $('explainBlock').classList.add('hidden');
    $('errorBoxes').innerHTML = '';
    $('previewImg').removeAttribute('src');
    resetPipeline();
  }

  function showTaskWaiting(work) {
    $('resultArea').classList.remove('hidden');
    $('previewImg').src = work.url;
    $('errorBoxes').innerHTML = '';
    $('ocrBlock').classList.add('hidden');
    $('traceBlock').classList.add('hidden');
    $('explainBlock').classList.add('hidden');
    resetPipeline();
    tag('诊断进行中');
    setPipeState(1, 'active');
  }

  async function runDetection() {
    if (!isTeacherView()) {
      setActionTip('当前账号不能发起 AI 诊断。', 'error');
      return;
    }

    const work = getSelectedWork();
    if (!work) {
      setActionTip('请先选择一张作业图片。', 'error');
      return;
    }

    const button = $('btnDetect');
    setButtonBusy(button, true, '正在诊断');
    setHeaderActionState('diagnosis');
    stopDiagnosisPolling();
    state.activeSubmissionId = work.id;

    $('resultArea').classList.remove('hidden');
    $('previewImg').src = work.url;
    $('errorBoxes').innerHTML = '';
    resetPipeline();
    tag('正在创建诊断任务');
    $('resultArea').scrollIntoView({ behavior: 'smooth', block: 'start' });

    try {
      const task = await createSubmissionDiagnosisTask(work.submissionId, {
        isSocratic: false,
        problemType: 'matrix'
      });

      const finished = handleTaskSnapshot(task, work);
      if (finished) {
        setButtonBusy(button, false);
        state.activeSubmissionId = '';
        await refreshSubmissionData({ preserveSelection: true });
        return;
      }

      startDiagnosisPolling(task?.taskId, work);
    } catch (error) {
      showTaskFailure(error.message || '诊断失败，请稍后重试。');
      setButtonBusy(button, false);
      state.activeSubmissionId = '';
      await refreshSubmissionData({ preserveSelection: true });
    }
  }

  function startDiagnosisPolling(taskId, work) {
    if (!taskId) {
      showTaskFailure('诊断任务创建失败。');
      setButtonBusy($('btnDetect'), false);
      state.activeSubmissionId = '';
      return;
    }

    stopDiagnosisPolling();
    state.activeTaskId = taskId;
    state.pollToken += 1;
    const token = state.pollToken;

    state.pollTimer = window.setTimeout(() => pollDiagnosisTask(taskId, work, token), 900);
  }

  async function pollDiagnosisTask(taskId, work, token) {
    try {
      const task = await fetchDiagnosisTask(taskId);
      if (token !== state.pollToken) {
        return;
      }

      const finished = handleTaskSnapshot(task, work);
      if (finished) {
        stopDiagnosisPolling();
        setButtonBusy($('btnDetect'), false);
        state.activeSubmissionId = '';
        await refreshSubmissionData({ preserveSelection: true });
        return;
      }

      state.pollTimer = window.setTimeout(() => pollDiagnosisTask(taskId, work, token), 1100);
    } catch (error) {
      if (token !== state.pollToken) {
        return;
      }
      stopDiagnosisPolling();
      showTaskFailure(error.message || '诊断进度获取失败，请稍后重试。');
      setButtonBusy($('btnDetect'), false);
      state.activeSubmissionId = '';
      await refreshSubmissionData({ preserveSelection: true });
    }
  }

  function stopDiagnosisPolling() {
    if (state.pollTimer) {
      window.clearTimeout(state.pollTimer);
    }
    state.pollTimer = 0;
    state.activeTaskId = '';
    state.pollToken += 1;
  }

  function handleTaskSnapshot(task, work) {
    if (!task) {
      return false;
    }

    updatePipelineFromTask(task);
    const preview = normalizeDiagnosisPreview(task.partialResult || {}, task);
    const activeWork = getSelectedWork();
    const shouldRenderPreview = activeWork && work && activeWork.id === work.id;

    if (shouldRenderPreview) {
      if (work.url) {
        $('previewImg').src = work.url;
      }
      if (preview.steps?.length) {
        showTrace(preview.steps);
      }
      if (buildOcrFragments(preview).length) {
        showOCR(buildOcrFragments(preview));
      }
      if (preview.imageHighlights?.length) {
        showBoxes(preview.imageHighlights);
      }
    }

    tag(task.stageMessage || preview.stageMessage || mapTaskStatusText(task.status));

    if (task.status === 'done' && task.finalResult) {
      const result = normalizeDiagnosisResult(task.finalResult);
      if (shouldRenderPreview) {
        renderDiagnosisResult(result, {
          imageUrl: work?.url,
          tagText: '诊断完成，结果已写入数据库'
        });
      }
      setActionTip(`✅ ${work?.file || '当前作业'} 诊断完成。`, 'ok');
      return true;
    }

    if (task.status === 'failed') {
      showTaskFailure(task.errorMessage || '诊断失败，请稍后重试。');
      return true;
    }

    return false;
  }

  function updatePipelineFromTask(task) {
    if (!task) {
      resetPipeline();
      return;
    }

    if (task.status === 'done') {
      document.querySelectorAll('.pipe-step').forEach((item) => {
        item.classList.remove('active', 'fail');
        item.classList.add('done');
      });
      return;
    }

    const progress = Number(task.progress || 0);
    let activeStep = 1;
    if (progress >= 75) {
      activeStep = 4;
    } else if (progress >= 50) {
      activeStep = 3;
    } else if (progress >= 20) {
      activeStep = 2;
    }

    document.querySelectorAll('.pipe-step').forEach((item) => {
      const currentStep = Number(item.dataset.n || 0);
      item.classList.remove('active', 'done', 'fail');

      if (task.status === 'failed' && currentStep === activeStep) {
        item.classList.add('fail');
        return;
      }

      if (currentStep < activeStep) {
        item.classList.add('done');
        return;
      }

      if (currentStep === activeStep) {
        item.classList.add('active');
      }
    });
  }

  function resetPipeline() {
    document.querySelectorAll('.pipe-step').forEach((item) => {
      item.classList.remove('done', 'active', 'fail');
    });
    setPipeState(1, 'active');
    tag('等待');
  }

  function setPipeState(stepNo, stateName) {
    const step = document.querySelector(`.pipe-step[data-n="${stepNo}"]`);
    if (!step) {
      return;
    }
    step.classList.remove('done', 'active', 'fail');
    step.classList.add(stateName);
  }

  function mapTaskStatusText(status) {
    if (status === 'queued') {
      return '已进入队列';
    }
    if (status === 'reasoning') {
      return 'AI 正在分析';
    }
    if (status === 'done') {
      return '诊断完成';
    }
    if (status === 'failed') {
      return '诊断失败';
    }
    return '处理中';
  }

  function showTaskFailure(message) {
    updatePipelineFromTask({ status: 'failed', progress: 100 });
    tag(message || '诊断失败', false, true);
    setActionTip(message || '诊断失败，请稍后重试。', 'error');
  }

  function renderDiagnosisResult(result, { imageUrl = '', tagText = '' } = {}) {
    $('resultArea').classList.remove('hidden');
    if (imageUrl) {
      $('previewImg').src = imageUrl;
    }

    const fragments = buildOcrFragments(result);
    showOCR(fragments);
    showTrace(result.steps || []);
    showExplain(result);
    showBoxes(result.imageHighlights || []);
    document.querySelectorAll('.pipe-step').forEach((item) => {
      item.classList.remove('active', 'fail');
      item.classList.add('done');
    });
    tag(tagText || mapDiagnosisStatus(result.status), true, false);
  }

  function buildOcrFragments(result = {}) {
    const fragments = [];

    if (result.problemText) {
      fragments.push({ label: '题目摘要', text: result.problemText });
    }

    if (Array.isArray(result.matrixExpressions) && result.matrixExpressions.length) {
      fragments.push({
        label: '矩阵表达式',
        text: result.matrixExpressions.join('\n')
      });
    }

    if (Array.isArray(result.studentSteps) && result.studentSteps.length) {
      fragments.push({
        label: '学生步骤',
        text: result.studentSteps.join('\n')
      });
    }

    if (result.feedback) {
      fragments.push({ label: '诊断摘要', text: result.feedback });
    }

    if (result.errorIndex) {
      fragments.push({ label: '错误步骤', text: `第 ${result.errorIndex} 步` });
    }

    if (Array.isArray(result.tags) && result.tags.length) {
      fragments.push({ label: '标签', text: result.tags.join(' / ') });
    }

    if (result.mathData && typeof result.mathData === 'object') {
      Object.entries(result.mathData).slice(0, 2).forEach(([key, value]) => {
        fragments.push({
          label: key,
          text: typeof value === 'string' ? value : JSON.stringify(value, null, 2)
        });
      });
    }

    if (!fragments.length) {
      fragments.push({
        label: '诊断状态',
        text: mapDiagnosisStatus(result.status)
      });
    }

    return fragments;
  }

  function showOCR(fragments) {
    const block = $('ocrBlock');
    if (!block) {
      return;
    }

    $('ocrContent').innerHTML = fragments.map((fragment) => `
      <div class="ocr-item">
        <span class="ocr-tag">${esc(fragment.label)}</span>
        <div class="ocr-code">${esc(fragment.text)}</div>
      </div>
    `).join('');

    block.classList.remove('hidden');
  }

  function showTrace(steps = []) {
    const block = $('traceBlock');
    if (!block) {
      return;
    }

    if (!steps.length) {
      $('traceCards').innerHTML = '<div class="student-list__empty">当前结果没有返回可拆分的详细步骤。</div>';
      block.classList.remove('hidden');
      return;
    }

    $('traceCards').innerHTML = steps.map((step, index) => {
      const isWrong = Boolean(step.isWrong || step.errorMessage || (step.matrixCellDiffs || []).length);
      const open = isWrong ? ' open' : '';
      const cls = isWrong ? 'wrong' : 'correct';
      const icon = isWrong ? '❌' : '✅';
      const formulaText = step.highlightedLatex || step.latex || step.content || '暂无公式内容';
      const noteText = step.explanation || step.errorMessage || '';
      const diffLines = Array.isArray(step.matrixCellDiffs) ? step.matrixCellDiffs : [];

      let body = `<div class="formula-box">${esc(formulaText)}</div>`;
      if (noteText) {
        body += `<div class="step-note"><strong>📝</strong> ${renderText(noteText)}</div>`;
      }
      if (diffLines.length) {
        body += `<div class="correct-answer-box">
          <div class="ca-label">✅ 正确结果提示</div>
          <div class="formula-box">${diffLines.map((diff) => `${diff.row || '-'}行${diff.col || '-'}列：应为 ${diff.expected || '-'}，写成了 ${diff.actual || '-'}`).join('\n')}</div>
        </div>`;
      }

      return `
        <div class="step-card ${cls}${open}">
          <div class="step-top" onclick="this.parentElement.classList.toggle('open')">
            <div class="step-left">
              <div class="step-num">${step.stepNo || index + 1}</div>
              <div class="step-name">${esc(step.title || `步骤 ${index + 1}`)}</div>
            </div>
            <div class="step-icon">${icon}</div>
          </div>
          <div class="step-body">${body}</div>
        </div>
      `;
    }).join('');

    block.classList.remove('hidden');
  }

  function showExplain(result = {}) {
    const block = $('explainBlock');
    if (!block) {
      return;
    }

    const items = [];
    const steps = Array.isArray(result.steps) ? result.steps : [];

    steps.forEach((step, index) => {
      const diffs = Array.isArray(step.matrixCellDiffs) ? step.matrixCellDiffs : [];
      if (diffs.length) {
        diffs.forEach((diff) => {
          items.push({
            title: `步骤 ${step.stepNo || index + 1} · ${step.title || '错误定位'}`,
            loc: `第 ${diff.row || '-'} 行 · 第 ${diff.col || '-'} 列`,
            wrote: diff.actual || '-',
            correct: diff.expected || '-',
            explain: diff.reason || step.explanation || step.errorMessage || result.feedback || '系统检测到该位置与标准答案不一致。',
            suggest: '建议按列逐项核对，确认每一步行变换或代数运算只作用在目标对象上。'
          });
        });
        return;
      }

      if (step.isWrong || step.errorMessage || step.explanation) {
        items.push({
          title: `步骤 ${step.stepNo || index + 1} · ${step.title || '错误定位'}`,
          loc: result.errorIndex ? `第 ${result.errorIndex} 步附近` : '当前步骤',
          wrote: step.content || step.highlightedLatex || step.latex || '学生当前写法',
          correct: result.feedback || '请参考系统诊断建议',
          explain: step.errorMessage || step.explanation || result.feedback || '系统检测到该步骤存在风险。',
          suggest: '建议对照上一步结果重新演算，并检查符号、行列位置和每一列的运算是否完整。'
        });
      }
    });

    if (!items.length && result.feedback && result.status !== 'correct') {
      items.push({
        title: '系统诊断摘要',
        loc: result.errorIndex ? `第 ${result.errorIndex} 步` : '待人工复核',
        wrote: '请查看系统摘要',
        correct: '请结合教师讲评进一步确认',
        explain: result.feedback,
        suggest: '建议结合原题和作答图片一起回看，必要时重新发起诊断。'
      });
    }

    if (!items.length) {
      $('explainCards').innerHTML = `
        <div class="all-correct">
          <div class="big">🎉</div>
          <div class="title">当前未发现明显错误</div>
          <div class="sub">这张作业的可识别步骤没有被系统标记为错误。</div>
        </div>
      `;
      block.classList.remove('hidden');
      return;
    }

    $('explainCards').innerHTML = items.map((item) => `
      <div class="err-card">
        <div class="err-card-title">
          <div class="err-icon-circle">⚠️</div>
          ${esc(item.title)}
        </div>

        <div class="err-grid">
          <div class="err-cell loc">
            <div class="cell-label">错误位置</div>
            <div class="cell-value">${esc(item.loc)}</div>
          </div>
          <div class="err-cell">
            <div class="cell-label">所在步骤</div>
            <div class="cell-value">${esc(item.title)}</div>
          </div>
          <div class="err-cell wrong">
            <div class="cell-label">学生写的</div>
            <div class="cell-value">${esc(item.wrote)}</div>
          </div>
          <div class="err-cell right">
            <div class="cell-label">建议参考</div>
            <div class="cell-value">${esc(item.correct)}</div>
          </div>
        </div>

        <div class="err-explain">
          <div class="ex-label">错误分析</div>
          <p>${renderText(item.explain)}</p>
        </div>

        <div class="err-suggest">
          <div class="sug-icon">🎯</div>
          <div class="sug-text">${renderText(item.suggest)}</div>
        </div>
      </div>
    `).join('');

    block.classList.remove('hidden');
  }

  function showBoxes(highlights = []) {
    const wrap = $('errorBoxes');
    if (!wrap) {
      return;
    }

    wrap.innerHTML = '';
    highlights.forEach((box, index) => {
      const left = toPercent(box.x);
      const top = toPercent(box.y);
      const width = toPercent(box.width);
      const height = toPercent(box.height);

      if (width <= 0 || height <= 0) {
        return;
      }

      const item = document.createElement('div');
      item.className = 'err-box';
      item.style.left = `${left}%`;
      item.style.top = `${top}%`;
      item.style.width = `${width}%`;
      item.style.height = `${height}%`;
      item.innerHTML = `<div class="err-label">⚠ ${esc(box.label || `错误点 ${index + 1}`)}</div>`;
      item.addEventListener('click', (event) => {
        event.stopPropagation();
        $('explainBlock')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      });
      wrap.appendChild(item);
    });
  }

  function toPercent(value) {
    const numeric = Number(value || 0);
    if (!Number.isFinite(numeric)) {
      return 0;
    }
    return numeric <= 1 ? numeric * 100 : numeric;
  }

  function tag(text, success = false, fail = false) {
    const element = $('pipeTag');
    if (!element) {
      return;
    }
    element.textContent = text || '等待';
    element.style.background = success ? 'var(--ok-bg)' : fail ? 'var(--err-bg)' : '';
    element.style.color = success ? 'var(--ok)' : fail ? 'var(--err)' : '';
  }

  function mapDiagnosisStatus(status) {
    if (status === 'error_found') {
      return '已定位到错误';
    }
    if (status === 'correct') {
      return '未发现明显错误';
    }
    if (status === 'unable_to_judge') {
      return '需要人工复核';
    }
    return status ? String(status).replaceAll('_', ' ') : '诊断完成';
  }

  function setButtonBusy(button, busy, busyLabel = '') {
    if (!button) {
      return;
    }

    const label = button.querySelector('.action-button__label');
    if (label && !button.dataset.idleLabel) {
      button.dataset.idleLabel = label.textContent;
    }

    if (busy) {
      button.classList.add('is-loading');
      button.disabled = true;
      if (label && busyLabel) {
        label.textContent = busyLabel;
      }
      return;
    }

    button.classList.remove('is-loading');
    if (label && button.dataset.idleLabel) {
      label.textContent = button.dataset.idleLabel;
    }

    if (button.id === 'btnDetect') {
      updateDetectButton(getSelectedWork());
      return;
    }

    button.disabled = false;
  }

  function setHeaderActionState(name) {
    document.querySelectorAll('[data-review-tab]').forEach((button) => {
      const active = button.dataset.reviewTab === name;
      button.classList.toggle('action-button--secondary', active);
      button.classList.toggle('action-button--ghost', !active);
      button.classList.toggle('is-active', active);
    });
  }

  function focusDiagnosis() {
    if (state.drawer && !state.drawer.drawer.classList.contains('hidden')) {
      state.drawer.close();
    } else {
      setHeaderActionState('diagnosis');
    }
  }

  async function openNotebook() {
    const button = $('openNotebookBtn');
    setHeaderActionState('notebook');
    setButtonBusy(button, true, '加载中');

    try {
      const historyResponse = await fetchHistory();
      state.drawer.openNotebook(historyResponse);
    } catch (error) {
      $('drawerBody').innerHTML = `<div class="student-list__empty">${esc(error.message || '错题本加载失败，请稍后重试。')}</div>`;
      state.drawer.show();
    } finally {
      setButtonBusy(button, false);
    }
  }

  async function openStats() {
    const button = $('openStatsBtn');
    setHeaderActionState('stats');
    setButtonBusy(button, true, '加载中');

    try {
      const summaryResponse = await fetchDashboardSummary();
      state.drawer.openStats(summaryResponse);
    } catch (error) {
      $('drawerBody').innerHTML = `<div class="student-list__empty">${esc(error.message || '统计面板加载失败，请稍后重试。')}</div>`;
      state.drawer.show();
    } finally {
      setButtonBusy(button, false);
    }
  }

  async function logout() {
    const button = $('logoutBtn');
    setButtonBusy(button, true, '退出中');
    try {
      await logoutUser();
    } finally {
      window.location.href = '/login.html';
    }
  }

  function openLightbox(src) {
    const resolvedSrc = src || $('previewImg')?.getAttribute('src');
    if (!resolvedSrc) {
      return;
    }

    $('lbImg').src = resolvedSrc;
    $('lightbox').classList.add('open');
    document.body.style.overflow = 'hidden';
  }

  function closeLightbox(event) {
    if (event && event.target && event.target.tagName === 'IMG') {
      return;
    }

    $('lightbox').classList.remove('open');
    document.body.style.overflow = '';
  }

  function formatTime(timestamp) {
    if (!timestamp) {
      return '-';
    }

    const date = new Date(timestamp);
    if (Number.isNaN(date.getTime())) {
      return '-';
    }

    return date.toLocaleString('zh-CN', { hour12: false });
  }

  function parseTime(timestamp) {
    const time = new Date(timestamp || 0).getTime();
    return Number.isFinite(time) ? time : 0;
  }

  function esc(value) {
    const element = document.createElement('div');
    element.textContent = value ?? '';
    return element.innerHTML;
  }

  function renderText(value) {
    return esc(value).replace(/\n/g, '<br>');
  }

  return {
    init,
    pickStu,
    pickImg,
    goBack,
    runDetection,
    triggerUpload,
    focusDiagnosis,
    openNotebook,
    openStats,
    logout,
    openLightbox,
    closeLightbox
  };
})();

window.App = App;

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', () => App.init());
} else {
  App.init();
}
