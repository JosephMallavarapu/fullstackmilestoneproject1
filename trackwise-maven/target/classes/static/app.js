/* ═══════════════════════════════════════════════════
   TrackWise — SPA Application  v2.0
   Auth · SPA Router · API Client · Page Renderers
   ═══════════════════════════════════════════════════ */

const API = '/api/v1';
let token = localStorage.getItem('tw_token');
let currentUser = null;
try { currentUser = JSON.parse(localStorage.getItem('tw_user')); } catch { currentUser = null; }

/* ═══ API CLIENT ═══ */
async function api(path, opts = {}) {
    const headers = { 'Content-Type': 'application/json' };
    if (token) headers['Authorization'] = 'Bearer ' + token;
    try {
        const res = await fetch(API + path, Object.assign({}, opts, { headers: headers }));
        if (res.status === 401) { logout(); throw new Error('Session expired'); }
        if (res.status === 403) throw new Error('Access denied');
        if (!res.ok) {
            var errText = '';
            try { var j = await res.json(); errText = j.error || j.message || res.statusText; } catch { errText = res.statusText; }
            throw new Error(errText);
        }
        var text = await res.text();
        try { return text ? JSON.parse(text) : null; } catch { return text; }
    } catch (err) {
        if (err.message === 'Session expired') throw err;
        throw err;
    }
}

/* ═══ TOAST ═══ */
function toast(msg, type) {
    type = type || 'info';
    var el = document.createElement('div');
    el.className = 'toast toast-' + type;
    var icon = type === 'success' ? 'check_circle' : type === 'error' ? 'error' : 'info';
    el.innerHTML = '<span class="material-icons-round">' + icon + '</span>' + msg;
    document.getElementById('toast-container').appendChild(el);
    setTimeout(function () { el.style.opacity = '0'; setTimeout(function () { el.remove(); }, 300); }, 3500);
}

/* ═══ AUTH ═══ */
document.getElementById('show-register').addEventListener('click', function (e) { e.preventDefault(); toggleAuth('register'); });
document.getElementById('show-login').addEventListener('click', function (e) { e.preventDefault(); toggleAuth('login'); });

function toggleAuth(form) {
    document.getElementById('login-form').className = 'auth-form' + (form === 'login' ? ' active' : '');
    document.getElementById('register-form').className = 'auth-form' + (form === 'register' ? ' active' : '');
    document.getElementById('auth-error').style.display = 'none';
    document.getElementById('reg-error').style.display = 'none';
}

/* Login */
document.getElementById('login-form').addEventListener('submit', function (e) {
    e.preventDefault();
    var btn = document.getElementById('login-btn');
    btn.disabled = true;
    api('/auth/login', {
        method: 'POST',
        body: JSON.stringify({
            email: document.getElementById('login-email').value,
            password: document.getElementById('login-password').value
        })
    }).then(function (data) {
        if (data && data.error) throw new Error(data.error);
        token = data.token;
        currentUser = { id: data.userId, email: data.email, role: data.role, firstName: data.firstName };
        localStorage.setItem('tw_token', token);
        localStorage.setItem('tw_user', JSON.stringify(currentUser));
        showApp();
        toast('Welcome back, ' + currentUser.firstName + '!', 'success');
    }).catch(function (err) {
        var errEl = document.getElementById('auth-error');
        errEl.textContent = err.message || 'Login failed';
        errEl.style.display = 'block';
    }).finally(function () { btn.disabled = false; });
});

/* Register */
document.getElementById('register-form').addEventListener('submit', function (e) {
    e.preventDefault();
    var btn = document.getElementById('register-btn');
    btn.disabled = true;
    api('/auth/register', {
        method: 'POST',
        body: JSON.stringify({
            firstName: document.getElementById('reg-first').value,
            lastName: document.getElementById('reg-last').value,
            email: document.getElementById('reg-email').value,
            password: document.getElementById('reg-password').value,
            roleId: parseInt(document.getElementById('reg-role').value),
            departmentId: parseInt(document.getElementById('reg-dept').value)
        })
    }).then(function (data) {
        if (data && data.error) throw new Error(data.error);
        toast('Account created! Please sign in.', 'success');
        toggleAuth('login');
        document.getElementById('login-email').value = document.getElementById('reg-email').value;
    }).catch(function (err) {
        var errEl = document.getElementById('reg-error');
        errEl.textContent = err.message || 'Registration failed';
        errEl.style.display = 'block';
    }).finally(function () { btn.disabled = false; });
});

