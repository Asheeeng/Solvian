import { requireGuest } from './common/auth-guard.js';
import { loginUser } from './common/storage.js';
import { initAuthScene } from './modules/auth-scene.js?v=20260407b';
import {
  initCaptcha,
  initPasswordField,
  initRoleSwitch,
  initThemeToggle
} from './modules/auth-enhancements.js?v=20260407b';

if (requireGuest()) {
  const root = document.body;
  const form = document.getElementById('loginForm');
  const usernameInput = document.getElementById('username');
  const passwordInput = document.getElementById('password');
  const captchaInput = document.getElementById('loginCaptchaInput');
  const roleInput = document.getElementById('role');
  const errorText = document.getElementById('loginError');
  const noticeText = document.getElementById('loginNotice');
  const submitButton = document.getElementById('loginSubmit');
  const thirdPartyLoginButton = document.getElementById('thirdPartyLoginBtn');
  const forgotPasswordButton = document.getElementById('forgotPasswordBtn');
  const themeToggle = document.getElementById('themeToggle');
  const roleButtons = Array.from(document.querySelectorAll('.auth-role-chip'));

  let noticeTimer = null;
  let captchaController;

  const sceneController = initAuthScene({
    scene: document.getElementById('authScene')
  });

  const themeController = initThemeToggle({
    root,
    toggle: themeToggle,
    onChange() {
      if (captchaController) {
        captchaController.redraw();
      }
    }
  });

  captchaController = initCaptcha({
    canvas: document.getElementById('loginCaptchaCanvas'),
    input: document.getElementById('loginCaptchaInput'),
    refreshButton: document.getElementById('loginCaptchaRefresh'),
    getTheme: () => themeController.getTheme()
  });

  initRoleSwitch({
    input: roleInput,
    buttons: roleButtons
  });

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

  initPasswordField({
    input: passwordInput,
    toggle: document.getElementById('passwordToggle'),
    onStateChange: syncSceneState
  });

  [usernameInput, passwordInput, captchaInput].forEach((input) => {
    input.addEventListener('input', () => {
      errorText.textContent = '';
    });
    input.addEventListener('focus', syncSceneState);
    input.addEventListener('blur', syncSceneState);
  });

  forgotPasswordButton.addEventListener('click', () => {
    setNotice('忘记密码功能暂未开放，后续会接入正式找回流程。');
  });

  thirdPartyLoginButton.addEventListener('click', () => {
    setNotice('企业微信登录暂未开放，请先使用账号密码登录。');
  });

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!form.reportValidity()) {
      return;
    }

    const captchaValidation = captchaController.validate();
    if (!captchaValidation.ok) {
      errorText.textContent = captchaValidation.message;
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
        captchaController.refresh();
        return;
      }

      window.location.href = '/home.html';
    } finally {
      submitButton.disabled = false;
      submitButton.textContent = '登录';
    }
  });
}
