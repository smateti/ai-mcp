// All API calls go through cobol-service
const API_BASE = 'http://localhost:8082';

// State
let projects = [];
let selectedProject = null;
let runs = [];
let selectedRun = null;
let programs = [];
let stats = {};
let currentFilter = 'all';
let chartInstance = null;
let barChartInstance = null;
let chatMessages = []; // per-run chat history
let pollInterval = null;

// API helpers
const api = path => fetch(`${API_BASE}${path}`).then(r => r.json());
const apiPost = (path, body) => fetch(`${API_BASE}${path}`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
}).then(r => r.json());
const apiPut = (path, body) => fetch(`${API_BASE}${path}`, {
  method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
}).then(r => r.json());
const apiDelete = path => fetch(`${API_BASE}${path}`, { method: 'DELETE' }).then(r => r.json());

// Init
document.addEventListener('DOMContentLoaded', async () => {
  await loadProjects();
});

// ───── PROJECT MANAGEMENT ─────

async function loadProjects() {
  try {
    projects = await api('/api/projects');
    renderProjectDropdown();
    if (projects.length === 0) {
      showProjectManager();
    }
  } catch (e) {
    document.getElementById('content').innerHTML =
      '<div class="empty-state"><div class="icon">&#9888;</div><p>Cannot connect to service. Ensure cobol-service is running on port 8082.</p></div>';
  }
}

function renderProjectDropdown() {
  const sel = document.getElementById('project-select');
  sel.innerHTML = '<option value="">-- Select Project --</option>' +
    projects.map(p => `<option value="${p.id}"${selectedProject && selectedProject.id === p.id ? ' selected' : ''}>${escapeHtml(p.name)}</option>`).join('');
}

async function onProjectChange() {
  const sel = document.getElementById('project-select');
  const id = sel.value;
  if (!id) {
    selectedProject = null;
    selectedRun = null;
    document.getElementById('run-selector').style.display = 'none';
    document.getElementById('scoped-nav').style.display = 'none';
    document.getElementById('sidebar-footer').textContent = 'Select a project to begin';
    document.getElementById('content').innerHTML = '<div class="empty-state"><div class="icon">&#9670;</div><p>Select a project from the sidebar, or create a new one.</p></div>';
    chatMessages = [];
    return;
  }
  selectedProject = projects.find(p => p.id == id);
  selectedRun = null;
  chatMessages = [];
  await loadRuns();
}

function showProjectManager() {
  clearPolling();
  document.getElementById('breadcrumb').innerHTML = '<span>Project Manager</span>';
  const content = document.getElementById('content');
  content.innerHTML = `
    <div class="project-manager">
      <h2>Projects</h2>
      <div class="project-form-card">
        <h3 id="project-form-title">Register New Project</h3>
        <input type="hidden" id="pf-id" value="">
        <div class="form-group">
          <label>Project Name</label>
          <input type="text" id="pf-name" placeholder="e.g., CardDemo">
        </div>
        <div class="form-group">
          <label>Description <span class="optional">(optional)</span></label>
          <input type="text" id="pf-desc" placeholder="e.g., Credit card processing system">
        </div>
        <div class="form-group">
          <label>Base Path</label>
          <input type="text" id="pf-base" placeholder="e.g., D:/apps/ws/ws12/cbl/carddemo">
        </div>
        <div class="form-row">
          <div class="form-group" style="flex:1">
            <label>Programs Subfolder</label>
            <input type="text" id="pf-prog" placeholder="programs" value="programs">
          </div>
          <div class="form-group" style="flex:1">
            <label>Copybooks Subfolder</label>
            <input type="text" id="pf-copy" placeholder="copybooks" value="copybooks">
          </div>
        </div>
        <div class="form-actions">
          <button class="btn btn-accent" onclick="saveProject()">Save Project</button>
          <button class="btn btn-secondary" onclick="resetProjectForm()">Cancel</button>
        </div>
        <div id="pf-error" class="form-error"></div>
      </div>
      <div id="project-table"></div>
    </div>
  `;
  renderProjectTable();
}

function renderProjectTable() {
  const el = document.getElementById('project-table');
  if (!el) return;
  if (!projects.length) { el.innerHTML = '<p class="text-muted" style="margin-top:16px">No projects registered yet.</p>'; return; }
  el.innerHTML = `
    <div class="table-card" style="margin-top:20px">
      <table>
        <thead><tr><th>Name</th><th>Base Path</th><th>Programs</th><th>Copybooks</th><th>Created</th><th>Actions</th></tr></thead>
        <tbody>${projects.map(p => `<tr>
          <td style="font-weight:600;color:var(--text-primary)">${escapeHtml(p.name)}</td>
          <td style="max-width:250px;overflow:hidden;text-overflow:ellipsis;font-family:Consolas,monospace;font-size:12px">${escapeHtml(p.basePath)}</td>
          <td>${escapeHtml(p.programsSubPath)}</td>
          <td>${escapeHtml(p.copybooksSubPath)}</td>
          <td>${p.createdAt ? new Date(p.createdAt).toLocaleDateString() : '-'}</td>
          <td>
            <button class="btn-sm btn-secondary" onclick="editProject(${p.id})">Edit</button>
            <button class="btn-sm btn-danger" onclick="deleteProject(${p.id})">Delete</button>
          </td>
        </tr>`).join('')}</tbody>
      </table>
    </div>
  `;
}

async function saveProject() {
  const id = document.getElementById('pf-id').value;
  const body = {
    name: document.getElementById('pf-name').value.trim(),
    description: document.getElementById('pf-desc').value.trim(),
    basePath: document.getElementById('pf-base').value.trim(),
    programsSubPath: document.getElementById('pf-prog').value.trim() || 'programs',
    copybooksSubPath: document.getElementById('pf-copy').value.trim() || 'copybooks'
  };
  if (!body.name || !body.basePath) {
    document.getElementById('pf-error').textContent = 'Name and Base Path are required.';
    return;
  }
  try {
    if (id) {
      await apiPut(`/api/projects/${id}`, body);
    } else {
      await apiPost('/api/projects', body);
    }
    await loadProjects();
    resetProjectForm();
    renderProjectTable();
  } catch (e) {
    document.getElementById('pf-error').textContent = 'Failed to save project: ' + e.message;
  }
}

