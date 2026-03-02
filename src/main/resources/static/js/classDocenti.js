let classes = [];
const API_URL = '/docente/view';
const tableBody = document.getElementById('table-body');

async function loadClasses() {
    try {
        const response = await fetch(API_URL);

        if (!response.ok) {
            throw new Error(`Errore nel caricamento (Status: ${response.status})`);
        }

        const data = await response.json();

        classes = data.map(cls => ({
            id: cls.id,
            nome: cls.nome,
            anno: cls.anno,
            sezione: cls.sezione,
            stato: cls.stato,
            createdAt: cls.createdAt,
            updatedAt: cls.updatedAt
        }));

        renderTable();

    } catch (error) {
        console.error(error);
        tableBody.innerHTML =
            `<tr>
                <td colspan="6" style="text-align:center; padding: 2rem; color: red;">
                    ${error.message}
                </td>
             </tr>`;
    }
}

function renderTable() {

    tableBody.innerHTML = '';

    if (classes.length === 0) {
        tableBody.innerHTML =
            `<tr>
                <td colspan="6" style="text-align:center; padding: 2rem;">
                    Nessuna classe trovata
                </td>
             </tr>`;
        return;
    }

    classes.forEach(cls => {
        const tr = document.createElement('tr');

        tr.innerHTML = `
            <td><strong>${cls.nome}</strong></td>
            <td>${cls.anno}</td>
            <td>${cls.sezione}</td>
            <td>${cls.stato}</td>
            <td>${cls.createdAt}</td>
            <td>${cls.updatedAt}</td>
        `;

        tableBody.appendChild(tr);
    });
}

document.addEventListener("DOMContentLoaded", loadClasses);