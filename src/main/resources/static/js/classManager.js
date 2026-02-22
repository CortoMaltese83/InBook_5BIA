// Initial Data
let classes = [];
const API_URL = '/classe-data';

let editingId = null;
let deleteId = null;
let dateInterval = null;

// DOM Elements
const tableBody = document.getElementById('table-body');
const modalBackdrop = document.getElementById('modal-backdrop');
const formModal = document.getElementById('form-modal');
const deleteModal = document.getElementById('delete-modal');
const modalTitle = document.getElementById('modal-title');
const classForm = document.getElementById('class-form');
const btnSave = document.getElementById('btn-save');
const btnConfirmDelete = document.getElementById('btn-confirm-delete');
const addBtn = document.getElementById('add-btn');
const classIdInput = document.getElementById('class-id');
const deleteClassIdInput = document.getElementById('delete-class-id');
const deleteForm = document.getElementById('delete-form');

// Inputs
const nameInput = document.getElementById('nome');
const yearInput = document.getElementById('class-anno');
const sectionInput = document.getElementById('sezione');
const statusInput = document.getElementById('stato');
const createdAtDisplay = document.getElementById('created-at-display');
const updatedAtDisplay = document.getElementById('updated-at-display');


// --- Helpers ---

const getStatusBadge = (status) => {
    const map = {
        'active': { class: 'status-active', icon: 'fa-check', text: 'Attiva' },
        'archived': { class: 'status-archived', icon: 'fa-archive', text: 'Archiviata' },
        'hidden': { class: 'status-hidden', icon: 'fa-eye-slash', text: 'Nascosta' }
    };
    const s = map[status] || map['active'];
    return `<span class="status-badge ${s.class}"><i class="fa-solid ${s.icon}"></i> ${s.text}</span>`;
};

const formatDate = (date) => {
    // Format YYYY-MM-DD HH:MM:SS
    const d = new Date(date);
    const pad = (n) => n.toString().padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
};


// --- Core Functions ---

async function loadClasses() {
    try {
        const response = await fetch(API_URL);
        if (!response.ok) throw new Error(`Errore nel caricamento delle classi (Status: ${response.status})`);
        const data = await response.json();
        classes = data.map(cls => ({
            id: cls.id,
            nome: cls.nome,
            anno: cls.anno,
            sezione: cls.sezione,
            stato: cls.stato,
            createdAt: cls.created_at,
            updatedAt: cls.updated_at
        }));
        renderTable();
    } catch (error) {
        console.error('Error:', error);
        tableBody.innerHTML = '<tr><td colspan="7" style="text-align:center; padding: 2rem; color: #ef4444;">Errore nel caricamento dei dati: ' + error.message + '</td></tr>';
    }
}

function renderTable() {
    tableBody.innerHTML = '';
    if (classes.length === 0) {
        tableBody.innerHTML = '<tr><td colspan="7" style="text-align:center; padding: 2rem; color: #6b7280;">Nessuna classe presente. Clicca su "Aggiungi" per crearne una nuova.</td></tr>';
        return;
    }

    classes.forEach((cls, index) => {
        const tr = document.createElement('tr');
        tr.style.animationDelay = `${index * 0.05}s`;
        tr.innerHTML = `
            <td><strong>${cls.nome}</strong></td>
            <td>${cls.anno}</td>
            <td>${cls.sezione}</td>
            <td>${getStatusBadge(cls.stato)}</td>
            <td>${formatDate(cls.createdAt)}</td>
            <td>${formatDate(cls.updatedAt)}</td>
            <td>
                <button class="action-btn btn-edit" onclick="openEditModal(${cls.id})"><i class="fa-solid fa-pencil"></i></button>
                <button class="action-btn btn-delete" onclick="openDeleteModal(${cls.id})"><i class="fa-solid fa-trash"></i></button>
            </td>
        `;
        tableBody.appendChild(tr);
    });
}

function openAddModal() {
    editingId = null;
    modalTitle.textContent = "Aggiungi Classe";
    btnSave.textContent = "Aggiungi";
    classForm.action = "/classe";
    classIdInput.value = "";
    showModal(formModal);
}

