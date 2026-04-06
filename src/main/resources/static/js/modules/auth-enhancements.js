const THEME_STORAGE_KEY = 'solvian_auth_theme';
const CAPTCHA_CHARS = '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';

function resolveTheme(theme) {
  return theme === 'dark' ? 'dark' : 'light';
}

function generateCaptchaCode(length = 4) {
  let code = '';
  for (let index = 0; index < length; index += 1) {
    const randomIndex = Math.floor(Math.random() * CAPTCHA_CHARS.length);
    code += CAPTCHA_CHARS[randomIndex];
  }
  return code;
}

function drawCaptcha(canvas, code, theme) {
  const context = canvas.getContext('2d');
  if (!context) {
    return;
  }

  const isDark = theme === 'dark';
  const width = canvas.width;
  const height = canvas.height;

  context.clearRect(0, 0, width, height);

  const gradient = context.createLinearGradient(0, 0, width, height);
  if (isDark) {
    gradient.addColorStop(0, '#0b1320');
    gradient.addColorStop(1, '#172433');
  } else {
    gradient.addColorStop(0, '#fff8e6');
    gradient.addColorStop(1, '#eef4ff');
  }
  context.fillStyle = gradient;
  context.fillRect(0, 0, width, height);

  for (let index = 0; index < 5; index += 1) {
    context.strokeStyle = isDark
      ? `rgba(110, 231, 183, ${0.16 + index * 0.03})`
      : `rgba(79, 70, 229, ${0.12 + index * 0.03})`;
    context.lineWidth = 1;
    context.beginPath();
    context.moveTo(Math.random() * width, Math.random() * height);
    context.bezierCurveTo(
      Math.random() * width,
      Math.random() * height,
      Math.random() * width,
      Math.random() * height,
      Math.random() * width,
      Math.random() * height
    );
    context.stroke();
  }

  for (let index = 0; index < 18; index += 1) {
    context.fillStyle = isDark
      ? `rgba(255, 255, 255, ${0.08 + Math.random() * 0.16})`
      : `rgba(15, 23, 42, ${0.06 + Math.random() * 0.12})`;
    context.beginPath();
    context.arc(Math.random() * width, Math.random() * height, Math.random() * 2 + 0.6, 0, Math.PI * 2);
    context.fill();
  }

  context.textBaseline = 'middle';
  context.textAlign = 'center';

  [...code].forEach((character, index) => {
    const x = 24 + index * 28;
    const y = height / 2 + (Math.random() * 6 - 3);
    const rotation = (Math.random() * 18 - 9) * Math.PI / 180;

    context.save();
    context.translate(x, y);
    context.rotate(rotation);
    context.font = '700 26px "Avenir Next", "SF Pro Display", sans-serif';
    context.fillStyle = isDark
      ? ['#f8fafc', '#e2e8f0', '#c7d2fe', '#fef08a'][index % 4]
      : ['#111827', '#334155', '#4f46e5', '#0f766e'][index % 4];
    context.fillText(character, 0, 0);
    context.restore();
  });
}

export function initThemeToggle({ root = document.body, toggle, onChange }) {
  let theme = resolveTheme(localStorage.getItem(THEME_STORAGE_KEY) || root.dataset.theme);

  function applyTheme(nextTheme) {
    theme = resolveTheme(nextTheme);
    root.dataset.theme = theme;
    localStorage.setItem(THEME_STORAGE_KEY, theme);

    if (toggle) {
      toggle.setAttribute('aria-pressed', String(theme === 'dark'));
      const label = toggle.querySelector('[data-theme-label]');
      if (label) {
        label.textContent = theme === 'dark' ? '浅色模式' : '深色模式';
      }
    }

    if (typeof onChange === 'function') {
      onChange(theme);
    }
  }

  if (toggle) {
    toggle.addEventListener('click', () => {
      applyTheme(theme === 'dark' ? 'light' : 'dark');
    });
  }

  applyTheme(theme);

  return {
    getTheme() {
      return theme;
    },
    setTheme: applyTheme
  };
}

export function initRoleSwitch({ input, buttons }) {
  const roleButtons = buttons || [];

  function setRole(role) {
    if (input) {
      input.value = role;
    }

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

  setRole(input?.value || roleButtons[0]?.dataset.role || 'STUDENT');

  return {
    setRole,
    getRole() {
      return input?.value || '';
    }
  };
}

export function initPasswordField({ input, toggle, onStateChange }) {
  if (!input || !toggle) {
    return {
      isVisible() {
        return false;
      },
      setVisible() {}
    };
  }

  function emitState() {
    if (typeof onStateChange === 'function') {
      onStateChange({
        visible: input.type === 'text',
        focused: document.activeElement === input
      });
    }
  }

  function setVisible(visible) {
    input.type = visible ? 'text' : 'password';
    toggle.classList.toggle('is-active', visible);
    toggle.setAttribute('aria-pressed', String(visible));
    toggle.setAttribute('aria-label', visible ? '隐藏密码' : '显示密码');
    emitState();
  }

  toggle.addEventListener('click', () => {
    setVisible(input.type === 'password');
    input.focus({ preventScroll: true });
  });

  input.addEventListener('focus', emitState);
  input.addEventListener('blur', emitState);

  setVisible(false);

  return {
    isVisible() {
      return input.type === 'text';
    },
    setVisible
  };
}

export function initCaptcha({ canvas, input, refreshButton, getTheme }) {
  let code = generateCaptchaCode();

  function redraw() {
    drawCaptcha(canvas, code, typeof getTheme === 'function' ? getTheme() : 'light');
  }

  function refresh() {
    code = generateCaptchaCode();
    if (input) {
      input.value = '';
    }
    redraw();
  }

  if (refreshButton) {
    refreshButton.addEventListener('click', refresh);
  }

  refresh();

  return {
    refresh,
    redraw,
    validate() {
      const rawValue = input?.value?.trim().toUpperCase() || '';
      if (!rawValue) {
        return {
          ok: false,
          message: '请输入图形验证码'
        };
      }

      if (rawValue !== code) {
        refresh();
        return {
          ok: false,
          message: '验证码不正确，请重新输入'
        };
      }

      return { ok: true };
    }
  };
}
