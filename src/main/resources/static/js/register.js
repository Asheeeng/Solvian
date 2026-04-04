import { requireGuest } from './common/auth-guard.js';
import { registerUser } from './common/storage.js';

if (requireGuest()) {
  const form = document.getElementById('registerForm');
  const errorText = document.getElementById('registerError');
  const successText = document.getElementById('registerSuccess');

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    errorText.textContent = '';
    successText.textContent = '';

    const username = document.getElementById('username').value.trim();
    const password = document.getElementById('password').value;
    const confirmPassword = document.getElementById('confirmPassword').value;
    const role = document.getElementById('role').value;

    if (password !== confirmPassword) {
      errorText.textContent = '两次输入的密码不一致。';
      return;
    }

    const result = await registerUser({ username, password, role });
    if (!result.ok) {
      errorText.textContent = result.message || '注册失败';
      return;
    }

    successText.textContent = '注册成功，正在跳转登录页...';
    form.reset();

    setTimeout(() => {
      window.location.href = '/login.html';
    }, 800);
  });
}
