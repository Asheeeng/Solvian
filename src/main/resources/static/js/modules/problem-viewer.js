export class ProblemViewer {
  constructor({
    fileInput,
    previewImage,
    placeholder,
    modal,
    modalImage,
    closeModalBtn
  }) {
    this.fileInput = fileInput;
    this.previewImage = previewImage;
    this.placeholder = placeholder;
    this.modal = modal;
    this.modalImage = modalImage;
    this.closeModalBtn = closeModalBtn;

    this.imageDataUrl = '';
    this.imageName = '';
    this.selectedFile = null;
  }

  init() {
    this.fileInput.addEventListener('change', (e) => this.onFileChange(e));
    this.previewImage.addEventListener('click', () => this.openModal());
    this.closeModalBtn.addEventListener('click', () => this.closeModal());

    this.modal.addEventListener('click', (e) => {
      if (e.target.dataset.closeModal === 'true') {
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

  onFileChange(event) {
    const file = event.target.files && event.target.files[0];
    if (!file) {
      return;
    }

    this.selectedFile = file;
    this.imageName = file.name;

    const reader = new FileReader();
    reader.onload = () => {
      this.imageDataUrl = String(reader.result || '');
      this.previewImage.src = this.imageDataUrl;
      this.modalImage.src = this.imageDataUrl;
      this.previewImage.classList.remove('hidden');
      this.placeholder.classList.add('hidden');
    };
    reader.readAsDataURL(file);
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