function logout() {
    token = null; currentUser = null;
    localStorage.removeItem('tw_token');
    localStorage.removeItem('tw_user');
    document.getElementById('app').style.display = 'none';
    document.getElementById('auth-screen').style.display = 'flex';
    document.getElementById('login-form').reset();
    document.getElementById('auth-error').style.display = 'none';
    toggleAuth('login');
}

document.getElementById('logout-btn').addEventListener('click', logout);

/* ═══ APP INIT ═══ */
function showApp() {
    document.getElementById('auth-screen').style.display = 'none';
    document.getElementById('app').style.display = 'flex';
    document.getElementById('user-name').textContent = currentUser.firstName || currentUser.email || 'User';
    document.getElementById('user-role').textContent = currentUser.role || 'EMPLOYEE';
    document.getElementById('user-avatar').textContent = (currentUser.firstName || 'U').substring(0, 2).toUpperCase();
    // Role-based: hide Approvals for employees
    var isAdmin = currentUser.role === 'ADMIN' || currentUser.role === 'MANAGER';
    var approvalNav = document.querySelector('[data-page="approvals"]');
    if (approvalNav) approvalNav.style.display = isAdmin ? '' : 'none';
    navigateTo('dashboard');
}

/* ═══ SPA ROUTER ═══ */
var currentPage = 'dashboard';

function navigateTo(page) {
    currentPage = page;
    var items = document.querySelectorAll('.nav-item');
    for (var i = 0; i < items.length; i++) {
        items[i].className = 'nav-item' + (items[i].getAttribute('data-page') === page ? ' active' : '');
    }
    var titles = {
        dashboard: 'Dashboard', expenses: 'My Expenses', approvals: 'Approvals',
        policy: 'Policy Engine', currency: 'Multi-Currency', notifications: 'Notifications',
        erp: 'ERP Integrations', audit: 'Audit & Compliance'
    };
    document.getElementById('page-title').textContent = titles[page] || page;
    document.getElementById('new-expense-btn').style.display = (page === 'expenses' || page === 'dashboard') ? '' : 'none';
    renderPage(page);
}

// Nav click handlers
var navItems = document.querySelectorAll('.nav-item');
for (var i = 0; i < navItems.length; i++) {
    (function (el) {
        el.addEventListener('click', function (e) {
            e.preventDefault();
            navigateTo(el.getAttribute('data-page'));
            document.getElementById('sidebar').classList.remove('open');
        });
    })(navItems[i]);
}

document.getElementById('menu-toggle').addEventListener('click', function () {
    document.getElementById('sidebar').classList.toggle('open');
});

/* ═══ PAGE RENDERER ═══ */
function renderPage(page) {
    var area = document.getElementById('content-area');
    area.innerHTML = '<div class="spinner"></div>';
    var fn;
    switch (page) {
        case 'dashboard': fn = renderDashboard; break;
        case 'expenses': fn = renderExpenses; break;
        case 'approvals': fn = renderApprovals; break;
        case 'policy': fn = renderPolicy; break;
        case 'currency': fn = renderCurrency; break;
        case 'notifications': fn = renderNotifications; break;
        case 'erp': fn = renderErp; break;
        case 'audit': fn = renderAudit; break;
        default: area.innerHTML = '<div class="empty-state"><h3>Page not found</h3></div>'; return;
    }
    fn(area).catch(function (err) {
        console.error('Page render error:', err);
        area.innerHTML = '<div class="empty-state"><span class="material-icons-round">error_outline</span><h3>Error loading page</h3><p>' + err.message + '</p></div>';
    });
}