function editProject(id) {
  const p = projects.find(pr => pr.id === id);
  if (!p) return;
  document.getElementById('pf-id').value = p.id;
  document.getElementById('pf-name').value = p.name;
  document.getElementById('pf-desc').value = p.description || '';
  document.getElementById('pf-base').value = p.basePath;
  document.getElementById('pf-prog').value = p.programsSubPath;
  document.getElementById('pf-copy').value = p.copybooksSubPath;
  document.getElementById('project-form-title').textContent = 'Edit Project';
}

async function deleteProject(id) {
  const p = projects.find(pr => pr.id === id);
  if (!confirm(`Delete project "${p.name}" and ALL its runs and data?`)) return;
  try {
    await apiDelete(`/api/projects/${id}`);
    if (selectedProject && selectedProject.id === id) {
      selectedProject = null;
      selectedRun = null;
      document.getElementById('run-selector').style.display = 'none';
      document.getElementById('scoped-nav').style.display = 'none';
    }
    await loadProjects();
    renderProjectTable();
  } catch (e) { alert('Failed to delete: ' + e.message); }
}

function resetProjectForm() {
  document.getElementById('pf-id').value = '';
  document.getElementById('pf-name').value = '';
  document.getElementById('pf-desc').value = '';
  document.getElementById('pf-base').value = '';
  document.getElementById('pf-prog').value = 'programs';
  document.getElementById('pf-copy').value = 'copybooks';
  document.getElementById('pf-error').textContent = '';
  document.getElementById('project-form-title').textContent = 'Register New Project';
}

// ───── RUN MANAGEMENT ─────

async function loadRuns() {
  if (!selectedProject) return;
  document.getElementById('run-selector').style.display = 'block';
  try {
    runs = await api(`/api/projects/${selectedProject.id}/runs`);
    renderRunList();
    const completed = runs.find(r => r.status === 'COMPLETED');
    if (completed) {
      selectRun(completed);
    } else if (runs.length) {
      const running = runs.find(r => r.status === 'RUNNING' || r.status === 'PENDING');
      if (running) {
        selectRun(running);
        startPolling(running.id);
      } else {
        // Show the most recent stopped/failed run
        selectRun(runs[0]);
      }
    } else {
      document.getElementById('scoped-nav').style.display = 'none';
      document.getElementById('sidebar-footer').textContent = `${selectedProject.name} - No runs`;
      document.getElementById('content').innerHTML = '<div class="empty-state"><p>No runs for this project. Click "+ New Run" to start analysis.</p></div>';
    }
  } catch (e) { console.error('Failed to load runs:', e); }
}

function renderRunList() {
  const el = document.getElementById('run-list');
  if (!runs.length) { el.innerHTML = '<div class="text-muted" style="padding:8px 16px;font-size:12px">No runs yet</div>'; return; }
  el.innerHTML = runs.map(r => `
    <div class="run-item ${selectedRun && selectedRun.id === r.id ? 'active' : ''}" onclick="onRunClick(${r.id})">
      <div class="run-item-main">
        <span class="run-status-dot run-status-${r.status.toLowerCase()}"></span>
        <span class="run-item-label">Run #${r.id}</span>
        ${r.programCount ? `<span class="run-item-count">${r.programCount} prg</span>` : ''}
      </div>
      <div class="run-item-sub">
        ${r.status === 'RUNNING' ? `<span class="run-progress-text">${r.currentStep || ''} ${r.progress || 0}%</span>` : ''}
        ${r.startedAt ? '<span>' + new Date(r.startedAt).toLocaleDateString() + '</span>' : ''}
        <button class="run-delete-btn" onclick="event.stopPropagation();deleteRun(${r.id})" title="Delete run">&#10005;</button>
      </div>
    </div>
  `).join('');
}

async function onRunClick(runId) {
  const run = runs.find(r => r.id === runId);
  if (!run) return;
  selectRun(run);
}

async function selectRun(run) {
  selectedRun = run;
  chatMessages = [];
  clearPolling();
  renderRunList();

  if (run.status === 'RUNNING' || run.status === 'PENDING') {
    startPolling(run.id);
    showRunProgress(run);
    return;
  }

  if (run.status === 'COMPLETED' && run.batchRunId) {
    document.getElementById('scoped-nav').style.display = 'block';
    setupSidebar();
    try {
      const brid = run.batchRunId;
      [stats, programs] = await Promise.all([
        api(`/api/search/stats?batchRunId=${brid}`),
        api(`/api/search/programs?batchRunId=${brid}`)
      ]);
      renderProgramList(programs);
      document.getElementById('sidebar-footer').textContent = `${selectedProject.name} - Run #${run.id} (${programs.length} programs)`;
      showDashboard();
    } catch (e) {
      document.getElementById('content').innerHTML = '<div class="empty-state"><p>Failed to load run data.</p></div>';
    }
  } else if (run.status === 'FAILED' || run.status === 'STOPPED') {
    document.getElementById('scoped-nav').style.display = 'none';
    const icon = run.status === 'STOPPED' ? '&#9632;' : '&#10007;';
    const color = run.status === 'STOPPED' ? 'var(--yellow, #d29922)' : 'var(--red)';
    const msg = run.status === 'STOPPED' ? 'Stopped by user' : (run.errorMessage || 'Unknown error');
    document.getElementById('content').innerHTML = `
      <div class="empty-state">
        <div class="icon" style="color:${color}">${icon}</div>
        <p>Run #${run.id} ${run.status.toLowerCase()}: ${escapeHtml(msg)}</p>
        <div style="margin-top:16px;display:flex;gap:8px">
          ${run.batchRunId ? `<button class="btn btn-secondary" onclick="viewStoppedRunData(${run.id}, '${run.batchRunId}')">View Partial Results</button>` : ''}
          <button class="btn btn-danger" onclick="deleteRun(${run.id})">Delete Run</button>
        </div>
      </div>`;
  }
}

