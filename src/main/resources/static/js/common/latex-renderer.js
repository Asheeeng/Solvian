let renderQueue = Promise.resolve();
let mathJaxReadyPromise = null;

function sleep(ms) {
  return new Promise((resolve) => {
    window.setTimeout(resolve, ms);
  });
}

function hasRenderableLatex(stepElement) {
  if (!stepElement) {
    return false;
  }
  return Array.from(stepElement.querySelectorAll('[data-has-latex="true"]'))
    .some((node) => String(node.textContent || '').trim());
}

function waitForMathJax() {
  if (!mathJaxReadyPromise) {
    mathJaxReadyPromise = (async () => {
      for (let attempt = 0; attempt < 30; attempt += 1) {
        const mathJax = window.MathJax;
        if (mathJax?.typesetPromise) {
          try {
            await Promise.resolve(mathJax.startup?.promise);
          } catch (_) {
            // 启动 promise 异常时继续兜底，不阻塞后续渲染尝试。
          }
          return mathJax;
        }
        await sleep(100);
      }
      return null;
    })();
  }

  return mathJaxReadyPromise;
}

export function renderLatex(stepElement) {
  if (!stepElement || !stepElement.isConnected || !hasRenderableLatex(stepElement)) {
    return Promise.resolve(false);
  }

  stepElement.dataset.latexRenderState = 'queued';

  renderQueue = renderQueue
    .catch(() => undefined)
    .then(async () => {
      if (!stepElement.isConnected || !hasRenderableLatex(stepElement)) {
        return false;
      }

      const mathJax = await waitForMathJax();
      if (!mathJax?.typesetPromise) {
        stepElement.dataset.latexRenderState = 'skipped';
        return false;
      }

      try {
        stepElement.dataset.latexRenderState = 'rendering';
        if (typeof mathJax.typesetClear === 'function') {
          mathJax.typesetClear([stepElement]);
        }
        await mathJax.typesetPromise([stepElement]);
        stepElement.dataset.latexRenderState = 'done';
        return true;
      } catch (error) {
        stepElement.dataset.latexRenderState = 'failed';
        return false;
      }
    });

  return renderQueue;
}

export function renderLatexBatch(stepElements = []) {
  const elements = Array.isArray(stepElements)
    ? stepElements.filter((stepElement) => stepElement && stepElement.isConnected)
    : [];

  if (!elements.length) {
    return Promise.resolve([]);
  }

  return Promise.allSettled(elements.map((stepElement) => renderLatex(stepElement)));
}
