import { requireGuest } from './common/auth-guard.js';
import { loginUser } from './common/storage.js';
import { initLoginScene } from './modules/login-scene.js';

if (requireGuest()) {
  const form = document.getElementById('loginForm');
  const usernameInput = document.getElementById('username');
  const passwordInput = document.getElementById('password');
  const roleInput = document.getElementById('role');
  const errorText = document.getElementById('loginError');
  const noticeText = document.getElementById('loginNotice');
  const submitButton = document.getElementById('loginSubmit');
  const passwordToggle = document.getElementById('passwordToggle');
  const thirdPartyLoginButton = document.getElementById('thirdPartyLoginBtn');
  const forgotPasswordButton = document.getElementById('forgotPasswordBtn');
  const roleButtons = Array.from(document.querySelectorAll('.role-chip'));
  const sceneController = initLoginScene({
    scene: document.getElementById('loginScene')
  });

  let noticeTimer = null;

  function setNotice(message = '') {
    if (noticeTimer) {
      clearTimeout(noticeTimer);
      noticeTimer = null;
    }

    noticeText.textContent = message;

    if (message) {
      noticeTimer = setTimeout(() => {
        noticeText.textContent = '';
      }, 2600);
    }
  }

  function syncSceneState() {
    sceneController.setBehaviorState({
      peeking: document.activeElement === passwordInput && passwordInput.type === 'password',
      privacy: passwordInput.type === 'text'
    });
  }

  function setPasswordVisibility(visible) {
    passwordInput.type = visible ? 'text' : 'password';
    passwordToggle.classList.toggle('is-active', visible);
    passwordToggle.setAttribute('aria-pressed', String(visible));
    passwordToggle.setAttribute('aria-label', visible ? '隐藏密码' : '显示密码');
    syncSceneState();
  }

  function setRole(role) {
    roleInput.value = role;
    roleButtons.forEach((button) => {
      const isActive = button.dataset.role === role;
      button.classList.toggle('is-active', isActive);
      button.setAttribute('aria-checked', String(isActive));
      button.tabIndex = isActive ? 0 : -1;
    });
  }

  function moveRoleFocus(offset) {
    const currentIndex = roleButtons.findIndex((button) => button.classList.contains('is-active'));
    const safeIndex = currentIndex >= 0 ? currentIndex : 0;
    const nextIndex = (safeIndex + offset + roleButtons.length) % roleButtons.length;
    const nextButton = roleButtons[nextIndex];
    setRole(nextButton.dataset.role);
    nextButton.focus();
  }

  roleButtons.forEach((button) => {
    button.addEventListener('click', () => {
      setRole(button.dataset.role);
    });

    button.addEventListener('keydown', (event) => {
      if (event.key === 'ArrowRight' || event.key === 'ArrowDown') {
        event.preventDefault();
        moveRoleFocus(1);
      }
      if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') {
        event.preventDefault();
        moveRoleFocus(-1);
      }
    });
  });

  passwordToggle.addEventListener('click', () => {
    setPasswordVisibility(passwordInput.type === 'password');
    passwordInput.focus({ preventScroll: true });
  });

  passwordInput.addEventListener('focus', syncSceneState);
  passwordInput.addEventListener('blur', syncSceneState);
  usernameInput.addEventListener('focus', syncSceneState);
  usernameInput.addEventListener('blur', syncSceneState);

  thirdPartyLoginButton.addEventListener('click', () => {
    setNotice('企业微信登录暂未开放，请先使用账号密码登录。');
  });

  forgotPasswordButton.addEventListener('click', () => {
    setNotice('忘记密码功能暂未开放，请联系管理员协助处理。');
  });

  [usernameInput, passwordInput].forEach((input) => {
    input.addEventListener('input', () => {
      errorText.textContent = '';
    });
  });

  setRole(roleInput.value || 'STUDENT');
  setPasswordVisibility(false);

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!form.reportValidity()) {
      return;
    }

    const username = usernameInput.value.trim();
    const password = passwordInput.value;
    const role = roleInput.value;

    errorText.textContent = '';
    setNotice('');
    submitButton.disabled = true;
    submitButton.textContent = '登录中...';

    try {
      const result = await loginUser({ username, password, role });
      if (!result.ok) {
        errorText.textContent = result.message || '登录失败';
        return;
      }

      window.location.href = '/home.html';
    } finally {
      submitButton.disabled = false;
      submitButton.textContent = '登录';
    }
  });
}
