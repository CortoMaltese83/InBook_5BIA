// ==========================================
// FR-06 — Elenco Materie per Classe
// - Docente: vede le materie permesse dalla policy (tipicamente le proprie)
// - Admin: vede tutte le materie della classe
// La policy è applicata lato backend: qui mostriamo ciò che l'API ritorna.
// ==========================================

// ---- API endpoints (adegua se diverso nel backend) ----
const SUBJECTS_API_URL = '/materia-data';
const BOOK_LOOKUP_API_URL = '/book/lookup';

// ---- State ----
let subjects = [];
let selectedClassId = null;
let selectedClassLabel = null;
let canAddSubject = false;

let editingId = null;
let deleteId = null;
let dateInterval = null;
let currentPage = 0;
let pageSize = 25;
let totalItems = 0;
let totalPages = 0;
let searchTimeout = null;

// ---- DOM Elements ----
// (HTML required IDs)
// Class selector (optional now; class comes from URL): <select id="class-select"></select>
const classSelect = document.getElementById('class-select');
const bookLegendToggle = document.getElementById('book-legend-toggle');
const bookLegendHint = document.getElementById('book-legend-hint');
const searchInput = document.getElementById('subject-search');
const bookStatusFilter = document.getElementById('book-status-filter');
const pageSizeSelect = document.getElementById('page-size');
const resetFiltersBtn = document.getElementById('reset-filters');
const resultsSummary = document.getElementById('results-summary');
const paginationInfo = document.getElementById('pagination-info');
const prevPageBtn = document.getElementById('prev-page');
const nextPageBtn = document.getElementById('next-page');

// Table
const tableBody = document.getElementById('table-body');

// Modals
const modalBackdrop = document.getElementById('modal-backdrop');
const formModal = document.getElementById('form-modal');
const deleteModal = document.getElementById('delete-modal');
const bookModal = document.getElementById('book-modal');
const modalTitle = document.getElementById('modal-title');
const bookModalTitle = document.getElementById('book-modal-title');

// Form & buttons
// (rename the form in HTML): id="subject-form"
const subjectForm = document.getElementById('subject-form');
const btnSave = document.getElementById('btn-save');
const btnConfirmDelete = document.getElementById('btn-confirm-delete');
const btnSaveBook = document.getElementById('btn-save-book');
const addBtn = document.getElementById('add-btn');

// Hidden inputs (rename in HTML)
const subjectIdInput = document.getElementById('subject-id');
const deleteSubjectIdInput = document.getElementById('delete-subject-id');
const bookSubjectIdInput = document.getElementById('book-subject-id');
const bookClasseIdInput = document.getElementById('book-classe-id');

// Inputs (rename in HTML)
const nomeMateriaInput = document.getElementById('nome-materia');
const bookIsbnInput = document.getElementById('book-isbn');
const bookAuthorInput = document.getElementById('book-author');
const bookTitleInput = document.getElementById('book-title');
const bookVolumeInput = document.getElementById('book-volume');
const bookPublisherInput = document.getElementById('book-publisher');
const bookPriceInput = document.getElementById('book-price');
const bookBuyInput = document.getElementById('book-buy');
const bookRecommendedInput = document.getElementById('book-recommended');
const bookLookupBtn = document.getElementById('book-lookup-btn');
const bookLookupStatus = document.getElementById('book-lookup-status');

// Displays
const createdAtDisplay = document.getElementById('created-at-display');
const updatedAtDisplay = document.getElementById('updated-at-display');


// -------------------------
// Helpers
// -------------------------

const formatDate = (date) => {
    const d = new Date(date);
    const pad = (n) => n.toString().padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
};

const escapeHtml = (str) => {
    if (str === null || str === undefined) return '';
    return String(str)
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#039;');
};

const sanitizeIsbn = (value) => String(value ?? '').replace(/\D/g, '');

function setBookLookupStatus(message = '', type = '') {
    if (!bookLookupStatus) return;
    bookLookupStatus.textContent = message;
    bookLookupStatus.className = 'lookup-status';
    if (message) bookLookupStatus.classList.add('visible');
    if (type) bookLookupStatus.classList.add(type);
}

function setBookLookupLoading(isLoading) {
    if (!bookLookupBtn) return;
    bookLookupBtn.disabled = isLoading;
    bookLookupBtn.setAttribute('aria-busy', String(isLoading));
}

