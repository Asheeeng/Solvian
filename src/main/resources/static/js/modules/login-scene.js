const MAX_PUPIL_X = 4.5;
const MAX_PUPIL_Y = 3;

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value));
}

export function initLoginScene({ scene }) {
  if (!scene) {
    return {
      setBehaviorState() {},
      destroy() {}
    };
  }

  const prefersReducedMotion = window.matchMedia
    ? window.matchMedia('(prefers-reduced-motion: reduce)').matches
    : false;

  const state = {
    peeking: false,
    privacy: false
  };

  function applyLook(x, y) {
    scene.style.setProperty('--pupil-x', `${x}px`);
    scene.style.setProperty('--pupil-y', `${y}px`);
  }

  function applyState() {
    scene.classList.toggle('is-peeking', state.peeking && !state.privacy);
    scene.classList.toggle('is-privacy', state.privacy);

    if (state.privacy) {
      applyLook(0, 0);
      return;
    }

    if (state.peeking) {
      applyLook(3.6, -0.4);
      return;
    }

    applyLook(0, 0);
  }

  function handlePointerMove(event) {
    if (prefersReducedMotion || state.privacy || state.peeking) {
      return;
    }

    const rect = scene.getBoundingClientRect();
    if (!rect.width || !rect.height) {
      return;
    }

    const relativeX = (event.clientX - (rect.left + rect.width / 2)) / rect.width;
    const relativeY = (event.clientY - (rect.top + rect.height / 2)) / rect.height;

    applyLook(
      clamp(relativeX * MAX_PUPIL_X * 2.2, -MAX_PUPIL_X, MAX_PUPIL_X),
      clamp(relativeY * MAX_PUPIL_Y * 2, -MAX_PUPIL_Y, MAX_PUPIL_Y)
    );
  }

  function handlePointerLeave() {
    if (!state.peeking && !state.privacy) {
      applyLook(0, 0);
    }
  }

  if (prefersReducedMotion) {
    scene.classList.add('is-reduced-motion');
  } else {
    window.addEventListener('pointermove', handlePointerMove, { passive: true });
    window.addEventListener('blur', handlePointerLeave);
  }

  applyState();

  return {
    setBehaviorState(nextState = {}) {
      state.peeking = Boolean(nextState.peeking);
      state.privacy = Boolean(nextState.privacy);
      applyState();
    },
    destroy() {
      if (!prefersReducedMotion) {
        window.removeEventListener('pointermove', handlePointerMove);
        window.removeEventListener('blur', handlePointerLeave);
      }
    }
  };
}
