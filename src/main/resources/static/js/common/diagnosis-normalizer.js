function isPlainObject(value) {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

function stripCodeFence(value) {
  const text = String(value ?? '').trim();
  if (!text.startsWith('```')) {
    return text;
  }
  return text
    .replace(/^```(?:json|latex|tex)?\s*/i, '')
    .replace(/\s*```$/i, '')
    .trim();
}

function tryParseJson(value, depth = 0) {
  if (depth > 3 || value == null) {
    return null;
  }

  if (Array.isArray(value) || isPlainObject(value)) {
    return value;
  }

  if (typeof value !== 'string') {
    return null;
  }

  const text = stripCodeFence(value);
  if (!text) {
    return null;
  }

  try {
    const parsed = JSON.parse(text);
    if (typeof parsed === 'string' && parsed !== text) {
      return tryParseJson(parsed, depth + 1) ?? parsed;
    }
    return parsed;
  } catch (_) {
    const firstBrace = text.indexOf('{');
    const lastBrace = text.lastIndexOf('}');
    if (firstBrace >= 0 && lastBrace > firstBrace) {
      try {
        return tryParseJson(text.slice(firstBrace, lastBrace + 1), depth + 1);
      } catch (_) {
        return null;
      }
    }
    return null;
  }
}

function firstDefined(source, keys = [], fallback = null) {
  if (!source) {
    return fallback;
  }
  for (const key of keys) {
    if (source[key] !== undefined && source[key] !== null) {
      return source[key];
    }
  }
  return fallback;
}

function normalizeText(value) {
  if (value == null) {
    return '';
  }
  if (typeof value === 'string') {
    return stripCodeFence(value).replace(/\\n/g, '\n').trim();
  }
  return String(value).trim();
}

function normalizeLatexSyntax(value) {
  const text = normalizeText(value);
  if (!text) {
    return '';
  }
  if (!text.includes('\\begin{bmatrix}')) {
    return text;
  }
  return text
    .replace(/\\\\/g, ' __ROW_BREAK__ ')
    .replace(/\\(?=\s*[-+\d])/g, ' __ROW_BREAK__ ')
    .replace(/\s*__ROW_BREAK__\s*/g, ' \\\\ ')
    .replace(/\s+/g, ' ')
    .trim();
}

function looksLikeResultObject(value) {
  return isPlainObject(value) && (Array.isArray(value.steps) || value.status || value.errorIndex || value.error_index);
}

function looksLikeRawJsonText(value) {
  if (!value || typeof value !== 'string') {
    return false;
  }
  const text = value.trim();
  return (text.startsWith('{') && text.endsWith('}') && (text.includes('"status"') || text.includes('"steps"')))
    || (text.startsWith('[') && text.endsWith(']') && text.includes('"step'));
}

function normalizeNumber(value) {
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : null;
}

function normalizeHighlights(rawHighlights, errorIndex = null) {
  const parsed = Array.isArray(rawHighlights)
    ? rawHighlights
    : Array.isArray(tryParseJson(rawHighlights))
      ? tryParseJson(rawHighlights)
      : [];

  const normalized = parsed
    .map((item, index) => {
      const source = isPlainObject(item) ? item : null;
      if (!source) {
        return null;
      }
      const width = normalizeNumber(firstDefined(source, ['width', 'w']));
      const height = normalizeNumber(firstDefined(source, ['height', 'h']));
      return {
        x: normalizeNumber(firstDefined(source, ['x', 'left'])),
        y: normalizeNumber(firstDefined(source, ['y', 'top'])),
        width,
        height,
        label: normalizeText(firstDefined(source, ['label', 'title'], `错误点 ${index + 1}`)),
        stepNo: normalizeNumber(firstDefined(source, ['stepNo', 'step_no'], null)),
        severity: normalizeText(firstDefined(source, ['severity', 'level'], 'high')) || 'high',
        coordinateType: normalizeText(firstDefined(source, ['coordinateType', 'coordinate_type'], 'ratio')) || 'ratio',
        mock: Boolean(firstDefined(source, ['mock'], false))
      };
    })
    .filter((item) => item && item.width > 0 && item.height > 0);

  if (normalized.length) {
    return normalized;
  }

  if (errorIndex == null) {
    return [];
  }

  const fallbackPositions = [
    { x: 0.10, y: 0.16, width: 0.30, height: 0.18 },
    { x: 0.52, y: 0.28, width: 0.28, height: 0.18 },
    { x: 0.18, y: 0.48, width: 0.34, height: 0.18 },
    { x: 0.56, y: 0.58, width: 0.24, height: 0.16 }
  ];
  const resolvedIndex = Math.max(1, Number(errorIndex) || 1);
  const box = fallbackPositions[(resolvedIndex - 1) % fallbackPositions.length];
  return [{
    ...box,
    label: `错误步骤 ${resolvedIndex}`,
    stepNo: resolvedIndex,
    severity: 'high',
    coordinateType: 'ratio',
    mock: true
  }];
}

function extractLatexFromText(value) {
  const text = normalizeLatexSyntax(value);
  if (!text || looksLikeRawJsonText(text)) {
    return '';
  }

  const matrixMatch = text.match(/(\\begin\{[a-zA-Z*]+\}[\s\S]+?\\end\{[a-zA-Z*]+\})/);
  if (matrixMatch) {
    return matrixMatch[1].trim();
  }

  const displayMatch = text.match(/\\\[([\s\S]+?)\\\]/);
  if (displayMatch) {
    return displayMatch[1].trim();
  }

  const blockMatch = text.match(/\$\$([\s\S]+?)\$\$/);
  if (blockMatch) {
    return blockMatch[1].trim();
  }

  return '';
}

function unwrapLatex(value) {
  let text = normalizeLatexSyntax(value);
  if (!text) {
    return '';
  }

  const parsed = tryParseJson(text);
  if (typeof parsed === 'string') {
    text = normalizeLatexSyntax(parsed);
  } else if (looksLikeResultObject(parsed) || isPlainObject(parsed)) {
    text = normalizeLatexSyntax(firstDefined(parsed, ['highlightedLatex', 'highlighted_latex', 'latex', 'tex', 'formula'], ''));
  }

  if (text.startsWith('\\[') && text.endsWith('\\]')) {
    text = text.slice(2, -2).trim();
  } else if (text.startsWith('$$') && text.endsWith('$$')) {
    text = text.slice(2, -2).trim();
  } else if (text.startsWith('$') && text.endsWith('$') && text.length > 2) {
    text = text.slice(1, -1).trim();
  }

  return text;
}

function escapeRegex(value) {
  return String(value).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function applyLocalHighlights(latex, latexHighlights = [], matrixCellDiffs = []) {
  let resolved = unwrapLatex(latex);
  if (!resolved) {
    return '';
  }

  const highlightTargets = [];
  for (const item of latexHighlights) {
    const target = normalizeText(firstDefined(item, ['target', 'actual'], ''));
    if (target) {
      highlightTargets.push(target);
    }
  }
  for (const item of matrixCellDiffs) {
    const actual = normalizeText(firstDefined(item, ['actual', 'student'], ''));
    if (actual) {
      highlightTargets.push(actual);
    }
  }

  for (const target of [...new Set(highlightTargets)]) {
    if (!target || resolved.includes(`\\color{red}{${target}}`)) {
      continue;
    }
    resolved = resolved.replace(new RegExp(escapeRegex(target)), `\\color{red}{${target}}`);
  }

  return resolved;
}

function normalizeLatexHighlights(rawHighlights) {
  const items = Array.isArray(rawHighlights) ? rawHighlights : Array.isArray(tryParseJson(rawHighlights)) ? tryParseJson(rawHighlights) : [];
  return items
    .map((item) => ({
      target: normalizeText(firstDefined(item, ['target', 'text', 'actual'], '')),
      label: normalizeText(firstDefined(item, ['label', 'reason'], '')),
      severity: normalizeText(firstDefined(item, ['severity', 'level'], 'medium')) || 'medium',
      start: normalizeNumber(firstDefined(item, ['start', 'startIndex', 'start_index'], null)),
      end: normalizeNumber(firstDefined(item, ['end', 'endIndex', 'end_index'], null))
    }))
    .filter((item) => item.target || item.label);
}

function normalizeMatrixCellDiffs(rawDiffs) {
  const items = Array.isArray(rawDiffs) ? rawDiffs : Array.isArray(tryParseJson(rawDiffs)) ? tryParseJson(rawDiffs) : [];
  return items
    .map((item) => ({
      row: normalizeNumber(firstDefined(item, ['row', 'rowIndex', 'row_index'], null)),
      col: normalizeNumber(firstDefined(item, ['col', 'column', 'colIndex', 'col_index'], null)),
      expected: normalizeText(firstDefined(item, ['expected', 'correct'], '')),
      actual: normalizeText(firstDefined(item, ['actual', 'student'], '')),
      reason: normalizeText(firstDefined(item, ['reason', 'label'], '')),
      severity: normalizeText(firstDefined(item, ['severity', 'level'], 'medium')) || 'medium'
    }))
    .filter((item) => item.expected || item.actual || item.reason);
}

function normalizeStep(rawStep, index) {
  let source = rawStep;
  if (typeof source === 'string') {
    source = tryParseJson(source) ?? { content: source };
  }
  if (!isPlainObject(source)) {
    source = { content: String(rawStep ?? '') };
  }

  const nestedContent = tryParseJson(firstDefined(source, ['content', 'description', 'detail'], ''));
  if (looksLikeResultObject(nestedContent)) {
    const nestedNormalized = normalizeDiagnosisResult(nestedContent);
    if (nestedNormalized.steps.length) {
      return nestedNormalized.steps[0];
    }
  } else if (isPlainObject(nestedContent)) {
    source = { ...nestedContent, ...source };
  }

  const title = normalizeText(firstDefined(source, ['title', 'name'], `步骤 ${index + 1}`)) || `步骤 ${index + 1}`;
  let content = normalizeText(firstDefined(source, ['content', 'description', 'detail'], ''));
  let explanation = normalizeText(firstDefined(source, ['explanation', 'errorMessage', 'error_message', 'reason'], ''));

  if (looksLikeRawJsonText(content)) {
    content = '';
  }
  if (looksLikeRawJsonText(explanation)) {
    explanation = '';
  }

  const latexHighlights = normalizeLatexHighlights(firstDefined(source, ['latexHighlights', 'latex_highlights'], []));
  const matrixCellDiffs = normalizeMatrixCellDiffs(firstDefined(source, ['matrixCellDiffs', 'matrix_cell_diffs'], []));
  const rawLatex = unwrapLatex(firstDefined(source, ['latex', 'tex', 'formula'], ''));
  const rawHighlightedLatex = unwrapLatex(firstDefined(source, ['highlightedLatex', 'highlighted_latex', 'highlightLatex'], ''));

  let latex = rawLatex || extractLatexFromText(content) || extractLatexFromText(explanation);
  let highlightedLatex = rawHighlightedLatex;
  if (!highlightedLatex && latex) {
    highlightedLatex = applyLocalHighlights(latex, latexHighlights, matrixCellDiffs);
    if (highlightedLatex === latex) {
      highlightedLatex = '';
    }
  }

  if (latex && content === latex) {
    content = '';
  }
  if (!content && explanation && explanation === latex) {
    explanation = '';
  }

  const isWrong = Boolean(firstDefined(source, ['isWrong', 'is_wrong', 'wrong'], false));

  return {
    stepNo: normalizeNumber(firstDefined(source, ['stepNo', 'step_no', 'step'], index + 1)) || index + 1,
    title,
    content,
    latex,
    highlightedLatex,
    isWrong,
    explanation,
    errorMessage: isWrong ? (explanation || null) : null,
    latexHighlights,
    matrixCellDiffs,
    imageHighlights: normalizeHighlights(firstDefined(source, ['imageHighlights', 'image_highlights', 'boxes', 'boundingBoxes'], []), null)
  };
}

function unwrapEmbeddedResult(rawResult) {
  let source = rawResult;
  if (typeof source === 'string') {
    source = tryParseJson(source) ?? source;
  }

  if (!isPlainObject(source)) {
    return source;
  }

  const steps = Array.isArray(source.steps) ? source.steps : [];
  if (steps.length === 1) {
    const candidate = normalizeText(firstDefined(steps[0], ['content', 'explanation'], typeof steps[0] === 'string' ? steps[0] : ''));
    const parsed = tryParseJson(candidate);
    if (looksLikeResultObject(parsed)) {
      return unwrapEmbeddedResult(parsed);
    }
  }

  return source;
}

export function normalizeDiagnosisResult(rawResult = {}) {
  const source = unwrapEmbeddedResult(rawResult);
  const result = isPlainObject(source) ? source : {};

  let stepsSource = firstDefined(result, ['steps', 'step_list'], []);
  const parsedSteps = tryParseJson(stepsSource);
  if (Array.isArray(parsedSteps)) {
    stepsSource = parsedSteps;
  }

  const steps = Array.isArray(stepsSource)
    ? stepsSource.map((step, index) => normalizeStep(step, index))
    : [];

  const status = normalizeText(firstDefined(result, ['status'], 'unable_to_judge')) || 'unable_to_judge';
  const errorIndex = normalizeNumber(firstDefined(result, ['errorIndex', 'error_index'], null));
  const diffInfo = isPlainObject(firstDefined(result, ['diffInfo', 'diff_info'], null))
    ? firstDefined(result, ['diffInfo', 'diff_info'], {})
    : (tryParseJson(firstDefined(result, ['diffInfo', 'diff_info'], '')) || {});
  const imageHighlights = normalizeHighlights(
    firstDefined(result, ['imageHighlights', 'image_highlights', 'boxes', 'boundingBoxes'], []),
    status === 'error_found' ? errorIndex : null
  );

  const rawTags = firstDefined(result, ['tags'], []);
  const parsedTags = Array.isArray(rawTags) ? rawTags : Array.isArray(tryParseJson(rawTags)) ? tryParseJson(rawTags) : [];

  return {
    recordId: normalizeText(firstDefined(result, ['recordId', 'record_id'], '')),
    status,
    steps,
    feedback: normalizeText(firstDefined(result, ['feedback'], '')),
    errorIndex,
    tags: parsedTags.map((tag) => normalizeText(tag)).filter(Boolean),
    imageHighlights,
    subjectScope: normalizeText(firstDefined(result, ['subjectScope', 'problemType', 'subject_scope'], 'matrix')) || 'matrix',
    isMatrixProblem: Boolean(firstDefined(result, ['isMatrixProblem', 'is_matrix_problem'], true)),
    diffInfo: isPlainObject(diffInfo) ? diffInfo : {},
    mathData: isPlainObject(firstDefined(result, ['mathData', 'math_data'], {})) ? firstDefined(result, ['mathData', 'math_data'], {}) : {},
    isSocratic: Boolean(firstDefined(result, ['isSocratic', 'is_socratic'], false))
  };
}

export function normalizeDiagnosisPreview(rawPreview = {}, task = {}) {
  const preview = isPlainObject(rawPreview) ? rawPreview : {};
  const normalized = normalizeDiagnosisResult({
    ...preview,
    status: preview.status || task.status || 'reasoning',
    errorIndex: firstDefined(preview, ['errorIndex', 'error_index'], null)
  });

  const previewExpressions = firstDefined(preview, ['matrixExpressions', 'matrix_expressions'], []);
  const previewSteps = firstDefined(preview, ['studentSteps', 'student_steps'], []);
  const resolvedExpressions = Array.isArray(previewExpressions)
    ? previewExpressions
    : (Array.isArray(tryParseJson(previewExpressions)) ? tryParseJson(previewExpressions) : []);
  const resolvedStudentSteps = Array.isArray(previewSteps)
    ? previewSteps
    : (Array.isArray(tryParseJson(previewSteps)) ? tryParseJson(previewSteps) : []);

  return {
    ...preview,
    ...normalized,
    stageMessage: task.stageMessage || preview.stageMessage || '',
    problemText: normalizeText(firstDefined(preview, ['problemText', 'problem_text'], '')),
    matrixExpressions: resolvedExpressions.map((item) => normalizeText(item)).filter(Boolean),
    studentSteps: resolvedStudentSteps.map((item) => normalizeText(item)).filter(Boolean),
    confidence: normalizeNumber(firstDefined(preview, ['confidence'], null)),
    summary: normalizeText(firstDefined(preview, ['summary', 'rawSummary', 'raw_summary'], '')),
    cacheHit: Boolean(firstDefined(preview, ['cacheHit', 'cache_hit'], false))
  };
}