/* ═══ DASHBOARD ═══ */
async function renderDashboard(area) {
    var isAdmin = currentUser.role === 'ADMIN' || currentUser.role === 'MANAGER';

    if (isAdmin) {
        // ═══ ADMIN DASHBOARD ═══
        var allExpenses = [];
        try { var res = await api('/expenses/all'); allExpenses = (res && res.data) || res || []; } catch (e) { console.warn('admin expenses:', e); }
        if (!Array.isArray(allExpenses)) allExpenses = [];
        var pending = allExpenses.filter(function (e) { return e.status === 'PENDING'; });
        var approved = allExpenses.filter(function (e) { return e.status === 'APPROVED'; });
        var rejected = allExpenses.filter(function (e) { return e.status === 'REJECTED'; });
        var totalAmt = allExpenses.reduce(function (s, e) { return s + Number(e.amountUsd || 0); }, 0);

        area.innerHTML =
            '<div class="stats-grid" style="animation:slideUp .4s ease">' +
            statCard('purple', 'receipt_long', allExpenses.length, 'Total Expenses') +
            statCard('orange', 'hourglass_top', pending.length, 'Awaiting Approval') +
            statCard('green', 'check_circle', approved.length, 'Approved') +
            statCard('blue', 'account_balance_wallet', '$' + formatNum(totalAmt), 'Total Amount') +
            '</div>' +
            '<div class="card" style="animation:slideUp .5s ease">' +
            '<div class="card-header"><h3>⏳ Pending Approvals (' + pending.length + ')</h3></div>' +
            '<div class="card-body">' +
            (pending.length === 0
                ? '<div class="empty-state"><span class="material-icons-round">check_circle</span><h3>All caught up!</h3><p>No expenses pending approval</p></div>'
                : expenseTable(pending, true)) +
            '</div></div>';
    } else {
        // ═══ EMPLOYEE DASHBOARD ═══
        var myExp = [];
        try { var res2 = await api('/expenses/user/' + currentUser.id); myExp = (res2 && res2.data) || res2 || []; } catch (e) { console.warn('my expenses:', e); }
        if (!Array.isArray(myExp)) myExp = [];
        var sub = myExp.length;
        var pend = myExp.filter(function (e) { return e.status === 'PENDING'; }).length;
        var appr = myExp.filter(function (e) { return e.status === 'APPROVED'; }).length;
        var rej = myExp.filter(function (e) { return e.status === 'REJECTED'; }).length;
        var amt = myExp.reduce(function (s, e) { return s + Number(e.amountUsd || 0); }, 0);

        area.innerHTML =
            '<div class="stats-grid" style="animation:slideUp .4s ease">' +
            statCard('purple', 'send', sub, 'Submitted') +
            statCard('orange', 'hourglass_top', pend, 'Pending') +
            statCard('green', 'check_circle', appr, 'Approved') +
            statCard('blue', 'account_balance_wallet', '$' + formatNum(amt), 'Total Amount') +
            '</div>' +
            '<div style="display:grid;grid-template-columns:1fr 1fr;gap:16px" class="dashboard-grid">' +
            '<div class="card" style="animation:slideUp .5s ease"><div class="card-header"><h3>Quick Actions</h3></div><div style="padding:20px;display:grid;gap:10px">' +
            '<button class="btn btn-primary btn-full" onclick="openExpenseModal()"><span class="material-icons-round">add</span>Submit New Expense</button>' +
            '<button class="btn btn-ghost btn-full" onclick="navigateTo(\'expenses\')"><span class="material-icons-round">receipt_long</span>View My Expenses</button>' +
            '<button class="btn btn-ghost btn-full" onclick="navigateTo(\'notifications\')"><span class="material-icons-round">notifications</span>View Notifications</button>' +
            '</div></div>' +
            '<div class="card" style="animation:slideUp .6s ease"><div class="card-header"><h3>Recent Expenses</h3></div><div class="card-body">' +
            (myExp.length === 0
                ? '<div class="empty-state"><span class="material-icons-round">receipt_long</span><h3>No expenses yet</h3><p>Submit your first expense!</p></div>'
                : '<table class="data-table"><thead><tr><th>Title</th><th>Amount</th><th>Status</th></tr></thead><tbody>' +
                myExp.slice(0, 5).map(function (e) {
                    return '<tr><td>' + (e.title || '—') + '</td><td>$' + Number(e.amountUsd || 0).toFixed(2) + '</td><td><span class="status status-' + (e.status || '').toLowerCase() + '">' + (e.status || '—') + '</span></td></tr>';
                }).join('') + '</tbody></table>') +
            '</div></div></div>';
    }
}