function showRunProgress(run) {
  document.getElementById('scoped-nav').style.display = 'none';
  document.getElementById('breadcrumb').innerHTML = `<a onclick="showProjectManager()">Projects</a><span class="sep">/</span><span>${selectedProject.name}</span><span class="sep">/</span><span>Run #${run.id}</span>`;
  document.getElementById('content').innerHTML = `
    <div class="batch-panel">
      <div class="batch-panel-header">
        <h2>Analysis in Progress</h2>
        <div style="display:flex;gap:8px">
          <button class="btn btn-danger" onclick="stopRun(${run.id})">Stop</button>
          <button class="btn btn-secondary btn-sm" onclick="deleteRun(${run.id})">Delete</button>
        </div>
      </div>
      <div class="running-job-card">
        <div class="running-job-header">
          <span class="badge badge-status-${run.status.toLowerCase()}">${run.status}</span>
          <span class="running-job-title">Run #${run.id}</span>
          <span class="running-job-step">${run.currentStep || 'Starting...'}</span>
        </div>
        <div class="progress-bar"><div class="progress-fill" id="progress-fill" style="width:${run.progress||0}%"></div></div>
        <div class="running-job-details">
          <span id="progress-text">${run.progress||0}% complete</span>
          <span>${run.programCount ? run.programCount + ' programs' : ''}</span>
        </div>
      </div>
      <div class="completed-programs-section">
        <h3 id="completed-programs-title">Completed Programs</h3>
        <div id="completed-programs-list" class="completed-programs-list">
          <div class="text-muted">Loading...</div>
        </div>
      </div>
    </div>
  `;
  if (run.batchRunId) loadCompletedPrograms(run.batchRunId);
}

async function loadCompletedPrograms(batchRunId) {
  try {
    const progs = await api(`/api/search/programs?batchRunId=${batchRunId}`);
    const titleEl = document.getElementById('completed-programs-title');
    const listEl = document.getElementById('completed-programs-list');
    if (!titleEl || !listEl) return;

    const analyzed = progs.filter(p => p.businessSummary);
    const parsed = progs.filter(p => !p.businessSummary);
    titleEl.textContent = `Completed Programs (${analyzed.length} analyzed / ${progs.length} parsed)`;

    if (!progs.length) {
      listEl.innerHTML = '<div class="text-muted">No programs parsed yet...</div>';
      return;
    }

    listEl.innerHTML = `<table class="compact-table">
      <thead><tr><th>Program</th><th>Type</th><th>Lines</th><th>Status</th></tr></thead>
      <tbody>
        ${progs.map(p => `<tr>
          <td><span class="program-link" onclick="showProgramFromProgress('${p.programId}')">${p.programName}</span></td>
          <td><span class="badge badge-type-${(p.programType||'').toLowerCase()}">${p.programType}</span></td>
          <td>${p.lineCount}</td>
          <td>${p.businessSummary ? '<span class="badge badge-bool-true">Analyzed</span>' : '<span class="badge badge-bool-false">Parsed</span>'}</td>
        </tr>`).join('')}
      </tbody>
    </table>`;
  } catch (e) {
    const el = document.getElementById('completed-programs-list');
    if (el) el.innerHTML = '<div class="text-muted">Could not load programs.</div>';
  }
}

async function showProgramFromProgress(programId) {
  // Show program detail while keeping polling active, with a back link to progress
  const content = document.getElementById('content');
  content.innerHTML = '<div class="loading"><div class="spinner"></div>Loading program...</div>';

  document.getElementById('breadcrumb').innerHTML =
    `<a onclick="showProjectManager()">Projects</a><span class="sep">/</span><span>${selectedProject.name}</span><span class="sep">/</span><a onclick="showRunProgress(selectedRun)">Run #${selectedRun.id}</a><span class="sep">/</span><span>${programId}</span>`;

  try {
    const [prog, paragraphs, deps] = await Promise.all([
      api(`/api/search/programs/${programId}`),
      api(`/api/search/programs/${programId}/paragraphs`),
      api(`/api/search/programs/${programId}/dependencies`)
    ]);

    content.innerHTML = `
      <div style="margin-bottom:12px">
        <button class="btn btn-secondary btn-sm" onclick="showRunProgress(selectedRun)">&larr; Back to Progress</button>
      </div>
      <div class="tabs">
        <div class="tab active" data-tab="overview">Overview</div>
        <div class="tab" data-tab="rules">Business Rules</div>
        <div class="tab" data-tab="paragraphs">Paragraphs (${paragraphs.length})</div>
        <div class="tab" data-tab="deps">Dependencies (${deps.length})</div>
        <div class="tab" data-tab="source">Source</div>
      </div>
      <div id="tab-overview" class="tab-content active"></div>
      <div id="tab-rules" class="tab-content"></div>
      <div id="tab-paragraphs" class="tab-content"></div>
      <div id="tab-deps" class="tab-content"></div>
      <div id="tab-source" class="tab-content"></div>
    `;

    setupTabs();
    renderOverview(prog);
    renderBusinessRules(prog, paragraphs);
    renderParagraphs(paragraphs);
    renderDependencies(deps);
    renderSource(prog);
  } catch (e) {
    content.innerHTML = `<div class="empty-state"><p>Failed to load program: ${escapeHtml(e.message)}</p><button class="btn btn-secondary" onclick="showRunProgress(selectedRun)">Back to Progress</button></div>`;
  }
}

async function stopRun(runId) {
  if (!confirm('Stop this analysis run?')) return;
  try {
    await apiPost(`/api/jobs/${runId}/stop`, {});
    clearPolling();
    await loadRuns();
    const run = runs.find(r => r.id === runId);
    if (run) selectRun(run);
  } catch (e) { alert('Failed to stop run: ' + e.message); }
}

async function viewStoppedRunData(runId, batchRunId) {
  const run = runs.find(r => r.id === runId);
  if (!run) return;
  selectedRun = run;
  document.getElementById('scoped-nav').style.display = 'block';
  setupSidebar();
  try {
    [stats, programs] = await Promise.all([
      api(`/api/search/stats?batchRunId=${batchRunId}`),
      api(`/api/search/programs?batchRunId=${batchRunId}`)
    ]);
    renderProgramList(programs);
    document.getElementById('sidebar-footer').textContent = `${selectedProject.name} - Run #${runId} (${programs.length} programs, partial)`;
    showDashboard();
  } catch (e) {
    document.getElementById('content').innerHTML = '<div class="empty-state"><p>Failed to load partial run data.</p></div>';
  }
}

