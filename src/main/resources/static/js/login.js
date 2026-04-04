import { requireGuest } from './common/auth-guard.js';
import { loginUser } from './common/storage.js';

if (requireGuest()) {
  const form = document.getElementById('loginForm');
  const errorText = document.getElementById('loginError');

  form.addEventListener('submit', async (event) => {
    event.preventDefault();

    const username = document.getElementById('username').value.trim();
    const password = document.getElementById('password').value;
    const role = document.getElementById('role').value;

    errorText.textContent = '';

    const result = await loginUser({ username, password, role });
    if (!result.ok) {
      errorText.textContent = result.message || '登录失败';
      return;
    }

    window.location.href = '/home.html';
  });
}
