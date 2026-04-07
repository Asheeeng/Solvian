const THEME_STORAGE_KEY = 'solvian_auth_theme';

function resolveTheme(theme) {
  return theme === 'dark' ? 'dark' : 'light';
}

export function initWorkspaceTheme({ root = document.body, toggle, onChange }) {
  let theme = resolveTheme(localStorage.getItem(THEME_STORAGE_KEY) || root.dataset.theme);

  function applyTheme(nextTheme) {
    theme = resolveTheme(nextTheme);
    root.dataset.theme = theme;
    localStorage.setItem(THEME_STORAGE_KEY, theme);

    if (toggle) {
      toggle.setAttribute('aria-pressed', String(theme === 'dark'));
      toggle.dataset.theme = theme;

      const label = toggle.querySelector('[data-theme-label]');
      if (label) {
        label.textContent = theme === 'dark' ? '浅色界面' : '深色界面';
      }

      const glyph = toggle.querySelector('[data-theme-glyph]');
      if (glyph) {
        glyph.textContent = theme === 'dark' ? '☀' : '☾';
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
