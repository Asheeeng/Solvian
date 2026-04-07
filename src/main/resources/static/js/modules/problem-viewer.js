function escapeHtml(raw) {
  return String(raw ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function toPercent(value, coordinateType = 'ratio') {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return 0;
  }

  const resolved = coordinateType === 'percent'
    ? numeric
    : (numeric <= 1 ? numeric * 100 : numeric);

  return Math.max(0, Math.min(resolved, 100));
}

function toSpanPercent(startValue, spanValue, coordinateType = 'ratio') {
  const start = toPercent(startValue, coordinateType);
  const end = toPercent(Number(startValue) + Number(spanValue), coordinateType);
  return Math.max(0, end - start);
}

function severityToClass(severity) {
  if (severity === 'low') {
    return 'image-highlight-box image-highlight-box--low';
  }
  if (severity === 'medium') {
    return 'image-highlight-box image-highlight-box--medium';
  }
  return 'image-highlight-box';
}

export class ProblemViewer {
  constructor({
    fileInput,
    previewImage,
    imageCanvas,
    previewOverlay,
    placeholder,
    modal,
    modalImage,
    modalOverlay,
    closeModalBtn,
    fileMetaText
  }) {
    this.fileInput = fileInput;
    this.previewImage = previewImage;
    this.imageCanvas = imageCanvas;
    this.previewOverlay = previewOverlay;
    this.placeholder = placeholder;
    this.modal = modal;
    this.modalImage = modalImage;
    this.modalOverlay = modalOverlay;
    this.closeModalBtn = closeModalBtn;
    this.fileMetaText = fileMetaText;

    this.imageDataUrl = '';
    this.imageName = '';
    this.selectedFile = null;
    this.highlights = [];
  }

  init() {
    this.fileInput.addEventListener('change', (event) => this.onFileChange(event));
    this.previewImage.addEventListener('click', () => this.openModal());
    this.closeModalBtn.addEventListener('click', () => this.closeModal());

    this.modal.addEventListener('click', (event) => {
      if (event.target.dataset.closeModal === 'true') {
        this.closeModal();
      }
    });
  }

  hasImage() {
    return Boolean(this.imageDataUrl && this.selectedFile);
  }

  getImageMeta() {
    return {
      imageName: this.imageName,
      imageDataUrl: this.imageDataUrl
    };
  }

  getSelectedFile() {
    return this.selectedFile;
  }

  setHighlights(highlights = []) {
    this.highlights = Array.isArray(highlights) ? highlights.filter((item) => this.isValidHighlight(item)) : [];
    this.renderHighlights();
  }

  clearHighlights() {
    this.setHighlights([]);
  }

  onFileChange(event) {
    const file = event.target.files && event.target.files[0];
    if (!file) {
      return;
    }

    this.selectedFile = file;
    this.imageName = file.name;
    this.clearHighlights();

    if (this.fileMetaText) {
      const sizeInMb = (file.size / (1024 * 1024)).toFixed(2);
      this.fileMetaText.textContent = `${file.name} · ${sizeInMb} MB · 支持叠加错误定位框与放大查看`;
    }

    const reader = new FileReader();
    reader.onload = () => {
      this.imageDataUrl = String(reader.result || '');
      this.previewImage.src = this.imageDataUrl;
      this.modalImage.src = this.imageDataUrl;
      this.imageCanvas.classList.remove('hidden');
      this.placeholder.classList.add('hidden');
      this.renderHighlights();
    };
    reader.readAsDataURL(file);
  }

  isValidHighlight(item) {
    if (!item) {
      return false;
    }

    const width = Number(item.width);
    const height = Number(item.height);

    return Number.isFinite(width) && width > 0 && Number.isFinite(height) && height > 0;
  }

  renderHighlights() {
    const hasImage = Boolean(this.imageDataUrl);
    const hasHighlights = hasImage && this.highlights.length > 0;
    const markup = hasHighlights ? this.highlights.map((item, index) => this.renderHighlightBox(item, index)).join('') : '';

    [this.previewOverlay, this.modalOverlay].forEach((layer) => {
      if (!layer) {
        return;
      }

      layer.innerHTML = markup;
      layer.classList.toggle('hidden', !hasHighlights);
    });
  }

  renderHighlightBox(item, index) {
    const coordinateType = item.coordinateType || 'ratio';
    const left = toPercent(item.x, coordinateType);
    const top = toPercent(item.y, coordinateType);
    const width = toSpanPercent(item.x, item.width, coordinateType);
    const height = toSpanPercent(item.y, item.height, coordinateType);
    const label = item.label || (item.stepNo ? `错误步骤 ${item.stepNo}` : `错误点 ${index + 1}`);
    const tagText = item.stepNo ? `第 ${item.stepNo} 步` : '疑似错误';

    return `
      <div
        class="${severityToClass(item.severity)}"
        style="left:${left}%;top:${top}%;width:${width}%;height:${height}%;"
      >
        <div class="image-highlight-box__badge">
          <span>${escapeHtml(label)}</span>
          <span class="image-highlight-box__tag">${escapeHtml(tagText)}</span>
        </div>
      </div>
    `;
  }

  openModal() {
    if (!this.imageDataUrl) {
      return;
    }
    this.modal.classList.remove('hidden');
    this.modal.setAttribute('aria-hidden', 'false');
  }

  closeModal() {
    this.modal.classList.add('hidden');
    this.modal.setAttribute('aria-hidden', 'true');
  }
}