const DEFAULT_BUSINESS_RULES_PROMPT = `You are analyzing COBOL source code. List the business rules you find.

A business rule is domain logic that a business analyst would care about:
- Validations on business data (account numbers, amounts, dates, limits)
- Calculations (interest, fees, balances, totals)
- Threshold/limit checks (credit limits, minimum amounts)
- Eligibility conditions (who qualifies for what)
- Business decisions (approve/reject, categorize, route)
- Status transitions (active/inactive, open/closed)

Skip technical/infrastructure concerns: file I/O status checks, CICS SEND/RECEIVE,
screen handling, ABEND processing, program flow control (GOBACK, STOP RUN).

Output one rule per line. Start each line with a category tag in brackets:
[VALIDATION] Account number must be numeric and non-zero
[CALCULATION] Monthly interest = principal * annual rate / 12
[LIMIT_CHECK] Transaction rejected if amount exceeds credit limit

If this program has no business rules (e.g. it is a utility program), output:
NO_BUSINESS_RULES`;

function startNewRun() {
  if (!selectedProject) return;
  showRunConfigModal();
}

function showRunConfigModal() {
  // Remove existing modal if any
  const existing = document.getElementById('run-config-modal');
  if (existing) existing.remove();

  const modal = document.createElement('div');
  modal.id = 'run-config-modal';
  modal.className = 'modal-backdrop';
  modal.innerHTML = `
    <div class="modal-panel">
      <h3>New Analysis Run</h3>
      <p class="modal-subtitle">Project: ${escapeHtml(selectedProject.name)}</p>

      <div class="modal-field">
        <label for="run-label-input">Run Label (optional)</label>
        <input type="text" id="run-label-input" placeholder="e.g. Initial Analysis, After Refactor..." />
      </div>

      <div class="modal-field">
        <button type="button" class="prompt-toggle" onclick="togglePromptSection()">
          &#9654; Advanced: Customize Business Rules Prompt
        </button>
        <div id="prompt-section" class="prompt-section" style="display:none;">
          <p class="prompt-hint">Customize the system prompt sent to the LLM for business rule extraction. Leave as default unless you have specific needs.</p>
          <textarea id="custom-prompt-input" rows="14">${escapeHtml(DEFAULT_BUSINESS_RULES_PROMPT)}</textarea>
          <button type="button" class="btn-small" onclick="document.getElementById('custom-prompt-input').value = DEFAULT_BUSINESS_RULES_PROMPT">Reset to Default</button>
        </div>
      </div>

      <div class="modal-actions">
        <button class="btn btn-primary" onclick="confirmStartRun()">Start Run</button>
        <button class="btn btn-secondary" onclick="closeRunConfigModal()">Cancel</button>
      </div>
    </div>
  `;
  document.body.appendChild(modal);
  // Close on backdrop click
  modal.addEventListener('click', e => { if (e.target === modal) closeRunConfigModal(); });
}

function togglePromptSection() {
  const section = document.getElementById('prompt-section');
  const btn = document.querySelector('.prompt-toggle');
  if (section.style.display === 'none') {
    section.style.display = 'block';
    btn.innerHTML = '&#9660; Advanced: Customize Business Rules Prompt';
  } else {
    section.style.display = 'none';
    btn.innerHTML = '&#9654; Advanced: Customize Business Rules Prompt';
  }
}

function closeRunConfigModal() {
  const modal = document.getElementById('run-config-modal');
  if (modal) modal.remove();
}

async function confirmStartRun() {
  const runLabel = document.getElementById('run-label-input').value.trim();
  const promptValue = document.getElementById('custom-prompt-input').value.trim();
  // Only send customPrompt if user changed it from default
  const customPrompt = (promptValue === DEFAULT_BUSINESS_RULES_PROMPT.trim()) ? '' : promptValue;

  closeRunConfigModal();

  try {
    const body = { runLabel };
    if (customPrompt) body.customPrompt = customPrompt;
    const result = await apiPost(`/api/projects/${selectedProject.id}/runs`, body);
    await loadRuns();
    const newRun = runs.find(r => r.id === result.id);
    if (newRun) selectRun(newRun);
  } catch (e) { alert('Failed to start run: ' + e.message); }
}

async function deleteRun(runId) {
  if (!confirm(`Delete Run #${runId} and all its data? This will stop the batch (if running) and remove all ES + Qdrant artifacts.`)) return;
  try {
    clearPolling();
    await apiDelete(`/api/jobs/${runId}`);
    if (selectedRun && selectedRun.id === runId) {
      selectedRun = null;
      document.getElementById('scoped-nav').style.display = 'none';
    }
    await loadRuns();
  } catch (e) { alert('Failed to delete run: ' + e.message); }
}

// ───── POLLING ─────

function startPolling(jobId) {
  clearPolling();
  pollInterval = setInterval(async () => {
    try {
      const job = await api(`/api/jobs/${jobId}`);
      const idx = runs.findIndex(r => r.id === jobId);
      if (idx >= 0) runs[idx] = job;
      if (selectedRun && selectedRun.id === jobId) {
        selectedRun = job;
        const fill = document.getElementById('progress-fill');
        const text = document.getElementById('progress-text');
        if (fill) fill.style.width = `${job.progress||0}%`;
        if (text) text.textContent = `${job.currentStep||'Running'} - ${job.progress||0}%`;
        // Refresh completed programs list
        if (job.batchRunId) loadCompletedPrograms(job.batchRunId);
      }
      renderRunList();
      if (job.status === 'COMPLETED' || job.status === 'FAILED' || job.status === 'STOPPED') {
        clearPolling();
        await loadRuns();
        const updated = runs.find(r => r.id === job.id);
        if (updated) selectRun(updated);
      }
    } catch (e) { /* keep polling */ }
  }, 3000);
}

function clearPolling() {
  if (pollInterval) { clearInterval(pollInterval); pollInterval = null; }
}

// ───── SIDEBAR SETUP ─────

