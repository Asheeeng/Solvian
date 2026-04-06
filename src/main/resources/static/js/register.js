import { requireGuest } from './common/auth-guard.js';
import { registerUser } from './common/storage.js';
import { initAuthScene } from './modules/auth-scene.js?v=20260407b';
import {
  initCaptcha,
  initPasswordField,
  initRoleSwitch,
  initThemeToggle
} from './modules/auth-enhancements.js?v=20260407b';

if (requireGuest()) {
  const root = document.body;
  const form = document.getElementById('registerForm');
  const usernameInput = document.getElementById('username');
  const passwordInput = document.getElementById('password');
  const confirmPasswordInput = document.getElementById('confirmPassword');
  const captchaInput = document.getElementById('registerCaptchaInput');
  const roleInput = document.getElementById('role');
  const errorText = document.getElementById('registerError');
  const successText = document.getElementById('registerSuccess');
  const submitButton = document.getElementById('registerSubmit');
  const themeToggle = document.getElementById('themeToggle');
  const roleButtons = Array.from(document.querySelectorAll('.auth-role-chip'));
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
    canvas: document.getElementById('registerCaptchaCanvas'),
    input: document.getElementById('registerCaptchaInput'),
    refreshButton: document.getElementById('registerCaptchaRefresh'),
    getTheme: () => themeController.getTheme()
  });

  const roleController = initRoleSwitch({
    input: roleInput,
    buttons: roleButtons
  });

  function syncSceneState() {
    const passwordFields = [passwordInput, confirmPasswordInput];
    sceneController.setBehaviorState({
      peeking: passwordFields.some((input) => document.activeElement === input && input.type === 'password'),
      privacy: passwordFields.some((input) => input.type === 'text')
    });
  }

  const passwordController = initPasswordField({
    input: passwordInput,
    toggle: document.getElementById('passwordToggle'),
    onStateChange: syncSceneState
  });

  const confirmPasswordController = initPasswordField({
    input: confirmPasswordInput,
    toggle: document.getElementById('confirmPasswordToggle'),
    onStateChange: syncSceneState
  });

  [usernameInput, passwordInput, confirmPasswordInput, captchaInput].forEach((input) => {
    input.addEventListener('input', () => {
      errorText.textContent = '';
      successText.textContent = '';
    });
    input.addEventListener('focus', syncSceneState);
    input.addEventListener('blur', syncSceneState);
  });

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!form.reportValidity()) {
      return;
    }

    errorText.textContent = '';
    successText.textContent = '';

    const password = passwordInput.value;
    const confirmPassword = confirmPasswordInput.value;

    if (password !== confirmPassword) {
      errorText.textContent = '两次输入的密码不一致。';
      return;
    }

    const captchaValidation = captchaController.validate();
    if (!captchaValidation.ok) {
      errorText.textContent = captchaValidation.message;
      return;
    }

    submitButton.disabled = true;
    submitButton.textContent = '创建中...';

    try {
      const result = await registerUser({
        username: usernameInput.value.trim(),
        password,
        role: roleInput.value
      });

      if (!result.ok) {
        errorText.textContent = result.message || '注册失败';
        captchaController.refresh();
        return;
      }

      successText.textContent = '注册成功，正在跳转登录页...';
      form.reset();
      roleController.setRole('STUDENT');
      passwordController.setVisible(false);
      confirmPasswordController.setVisible(false);
      syncSceneState();
      captchaController.refresh();

      setTimeout(() => {
        window.location.href = '/login.html';
      }, 900);
    } finally {
      submitButton.disabled = false;
      submitButton.textContent = '创建账户';
    }
  });
}
