export class ProblemViewer {
  constructor({
    fileInput,
    previewImage,
    imageCanvas,
    placeholder,
    modal,
    modalImage,
    closeModalBtn,
    fileMetaText
  }) {
    this.fileInput = fileInput;
    this.previewImage = previewImage;
    this.imageCanvas = imageCanvas;
    this.placeholder = placeholder;
    this.modal = modal;
    this.modalImage = modalImage;
    this.closeModalBtn = closeModalBtn;
    this.fileMetaText = fileMetaText;

    this.imageDataUrl = '';
    this.imageName = '';
    this.selectedFile = null;
    this.highlights = [];
  }

  init() {
    this.fileInput?.addEventListener('change', (event) => this.onFileChange(event));
    this.previewImage?.addEventListener('click', () => this.openModal());
    this.closeModalBtn?.addEventListener('click', () => this.closeModal());

    this.modal?.addEventListener('click', (event) => {
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
    this.highlights = Array.isArray(highlights) ? highlights : [];
  }

  clearHighlights() {
    this.highlights = [];
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
      this.fileMetaText.textContent = `${file.name} · ${sizeInMb} MB · 上传后可直接点击放大查看`;
    }

    const reader = new FileReader();
    reader.onload = () => {
      this.imageDataUrl = String(reader.result || '');
      if (this.previewImage) {
        this.previewImage.src = this.imageDataUrl;
        this.previewImage.alt = this.imageName || '题目预览';
      }
      if (this.modalImage) {
        this.modalImage.src = this.imageDataUrl;
        this.modalImage.alt = this.imageName || '放大预览';
      }
      this.imageCanvas?.classList.remove('hidden');
      this.placeholder?.classList.add('hidden');
    };
    reader.readAsDataURL(file);
  }

  openModal() {
    if (!this.imageDataUrl) {
      return;
    }
    this.modal?.classList.remove('hidden');
    this.modal?.setAttribute('aria-hidden', 'false');
  }

  closeModal() {
    this.modal?.classList.add('hidden');
    this.modal?.setAttribute('aria-hidden', 'true');
  }
}