function setupSidebar() {
  const searchEl = document.getElementById('search');
  const newSearch = searchEl.cloneNode(true);
  searchEl.parentNode.replaceChild(newSearch, searchEl);
  newSearch.addEventListener('input', e => {
    const q = e.target.value.toLowerCase();
    filterPrograms(q, currentFilter);
  });

  document.querySelectorAll('.filter-btn').forEach(btn => {
    const newBtn = btn.cloneNode(true);
    btn.parentNode.replaceChild(newBtn, btn);
    newBtn.addEventListener('click', () => {
      document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));
      newBtn.classList.add('active');
      currentFilter = newBtn.dataset.filter;
      const q = document.getElementById('search').value.toLowerCase();
      filterPrograms(q, currentFilter);
    });
  });

  document.querySelectorAll('.nav-item[data-view]').forEach(item => {
    const newItem = item.cloneNode(true);
    item.parentNode.replaceChild(newItem, item);
    newItem.addEventListener('click', () => {
      if (newItem.dataset.view === 'dashboard') showDashboard();
      else if (newItem.dataset.view === 'graph') showGraph();
    });
  });
}

function filterPrograms(query, filter) {
  const filtered = programs.filter(p => {
    const matchesQuery = p.programName.toLowerCase().includes(query);
    const matchesFilter = filter === 'all' || p.programType === filter;
    return matchesQuery && matchesFilter;
  });
  renderProgramList(filtered);
}

function renderProgramList(list) {
  const el = document.getElementById('program-list');
  if (!el) return;
  document.getElementById('program-count').textContent = `Programs (${list.length})`;
  el.innerHTML = list.map(p => `
    <div class="nav-item" onclick="showProgram('${p.programId}')">
      ${p.programName}
      <span class="badge badge-${p.programType === 'CICS' ? 'cics' : p.programType === 'BATCH' ? 'batch' : 'sub'}">${p.programType}</span>
    </div>
  `).join('');
}

function setActiveNav(view) {
  document.querySelectorAll('.nav-item').forEach(i => i.classList.remove('active'));
  const navItem = document.querySelector(`.nav-item[data-view="${view}"]`);
  if (navItem) navItem.classList.add('active');
}

function batchRunParam() {
  return selectedRun && selectedRun.batchRunId ? `batchRunId=${selectedRun.batchRunId}` : '';
}

// ───── DASHBOARD ─────

async function showDashboard() {
  setActiveNav('dashboard');
  document.getElementById('breadcrumb').innerHTML =
    `<a onclick="showProjectManager()">Projects</a><span class="sep">/</span><span>${selectedProject.name}</span><span class="sep">/</span><a onclick="showDashboard()">Dashboard</a>`;

  const brp = batchRunParam();
  try {
    [stats, programs] = await Promise.all([
      api(`/api/search/stats?${brp}`),
      api(`/api/search/programs?${brp}`)
    ]);
    renderProgramList(programs);
    document.getElementById('sidebar-footer').textContent = `${selectedProject.name} - Run #${selectedRun.id} (${programs.length} programs)`;
  } catch (e) { /* use cached */ }

  const content = document.getElementById('content');
  content.innerHTML = `
    <div class="batch-panel-header" style="margin-bottom:16px">
      <h2 style="margin:0;font-size:18px;font-weight:600">Run #${selectedRun.id} ${selectedRun.status === 'COMPLETED' ? '' : `<span class="badge badge-status-${selectedRun.status.toLowerCase()}">${selectedRun.status}</span>`}</h2>
      <button class="btn btn-danger btn-sm" onclick="deleteRun(${selectedRun.id})">Delete Run</button>
    </div>
    <div class="stats-grid">
      <div class="stat-card"><div class="label">Total Programs</div><div class="value blue">${stats.totalPrograms || 0}</div></div>
      <div class="stat-card"><div class="label">CICS Programs</div><div class="value cyan">${stats.cicsPrograms || 0}</div></div>
      <div class="stat-card"><div class="label">Batch Programs</div><div class="value green">${stats.batchPrograms || 0}</div></div>
      <div class="stat-card"><div class="label">Subroutines</div><div class="value purple">${stats.subroutinePrograms || 0}</div></div>
      <div class="stat-card"><div class="label">Total Lines</div><div class="value orange">${stats.totalLines || 0}</div></div>
      <div class="stat-card"><div class="label">Total Paragraphs</div><div class="value cyan">${stats.totalParagraphs || 0}</div></div>
    </div>
    <div class="charts-row">
      <div class="chart-card">
        <h3>Program Types</h3>
        <canvas id="typeChart" height="260"></canvas>
      </div>
      <div class="chart-card">
        <h3>Largest Programs (by Lines)</h3>
        <canvas id="sizeChart" height="260"></canvas>
      </div>
    </div>
    <div class="table-card">
      <h3>All Programs</h3>
      <table>
        <thead><tr>
          <th>Program</th><th>Type</th><th>Lines</th><th>Paragraphs</th><th>Features</th><th>Analysis</th>
        </tr></thead>
        <tbody>
          ${programs.map(p => `<tr>
            <td><span class="program-link" onclick="showProgram('${p.programId}')">${p.programName}</span></td>
            <td><span class="badge badge-type-${p.programType.toLowerCase()}">${p.programType}</span></td>
            <td>${p.lineCount}</td>
            <td>${p.paragraphCount}</td>
            <td>${p.usesCics ? '<span class="badge badge-feature">CICS</span>' : ''}${p.usesDb2 ? '<span class="badge badge-feature">DB2</span>' : ''}${p.usesIdms ? '<span class="badge badge-feature">IDMS</span>' : ''}</td>
            <td>${p.businessSummary ? '<span class="badge badge-bool-true">Done</span>' : '<span class="badge badge-bool-false">Pending</span>'}</td>
          </tr>`).join('')}
        </tbody>
      </table>
    </div>
    <div class="chat-inline-card">
      <div class="chat-inline-header">
        <h3>Ask about this run</h3>
        <span class="chat-run-badge">${escapeHtml(selectedProject.name)} - Run #${selectedRun.id}</span>
      </div>
      <div class="chat-inline-input">
        <input type="text" id="chat-input" placeholder="Ask a question about the COBOL codebase..."
               onkeydown="if(event.key==='Enter')sendChat()">
        <button class="btn btn-accent" onclick="sendChat()">Ask</button>
      </div>
      <div class="chat-messages" id="chat-messages"></div>
    </div>
  `;

  renderCharts();
  renderChatHistory();
}