function applyBookLookupResult(data) {
    if (!data) return;

    if (bookIsbnInput && data.isbn) bookIsbnInput.value = data.isbn;
    if (bookAuthorInput && data.autore) bookAuthorInput.value = data.autore;
    if (bookTitleInput && data.titolo) bookTitleInput.value = data.titolo;
    if (bookPublisherInput && data.casaEditrice) bookPublisherInput.value = data.casaEditrice;

    if (bookVolumeInput && data.volume !== null && data.volume !== undefined) {
        const parsedVolume = Number(data.volume);
        if (Number.isInteger(parsedVolume) && parsedVolume >= 0) {
            bookVolumeInput.value = String(parsedVolume);
        }
    }

    if (bookPriceInput && data.prezzo !== null && data.prezzo !== undefined) {
        const parsedPrice = Number(data.prezzo);
        if (Number.isFinite(parsedPrice) && parsedPrice >= 0) {
            bookPriceInput.value = parsedPrice.toFixed(2);
        }
    }
}

async function lookupBookFromAie() {
    if (!bookIsbnInput) return;

    const isbn = sanitizeIsbn(bookIsbnInput.value);
    if (isbn.length < 13) {
        setBookLookupStatus('Inserisci almeno 13 cifre ISBN.', 'error');
        return;
    }

    setBookLookupLoading(true);
    setBookLookupStatus('Ricerca AIE in corso...', 'loading');

    try {
        const response = await fetch(`${BOOK_LOOKUP_API_URL}?isbn=${encodeURIComponent(isbn)}`, {
            headers: { 'Accept': 'application/json' }
        });
        let payload = {};
        try {
            payload = await response.json();
        } catch (ignore) {
            payload = {};
        }
        if (!response.ok) {
            throw new Error(payload.message || `Ricerca non riuscita (HTTP ${response.status})`);
        }

        applyBookLookupResult(payload);
        setBookLookupStatus('Dati compilati da AIE.', 'success');
    } catch (error) {
        console.error('AIE lookup error:', error);
        setBookLookupStatus(error.message || 'Ricerca AIE non riuscita.', 'error');
    } finally {
        setBookLookupLoading(false);
    }
}

// IMPORTANT: class id is fixed by URL query string (?classeId=...)
// We do NOT read from classSelect anymore.
function getSelectedClassId() {
    return selectedClassId;
}

function buildSubjectsUrl(classeId) {
    const params = new URLSearchParams();
    params.set('classeId', String(classeId));
    params.set('page', String(currentPage));
    params.set('size', String(pageSize));

    const search = searchInput ? searchInput.value.trim() : '';
    const bookStatus = bookStatusFilter ? bookStatusFilter.value : '';
    if (search) params.set('search', search);
    if (bookStatus) params.set('bookStatus', bookStatus);

    return `${SUBJECTS_API_URL}?${params.toString()}`;
}


function updateSelectedClassDisplay() {
    const el = document.getElementById('class-selected');
    if (!el) return;

    const clsId = getSelectedClassId();
    if (!clsId) {
        el.textContent = '-';
        return;
    }

    // Prefer label coming from API/controller
    const lbl = (selectedClassLabel || '').trim();
    if (lbl) {
        el.textContent = lbl;
        return;
    }

    el.textContent = '-';
}

function updateSubjectCreateAvailability() {
    if (addBtn) addBtn.hidden = !canAddSubject;
}


// -------------------------
// Load & Render
// -------------------------