/* ═══ EXPENSES ═══ */
async function renderExpenses(area) {
    var expenses = [];
    try { var res = await api('/expenses/user/' + currentUser.id); expenses = (res && res.data) || res || []; } catch (e) { console.error('expenses:', e); }
    if (!Array.isArray(expenses)) expenses = [];
    area.innerHTML =
        '<div class="card" style="animation:slideUp .4s ease"><div class="card-header"><h3>My Expenses (' + expenses.length + ')</h3>' +
        '<button class="btn btn-primary btn-sm" onclick="openExpenseModal()"><span class="material-icons-round">add</span>New</button></div>' +
        '<div class="card-body">' +
        (expenses.length === 0
            ? '<div class="empty-state"><span class="material-icons-round">receipt_long</span><h3>No expenses yet</h3><p>Submit your first expense to get started</p></div>'
            : expenseTable(expenses, false)) +
        '</div></div>';
}

/* ═══ APPROVALS ═══ */
async function renderApprovals(area) {
    var expenses = [];
    try { var res = await api('/expenses/all'); expenses = (res && res.data) || res || []; } catch (e) { console.warn('approvals:', e); }
    if (!Array.isArray(expenses)) expenses = [];
    var pending = expenses.filter(function (e) { return e.status === 'PENDING'; });
    var approved = expenses.filter(function (e) { return e.status === 'APPROVED'; });
    var rejected = expenses.filter(function (e) { return e.status === 'REJECTED'; });
    var totalAmt = expenses.reduce(function (s, e) { return s + Number(e.amountUsd || 0); }, 0);
    area.innerHTML =
        '<div class="stats-grid" style="animation:slideUp .4s ease">' +
        statCard('purple', 'receipt_long', expenses.length, 'Total Submissions') +
        statCard('orange', 'hourglass_top', pending.length, 'Pending Review') +
        statCard('green', 'check_circle', approved.length, 'Approved') +
        statCard('blue', 'account_balance_wallet', '$' + formatNum(totalAmt), 'Total Value') +
        '</div>' +
        '<div class="card" style="animation:slideUp .5s ease;margin-top:16px"><div class="card-header"><h3>⏳ Pending Approvals (' + pending.length + ')</h3></div>' +
        '<div class="card-body">' +
        (pending.length === 0
            ? '<div class="empty-state"><span class="material-icons-round">check_circle</span><h3>All caught up!</h3><p>No expenses pending approval</p></div>'
            : expenseTable(pending, true)) +
        '</div></div>' +
        (approved.length > 0
            ? '<div class="card" style="animation:slideUp .6s ease;margin-top:16px"><div class="card-header"><h3>✅ Recently Approved (' + approved.length + ')</h3></div><div class="card-body">' + expenseTable(approved, false) + '</div></div>'
            : '');
}

async function approveExpense(id) {
    try {
        await api('/expenses/' + id + '/approve', { method: 'POST', body: JSON.stringify({ approverId: currentUser.id, remarks: 'Approved via UI' }) });
        toast('Expense approved!', 'success');
        renderPage(currentPage);
    } catch (err) { toast('Failed: ' + err.message, 'error'); }
}

async function rejectExpense(id) {
    var reason = prompt('Rejection reason:');
    if (!reason) return;
    try {
        await api('/expenses/' + id + '/reject', { method: 'POST', body: JSON.stringify({ approverId: currentUser.id, reason: reason }) });
        toast('Expense rejected', 'success');
        renderPage(currentPage);
    } catch (err) { toast('Failed: ' + err.message, 'error'); }
}