function renderChatHistory() {
  const el = document.getElementById('chat-messages');
  if (!el) return;
  if (chatMessages.length === 0) {
    el.innerHTML = '';
    return;
  }
  el.innerHTML = chatMessages.map(m =>
    `<div class="chat-msg ${m.role}"><div class="msg-content">${m.html}</div>${m.sourcesHtml || ''}</div>`
  ).join('');
  el.scrollTop = el.scrollHeight;
}

function renderCharts() {
  const cics = programs.filter(p => p.programType === 'CICS').length;
  const batch = programs.filter(p => p.programType === 'BATCH').length;
  const sub = programs.filter(p => p.programType === 'SUBROUTINE').length;

  if (chartInstance) chartInstance.destroy();
  const typeCanvas = document.getElementById('typeChart');
  if (typeCanvas) {
    chartInstance = new Chart(typeCanvas, {
      type: 'doughnut',
      data: {
        labels: ['CICS', 'Batch', 'Subroutine'],
        datasets: [{ data: [cics, batch, sub], backgroundColor: ['#58a6ff', '#3fb950', '#bc8cff'], borderWidth: 0 }]
      },
      options: {
        responsive: true,
        plugins: { legend: { position: 'bottom', labels: { color: '#8b949e', padding: 16 } } }
      }
    });
  }

  const top10 = [...programs].sort((a, b) => b.lineCount - a.lineCount).slice(0, 10);
  const colors = top10.map(p => p.programType === 'CICS' ? '#58a6ff' : p.programType === 'BATCH' ? '#3fb950' : '#bc8cff');

  if (barChartInstance) barChartInstance.destroy();
  const sizeCanvas = document.getElementById('sizeChart');
  if (sizeCanvas) {
    barChartInstance = new Chart(sizeCanvas, {
      type: 'bar',
      data: {
        labels: top10.map(p => p.programName),
        datasets: [{ label: 'Lines of Code', data: top10.map(p => p.lineCount), backgroundColor: colors, borderWidth: 0, borderRadius: 3 }]
      },
      options: {
        responsive: true,
        indexAxis: 'y',
        plugins: { legend: { display: false } },
        scales: {
          x: { grid: { color: '#21262d' }, ticks: { color: '#8b949e' } },
          y: { grid: { display: false }, ticks: { color: '#8b949e', font: { size: 11 } } }
        }
      }
    });
  }
}

// ───── PROGRAM DETAIL ─────

async function showProgram(id) {
  setActiveNav(null);
  document.querySelectorAll('#program-list .nav-item').forEach(i => {
    i.classList.toggle('active', i.textContent.trim().startsWith(id));
  });

  document.getElementById('breadcrumb').innerHTML =
    `<a onclick="showProjectManager()">Projects</a><span class="sep">/</span><span>${selectedProject.name}</span><span class="sep">/</span><a onclick="showDashboard()">Dashboard</a><span class="sep">/</span><span>${id}</span>`;

  const content = document.getElementById('content');
  content.innerHTML = '<div class="loading"><div class="spinner"></div>Loading program...</div>';

  try {
    const [prog, paragraphs, deps] = await Promise.all([
      api(`/api/search/programs/${id}`),
      api(`/api/search/programs/${id}/paragraphs`),
      api(`/api/search/programs/${id}/dependencies`)
    ]);

    content.innerHTML = `
      <div class="tabs">
        <div class="tab active" data-tab="overview">Overview</div>
        <div class="tab" data-tab="rules">Business Rules</div>
        <div class="tab" data-tab="paragraphs">Paragraphs (${paragraphs.length})</div>
        <div class="tab" data-tab="deps">Dependencies (${deps.length})</div>
        <div class="tab" data-tab="source">Source</div>
      </div>
      <div id="tab-overview" class="tab-content active"></div>
      <div id="tab-rules" class="tab-content"></div>
      <div id="tab-paragraphs" class="tab-content"></div>
      <div id="tab-deps" class="tab-content"></div>
      <div id="tab-source" class="tab-content"></div>
    `;

    setupTabs();
    renderOverview(prog);
    renderBusinessRules(prog, paragraphs);
    renderParagraphs(paragraphs);
    renderDependencies(deps);
    renderSource(prog);
  } catch (e) {
    content.innerHTML = '<div class="empty-state"><p>Failed to load program details.</p></div>';
  }
}

function setupTabs() {
  document.querySelectorAll('.tab').forEach(tab => {
    tab.addEventListener('click', () => {
      document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
      document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
      tab.classList.add('active');
      document.getElementById(`tab-${tab.dataset.tab}`).classList.add('active');
    });
  });
}

