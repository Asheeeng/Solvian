const SESSION_KEY = 'copilot_auth_session';

function getSession() {
  const raw = localStorage.getItem(SESSION_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw);
  } catch (e) {
    return null;
  }
}

async function requestJson(url, options = {}) {
  const {
    method = 'GET',
    body,
    auth = true,
    headers = {}
  } = options;

  const finalHeaders = { ...headers };
  if (auth) {
    const session = getSession();
    if (session?.token) {
      finalHeaders['X-Auth-Token'] = session.token;
    }
  }

  const isFormData = body instanceof FormData;
  if (body && !isFormData) {
    finalHeaders['Content-Type'] = 'application/json';
  }

  const response = await fetch(url, {
    method,
    headers: finalHeaders,
    body: body ? (isFormData ? body : JSON.stringify(body)) : undefined
  });

  const contentType = response.headers.get('content-type') || '';
  const isJson = contentType.includes('application/json');
  const data = isJson ? await response.json() : null;

  if (!response.ok) {
    throw new Error(data?.message || `请求失败（${response.status}）`);
  }

  return data;
}

export function setCurrentUser(session) {
  localStorage.setItem(SESSION_KEY, JSON.stringify(session));
}

export function getCurrentUser() {
  return getSession();
}

export function clearCurrentUser() {
  localStorage.removeItem(SESSION_KEY);
}

export async function registerUser({ username, password, role }) {
  try {
    const data = await requestJson('/api/auth/register', {
      method: 'POST',
      auth: false,
      body: { username, password, role }
    });

    return {
      ok: Boolean(data?.success),
      message: data?.message || '',
      user: data?.user || null
    };
  } catch (error) {
    return {
      ok: false,
      message: error.message || '注册失败'
    };
  }
}

export async function loginUser({ username, password, role }) {
  try {
    const data = await requestJson('/api/auth/login', {
      method: 'POST',
      auth: false,
      body: { username, password, role }
    });

    if (!data?.success) {
      return {
        ok: false,
        message: data?.message || '登录失败'
      };
    }

    const session = {
      token: data.token,
      user: data.user,
      loginAt: Date.now()
    };
    setCurrentUser(session);

    return {
      ok: true,
      message: data.message,
      session
    };
  } catch (error) {
    return {
      ok: false,
      message: error.message || '登录失败'
    };
  }
}

export async function logoutUser() {
  try {
    await requestJson('/api/auth/logout', { method: 'POST', auth: true });
  } catch (error) {
    // 退出失败不阻塞本地清理。
  } finally {
    clearCurrentUser();
  }
}

export async function evaluateProblem({ file, isSocratic }) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('isSocratic', String(Boolean(isSocratic)));

  return requestJson('/api/evaluate', {
    method: 'POST',
    auth: true,
    body: formData
  });
}

export async function logEvent(payload) {
  try {
    return await requestJson('/api/log-event', {
      method: 'POST',
      auth: true,
      body: payload
    });
  } catch (error) {
    return {
      success: false,
      message: error.message
    };
  }
}

export async function submitAiFeedback(payload) {
  return requestJson('/api/ai-feedback', {
    method: 'POST',
    auth: true,
    body: payload
  });
}

export async function fetchHistory() {
  return requestJson('/api/history', { method: 'GET', auth: true });
}

export async function fetchDashboardSummary() {
  return requestJson('/api/dashboard-summary', { method: 'GET', auth: true });
}

export async function downloadPdfReport(recordId) {
  const session = getSession();
  const response = await fetch(`/api/report/${recordId}/pdf`, {
    method: 'GET',
    headers: {
      'X-Auth-Token': session?.token || ''
    }
  });

  if (!response.ok) {
    throw new Error(`报告下载失败（${response.status}）`);
  }

  const blob = await response.blob();
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `diagnosis-report-${recordId}.pdf`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
}