function openEditModal(id) {
    editingId = id;
    const cls = classes.find(c => c.id === id);
    if (!cls) return;

    modalTitle.textContent = "Modifica Classe";
    btnSave.textContent = "Aggiorna";

    if (cls.nome === `${cls.anno}${cls.sezione}`) {
        isNameManuallyEdited = false;
    } else {
        isNameManuallyEdited = true;
    }

    classForm.action = "/classe/modify";
    classIdInput.value = id;

    // Fill fields
    nameInput.value = cls.nome;
    yearInput.value = cls.anno;
    sectionInput.value = cls.sezione;
    statusInput.value = cls.stato;

    // Display existing dates immediately
    createdAtDisplay.textContent = formatDate(cls.createdAt);
    updatedAtDisplay.textContent = formatDate(cls.updatedAt);

    // Start updating them "real-time"
    startDateUpdates(formatDate(cls.createdAt));

    showModal(formModal);
}

function openDeleteModal(id) {
    deleteId = id;
    deleteClassIdInput.value = id;
    showModal(deleteModal);
}

function showModal(modal) {
    modalBackdrop.classList.add('visible');
    modal.classList.add('visible');
}

function closeAllModals() {
    modalBackdrop.classList.remove('visible');
    formModal.classList.remove('visible');
    deleteModal.classList.remove('visible');
    stopDateUpdates();
}

function startDateUpdates(fixedCreationDate = null) {
    stopDateUpdates();
    // Update immediately
    const update = () => {
        const now = new Date();
        const formatted = formatDate(now);
        updatedAtDisplay.textContent = formatted;

        // If it's a new record, creation date also updates (it's "now").
        // If editing, usually we keep creation date.
        if (!fixedCreationDate) {
            createdAtDisplay.textContent = formatted;
        } else {
            // Even if editing, user might want to see the creation date static
            createdAtDisplay.textContent = fixedCreationDate;
        }
    };
    update();
    dateInterval = setInterval(update, 10000); // 10 seconds
}

function stopDateUpdates() {
    if (dateInterval) clearInterval(dateInterval);
}

// --- Event Listeners ---

addBtn.addEventListener('click', openAddModal);

// Traditional form submission will happen automatically
// due to method="POST" and action="..." in HTML.
// We only keep the event listener if we want to do client-side validation,
// but for now let's keep it simple as requested.

// Close on backdrop click
modalBackdrop.addEventListener('click', closeAllModals);

// Initial Render
loadClasses();

// Auto-fill Name logic
let isNameManuallyEdited = false;

function updateNameField() {
    if (!isNameManuallyEdited) {
        const year = yearInput.value;
        const section = sectionInput.value.toUpperCase();
        if (year || section) {
            nameInput.value = `${year}${section}`;
        }
    }
}

yearInput.addEventListener('input', updateNameField);
sectionInput.addEventListener('input', updateNameField);

nameInput.addEventListener('input', () => {
    // If user manually types something that isn't the auto-generated value, mark as edited
    const autoValue = `${yearInput.value}${sectionInput.value.toUpperCase()}`;
    if (nameInput.value !== autoValue) {
        isNameManuallyEdited = true;
    }
    // If user clears it or matches auto, maybe reset? Let's keep it simple: manual input = manual control.
});

// --- Particle System ---

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
        // Natural movement
        this.x += this.velocity.x;
        this.y += this.velocity.y;

        // Bounce off edges
        if (this.x > this.canvas.width || this.x < 0) this.velocity.x *= -1;
        if (this.y > this.canvas.height || this.y < 0) this.velocity.y *= -1;

        // Mouse interaction
        let dx = mouse.x - this.x;
        let dy = mouse.y - this.y;
        let distance = Math.sqrt(dx * dx + dy * dy);
        let forceDirectionX = dx / distance;
        let forceDirectionY = dy / distance;
        let maxDistance = 150;
        let force = (maxDistance - distance) / maxDistance;
        let directionX = forceDirectionX * force * this.density;
        let directionY = forceDirectionY * force * this.density;

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
                let dx = this.particles[a].x - this.particles[b].x;
                let dy = this.particles[a].y - this.particles[b].y;
                let distance = Math.sqrt(dx * dx + dy * dy);

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
window.closeAllModals = closeAllModals;