/* ═══ POLICY ═══ */
async function renderPolicy(area) {
    var rules = [];
    try { rules = await api('/policy/rules') || []; } catch { }
    if (!Array.isArray(rules)) rules = [];
    area.innerHTML =
        '<div class="card" style="animation:slideUp .4s ease"><div class="card-header"><h3>Policy Rules</h3></div><div class="card-body">' +
        (rules.length === 0 ? '<div class="empty-state"><span class="material-icons-round">gavel</span><h3>No rules configured</h3></div>' :
            '<table class="data-table"><thead><tr><th>Rule</th><th>Type</th><th>Threshold</th><th>Action</th><th>Active</th></tr></thead><tbody>' +
            rules.map(function (r) {
                return '<tr><td><strong>' + (r.name || '—') + '</strong></td><td>' + (r.ruleType || '—') + '</td><td>' + (r.threshold ? '$' + Number(r.threshold).toFixed(2) : '—') + '</td><td>' + (r.action || '—') + '</td><td><span class="status ' + (r.isActive ? 'status-active' : 'status-inactive') + '">' + (r.isActive ? 'Active' : 'Inactive') + '</span></td></tr>';
            }).join('') + '</tbody></table>') +
        '</div></div>';
}

/* ═══ CURRENCY ═══ */
async function renderCurrency(area) {
    var rates = [];
    try { rates = await api('/currency/rates') || []; } catch { }
    if (!Array.isArray(rates)) rates = [];
    area.innerHTML =
        '<div class="card" style="animation:slideUp .4s ease;margin-bottom:16px"><div class="card-header"><h3>Currency Converter</h3></div>' +
        '<div style="padding:20px"><div class="form-row">' +
        '<div class="form-group"><label>Amount</label><input type="number" id="conv-amount" value="100" step="0.01"></div>' +
        '<div class="form-group"><label>From</label><select id="conv-from"><option>USD</option><option>EUR</option><option>GBP</option><option>INR</option><option>JPY</option></select></div>' +
        '<div class="form-group"><label>To</label><select id="conv-to"><option>INR</option><option>EUR</option><option>GBP</option><option>USD</option><option>JPY</option></select></div>' +
        '<div class="form-group" style="display:flex;align-items:flex-end"><button class="btn btn-primary" onclick="convertCurrency()"><span class="material-icons-round">currency_exchange</span>Convert</button></div>' +
        '</div><div id="conv-result" style="padding:12px;background:var(--bg-glass);border-radius:8px;margin-top:8px;display:none;font-size:1.1rem;font-weight:600"></div></div></div>' +
        '<div class="card" style="animation:slideUp .5s ease"><div class="card-header"><h3>Cached FX Rates (Base: USD)</h3></div><div class="card-body">' +
        (rates.length === 0 ? '<div class="empty-state"><span class="material-icons-round">currency_exchange</span><h3>No rates cached</h3></div>' :
            '<table class="data-table"><thead><tr><th>Currency</th><th>Rate</th><th>Source</th><th>Updated</th></tr></thead><tbody>' +
            rates.map(function (r) {
                return '<tr><td><strong>' + (r.targetCurrency || '—') + '</strong></td><td>' + Number(r.rate || 0).toFixed(4) + '</td><td>' + (r.source || '—') + '</td><td>' + formatDate(r.fetchedAt) + '</td></tr>';
            }).join('') + '</tbody></table>') +
        '</div></div>';
}

async function convertCurrency() {
    try {
        var res = await api('/currency/convert', { method: 'POST', body: JSON.stringify({ amount: document.getElementById('conv-amount').value, from: document.getElementById('conv-from').value, to: document.getElementById('conv-to').value }) });
        var el = document.getElementById('conv-result');
        el.innerHTML = document.getElementById('conv-amount').value + ' ' + document.getElementById('conv-from').value + ' = <span style="color:var(--accent-light)">' + Number(res.converted).toFixed(2) + ' ' + document.getElementById('conv-to').value + '</span>';
        el.style.display = 'block';
    } catch (err) { toast('Conversion failed: ' + err.message, 'error'); }
}