async function loadSubjects(classeId) {
    try {
        const response = await fetch(buildSubjectsUrl(classeId));
        if (!response.ok) throw new Error(`Errore nel caricamento delle materie (Status: ${response.status})`);

        const data = await response.json();
        const items = Array.isArray(data) ? data : (data.items ?? []);
        canAddSubject = !Array.isArray(data) && data.canAddSubject === true;

        // Extract class label from API response.
        // Requirement: show ONLY anno + sezione (e.g. "4 BIA"), never the class "nome" (e.g. "4BIA").
        selectedClassLabel = null;
        const labelSource = Array.isArray(data) ? (items[0] ?? null) : data;
        const fallbackLabelSource = items[0] ?? null;
        if (labelSource || fallbackLabelSource) {
            const anno = (
                labelSource?.classeAnnoScolastico ??
                labelSource?.classe_anno_scolastico ??
                labelSource?.anno ??
                fallbackLabelSource?.classe_anno_scolastico ??
                fallbackLabelSource?.classeAnnoScolastico ??
                fallbackLabelSource?.anno ??
                ''
            ).toString().trim();
            const sezione = (
                labelSource?.classeSezione ??
                labelSource?.classe_sezione ??
                labelSource?.sezione ??
                fallbackLabelSource?.classe_sezione ??
                fallbackLabelSource?.classeSezione ??
                fallbackLabelSource?.sezione ??
                ''
            ).toString().trim();

            const parts = [anno, sezione].filter(Boolean);
            selectedClassLabel = parts.length
                ? parts.join(' ')
                : ((labelSource?.classeLabel ?? fallbackLabelSource?.classe_label ?? fallbackLabelSource?.classeLabel ?? '').toString().trim() || null);
        }

        // Subject entity:
        // id, classe_id, docente_id, nome_materia, (book_id|bookId|hasBook), created_at, updated_at
        subjects = items.map(s => ({
            id: s.id,
            classeId: s.classe_id ?? s.classeId,
            docenteId: s.docente_id ?? s.docenteId,
            // opzionale: se il backend restituisce info del docente
            docenteNome: s.docente_nome ?? s.docenteNome ?? s.docente ?? s.teacher ?? null,
            nomeMateria: s.nome_materia ?? s.nomeMateria,
            // Support multiple backend shapes for book association
            bookId: s.book_id ?? s.bookId ?? (s.book ? (s.book.id ?? s.bookId ?? s.book_id) : null),
            bookDbId: s.book_db_id ?? s.bookDbId ?? (s.book ? (s.book.dbId ?? s.book.id ?? null) : null),
            bookIsbn: s.book_isbn ?? s.bookIsbn ?? s.book_id ?? s.bookId ?? (s.book ? (s.book.isbn ?? s.book.isbnCode ?? null) : null),
            bookAuthor: s.book_autore ?? s.bookAuthor ?? s.book_author ?? (s.book ? (s.book.autore ?? s.book.author ?? null) : null),
            bookTitle: s.book_titolo ?? s.bookTitle ?? (s.book ? (s.book.titolo ?? s.book.title ?? null) : null),
            bookVolume: s.book_volume ?? s.bookVolume ?? (s.book ? (s.book.volume ?? null) : null),
            bookPublisher: s.book_casa_editrice ?? s.bookCasaEditrice ?? s.book_publisher ?? (s.book ? (s.book.casaEditrice ?? s.book.publisher ?? null) : null),
            bookPrice: s.book_prezzo ?? s.bookPrice ?? s.book_price ?? (s.book ? (s.book.prezzo ?? s.book.price ?? null) : null),
            bookBuy: s.book_da_acquistare ?? s.bookDaAcquistare ?? (s.book ? (s.book.daAcquistare ?? null) : null),
            bookRecommended: s.book_consigliato ?? s.bookConsigliato ?? (s.book ? (s.book.consigliato ?? null) : null),
            hasBook: Boolean(
                s.hasBook ?? s.has_book ?? s.bookPresent ?? s.book_present ??
                (s.book_id ?? s.bookId ?? (s.book ? (s.book.id ?? s.bookId ?? s.book_id) : null))
            ),
            canEdit: Boolean(s.can_edit ?? s.canEdit),
            createdAt: s.created_at ?? s.createdAt,
            updatedAt: s.updated_at ?? s.updatedAt
        }));

        if (Array.isArray(data)) {
            totalItems = subjects.length;
            totalPages = subjects.length > 0 ? 1 : 0;
            currentPage = 0;
        } else {
            totalItems = Number(data.totalItems ?? subjects.length);
            totalPages = Number(data.totalPages ?? 0);
            currentPage = Number(data.page ?? currentPage);
            pageSize = Number(data.size ?? pageSize);
        }

        updateSelectedClassDisplay();
        updateSubjectCreateAvailability();
        updatePagination();
        renderTable();
    } catch (error) {
        console.error('Error:', error);
        canAddSubject = false;
        updateSubjectCreateAvailability();
        if (tableBody) {
            tableBody.innerHTML = `<tr><td colspan="6" style="text-align:center; padding: 2rem; color: #ef4444;">Errore nel caricamento dei dati: ${escapeHtml(error.message)}</td></tr>`;
        }
    }
}