function renderOverview(p) {
  document.getElementById('tab-overview').innerHTML = `
    <div class="detail-grid">
      <div class="detail-card">
        <h3>Program Info</h3>
        <div class="detail-row"><span class="label">Program Name</span><span class="val">${p.programName}</span></div>
        <div class="detail-row"><span class="label">Type</span><span class="val"><span class="badge badge-type-${(p.programType||'').toLowerCase()}">${p.programType}</span></span></div>
        <div class="detail-row"><span class="label">Author</span><span class="val">${p.author || '-'}</span></div>
        <div class="detail-row"><span class="label">Lines of Code</span><span class="val">${p.lineCount}</span></div>
        <div class="detail-row"><span class="label">Paragraphs</span><span class="val">${p.paragraphCount}</span></div>
        <div class="detail-row"><span class="label">Copybooks</span><span class="val">${(p.copybooks || []).length}</span></div>
        <div class="detail-row"><span class="label">Called Programs</span><span class="val">${(p.calledPrograms || []).join(', ') || 'None'}</span></div>
      </div>
      <div class="detail-card">
        <h3>Features</h3>
        <div class="detail-row"><span class="label">CICS</span><span class="val"><span class="badge badge-bool-${p.usesCics}">${p.usesCics ? 'Yes' : 'No'}</span></span></div>
        <div class="detail-row"><span class="label">DB2</span><span class="val"><span class="badge badge-bool-${p.usesDb2}">${p.usesDb2 ? 'Yes' : 'No'}</span></span></div>
        <div class="detail-row"><span class="label">IDMS</span><span class="val"><span class="badge badge-bool-${p.usesIdms}">${p.usesIdms ? 'Yes' : 'No'}</span></span></div>
        <div class="detail-row"><span class="label">IMS</span><span class="val"><span class="badge badge-bool-${p.usesIms}">${p.usesIms ? 'Yes' : 'No'}</span></span></div>
        <div class="detail-row"><span class="label">MQ</span><span class="val"><span class="badge badge-bool-${p.usesMq}">${p.usesMq ? 'Yes' : 'No'}</span></span></div>
        <div class="detail-row"><span class="label">Copybooks</span><span class="val">${(p.copybooks || []).join(', ') || 'None'}</span></div>
      </div>
    </div>
    <div class="summary-box">
      <h3>Business Logic Analysis</h3>
      <p class="${p.businessSummary ? '' : 'empty'}">${formatSummary(p.businessSummary) || 'No analysis available yet. Run the batch analysis first.'}</p>
    </div>
  `;
}

function renderBusinessRules(prog, paragraphs) {
  const el = document.getElementById('tab-rules');
  let html = '';

  if (prog.extractedBusinessRules && prog.extractedBusinessRules.length) {
    const grouped = {};
    const categoryIcons = {
      'VALIDATION': '&#10003;', 'CALCULATION': '&#9638;', 'LIMIT CHECK': '&#9888;',
      'BUSINESS DECISION': '&#9654;', 'ELIGIBILITY': '&#9733;', 'DATA MAPPING': '&#9679;',
      'STATUS TRANSITION': '&#9673;', 'RATE FEE': '%', 'THRESHOLD': '&#8593;',
      'DATA RULE': '&#9679;', 'STATUS CHECK': '&#9673;', 'RATE/FEE': '%'
    };
    prog.extractedBusinessRules.forEach(rule => {
      let cat = 'OTHER', desc = rule;
      // Handle [CATEGORY] description format
      const bracketMatch = rule.match(/^\[([A-Z_/ ]+)\]\s*(.+)/);
      if (bracketMatch) {
        cat = bracketMatch[1].trim().replace(/_/g, ' ');
        desc = bracketMatch[2].trim();
      } else {
        // Handle CATEGORY: description format
        const colonIdx = rule.indexOf(':');
        if (colonIdx > 0 && colonIdx < 30) {
          cat = rule.substring(0, colonIdx).trim().toUpperCase().replace(/_/g, ' ');
          desc = rule.substring(colonIdx + 1).trim();
        }
      }
      if (!grouped[cat]) grouped[cat] = [];
      grouped[cat].push(desc);
    });

    html += `<div class="rules-section"><h3>Business Rules (${prog.extractedBusinessRules.length})</h3>
      <div class="rules-list">`;
    for (const [cat, rules] of Object.entries(grouped)) {
      const icon = categoryIcons[cat] || '&#9670;';
      html += `<div class="rule-category-card">
        <div class="rule-category-header"><span class="rule-icon">${icon}</span> ${escapeHtml(cat)}</div>
        <div class="rule-category-items">${rules.map(r =>
          `<div class="rule-item">${escapeHtml(r)}</div>`
        ).join('')}</div>
      </div>`;
    }
    html += `</div></div>`;
  } else {
    html += '<div class="empty-state"><p>No business rules extracted yet. Run analysis to extract rules.</p></div>';
  }

  el.innerHTML = html;
}

function renderParagraphs(paragraphs) {
  const el = document.getElementById('tab-paragraphs');
  if (!paragraphs.length) { el.innerHTML = '<div class="empty-state"><p>No paragraphs found.</p></div>'; return; }
  el.innerHTML = paragraphs.map(p => `
    <div class="paragraph-card">
      <div class="paragraph-header">
        <span class="paragraph-name">${p.paragraphName}</span>
        <span class="badge">${p.type}</span>
        <span class="paragraph-lines">Lines ${p.startLine}-${p.endLine}</span>
        ${p.hasExecCics ? '<span class="badge badge-feature">CICS</span>' : ''}
        ${p.hasExecSql ? '<span class="badge badge-feature">SQL</span>' : ''}
        ${p.hasCallStatement ? '<span class="badge badge-feature">CALL</span>' : ''}
        ${p.calculations && p.calculations.length ? `<span class="badge badge-feature">CALC:${p.calculations.length}</span>` : ''}
      </div>
      ${p.businessSummary ? `<div class="paragraph-summary">${formatSummary(p.businessSummary)}</div>` : ''}
      ${p.calculations && p.calculations.length ? `<div class="paragraph-rules"><strong>Calculations:</strong> ${p.calculations.map(c => `<code>${escapeHtml(c)}</code>`).join(' ')}</div>` : ''}
      ${p.dataAccess && p.dataAccess.length ? `<div class="paragraph-rules"><strong>SQL:</strong> ${p.dataAccess.map(d => `<code>${escapeHtml(d)}</code>`).join(' ')}</div>` : ''}
      ${p.performsCalls && p.performsCalls.length ? `<div class="paragraph-performs">Performs: ${p.performsCalls.join(', ')}</div>` : ''}
    </div>
  `).join('');
}