/* ═══ NOTIFICATIONS ═══ */
async function renderNotifications(area) {
    var notifs = [];
    try { notifs = await api('/notifications/user/' + currentUser.id) || []; } catch { }
    if (!Array.isArray(notifs)) notifs = [];
    area.innerHTML =
        '<div class="card" style="animation:slideUp .4s ease"><div class="card-header"><h3>Notifications (' + notifs.length + ')</h3></div><div class="card-body">' +
        (notifs.length === 0 ? '<div class="empty-state"><span class="material-icons-round">notifications_none</span><h3>No notifications</h3><p>You\'re all caught up</p></div>' :
            notifs.map(function (n) {
                return '<div style="padding:16px 20px;border-bottom:1px solid var(--border);display:flex;align-items:flex-start;gap:14px;cursor:pointer" onclick="markNotifRead(' + n.id + ',this)">' +
                    '<span class="material-icons-round" style="color:' + (n.isRead ? 'var(--text-muted)' : 'var(--accent-light)') + '">' + getNotifIcon(n.type) + '</span>' +
                    '<div style="flex:1"><div style="font-weight:' + (n.isRead ? '400' : '600') + ';font-size:.9rem">' + (n.title || 'Notification') + '</div>' +
                    '<div style="color:var(--text-secondary);font-size:.8rem;margin-top:2px">' + (n.message || '') + '</div>' +
                    '<div style="color:var(--text-muted);font-size:.75rem;margin-top:6px">' + formatDate(n.createdAt) + '</div></div>' +
                    (!n.isRead ? '<span style="width:8px;height:8px;background:var(--accent);border-radius:50%;flex-shrink:0;margin-top:8px"></span>' : '') +
                    '</div>';
            }).join('')) +
        '</div></div>';
}

function getNotifIcon(type) {
    var icons = { EXPENSE_SUBMITTED: 'send', EXPENSE_APPROVED: 'thumb_up', EXPENSE_REJECTED: 'thumb_down', APPROVAL_REQUIRED: 'pending_actions', POLICY_VIOLATION: 'warning' };
    return icons[type] || 'notifications';
}

async function markNotifRead(id, el) {
    try { await api('/notifications/' + id + '/read', { method: 'PUT' }); } catch { }
}

/* ═══ ERP ═══ */
async function renderErp(area) {
    var integrations = [];
    try { integrations = await api('/erp/integrations') || []; } catch { }
    if (!Array.isArray(integrations)) integrations = [];
    area.innerHTML =
        '<div class="card" style="animation:slideUp .4s ease"><div class="card-header"><h3>ERP Integrations</h3></div><div class="card-body">' +
        (integrations.length === 0 ? '<div class="empty-state"><span class="material-icons-round">sync_alt</span><h3>No integrations configured</h3></div>' :
            '<table class="data-table"><thead><tr><th>Provider</th><th>Name</th><th>Status</th><th>Last Synced</th><th>Total</th></tr></thead><tbody>' +
            integrations.map(function (i) {
                return '<tr><td><strong>' + (i.provider || '—') + '</strong></td><td>' + (i.displayName || '—') + '</td><td><span class="status ' + (i.isActive ? 'status-active' : 'status-inactive') + '">' + (i.isActive ? 'Connected' : 'Off') + '</span></td><td>' + formatDate(i.lastSyncedAt) + '</td><td>' + (i.totalSynced || 0) + '</td></tr>';
            }).join('') + '</tbody></table>') +
        '</div></div>';
}

/* ═══ AUDIT ═══ */
async function renderAudit(area) {
    var logs = [];
    try { logs = await api('/audit') || []; } catch { }
    if (!Array.isArray(logs)) logs = [];
    area.innerHTML =
        '<div class="card" style="animation:slideUp .4s ease"><div class="card-header"><h3>Audit Trail</h3></div><div class="card-body">' +
        (logs.length === 0 ? '<div class="empty-state"><span class="material-icons-round">shield</span><h3>No audit entries yet</h3></div>' :
            '<table class="data-table"><thead><tr><th>Time</th><th>Entity</th><th>Action</th><th>By</th></tr></thead><tbody>' +
            logs.slice(0, 50).map(function (a) {
                return '<tr><td>' + formatDate(a.createdAt) + '</td><td><strong>' + (a.entityType || '—') + '</strong> #' + (a.entityId || '') + '</td><td>' + (a.action || '—') + '</td><td>' + (a.performedBy ? (a.performedBy.firstName || '') + ' ' + (a.performedBy.lastName || '') : '—') + '</td></tr>';
            }).join('') + '</tbody></table>') +
        '</div></div>';
}

/* ═══ EXPENSE MODAL ═══ */
document.getElementById('new-expense-btn').addEventListener('click', openExpenseModal);
document.getElementById('modal-close').addEventListener('click', closeExpenseModal);
document.getElementById('modal-cancel').addEventListener('click', closeExpenseModal);
document.getElementById('expense-modal').addEventListener('click', function (e) { if (e.target.id === 'expense-modal') closeExpenseModal(); });