function renderTable() {
    if (!tableBody) return;

    tableBody.innerHTML = '';

    const clsId = getSelectedClassId();

    if (!clsId) {
        tableBody.innerHTML = '<tr><td colspan="6" style="text-align:center; padding: 2rem; color: #6b7280;">Classe non specificata. Apri questa pagina passando <code>?classeId=...</code> nell\\\'URL.</td></tr>';
        return;
    }

    if (subjects.length === 0) {
        const hasFilters = Boolean((searchInput && searchInput.value.trim()) || (bookStatusFilter && bookStatusFilter.value));
        const message = hasFilters
            ? 'Nessuna materia corrisponde ai filtri impostati.'
            : (canAddSubject
                ? 'Nessuna materia presente per questa classe. Clicca su "Aggiungi" per crearne una nuova.'
                : 'Nessuna materia presente per questa classe.');
        tableBody.innerHTML = `<tr><td colspan="6" style="text-align:center; padding: 2rem; color: #6b7280;">${message}</td></tr>`;
        return;
    }

    subjects.forEach((s, index) => {
        const tr = document.createElement('tr');
        tr.style.animationDelay = `${index * 0.05}s`;

        const docenteLabel = s.docenteNome
            ? escapeHtml(s.docenteNome)
            : (s.docenteId ? `#${escapeHtml(s.docenteId)}` : '-');

        const semaforo = s.hasBook
            ? '<span title="Libro associato" style="display:inline-block;width:12px;height:12px;border-radius:999px;background:#22c55e;"></span>'
            : '<span title="Nessun libro" style="display:inline-block;width:12px;height:12px;border-radius:999px;background:#ef4444;"></span>';

        const bookButtonTitle = s.hasBook ? 'Modifica libro associato' : 'Associa libro';
        const actions = s.canEdit
            ? `
                <button class="action-btn btn-book" onclick="openBookModal(${s.id})" title="${bookButtonTitle}"><i class="fa-solid fa-book"></i></button>
                <button class="action-btn btn-edit" onclick="openEditModal(${s.id})"><i class="fa-solid fa-pencil"></i></button>
                <button class="action-btn btn-delete" onclick="openDeleteModal(${s.id})"><i class="fa-solid fa-trash"></i></button>
            `
            : '<span class="readonly-actions">Solo lettura</span>';

        tr.innerHTML = `
            <td><strong>${escapeHtml(s.nomeMateria)}</strong></td>
            <td style="text-align:center;">${semaforo}</td>
            <td>${docenteLabel}</td>
            <td>${s.createdAt ? formatDate(s.createdAt) : '-'}</td>
            <td>${s.updatedAt ? formatDate(s.updatedAt) : '-'}</td>
            <td>
                ${actions}
            </td>
        `;

        tableBody.appendChild(tr);
    });
}

function updatePagination() {
    if (resultsSummary) {
        const start = totalItems === 0 ? 0 : (currentPage * pageSize) + 1;
        const end = Math.min((currentPage + 1) * pageSize, totalItems);
        resultsSummary.textContent = totalItems === 0
            ? '0 materie'
            : `${start}-${end} di ${totalItems}`;
    }

    if (paginationInfo) {
        paginationInfo.textContent = totalPages === 0
            ? 'Pagina 0 di 0'
            : `Pagina ${currentPage + 1} di ${totalPages}`;
    }

    if (prevPageBtn) prevPageBtn.disabled = currentPage <= 0;
    if (nextPageBtn) nextPageBtn.disabled = totalPages === 0 || currentPage >= totalPages - 1;

    if (pageSizeSelect && pageSizeSelect.value !== String(pageSize)) {
        pageSizeSelect.value = String(pageSize);
    }
}

function positionBookLegendHint() {
    if (!bookLegendToggle || !bookLegendHint || bookLegendHint.hidden) return;

    const toggleRect = bookLegendToggle.getBoundingClientRect();
    const hintRect = bookLegendHint.getBoundingClientRect();
    const viewportMargin = 12;
    const gap = 8;

    let left = toggleRect.left + (toggleRect.width / 2) - (hintRect.width / 2);
    left = Math.max(viewportMargin, Math.min(left, window.innerWidth - hintRect.width - viewportMargin));

    const top = toggleRect.bottom + gap;

    bookLegendHint.style.left = `${left}px`;
    bookLegendHint.style.top = `${top}px`;
}