function renderDependencies(deps) {
  const el = document.getElementById('tab-deps');
  if (!deps.length) { el.innerHTML = '<div class="empty-state"><p>No dependencies found.</p></div>'; return; }

  const byType = {};
  deps.forEach(d => {
    if (!byType[d.dependencyType]) byType[d.dependencyType] = [];
    byType[d.dependencyType].push(d);
  });

  const types = Object.keys(byType);
  el.innerHTML = `
    <div class="dep-filters">
      <button class="dep-filter active" onclick="filterDeps(this, 'all')">All (${deps.length})</button>
      ${types.map(t => `<button class="dep-filter" onclick="filterDeps(this, '${t}')">${t} (${byType[t].length})</button>`).join('')}
    </div>
    <div class="table-card"><table>
      <thead><tr><th>Type</th><th>Target</th><th>Context</th><th>Details</th></tr></thead>
      <tbody>
        ${deps.map(d => `<tr class="dep-row" data-type="${d.dependencyType}">
          <td><span class="badge badge-dep-${d.dependencyType.toLowerCase()}">${d.dependencyType}</span></td>
          <td style="color:var(--text-primary);font-weight:500">${d.targetName}</td>
          <td style="color:var(--text-secondary)">${d.callingContext || '-'}</td>
          <td style="color:var(--text-muted);font-size:12px">${formatDetails(d.details)}</td>
        </tr>`).join('')}
      </tbody>
    </table></div>
  `;
}

function filterDeps(btn, type) {
  document.querySelectorAll('.dep-filter').forEach(b => b.classList.remove('active'));
  btn.classList.add('active');
  document.querySelectorAll('.dep-row').forEach(row => {
    row.style.display = (type === 'all' || row.dataset.type === type) ? '' : 'none';
  });
}

function renderSource(prog) {
  const el = document.getElementById('tab-source');
  if (prog.sourceCode) {
    el.innerHTML = `<div class="source-code"><pre>${escapeHtml(prog.sourceCode)}</pre></div>`;
  } else {
    el.innerHTML = '<div class="empty-state"><p>Source code not available.</p></div>';
  }
}

// ───── DEPENDENCY GRAPH ─────

async function showGraph() {
  setActiveNav('graph');
  document.getElementById('breadcrumb').innerHTML =
    `<a onclick="showProjectManager()">Projects</a><span class="sep">/</span><span>${selectedProject.name}</span><span class="sep">/</span><span>Dependency Graph</span>`;

  const content = document.getElementById('content');
  content.innerHTML = `
    <div class="graph-legend">
      <span><span class="dot dot-cics"></span> CICS</span>
      <span><span class="dot dot-batch"></span> Batch</span>
      <span><span class="dot dot-sub"></span> Subroutine</span>
      <span><span class="dot dot-external"></span> External</span>
    </div>
    <div id="cy"></div>
  `;

  try {
    const brp = batchRunParam();
    const graph = await api(`/api/search/dependency-graph?${brp}`);
    const elements = [];

    (graph.nodes || []).forEach(n => {
      elements.push({ data: { id: n.id, label: n.label, type: n.type } });
    });
    (graph.edges || []).forEach(e => {
      elements.push({ data: { source: e.source, target: e.target, callType: e.type || 'CALL' } });
    });

    if (!elements.length) {
      document.getElementById('cy').innerHTML = '<div class="empty-state" style="height:100%;display:flex;align-items:center;justify-content:center"><p>No dependency data available.</p></div>';
      return;
    }

    cytoscape({
      container: document.getElementById('cy'),
      elements: elements,
      style: [
        { selector: 'node', style: {
          'label': 'data(label)', 'background-color': '#58a6ff', 'color': '#e6edf3',
          'font-size': '11px', 'text-valign': 'bottom', 'text-margin-y': 8,
          'width': 36, 'height': 36, 'border-width': 2, 'border-color': '#30363d'
        }},
        { selector: 'node[type="BATCH"]', style: { 'background-color': '#3fb950' }},
        { selector: 'node[type="SUBROUTINE"]', style: { 'background-color': '#bc8cff' }},
        { selector: 'node[type="EXTERNAL"]', style: { 'background-color': '#d29922', 'shape': 'diamond', 'width': 28, 'height': 28 }},
        { selector: 'edge', style: {
          'width': 2, 'line-color': '#3fb950', 'target-arrow-color': '#3fb950',
          'target-arrow-shape': 'triangle', 'curve-style': 'bezier', 'arrow-scale': 0.8
        }}
      ],
      layout: { name: 'cose', padding: 50, nodeRepulsion: 8000, idealEdgeLength: 120 }
    });
  } catch {
    document.getElementById('cy').innerHTML = '<div class="empty-state"><p>Failed to load dependency graph.</p></div>';
  }
}

// ───── CHAT (inline in dashboard) ─────

async function sendChat() {
  if (!selectedRun || !selectedRun.batchRunId) return;

  const input = document.getElementById('chat-input');
  const question = input.value.trim();
  if (!question) return;

  // Add user message
  chatMessages.push({ role: 'user', html: escapeHtml(question) });
  const messagesEl = document.getElementById('chat-messages');
  renderChatHistory();
  input.value = '';

  // Show loading
  messagesEl.innerHTML += '<div class="chat-msg assistant" id="chat-loading"><div class="msg-content"><div class="spinner-small"></div> Searching and analyzing...</div></div>';
  messagesEl.scrollTop = messagesEl.scrollHeight;

  try {
    const body = { question, batchRunId: selectedRun.batchRunId };

    const result = await fetch(`${API_BASE}/api/rag/ask`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    }).then(r => r.json());

    let sourcesHtml = '';
    if (result.sources && result.sources.length) {
      sourcesHtml = '<div class="chat-sources">' +
        result.sources.slice(0, 5).map(s =>
          `<span class="source-chip" onclick="showProgram('${s.programId}')">${s.programName}${s.paragraphName ? ' / ' + s.paragraphName : ''}</span>`
        ).join('') + '</div>';
    }

    chatMessages.push({ role: 'assistant', html: formatSummary(result.answer), sourcesHtml });
  } catch (e) {
    chatMessages.push({ role: 'assistant', html: `Error: ${escapeHtml(e.message)}` });
  }

  renderChatHistory();
}

// ───── UTILITIES ─────

function escapeHtml(text) {
  if (!text) return '';
  const d = document.createElement('div');
  d.textContent = text;
  return d.innerHTML;
}

function formatSummary(text) {
  if (!text) return '';
  return escapeHtml(text).replace(/\n/g, '<br>');
}

function formatDetails(details) {
  if (!details || typeof details !== 'object') return '-';
  return Object.entries(details)
    .filter(([k, v]) => v !== null && v !== '' && k !== 'lineNumber')
    .map(([k, v]) => `${k}: ${v}`)
    .join(', ') || '-';
}
