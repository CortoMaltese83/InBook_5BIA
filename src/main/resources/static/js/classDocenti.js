let classes = [];
const API_URL = '/';
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
            tableBody.appendChild(tr);
        });
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