function setBookLegendVisible(isVisible) {
    if (!bookLegendToggle || !bookLegendHint) return;
    bookLegendHint.hidden = !isVisible;
    bookLegendToggle.setAttribute('aria-expanded', String(isVisible));
    if (isVisible) {
        positionBookLegendHint();
    }
}


// -------------------------
// Modals
// -------------------------

function openAddModal() {
    editingId = null;

    const clsId = getSelectedClassId();
    if (!clsId) {
        alert('Classe non specificata nell’URL (?classeId=...).');
        return;
    }
    if (!canAddSubject) {
        alert('Non puoi aggiungere materie a questa classe.');
        return;
    }

    modalTitle.textContent = 'Aggiungi Materia';
    btnSave.textContent = 'Aggiungi';

    // POST create (controller: /subjects/add)
    if (subjectForm) subjectForm.action = '/subjects/add';

    if (subjectIdInput) subjectIdInput.value = '';
    if (nomeMateriaInput) nomeMateriaInput.value = '';


    // Controller expects classeId
    const classeIdInput = document.getElementById('classe-id');
    if (classeIdInput) classeIdInput.value = String(clsId);

    // Dates display
    if (createdAtDisplay) createdAtDisplay.textContent = '';
    if (updatedAtDisplay) updatedAtDisplay.textContent = '';

    startDateUpdates(null);
    showModal(formModal);
}

function openEditModal(id) {
    editingId = id;

    const s = subjects.find(x => x.id === id);
    if (!s) return;
    if (!s.canEdit) return;

    modalTitle.textContent = 'Modifica Materia';
    btnSave.textContent = 'Aggiorna';

    // POST update (controller: /subjects/edit)
    if (subjectForm) subjectForm.action = '/subjects/edit';

    if (subjectIdInput) subjectIdInput.value = String(id);
    if (nomeMateriaInput) nomeMateriaInput.value = s.nomeMateria ?? '';

    // Controller expects classeId
    const classeIdInput = document.getElementById('classe-id');
    if (classeIdInput) classeIdInput.value = String(getSelectedClassId() || s.classeId || '');

    if (createdAtDisplay) createdAtDisplay.textContent = s.createdAt ? formatDate(s.createdAt) : '';
    if (updatedAtDisplay) updatedAtDisplay.textContent = s.updatedAt ? formatDate(s.updatedAt) : '';

    startDateUpdates(s.createdAt ? formatDate(s.createdAt) : null);
    showModal(formModal);
}

function openDeleteModal(id) {
    deleteId = id;
    const s = subjects.find(x => x.id === id);
    if (!s || !s.canEdit) return;
    if (deleteSubjectIdInput) deleteSubjectIdInput.value = String(id);
    const deleteClasseIdInput = document.getElementById('delete-classe-id');
    if (deleteClasseIdInput) deleteClasseIdInput.value = String(getSelectedClassId() || '');
    showModal(deleteModal);
}

function openBookModal(id) {
    const s = subjects.find(x => x.id === id);
    if (!s) return;
    if (!s.canEdit) return;

    if (bookSubjectIdInput) bookSubjectIdInput.value = String(id);
    if (bookClasseIdInput) bookClasseIdInput.value = String(getSelectedClassId() || s.classeId || '');
    if (bookModalTitle) bookModalTitle.textContent = s.hasBook ? 'Modifica Libro' : 'Associa Libro';
    if (btnSaveBook) btnSaveBook.textContent = s.hasBook ? 'Aggiorna' : 'Associa';
    if (bookIsbnInput) bookIsbnInput.value = s.bookIsbn ?? '';
    if (bookAuthorInput) bookAuthorInput.value = s.bookAuthor ?? '';
    if (bookTitleInput) bookTitleInput.value = s.bookTitle ?? '';
    if (bookVolumeInput) bookVolumeInput.value = s.bookVolume ?? '';
    if (bookPublisherInput) bookPublisherInput.value = s.bookPublisher ?? '';
    if (bookPriceInput) bookPriceInput.value = s.bookPrice ?? '';
    if (bookBuyInput) bookBuyInput.value = String(Boolean(s.bookBuy));
    if (bookRecommendedInput) bookRecommendedInput.value = String(Boolean(s.bookRecommended));
    setBookLookupStatus('');
    if (bookIsbnInput) setTimeout(() => bookIsbnInput.focus(), 0);

    showModal(bookModal);
}

function showModal(modal) {
    if (!modalBackdrop || !modal) return;
    modalBackdrop.classList.add('visible');
    modal.classList.add('visible');
}