function openExpenseModal() {
    document.getElementById('expense-form').reset();
    document.getElementById('exp-date').value = new Date().toISOString().split('T')[0];
    document.getElementById('expense-modal').style.display = 'flex';
}

function closeExpenseModal() {
    document.getElementById('expense-modal').style.display = 'none';
}

document.getElementById('expense-form').addEventListener('submit', function (e) {
    e.preventDefault();
    var btn = e.target.querySelector('button[type="submit"]');
    btn.disabled = true;
    btn.innerHTML = '<span class="material-icons-round">hourglass_top</span>Submitting...';
    api('/expenses', {
        method: 'POST',
        body: JSON.stringify({
            userId: currentUser.id,
            departmentId: 1,
            categoryId: parseInt(document.getElementById('exp-category').value) || 1,
            title: document.getElementById('exp-title').value,
            description: document.getElementById('exp-desc').value,
            vendor: document.getElementById('exp-vendor').value,
            amount: parseFloat(document.getElementById('exp-amount').value),
            currency: document.getElementById('exp-currency').value,
            expenseDate: document.getElementById('exp-date').value,
            receiptUrl: document.getElementById('exp-receipt').value || null
        })
    }).then(function () {
        closeExpenseModal();
        toast('Expense submitted successfully!', 'success');
        if (currentPage === 'expenses' || currentPage === 'dashboard') renderPage(currentPage);
    }).catch(function (err) { toast('Failed: ' + err.message, 'error'); })
        .finally(function () {
            btn.disabled = false;
            btn.innerHTML = '<span class="material-icons-round">send</span>Submit Expense';
        });
});

/* ═══ HELPERS ═══ */
function formatNum(n) { return Number(n || 0).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }); }
function formatDate(d) { if (!d) return '—'; try { return new Date(d).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' }); } catch { return d; } }

function statCard(color, icon, value, label) {
    return '<div class="stat-card ' + color + '"><div class="stat-icon"><span class="material-icons-round">' + icon + '</span></div><div class="stat-value">' + value + '</div><div class="stat-label">' + label + '</div></div>';
}

function expenseTable(expenses, showActions) {
    return '<table class="data-table"><thead><tr><th>Ref</th><th>Employee</th><th>Title</th><th>Amount</th><th>Date</th><th>Status</th>' + (showActions ? '<th>Actions</th>' : '') + '</tr></thead><tbody>' +
        expenses.map(function (e) {
            var name = e.submittedBy ? ((e.submittedBy.firstName || '') + ' ' + (e.submittedBy.lastName || '')) : '—';
            return '<tr><td><strong>' + (e.referenceCode || '—') + '</strong></td>' +
                '<td>' + name + '</td>' +
                '<td>' + (e.title || '—') + '</td>' +
                '<td><strong>$' + Number(e.amountUsd || 0).toFixed(2) + '</strong><br><span style="color:var(--text-muted);font-size:.7rem">' + (e.amount || '') + ' ' + (e.currency || '') + '</span></td>' +
                '<td>' + (e.expenseDate || '—') + '</td>' +
                '<td><span class="status status-' + (e.status || '').toLowerCase() + '">' + (e.status || '—') + '</span></td>' +
                (showActions ? '<td style="white-space:nowrap"><button class="btn btn-success btn-sm" onclick="approveExpense(' + e.id + ')"><span class="material-icons-round" style="font-size:16px">check</span>Approve</button> <button class="btn btn-danger btn-sm" onclick="rejectExpense(' + e.id + ')"><span class="material-icons-round" style="font-size:16px">close</span>Reject</button></td>' : '') +
                '</tr>';
        }).join('') + '</tbody></table>';
}

/* ═══ STARTUP ═══ */
(function init() {
    console.log('TrackWise v2.0 initializing...');
    if (token && currentUser && currentUser.id) {
        console.log('Resuming session for', currentUser.firstName);
        showApp();
    } else {
        console.log('No session — showing login');
        document.getElementById('auth-screen').style.display = 'flex';
        document.getElementById('app').style.display = 'none';
    }
})();
