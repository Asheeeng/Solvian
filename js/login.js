(() => {
  'use strict';

  const STORAGE_FLAG = 'solvian_logged';
  const SESSION_KEY = 'copilot_auth_session';
  const ROLE_LABEL = {
    teacher: '教师',
    student: '学生',
    admin: '管理员'
  };

  const ACCOUNTS = {
    tea123: { password: '123456', role: 'teacher' },
    stu001: { password: '123456', role: 'student' }
  };

  const loginForm = document.getElementById('loginForm');
  const userInput = document.getElementById('loginUser');
  const passInput = document.getElementById('loginPass');
  const submitButton = document.getElementById('loginSubmit');
  const toggleButton = document.getElementById('togglePassword');
  const errorBox = document.getElementById('loginError');
  const infoBox = document.getElementById('loginInfo');
  const forgotButton = document.getElementById('forgotBtn');
  const rememberMe = document.getElementById('rememberMe');

  let pointerX = window.innerWidth / 2;
  let pointerY = window.innerHeight / 2;
  let isPrivacyMode = false;
  let isRevealMode = false;
  let purplePeeking = false;
  let purplePeekTimer = null;
  let purplePeekResetTimer = null;
  let currentPasswordVisible = false;

  const characters = [
    {
      key: 'violet',
      root: document.getElementById('charViolet'),
      face: document.getElementById('faceViolet'),
      pupils: [document.getElementById('pupilVioletL'), document.getElementById('pupilVioletR')],
      eyeSelector: '.login-eye',
      baseX: 44,
      baseY: 42,
      maxX: 4.5,
      maxY: 3.6,
      privacy: { skew: -12, shiftX: 28, faceX: -24, faceY: 18, lookX: -4, lookY: -2 }
    },
    {
      key: 'graphite',
      root: document.getElementById('charGraphite'),
      face: document.getElementById('faceGraphite'),
      pupils: [document.getElementById('pupilGraphiteL'), document.getElementById('pupilGraphiteR')],
      eyeSelector: '.login-eye',
      baseX: 24,
      baseY: 34,
      maxX: 4.2,
      maxY: 3,
      privacy: { skew: -8, shiftX: 18, faceX: -16, faceY: 10, lookX: -4, lookY: -3 }
    },
    {
      key: 'apricot',
      root: document.getElementById('charApricot'),
      face: document.getElementById('faceApricot'),
      pupils: [document.getElementById('pupilApricotL'), document.getElementById('pupilApricotR')],
      eyeSelector: '.login-eye',
      baseX: 80,
      baseY: 82,
      maxX: 4.8,
      maxY: 3.4,
      privacy: { skew: -6, shiftX: 8, faceX: -14, faceY: 4, lookX: -4, lookY: -2 }
    },
    {
      key: 'lemon',
      root: document.getElementById('charLemon'),
      face: document.getElementById('faceLemon'),
      pupils: [document.getElementById('pupilLemonL'), document.getElementById('pupilLemonR')],
      mouth: document.getElementById('mouthLemon'),
      eyeSelector: '.login-eye',
      baseX: 44,
      baseY: 38,
      maxX: 4.6,
      maxY: 3.2,
      privacy: { skew: -10, shiftX: 14, faceX: -18, faceY: 8, lookX: -4, lookY: -2 }
    }
  ];

  function clamp(value, min, max) {
    return Math.max(min, Math.min(max, value));
  }

  function getRoleLabel(role) {
    return ROLE_LABEL[role] || role;
  }

  function showMessage(target, text) {
    if (!target) return;
    target.textContent = text || '';
    target.classList.toggle('hidden', !text);
  }

  function clearMessages() {
    showMessage(errorBox, '');
    showMessage(infoBox, '');
  }

  function shakeError() {
    if (!errorBox) return;
    errorBox.classList.remove('is-shaking');
    void errorBox.offsetWidth;
    errorBox.classList.add('is-shaking');
  }

  function getBodySkew(character) {
    if (!character.root) return 0;
    const rect = character.root.getBoundingClientRect();
    const centerX = rect.left + rect.width / 2;
    return clamp((pointerX - centerX) / 34, -6, 6);
  }

  function getFaceOffset(character) {
    if (!character.face) return { x: 0, y: 0 };
    const rect = character.face.getBoundingClientRect();
    const centerX = rect.left + rect.width / 2;
    const centerY = rect.top + rect.height / 2;
    return {
      x: clamp((pointerX - centerX) / 22, -14, 14),
      y: clamp((pointerY - centerY) / 30, -10, 10)
    };
  }

  function getPupilOffset(element, maxX, maxY) {
    if (!element) return { x: 0, y: 0 };
    const rect = element.getBoundingClientRect();
    const centerX = rect.left + rect.width / 2;
    const centerY = rect.top + rect.height / 2;
    return {
      x: clamp((pointerX - centerX) / 18, -maxX, maxX),
      y: clamp((pointerY - centerY) / 22, -maxY, maxY)
    };
  }

  function setPupils(character, offsetX, offsetY) {
    character.pupils.forEach((pupil) => {
      if (pupil) {
        pupil.style.transform = `translate(${offsetX}px, ${offsetY}px)`;
      }
    });
  }

  function applyFollowState(character) {
    const skew = getBodySkew(character);
    const faceOffset = getFaceOffset(character);
    const firstEye = character.root?.querySelector(character.eyeSelector);
    const offset = getPupilOffset(firstEye, character.maxX, character.maxY);

    if (character.root) {
      character.root.style.transform = `translateX(0px) skewX(${skew}deg)`;
    }
    if (character.face) {
      character.face.style.transform = `translate(${faceOffset.x}px, ${faceOffset.y}px)`;
    }
    if (character.mouth) {
      character.mouth.style.transform = `translate(${faceOffset.x / 1.4}px, ${faceOffset.y / 2}px)`;
    }

    setPupils(character, offset.x, offset.y);
  }

  function applyPrivacyState(character) {
    const config = character.privacy;
    if (character.root) {
      character.root.style.transform = `translateX(${config.shiftX}px) skewX(${config.skew}deg)`;
    }
    if (character.face) {
      character.face.style.transform = `translate(${config.faceX}px, ${config.faceY}px)`;
    }
    if (character.mouth) {
      character.mouth.style.transform = `translate(${config.faceX / 1.4}px, ${config.faceY / 2}px)`;
    }

    let lookX = config.lookX;
    let lookY = config.lookY;
    if (character.key === 'violet' && isRevealMode && purplePeeking) {
      lookX = 3.5;
      lookY = 1.5;
    }
    setPupils(character, lookX, lookY);
  }

  function animate() {
    characters.forEach((character) => {
      if (isPrivacyMode) {
        applyPrivacyState(character);
      } else {
        applyFollowState(character);
      }
    });
    window.requestAnimationFrame(animate);
  }

  function scheduleBlink(character) {
    const delay = Math.random() * 3200 + 2200;
    window.setTimeout(() => {
      character.root?.classList.add('is-blinking');
      window.setTimeout(() => {
        character.root?.classList.remove('is-blinking');
        scheduleBlink(character);
      }, 160);
    }, delay);
  }

  function clearPeekTimers() {
    if (purplePeekTimer) {
      window.clearTimeout(purplePeekTimer);
      purplePeekTimer = null;
    }
    if (purplePeekResetTimer) {
      window.clearTimeout(purplePeekResetTimer);
      purplePeekResetTimer = null;
    }
    purplePeeking = false;
  }

  function schedulePurplePeek() {
    clearPeekTimers();
    if (!isRevealMode) return;
    purplePeekTimer = window.setTimeout(() => {
      if (!isRevealMode) return;
      purplePeeking = true;
      purplePeekResetTimer = window.setTimeout(() => {
        purplePeeking = false;
        schedulePurplePeek();
      }, 820);
    }, Math.random() * 2400 + 1600);
  }

  function syncPasswordBehavior() {
    const focused = document.activeElement === passInput;
    const hasValue = passInput.value.trim().length > 0;
    isRevealMode = currentPasswordVisible && hasValue;
    isPrivacyMode = focused || isRevealMode;

    if (isRevealMode) {
      schedulePurplePeek();
    } else {
      clearPeekTimers();
    }
  }

  function togglePassword() {
    currentPasswordVisible = !currentPasswordVisible;
    passInput.type = currentPasswordVisible ? 'text' : 'password';
    toggleButton.setAttribute('aria-pressed', String(currentPasswordVisible));
    toggleButton.setAttribute('aria-label', currentPasswordVisible ? '隐藏密码' : '显示密码');
    toggleButton.textContent = currentPasswordVisible ? '🙈' : '👁';
    syncPasswordBehavior();
    passInput.focus({ preventScroll: true });
  }

  function quickFill(user, pass) {
    userInput.value = user;
    passInput.value = pass;
    clearMessages();
    syncPasswordBehavior();
    userInput.focus();
  }

  function persistSession(username, role) {
    localStorage.setItem(STORAGE_FLAG, 'true');
    localStorage.setItem('solvian_user', username);
    localStorage.setItem('solvian_role', role);

    if (rememberMe.checked) {
      localStorage.setItem('solvian_remember', 'true');
    } else {
      localStorage.removeItem('solvian_remember');
    }

    localStorage.setItem(SESSION_KEY, JSON.stringify({
      token: `demo-${role}-${username}`,
      user: {
        username,
        role,
        roleLabel: getRoleLabel(role)
      },
      loginAt: Date.now()
    }));
  }

  function doLogin(event) {
    event.preventDefault();
    clearMessages();

    const username = userInput.value.trim();
    const password = passInput.value.trim();

    if (!username || !password) {
      showMessage(errorBox, '请输入用户名和密码。');
      shakeError();
      return;
    }

    const account = ACCOUNTS[username];
    submitButton.disabled = true;
    submitButton.innerHTML = '<span>登录中...</span>';

    window.setTimeout(() => {
      if (!account || account.password !== password) {
        showMessage(errorBox, '用户名或密码错误，请检查后重试。');
        shakeError();
        submitButton.disabled = false;
        submitButton.innerHTML = '<span>登录进入作业诊断</span>';
        return;
      }

      persistSession(username, account.role);
      window.location.href = '/student-review.html';
    }, 320);
  }

  document.addEventListener('pointermove', (event) => {
    pointerX = event.clientX;
    pointerY = event.clientY;
  }, { passive: true });

  userInput.addEventListener('input', clearMessages);
  passInput.addEventListener('input', () => {
    clearMessages();
    syncPasswordBehavior();
  });
  passInput.addEventListener('focus', syncPasswordBehavior);
  passInput.addEventListener('blur', syncPasswordBehavior);

  toggleButton.addEventListener('click', togglePassword);
  forgotButton.addEventListener('click', () => {
    showMessage(infoBox, '当前演示环境暂未接入找回密码流程，请直接使用演示账号体验。');
  });
  loginForm.addEventListener('submit', doLogin);

  document.querySelectorAll('[data-user][data-pass]').forEach((button) => {
    button.addEventListener('click', () => {
      quickFill(button.dataset.user || '', button.dataset.pass || '');
    });
  });

  if (localStorage.getItem(STORAGE_FLAG) === 'true') {
    showMessage(infoBox, '当前浏览器里已有登录状态。你现在可以直接重新登录，或访问 /student-review.html 继续使用。');
  }

  characters.forEach(scheduleBlink);
  syncPasswordBehavior();
  window.requestAnimationFrame(animate);
})();
