/*
 * Hospital Appointments - single page frontend (plain JavaScript, no framework).
 *
 * How it works:
 *  - `state` holds everything the screen needs (logged-in user, lists loaded from the API, ...).
 *  - render() rebuilds the HTML of the page from `state`.
 *  - Clicks and form submits are handled in ONE place each (event delegation at the bottom).
 *  - api() adds the JWT token to every request and turns error responses into readable messages.
 */
(() => {
    'use strict';

    const root = document.getElementById('app');
    const toastEl = document.getElementById('toast');

    // ------------------------------------------------------------------ helpers

    // localStorage can be blocked (private mode), so every access is wrapped.
    const store = {
        get(key) { try { return localStorage.getItem(key); } catch (e) { return null; } },
        set(key, value) { try { localStorage.setItem(key, value); } catch (e) { /* ignore */ } },
        del(key) { try { localStorage.removeItem(key); } catch (e) { /* ignore */ } }
    };

    function safeParse(text) {
        try { return text ? JSON.parse(text) : null; } catch (e) { return null; }
    }

    // Escape text before putting it into HTML (prevents XSS from names, reasons, etc.).
    function esc(value) {
        return String(value ?? '').replace(/[&<>"']/g, (c) => (
            { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]
        ));
    }

    function todayStr() {
        const d = new Date();
        d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
        return d.toISOString().slice(0, 10);
    }

    const hhmm = (time) => String(time || '').slice(0, 5);

    function fmtDate(dateStr) {
        const d = new Date(dateStr + 'T00:00:00');
        if (isNaN(d)) return esc(dateStr);
        return d.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' });
    }

    // "Anita" -> "Dr. Anita", but "Dr. Anita" stays "Dr. Anita".
    const docName = (name) => (/^dr\.?\s/i.test(name || '') ? name : 'Dr. ' + name);

    const isFuture = (slot) => new Date(slot.slotDate + 'T' + slot.startTime) > new Date();

    const badge = (status) => `<span class="badge ${esc(String(status).toLowerCase())}">${esc(status)}</span>`;

    // ------------------------------------------------------------------ state

    const state = {
        token: store.get('token'),
        user: safeParse(store.get('user')),
        authTab: 'login',

        // patient
        doctorQuery: '',
        doctors: null,
        picker: null,        // { mode: 'book' | 'reschedule', doctorId, doctorName, date, slots, reason, appointmentId }
        appointments: null,

        // doctor
        profile: null,
        slots: null,
        docAppointments: null,

        // admin
        stats: null,
        pending: null
    };

    // ------------------------------------------------------------------ API

    async function api(path, options = {}) {
        const headers = { Accept: 'application/json' };
        if (options.body) headers['Content-Type'] = 'application/json';
        const isAuthCall = path.startsWith('/auth/');
        if (state.token && !isAuthCall) headers.Authorization = 'Bearer ' + state.token;

        let res;
        try {
            res = await fetch('/api' + path, { ...options, headers });
        } catch (e) {
            throw new Error('Cannot reach the server. Is the backend running?');
        }

        const text = await res.text();
        const data = safeParse(text);

        if (!res.ok) {
            if (res.status === 401 && state.token && !isAuthCall) {
                logout();
                throw new Error('Your session expired. Please log in again.');
            }
            throw new Error(errorMessage(data, res.status));
        }
        return data;
    }

    function errorMessage(data, status) {
        if (data && data.fieldErrors) {
            const messages = Object.values(data.fieldErrors);
            if (messages.length) return messages.join('. ');
        }
        if (data && data.message) return data.message;
        return 'Request failed (' + status + ')';
    }

    const get = (path) => api(path);
    const post = (path, body) => api(path, { method: 'POST', body: JSON.stringify(body) });
    const put = (path, body) => api(path, { method: 'PUT', body: body ? JSON.stringify(body) : undefined });
    const del = (path) => api(path, { method: 'DELETE' });

    // ------------------------------------------------------------------ small UI helpers

    let toastTimer = null;
    function flash(message, type) {
        toastEl.textContent = message;
        toastEl.className = 'toast' + (type === 'error' ? ' error' : '');
        toastEl.hidden = false;
        clearTimeout(toastTimer);
        toastTimer = setTimeout(() => { toastEl.hidden = true; }, 4500);
    }

    // Runs an async action, shows errors as a toast, and ignores clicks while one action is running.
    let busy = false;
    async function run(fn) {
        if (busy) return;
        busy = true;
        try {
            await fn();
        } catch (e) {
            flash(e.message || 'Something went wrong', 'error');
        } finally {
            busy = false;
        }
    }

    function logout() {
        store.del('token');
        store.del('user');
        state.token = null;
        state.user = null;
        state.authTab = 'login';
        state.doctors = null;
        state.picker = null;
        state.appointments = null;
        state.profile = null;
        state.slots = null;
        state.docAppointments = null;
        state.stats = null;
        state.pending = null;
        render();
    }

    // ------------------------------------------------------------------ data loading

    async function loadDoctors(page) {
        const q = encodeURIComponent(state.doctorQuery.trim());
        state.doctors = await get(`/doctors?specialization=${q}&page=${page}&size=6`);
    }

    async function loadAppointments() {
        state.appointments = await get('/patient/appointments/my');
    }

    async function loadPickerSlots() {
        const p = state.picker;
        if (!p) return;
        p.slots = await get(`/doctors/${p.doctorId}/slots?date=${encodeURIComponent(p.date)}`);
    }

    async function loadDoctorData() {
        [state.profile, state.slots, state.docAppointments] = await Promise.all([
            get('/doctor/profile'),
            get('/doctor/slots'),
            get('/doctor/appointments')
        ]);
    }

    async function loadAdminData() {
        [state.stats, state.pending] = await Promise.all([
            get('/admin/stats'),
            get('/admin/doctors/pending')
        ]);
    }

    async function loadRoleData() {
        const role = state.user && state.user.role;
        if (role === 'PATIENT') await Promise.all([loadDoctors(0), loadAppointments()]);
        else if (role === 'DOCTOR') await loadDoctorData();
        else if (role === 'ADMIN') await loadAdminData();
    }

    // ------------------------------------------------------------------ views: auth

    function authView() {
        const registering = state.authTab === 'register';
        return `
        <div class="auth-wrap">
            <div class="card auth">
                <h1>Hospital Appointments</h1>
                <p class="muted">Book a doctor online, or manage your availability.</p>
                <div class="tabs">
                    <button class="tab ${registering ? '' : 'active'}" data-action="tab" data-tab="login">Login</button>
                    <button class="tab ${registering ? 'active' : ''}" data-action="tab" data-tab="register">Register</button>
                </div>
                ${registering ? registerForm() : loginForm()}
            </div>
        </div>`;
    }

    function loginForm() {
        return `
        <form class="stack" data-form="login">
            <label>Email<input type="email" name="email" required autocomplete="username"></label>
            <label>Password<input type="password" name="password" required autocomplete="current-password"></label>
            <button class="btn primary" type="submit">Login</button>
        </form>`;
    }

    function registerForm() {
        return `
        <form class="stack" data-form="register">
            <label>I am a
                <select name="role" id="reg-role">
                    <option value="PATIENT">Patient</option>
                    <option value="DOCTOR">Doctor</option>
                </select>
            </label>
            <label>Full name<input name="name" required></label>
            <label>Email<input type="email" name="email" required autocomplete="username"></label>
            <label>Password (at least 6 characters)<input type="password" name="password" minlength="6" required autocomplete="new-password"></label>

            <div class="stack" id="patient-fields">
                <label>Age<input type="number" name="age" min="0" max="120"></label>
                <label>Gender
                    <select name="gender">
                        <option value="">Select</option>
                        <option>Male</option>
                        <option>Female</option>
                        <option>Other</option>
                    </select>
                </label>
                <label>Phone<input name="phone" inputmode="tel"></label>
            </div>

            <div class="stack" id="doctor-fields" hidden>
                <label>Specialization<input name="specialization" placeholder="e.g. Cardiology"></label>
                <label>Experience (years)<input type="number" name="experienceYears" min="0"></label>
                <label>Consultation fee<input type="number" name="fee" min="0" step="0.01"></label>
                <p class="muted">A new doctor account must be approved by the admin before patients can see it.</p>
            </div>

            <button class="btn primary" type="submit">Create account</button>
        </form>`;
    }

    // ------------------------------------------------------------------ views: shared

    function header() {
        const u = state.user;
        return `
        <header class="topbar">
            <div class="brand">Hospital Appointments</div>
            <div class="who">
                <span>${esc(u.name)}</span>
                <span class="badge role">${esc(u.role)}</span>
                <button class="btn small" data-action="logout">Logout</button>
            </div>
        </header>`;
    }

    // ------------------------------------------------------------------ views: patient

    function patientView() {
        return `
        <section class="card">
            <h2>Find a doctor</h2>
            <form class="row" data-form="search">
                <input name="q" placeholder="Search by specialization, e.g. Cardiology" value="${esc(state.doctorQuery)}">
                <button class="btn primary" type="submit">Search</button>
            </form>
            ${doctorList()}
        </section>
        ${pickerView()}
        <section class="card">
            <h2>My appointments</h2>
            ${patientAppointments()}
        </section>`;
    }

    function doctorList() {
        const page = state.doctors;
        if (!page) return '<p class="muted">Loading...</p>';
        if (!page.content.length) return '<p class="muted">No doctors found.</p>';

        const cards = page.content.map((d) => `
            <div class="doctor">
                <div>
                    <strong>${esc(docName(d.name))}</strong>
                    <div class="muted">${esc(d.specialization)} &middot; ${esc(d.experienceYears ?? 0)} yrs experience &middot; Fee ${esc(d.fee ?? '-')}</div>
                </div>
                <button class="btn small" data-action="pick-doctor" data-id="${d.id}" data-name="${esc(d.name)}">View slots</button>
            </div>`).join('');

        const pager = page.totalPages > 1 ? `
            <div class="pager">
                <button class="btn small" data-action="doctor-page" data-page="${page.number - 1}" ${page.first ? 'disabled' : ''}>Previous</button>
                <span class="muted">Page ${page.number + 1} of ${page.totalPages}</span>
                <button class="btn small" data-action="doctor-page" data-page="${page.number + 1}" ${page.last ? 'disabled' : ''}>Next</button>
            </div>` : '';

        return `<div class="list">${cards}</div>${pager}`;
    }

    function pickerView() {
        const p = state.picker;
        if (!p) return '';

        const title = p.mode === 'reschedule'
            ? `Reschedule: choose a new slot with ${docName(p.doctorName)}`
            : `Book an appointment with ${docName(p.doctorName)}`;

        let slotsHtml;
        if (!p.slots) {
            slotsHtml = '<p class="muted">Loading slots...</p>';
        } else {
            const free = p.slots.filter(isFuture);
            slotsHtml = free.length
                ? `<div class="slots">${free.map((s) =>
                    `<button class="slot" data-action="slot" data-id="${s.id}">${hhmm(s.startTime)} - ${hhmm(s.endTime)}</button>`).join('')}</div>`
                : '<p class="muted">No free slots on this date. Try another date.</p>';
        }

        const reasonInput = p.mode === 'book'
            ? `<label>Reason for visit (optional)<input id="reason" maxlength="500" placeholder="e.g. chest pain, regular checkup" value="${esc(p.reason)}"></label>`
            : '';

        return `
        <section class="card highlight" id="picker">
            <div class="row between">
                <h2>${esc(title)}</h2>
                <button class="btn small" data-action="close-picker">Close</button>
            </div>
            <div class="row">
                <label>Date<input type="date" id="picker-date" min="${todayStr()}" value="${esc(p.date)}"></label>
                ${reasonInput}
            </div>
            ${slotsHtml}
        </section>`;
    }

    function patientAppointments() {
        const list = state.appointments;
        if (!list) return '<p class="muted">Loading...</p>';
        if (!list.length) return '<p class="muted">You have no appointments yet. Find a doctor above and book a slot.</p>';

        const rows = list.map((a) => `
            <tr>
                <td>${esc(docName(a.doctorName))}<div class="muted">${esc(a.specialization)}</div></td>
                <td>${fmtDate(a.slotDate)}</td>
                <td>${hhmm(a.startTime)} - ${hhmm(a.endTime)}</td>
                <td>${esc(a.reason || '-')}</td>
                <td>${badge(a.status)}</td>
                <td class="actions">${a.status === 'BOOKED' ? `
                    <button class="btn small" data-action="reschedule" data-id="${a.id}" data-doctor="${a.doctorId}" data-name="${esc(a.doctorName)}">Reschedule</button>
                    <button class="btn small danger" data-action="cancel" data-id="${a.id}">Cancel</button>` : ''}
                </td>
            </tr>`).join('');

        return `
        <div class="table-wrap">
            <table>
                <thead><tr><th>Doctor</th><th>Date</th><th>Time</th><th>Reason</th><th>Status</th><th></th></tr></thead>
                <tbody>${rows}</tbody>
            </table>
        </div>`;
    }

    // ------------------------------------------------------------------ views: doctor

    function doctorView() {
        const p = state.profile;
        const waiting = p && !p.approved;

        return `
        ${waiting ? '<div class="banner">Your account is waiting for admin approval. You can add slots as soon as you are approved.</div>' : ''}
        <section class="card">
            <h2>Add availability</h2>
            <p class="muted">Choose a time window and a slot length. Example: 09:00 to 13:00 with 30-minute slots creates 8 slots.</p>
            <form class="grid" data-form="slots">
                <label>Date<input type="date" name="slotDate" min="${todayStr()}" value="${todayStr()}" required></label>
                <label>From<input type="time" name="startTime" value="09:00" required></label>
                <label>To<input type="time" name="endTime" value="13:00" required></label>
                <label>Slot length (minutes)<input type="number" name="slotMinutes" value="30" min="10" max="240" required></label>
                <button class="btn primary" type="submit" ${waiting ? 'disabled' : ''}>Create slots</button>
            </form>
        </section>
        <section class="card">
            <h2>My upcoming slots</h2>
            ${slotTable()}
        </section>
        <section class="card">
            <h2>Appointments</h2>
            ${doctorAppointmentTable()}
        </section>`;
    }

    function slotTable() {
        const list = state.slots;
        if (!list) return '<p class="muted">Loading...</p>';
        if (!list.length) return '<p class="muted">No upcoming slots yet.</p>';

        const rows = list.map((s) => `
            <tr>
                <td>${fmtDate(s.slotDate)}</td>
                <td>${hhmm(s.startTime)} - ${hhmm(s.endTime)}</td>
                <td>${badge(s.status)}</td>
                <td class="actions">${s.status === 'AVAILABLE'
                    ? `<button class="btn small danger" data-action="delete-slot" data-id="${s.id}">Delete</button>` : ''}</td>
            </tr>`).join('');

        return `
        <div class="table-wrap">
            <table>
                <thead><tr><th>Date</th><th>Time</th><th>Status</th><th></th></tr></thead>
                <tbody>${rows}</tbody>
            </table>
        </div>`;
    }

    function doctorAppointmentTable() {
        const list = state.docAppointments;
        if (!list) return '<p class="muted">Loading...</p>';
        if (!list.length) return '<p class="muted">No upcoming appointments.</p>';

        const rows = list.map((a) => `
            <tr>
                <td>${esc(a.patientName)}<div class="muted">${esc(a.patientPhone || '')}</div></td>
                <td>${fmtDate(a.slotDate)}</td>
                <td>${hhmm(a.startTime)} - ${hhmm(a.endTime)}</td>
                <td>${esc(a.reason || '-')}</td>
                <td>${badge(a.status)}</td>
                <td class="actions">${a.status === 'BOOKED'
                    ? `<button class="btn small" data-action="complete" data-id="${a.id}">Mark completed</button>` : ''}</td>
            </tr>`).join('');

        return `
        <div class="table-wrap">
            <table>
                <thead><tr><th>Patient</th><th>Date</th><th>Time</th><th>Reason</th><th>Status</th><th></th></tr></thead>
                <tbody>${rows}</tbody>
            </table>
        </div>`;
    }

    // ------------------------------------------------------------------ views: admin

    function adminView() {
        const s = state.stats;
        const stat = (label, value) => `<div class="stat"><strong>${esc(value)}</strong><span class="muted">${esc(label)}</span></div>`;

        const statsHtml = s ? `
            <div class="stats">
                ${stat('Users', s.totalUsers)}
                ${stat('Patients', s.patients)}
                ${stat('Doctors', s.doctors)}
                ${stat('Waiting for approval', s.pendingDoctors)}
                ${stat('Appointments', s.totalAppointments)}
                ${stat('Booked', s.booked)}
                ${stat('Completed', s.completed)}
                ${stat('Cancelled', s.cancelled)}
            </div>` : '<p class="muted">Loading...</p>';

        let pendingHtml;
        if (!state.pending) {
            pendingHtml = '<p class="muted">Loading...</p>';
        } else if (!state.pending.length) {
            pendingHtml = '<p class="muted">No doctors are waiting for approval.</p>';
        } else {
            const rows = state.pending.map((d) => `
                <tr>
                    <td>${esc(docName(d.name))}</td>
                    <td>${esc(d.specialization)}</td>
                    <td>${esc(d.experienceYears ?? 0)} yrs</td>
                    <td>${esc(d.fee ?? '-')}</td>
                    <td class="actions"><button class="btn small primary" data-action="approve" data-id="${d.id}">Approve</button></td>
                </tr>`).join('');
            pendingHtml = `
            <div class="table-wrap">
                <table>
                    <thead><tr><th>Doctor</th><th>Specialization</th><th>Experience</th><th>Fee</th><th></th></tr></thead>
                    <tbody>${rows}</tbody>
                </table>
            </div>`;
        }

        return `
        <section class="card"><h2>Overview</h2>${statsHtml}</section>
        <section class="card"><h2>Doctors waiting for approval</h2>${pendingHtml}</section>`;
    }

    // ------------------------------------------------------------------ render

    function render() {
        if (!state.token || !state.user) {
            root.innerHTML = authView();
            return;
        }
        const role = state.user.role;
        let body;
        if (role === 'PATIENT') body = patientView();
        else if (role === 'DOCTOR') body = doctorView();
        else body = adminView();

        root.innerHTML = header() + `<main class="container">${body}</main>`;
    }

    // ------------------------------------------------------------------ actions (clicks)

    function openPicker(mode, doctorId, doctorName, appointmentId) {
        state.picker = { mode, doctorId, doctorName, appointmentId, date: todayStr(), slots: null, reason: '' };
        render();
        const el = document.getElementById('picker');
        if (el && el.scrollIntoView) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
        run(async () => { await loadPickerSlots(); render(); });
    }

    async function chooseSlot(slotId) {
        const p = state.picker;
        if (!p) return;
        try {
            if (p.mode === 'reschedule') {
                await put(`/patient/appointments/${p.appointmentId}/reschedule`, { newSlotId: slotId });
                flash('Appointment rescheduled');
            } else {
                await post('/patient/appointments', { slotId, reason: (p.reason || '').trim() || null });
                flash('Appointment booked');
            }
            state.picker = null;
            await loadAppointments();
            render();
        } catch (e) {
            flash(e.message, 'error');
            // Most likely someone else took the slot: refresh the list so the patient sees what is still free.
            try { await loadPickerSlots(); render(); } catch (ignored) { /* keep the toast */ }
        }
    }

    function handleAction(action, data) {
        switch (action) {
            case 'tab':
                state.authTab = data.tab;
                render();
                break;

            case 'logout':
                logout();
                break;

            case 'doctor-page':
                run(async () => { await loadDoctors(Number(data.page)); render(); });
                break;

            case 'pick-doctor':
                openPicker('book', Number(data.id), data.name);
                break;

            case 'reschedule':
                openPicker('reschedule', Number(data.doctor), data.name, Number(data.id));
                break;

            case 'close-picker':
                state.picker = null;
                render();
                break;

            case 'slot':
                run(() => chooseSlot(Number(data.id)));
                break;

            case 'cancel':
                if (!window.confirm('Cancel this appointment?')) return;
                run(async () => {
                    await put(`/patient/appointments/${data.id}/cancel`);
                    flash('Appointment cancelled');
                    await loadAppointments();
                    render();
                });
                break;

            case 'delete-slot':
                if (!window.confirm('Delete this slot?')) return;
                run(async () => {
                    await del(`/doctor/slots/${data.id}`);
                    flash('Slot deleted');
                    state.slots = await get('/doctor/slots');
                    render();
                });
                break;

            case 'complete':
                run(async () => {
                    await put(`/doctor/appointments/${data.id}/complete`);
                    flash('Appointment marked as completed');
                    state.docAppointments = await get('/doctor/appointments');
                    render();
                });
                break;

            case 'approve':
                run(async () => {
                    await put(`/admin/doctors/${data.id}/approve`);
                    flash('Doctor approved');
                    await loadAdminData();
                    render();
                });
                break;

            default:
                break;
        }
    }

    // ------------------------------------------------------------------ forms (submits)

    function handleForm(name, form) {
        const f = Object.fromEntries(new FormData(form));

        if (name === 'login') {
            run(async () => {
                const res = await post('/auth/login', { email: f.email.trim(), password: f.password });
                state.token = res.token;
                state.user = { name: res.name, email: res.email, role: res.role };
                store.set('token', res.token);
                store.set('user', JSON.stringify(state.user));
                render();
                await loadRoleData();
                render();
            });
        }

        if (name === 'register') {
            const payload = { name: f.name.trim(), email: f.email.trim(), password: f.password, role: f.role };
            if (f.role === 'PATIENT') {
                if (f.age) payload.age = Number(f.age);
                if (f.gender) payload.gender = f.gender;
                if (f.phone) payload.phone = f.phone.trim();
            } else {
                payload.specialization = (f.specialization || '').trim();
                payload.experienceYears = f.experienceYears === '' ? null : Number(f.experienceYears);
                payload.fee = f.fee === '' ? null : Number(f.fee);
            }
            run(async () => {
                const res = await post('/auth/register', payload);
                flash(res.message || 'Account created. Please log in.');
                state.authTab = 'login';
                render();
            });
        }

        if (name === 'search') {
            state.doctorQuery = f.q || '';
            run(async () => { await loadDoctors(0); render(); });
        }

        if (name === 'slots') {
            run(async () => {
                const created = await post('/doctor/slots/bulk', {
                    slotDate: f.slotDate,
                    startTime: f.startTime,
                    endTime: f.endTime,
                    slotMinutes: Number(f.slotMinutes)
                });
                flash(created.length + ' slot(s) created');
                state.slots = await get('/doctor/slots');
                render();
            });
        }
    }

    // ------------------------------------------------------------------ events (one listener per type)

    document.addEventListener('click', (e) => {
        const el = e.target.closest('[data-action]');
        if (!el || el.disabled) return;
        handleAction(el.dataset.action, el.dataset);
    });

    document.addEventListener('submit', (e) => {
        const form = e.target.closest('form[data-form]');
        if (!form) return;
        e.preventDefault();
        handleForm(form.dataset.form, form);
    });

    document.addEventListener('change', (e) => {
        // Register form: show doctor or patient fields.
        if (e.target.id === 'reg-role') {
            const isDoctor = e.target.value === 'DOCTOR';
            document.getElementById('doctor-fields').hidden = !isDoctor;
            document.getElementById('patient-fields').hidden = isDoctor;
        }
        // Slot picker: another date was chosen.
        if (e.target.id === 'picker-date' && state.picker && e.target.value) {
            state.picker.date = e.target.value;
            state.picker.slots = null;
            render();
            run(async () => { await loadPickerSlots(); render(); });
        }
    });

    // Remember the typed reason, so it survives a re-render (for example after changing the date).
    document.addEventListener('input', (e) => {
        if (e.target.id === 'reason' && state.picker) state.picker.reason = e.target.value;
    });

    // ------------------------------------------------------------------ start

    render();
    if (state.token && state.user) {
        run(async () => { await loadRoleData(); render(); });
    }
})();