function closeAllModals() {
    if (modalBackdrop) modalBackdrop.classList.remove('visible');
    if (formModal) formModal.classList.remove('visible');
    if (deleteModal) deleteModal.classList.remove('visible');
    if (bookModal) bookModal.classList.remove('visible');
    stopDateUpdates();
}

function startDateUpdates(fixedCreationDate = null) {
    stopDateUpdates();

    const update = () => {
        const now = new Date();
        const formatted = formatDate(now);
        if (updatedAtDisplay) updatedAtDisplay.textContent = formatted;

        if (!fixedCreationDate) {
            if (createdAtDisplay) createdAtDisplay.textContent = formatted;
        } else {
            if (createdAtDisplay) createdAtDisplay.textContent = fixedCreationDate;
        }
    };

    update();
    dateInterval = setInterval(update, 10000);
}

function stopDateUpdates() {
    if (dateInterval) clearInterval(dateInterval);
    dateInterval = null;
}


// -------------------------
// Events
// -------------------------

if (addBtn) addBtn.addEventListener('click', openAddModal);

if (modalBackdrop) modalBackdrop.addEventListener('click', closeAllModals);

if (bookLookupBtn) {
    bookLookupBtn.addEventListener('click', lookupBookFromAie);
}

if (bookIsbnInput) {
    bookIsbnInput.addEventListener('input', () => setBookLookupStatus(''));
}

document.addEventListener('click', (event) => {
    const clickedToggle = event.target.closest?.('#book-legend-toggle');
    if (clickedToggle) {
        event.preventDefault();
        const isOpen = clickedToggle.getAttribute('aria-expanded') === 'true';
        setBookLegendVisible(!isOpen);
        return;
    }

    if (event.target.closest?.('#book-legend-hint')) {
        return;
    }

    setBookLegendVisible(false);
});

document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') {
        setBookLegendVisible(false);
    }
});

window.addEventListener('resize', () => positionBookLegendHint());
window.addEventListener('scroll', () => positionBookLegendHint(), true);

if (searchInput) {
    searchInput.addEventListener('input', () => {
        clearTimeout(searchTimeout);
        searchTimeout = setTimeout(() => {
            currentPage = 0;
            if (selectedClassId) loadSubjects(selectedClassId);
        }, 250);
    });
}

if (bookStatusFilter) {
    bookStatusFilter.addEventListener('change', () => {
        currentPage = 0;
        if (selectedClassId) loadSubjects(selectedClassId);
    });
}

if (pageSizeSelect) {
    pageSizeSelect.addEventListener('change', () => {
        pageSize = Number(pageSizeSelect.value) || 25;
        currentPage = 0;
        if (selectedClassId) loadSubjects(selectedClassId);
    });
}

if (resetFiltersBtn) {
    resetFiltersBtn.addEventListener('click', () => {
        if (searchInput) searchInput.value = '';
        if (bookStatusFilter) bookStatusFilter.value = '';
        currentPage = 0;
        if (selectedClassId) loadSubjects(selectedClassId);
    });
}

if (prevPageBtn) {
    prevPageBtn.addEventListener('click', () => {
        if (currentPage <= 0 || !selectedClassId) return;
        currentPage -= 1;
        loadSubjects(selectedClassId);
    });
}

if (nextPageBtn) {
    nextPageBtn.addEventListener('click', () => {
        if (totalPages === 0 || currentPage >= totalPages - 1 || !selectedClassId) return;
        currentPage += 1;
        loadSubjects(selectedClassId);
    });
}

// Initial load: the class is provided by the query string (?classeId=...)
(async () => {
    const params = new URLSearchParams(window.location.search);
    const paramClassIdRaw = params.get('classeId');
    const paramClassId = paramClassIdRaw ? Number(paramClassIdRaw) : null;

    // Hide/disable the class selector if it exists (class is fixed by URL)
    if (classSelect) {
        classSelect.disabled = true;
        classSelect.style.display = 'none';

        // If options are populated asynchronously elsewhere, update the label when they appear
        const observer = new MutationObserver(() => updateSelectedClassDisplay());
        observer.observe(classSelect, { childList: true, subtree: true });
        setTimeout(() => observer.disconnect(), 5000);
    }

    if (!paramClassId || !Number.isFinite(paramClassId)) {
        selectedClassId = null;
        selectedClassLabel = null;
        canAddSubject = false;
        subjects = [];
        totalItems = 0;
        totalPages = 0;
        currentPage = 0;
        updatePagination();
        updateSubjectCreateAvailability();
        renderTable();
        return;
    }

    selectedClassId = paramClassId;
    selectedClassLabel = null;
    updateSelectedClassDisplay();
    await loadSubjects(selectedClassId);
})();


// -------------------------
// Particle System (unchanged)
// -------------------------

class Particle {
    constructor(canvas, ctx) {
        this.canvas = canvas;
        this.ctx = ctx;
        this.x = Math.random() * canvas.width;
        this.y = Math.random() * canvas.height;
        this.size = Math.random() * 2 + 1;
        this.baseX = this.x;
        this.baseY = this.y;
        this.density = (Math.random() * 30) + 1;
        this.velocity = {
            x: (Math.random() - 0.5) * 0.5,
            y: (Math.random() - 0.5) * 0.5
        };
    }

    draw() {
        this.ctx.fillStyle = 'rgba(109, 0, 254, 0.3)';
        this.ctx.beginPath();
        this.ctx.arc(this.x, this.y, this.size, 0, Math.PI * 2);
        this.ctx.closePath();
        this.ctx.fill();
    }

    update(mouse) {
        this.x += this.velocity.x;
        this.y += this.velocity.y;

        if (this.x > this.canvas.width || this.x < 0) this.velocity.x *= -1;
        if (this.y > this.canvas.height || this.y < 0) this.velocity.y *= -1;

        const dx = (mouse?.x ?? 0) - this.x;
        const dy = (mouse?.y ?? 0) - this.y;
        const distance = Math.sqrt(dx * dx + dy * dy);

        if (!distance || !isFinite(distance)) return;

        const forceDirectionX = dx / distance;
        const forceDirectionY = dy / distance;
        const maxDistance = 150;
        const force = (maxDistance - distance) / maxDistance;
        const directionX = forceDirectionX * force * this.density;
        const directionY = forceDirectionY * force * this.density;

        if (distance < maxDistance) {
            this.x -= directionX;
            this.y -= directionY;
        }
    }
}

class ParticleSystem {
    constructor() {
        this.canvas = document.getElementById('particle-canvas');
        this.ctx = this.canvas.getContext('2d');
        this.particles = [];
        this.numberOfParticles = 100;
        this.mouse = { x: null, y: null };

        this.init();
        this.animate();

        window.addEventListener('mousemove', (e) => {
            this.mouse.x = e.x;
            this.mouse.y = e.y;
        });

        window.addEventListener('resize', () => {
            this.canvas.width = window.innerWidth;
            this.canvas.height = window.innerHeight;
            this.init();
        });
    }

    init() {
        this.canvas.width = window.innerWidth;
        this.canvas.height = window.innerHeight;
        this.particles = [];
        for (let i = 0; i < this.numberOfParticles; i++) {
            this.particles.push(new Particle(this.canvas, this.ctx));
        }
    }

    connect() {
        let opacityValue = 1;
        for (let a = 0; a < this.particles.length; a++) {
            for (let b = a; b < this.particles.length; b++) {
                const dx = this.particles[a].x - this.particles[b].x;
                const dy = this.particles[a].y - this.particles[b].y;
                const distance = Math.sqrt(dx * dx + dy * dy);

                if (distance < 100) {
                    opacityValue = 1 - (distance / 100);
                    this.ctx.strokeStyle = `rgba(109, 0, 254, ${opacityValue * 0.2})`;
                    this.ctx.lineWidth = 1;
                    this.ctx.beginPath();
                    this.ctx.moveTo(this.particles[a].x, this.particles[a].y);
                    this.ctx.lineTo(this.particles[b].x, this.particles[b].y);
                    this.ctx.stroke();
                }
            }
        }
    }

    animate() {
        this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
        for (let i = 0; i < this.particles.length; i++) {
            this.particles[i].update(this.mouse);
            this.particles[i].draw();
        }
        this.connect();
        requestAnimationFrame(this.animate.bind(this));
    }
}

// Initialize Particle System
document.addEventListener('DOMContentLoaded', () => {
    new ParticleSystem();
});

// Expose functions to global scope for HTML onclick
window.openEditModal = openEditModal;
window.openDeleteModal = openDeleteModal;
window.openBookModal = openBookModal;
window.closeAllModals = closeAllModals;
