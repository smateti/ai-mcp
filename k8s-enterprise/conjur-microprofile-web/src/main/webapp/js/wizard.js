var currentStep = 0;
var totalSteps = 10;
var stepState = {};
var stepTitles = [
    'Ref Data', 'Connection', 'Root Policy', 'JWT Auth', 'Environments',
    'Product', 'Resources', 'Hosts', 'Access & Secrets', 'Summary'
];
var wizRefData = {};

// === Wizard Context (persists across steps) ===
var wizCtx = {
    orgName: 'nimbus',
    authMethod: 'kubernetes',
    environments: [],
    productEnv: '',
    productName: '',
    appTypes: [],
    resourceTypes: [],
    databases: [],
    kafkaClusters: [],
    infraResources: [],
    hosts: [],
    existing: {
        loaded: false,
        orgs: [],
        envs: {},
        prods: {},
        appTypes: {},
        resTypes: {},
        resources: {},
        hosts: {}
    }
};

// ============================================
// Reference Data Management (Step 0)
// ============================================
function loadRefDataAdmin() {
    fetch('/api/refdata/all').then(function(r){return r.json();})
    .then(function(data){
        wizRefData = data;
        renderRefSection('organizations', data.organizations||[], false);
        renderRefSection('environments', data.environments||[], false);
        renderRefSection('products', data.products||[], false);
        renderRefSection('apptypes', data.apptypes||[], false);
        renderRefSection('resourcetypes', data.resourcetypes||[], true);
    }).catch(function(e){
        document.getElementById('refdata-content').innerHTML = '<p class="badge badge-err">Error loading reference data: '+e.message+'</p>';
    });
}

function renderRefSection(type, items, showFields) {
    var container = document.getElementById('ref-'+type);
    if(!container) return;
    var html = '<table><tr><th>Name</th><th>Description</th>';
    if(showFields) html += '<th>Fields</th>';
    html += '<th>Actions</th></tr>';
    items.forEach(function(item){
        html += '<tr><td><strong>'+esc(item.name)+'</strong></td><td>'+esc(item.description||'')+'</td>';
        if(showFields) html += '<td><code>'+esc(item.fields||'')+'</code></td>';
        html += '<td><button class="danger" onclick="deleteRefItem(\''+esc(type).replace(/'/g,"\\'")+'\','+item.id+')" '
            + 'style="height:26px;padding:2px 10px;font-size:0.8em;">Delete</button></td></tr>';
    });
    if(items.length===0) {
        html += '<tr><td colspan="'+(showFields?4:3)+'">No items. Add one below.</td></tr>';
    }
    html += '</table><p style="font-size:0.85em;color:#666;">Total: '+items.length+'</p>';
    container.innerHTML = html;
}

function addRefItem(type) {
    var name = document.getElementById('ref-add-'+type+'-name').value.trim();
    var desc = document.getElementById('ref-add-'+type+'-desc').value.trim();
    if(!name){ alert('Name is required'); return; }
    var body = { name: name, description: desc };
    if(type==='resourcetypes'){
        var fields = document.getElementById('ref-add-resourcetypes-fields').value.trim();
        if(fields) body.fields = fields;
    }
    apiCall('POST','/api/refdata/'+type, body)
    .then(function(d){
        if(d.error){ showResult('ref-add-'+type+'-result', d.error, true); }
        else {
            showResult('ref-add-'+type+'-result', d, false);
            document.getElementById('ref-add-'+type+'-name').value = '';
            document.getElementById('ref-add-'+type+'-desc').value = '';
            if(type==='resourcetypes') document.getElementById('ref-add-resourcetypes-fields').value = '';
            loadRefDataAdmin();
        }
    })
    .catch(function(e){ showResult('ref-add-'+type+'-result', e.message, true); });
}

function deleteRefItem(type, id) {
    if(!confirm('Delete this '+type.replace(/s$/,'')+' item?')) return;
    fetch('/api/refdata/'+type+'/'+id, { method:'DELETE' })
    .then(function(r){return r.json();})
    .then(function(d){ loadRefDataAdmin(); })
    .catch(function(e){ alert('Delete failed: '+e.message); });
}

// ============================================
// Load Existing State from Conjur
// ============================================
function loadExisting() {
    Promise.all([
        fetch('/api/resources?kind=policy').then(function(r){return r.json();}).catch(function(){return [];}),
        fetch('/api/resources?kind=host').then(function(r){return r.json();}).catch(function(){return [];}),
        fetch('/api/resources?kind=variable').then(function(r){return r.json();}).catch(function(){return [];})
    ]).then(function(results) {
        wizCtx.existing.orgs = [];
        wizCtx.existing.envs = {};
        wizCtx.existing.prods = {};
        wizCtx.existing.appTypes = {};
        wizCtx.existing.resTypes = {};
        wizCtx.existing.resources = {};
        wizCtx.existing.hosts = {};
        parseExistingPolicies(results[0]||[]);
        parseExistingHosts(results[1]||[]);
        parseExistingResources(results[2]||[]);
        wizCtx.existing.loaded = true;
        refreshExistingDisplay();
        if(wizRefData.environments) populateEnvCheckboxes(wizRefData.environments);
    }).catch(function(e){ console.error('loadExisting error:', e); });
}

function parseExistingPolicies(policies) {
    var paths = [];
    policies.forEach(function(r){ paths.push((r.id||'').split(':').pop()); });

    var os = {};
    paths.forEach(function(p){
        var f = p.split('/')[0];
        if(f && f!=='conjur') os[f] = true;
    });
    wizCtx.existing.orgs = Object.keys(os).sort();

    paths.forEach(function(p){
        var m = p.match(/^([^\/]+)\/environments\/([^\/]+)\/products/);
        if(m){
            if(!wizCtx.existing.envs[m[1]]) wizCtx.existing.envs[m[1]] = [];
            if(wizCtx.existing.envs[m[1]].indexOf(m[2])===-1) wizCtx.existing.envs[m[1]].push(m[2]);
        }
    });

    paths.forEach(function(p){
        var m = p.match(/^([^\/]+)\/environments\/([^\/]+)\/products\/([^\/]+)$/);
        if(m){
            var k = m[1]+'/'+m[2];
            if(!wizCtx.existing.prods[k]) wizCtx.existing.prods[k] = [];
            if(wizCtx.existing.prods[k].indexOf(m[3])===-1) wizCtx.existing.prods[k].push(m[3]);
        }
    });

    paths.forEach(function(p){
        var m = p.match(/^([^\/]+)\/environments\/([^\/]+)\/products\/([^\/]+)\/apps\/([^\/]+)$/);
        if(m){
            var k = m[1]+'/'+m[2]+'/'+m[3];
            if(!wizCtx.existing.appTypes[k]) wizCtx.existing.appTypes[k] = [];
            if(wizCtx.existing.appTypes[k].indexOf(m[4])===-1) wizCtx.existing.appTypes[k].push(m[4]);
        }
    });

    paths.forEach(function(p){
        var m = p.match(/^([^\/]+)\/environments\/([^\/]+)\/products\/([^\/]+)\/resources\/([^\/]+)$/);
        if(m){
            var k = m[1]+'/'+m[2]+'/'+m[3];
            if(!wizCtx.existing.resTypes[k]) wizCtx.existing.resTypes[k] = [];
            if(wizCtx.existing.resTypes[k].indexOf(m[4])===-1) wizCtx.existing.resTypes[k].push(m[4]);
        }
    });
}

function parseExistingHosts(hosts) {
    var seen = {};
    hosts.forEach(function(h){
        var path = (h.id||'').split(':').pop();
        var m = path.match(/^([^\/]+)\/environments\/([^\/]+)\/products\/([^\/]+)\/apps\/([^\/]+)\/(.+)/);
        if(m){
            var k = m[1]+'/'+m[2]+'/'+m[3];
            var hk = k+'/'+m[4]+'/'+m[5];
            if(!seen[hk]){
                seen[hk] = true;
                if(!wizCtx.existing.hosts[k]) wizCtx.existing.hosts[k] = [];
                wizCtx.existing.hosts[k].push({ appType: m[4], hostId: m[5] });
            }
        }
    });
}

function parseExistingResources(variables) {
    var seen = {};
    variables.forEach(function(v){
        var path = (v.id||'').split(':').pop();
        var m = path.match(/^([^\/]+)\/environments\/([^\/]+)\/products\/([^\/]+)\/resources\/([^\/]+)\/([^\/]+)\//);
        if(m){
            var k = m[1]+'/'+m[2]+'/'+m[3];
            var rk = k+'/'+m[4]+'/'+m[5];
            if(!seen[rk]){
                seen[rk] = true;
                if(!wizCtx.existing.resources[k]) wizCtx.existing.resources[k] = [];
                wizCtx.existing.resources[k].push({ type: m[4], name: m[5] });
            }
        }
    });
}

function refreshExistingDisplay() {
    if(wizCtx.existing.orgs.length > 0){
        document.getElementById('w-existingOrgs').style.display = 'block';
        var html = '';
        wizCtx.existing.orgs.forEach(function(org){
            html += '<span class="org-pill" onclick="selectExistingOrg(\''+esc(org)+'\')">'+esc(org)+' \u2713</span>';
        });
        document.getElementById('w-orgBadges').innerHTML = html;
        document.getElementById('w-orgName').value = wizCtx.existing.orgs[0];
        wizCtx.orgName = wizCtx.existing.orgs[0];
    }
}

function selectExistingOrg(orgName) {
    wizCtx.orgName = orgName;
    document.getElementById('w-orgName').value = orgName;
    stepState[2] = 'done';
    stepResult(2, 'Using existing organization: ' + orgName, 'ok');
    showStep(currentStep);
}

function selectExistingProduct(env, product) {
    var pk = wizCtx.orgName + '/' + env + '/' + product;
    wizCtx.productEnv = env;
    wizCtx.productName = product;
    wizCtx.appTypes = wizCtx.existing.appTypes[pk] || [];
    wizCtx.resourceTypes = wizCtx.existing.resTypes[pk] || [];
    stepState[5] = 'done';
    stepResult(5, 'Using existing product: ' + env + '/' + product
        + '\nApp Types: ' + (wizCtx.appTypes.join(', ')||'none')
        + '\nResource Types: ' + (wizCtx.resourceTypes.join(', ')||'none'), 'ok');
    showStep(currentStep);
}

// ============================================
// Load Reference Data for Wizard Dropdowns
// ============================================
function loadWizRefData() {
    fetch('/api/refdata/all').then(function(r){return r.json();})
    .then(function(data){
        wizRefData = data;
        populateEnvCheckboxes(data.environments||[]);
        populateAppTypeCheckboxes(data.apptypes||[]);
        populateResTypeCheckboxes(data.resourcetypes||[]);
    }).catch(function(e){ console.error('Wizard refdata load error:',e); });
}

function populateEnvCheckboxes(items) {
    var container = document.getElementById('w-env-group');
    if(!container) return;
    var existingEnvs = wizCtx.existing.envs[wizCtx.orgName] || [];
    var defaults = ['dev','qa','prod'];
    var seen = {};
    container.innerHTML = '';
    items.forEach(function(item){
        seen[item.name] = true;
        var exists = existingEnvs.indexOf(item.name) >= 0;
        var checked = exists ? ' checked disabled' : (defaults.indexOf(item.name)>=0 ? ' checked' : '');
        var badge = exists ? ' <span class="exists-badge">exists</span>' : '';
        container.innerHTML += '<label><input type="checkbox" class="w-env" value="'+esc(item.name)+'"'+checked+' /> '+esc(item.name)+badge+'</label>';
    });
    existingEnvs.forEach(function(env){
        if(!seen[env]){
            container.innerHTML += '<label><input type="checkbox" class="w-env" value="'+esc(env)+'" checked disabled /> '+esc(env)+' <span class="exists-badge">exists</span></label>';
        }
    });
}

function populateAppTypeCheckboxes(items) {
    var container = document.getElementById('w-appType-group');
    if(!container) return;
    var defaults = ['nims','batch'];
    container.innerHTML = '';
    items.forEach(function(item){
        var checked = defaults.indexOf(item.name) >= 0 ? ' checked' : '';
        container.innerHTML += '<label><input type="checkbox" class="w-appType" value="'+esc(item.name)+'"'+checked+' /> '+esc(item.name)+'</label>';
    });
}

function populateResTypeCheckboxes(items) {
    var container = document.getElementById('w-resType-group');
    if(!container) return;
    var defaults = ['dbs','kafka','ldap','api'];
    container.innerHTML = '';
    items.forEach(function(item){
        var checked = defaults.indexOf(item.name) >= 0 ? ' checked' : '';
        container.innerHTML += '<label><input type="checkbox" class="w-resType" value="'+esc(item.name)+'"'+checked+' /> '+esc(item.name)+'</label>';
    });
}

// ============================================
// Dropdown Helpers
// ============================================
function getAllKnownEnvs() {
    var es = {};
    wizCtx.environments.forEach(function(e){ es[e] = true; });
    (wizCtx.existing.envs[wizCtx.orgName]||[]).forEach(function(e){ es[e] = true; });
    return Object.keys(es).sort();
}

function getAllKnownProds(env) {
    var key = wizCtx.orgName + '/' + env;
    var ps = {};
    (wizCtx.existing.prods[key]||[]).forEach(function(p){ ps[p] = true; });
    if(wizCtx.productEnv === env && wizCtx.productName) ps[wizCtx.productName] = true;
    return Object.keys(ps).sort();
}

function populateEnvDropdown() {
    var sel = document.getElementById('w-prodEnv');
    if(!sel) return;
    var envs = getAllKnownEnvs();
    sel.innerHTML = '';
    envs.forEach(function(e){
        sel.innerHTML += '<option value="'+esc(e)+'"'+(e===wizCtx.productEnv?' selected':'')+'>'+esc(e)+'</option>';
    });
    onWizProdEnvChange();
}

function populateStepEnvDropdown(selId) {
    var sel = document.getElementById(selId);
    if(!sel) return;
    var envs = getAllKnownEnvs();
    sel.innerHTML = '';
    envs.forEach(function(e){
        sel.innerHTML += '<option value="'+esc(e)+'"'+(e===wizCtx.productEnv?' selected':'')+'>'+esc(e)+'</option>';
    });
}

function populateStepProductDropdown(envSelId, prodSelId) {
    var envSel = document.getElementById(envSelId);
    var prodSel = document.getElementById(prodSelId);
    if(!envSel || !prodSel) return;
    var env = envSel.value;
    var prods = getAllKnownProds(env);
    prodSel.innerHTML = '';
    if(prods.length === 0){
        prodSel.innerHTML = '<option value="">-- no products --</option>';
        return;
    }
    prods.forEach(function(p){
        prodSel.innerHTML += '<option value="'+esc(p)+'"'+(p===wizCtx.productName?' selected':'')+'>'+esc(p)+'</option>';
    });
}

function onWizProdEnvChange() {
    var env = document.getElementById('w-prodEnv').value;
    var key = wizCtx.orgName + '/' + env;
    var prods = wizCtx.existing.prods[key] || [];
    var container = document.getElementById('w-existingProducts');
    var list = document.getElementById('w-existingProductsList');
    if(prods.length > 0){
        container.style.display = 'block';
        var html = '<table><tr><th>Product</th><th>App Types</th><th>Resource Types</th><th></th></tr>';
        prods.forEach(function(p){
            var pk = wizCtx.orgName+'/'+env+'/'+p;
            var ats = (wizCtx.existing.appTypes[pk]||[]).join(', ') || '-';
            var rts = (wizCtx.existing.resTypes[pk]||[]).join(', ') || '-';
            html += '<tr><td><strong>'+esc(p)+'</strong></td><td>'+esc(ats)+'</td><td>'+esc(rts)+'</td>'
                + '<td><button onclick="selectExistingProduct(\''+esc(env)+'\',\''+esc(p)+'\')" '
                + 'style="height:28px;padding:2px 14px;font-size:0.85em;background:#28a745;">Select</button></td></tr>';
        });
        html += '</table>';
        list.innerHTML = html;
    } else {
        container.style.display = 'none';
    }
}

function onWizResEnvChange() {
    populateStepProductDropdown('w-resEnv', 'w-resProduct');
    onWizResProductChange();
}
function onWizResProductChange() {
    var env = document.getElementById('w-resEnv').value;
    var prod = document.getElementById('w-resProduct').value;
    showExistingResources(env, prod);
    updateResourceSections(env, prod);
}

function updateResourceSections(env, prod) {
    var pk = wizCtx.orgName + '/' + env + '/' + prod;
    var resTypes = wizCtx.existing.resTypes[pk] || wizCtx.resourceTypes || [];
    var dbSec = document.getElementById('w-dbSection');
    var kafkaSec = document.getElementById('w-kafkaSection');
    var infraSec = document.getElementById('w-infraSection');
    if(dbSec) dbSec.style.display = resTypes.indexOf('dbs') >= 0 ? 'block' : 'none';
    if(kafkaSec) kafkaSec.style.display = resTypes.indexOf('kafka') >= 0 ? 'block' : 'none';
    var hasInfra = resTypes.some(function(t){ return t !== 'dbs' && t !== 'kafka'; });
    if(infraSec) infraSec.style.display = hasInfra ? 'block' : 'none';
}

function showExistingResources(env, prod) {
    var container = document.getElementById('w-existingResources');
    var list = document.getElementById('w-existingResourcesList');
    if(!env || !prod){ container.style.display='none'; return; }
    var pk = wizCtx.orgName+'/'+env+'/'+prod;
    var resources = wizCtx.existing.resources[pk] || [];
    if(resources.length > 0){
        container.style.display = 'block';
        var html = '<table><tr><th>Type</th><th>Name</th></tr>';
        resources.forEach(function(r){
            html += '<tr><td><span class="badge badge-ok">'+esc(r.type)+'</span></td><td><code>'+esc(r.name)+'</code></td></tr>';
        });
        html += '</table><p style="font-size:0.85em;color:#666;">Total: '+resources.length+'</p>';
        list.innerHTML = html;
    } else {
        container.style.display = 'none';
    }
}

function onWizHostEnvChange() {
    populateStepProductDropdown('w-hostEnv', 'w-hostProduct');
    onWizHostProductChange();
}
function onWizHostProductChange() {
    var env = document.getElementById('w-hostEnv').value;
    var prod = document.getElementById('w-hostProduct').value;
    showExistingHosts(env, prod);
}

function showExistingHosts(env, prod) {
    var container = document.getElementById('w-existingHosts');
    var list = document.getElementById('w-existingHostsList');
    if(!env || !prod){ container.style.display='none'; return; }
    var pk = wizCtx.orgName+'/'+env+'/'+prod;
    var hosts = wizCtx.existing.hosts[pk] || [];
    if(hosts.length > 0){
        container.style.display = 'block';
        var html = '<table><tr><th>App Type</th><th>Host ID</th></tr>';
        hosts.forEach(function(h){
            html += '<tr><td><span class="badge badge-ok">'+esc(h.appType)+'</span></td><td><code>'+esc(h.hostId)+'</code></td></tr>';
        });
        html += '</table><p style="font-size:0.85em;color:#666;">Total: '+hosts.length+'</p>';
        list.innerHTML = html;
    } else {
        container.style.display = 'none';
    }
}

// ============================================
// Step 8: Access & Grants Handlers
// ============================================
function onWizGrantEnvChange() {
    populateStepProductDropdown('w-grantEnv', 'w-grantProduct');
    onWizGrantProductChange();
}

function onWizGrantProductChange() {
    var env = document.getElementById('w-grantEnv').value;
    var prod = document.getElementById('w-grantProduct').value;
    var pk = wizCtx.orgName + '/' + env + '/' + prod;
    var atSel = document.getElementById('w-grantAppType');
    if(!atSel) return;
    var appTypes = wizCtx.existing.appTypes[pk] || [];
    atSel.innerHTML = '';
    if(appTypes.length === 0){
        atSel.innerHTML = '<option value="">-- no app types --</option>';
    } else {
        appTypes.forEach(function(at){
            atSel.innerHTML += '<option value="'+esc(at)+'">'+esc(at)+'</option>';
        });
    }
    onWizGrantAppTypeChange();
    showGrantResources(env, prod);
    document.getElementById('w-grantList').innerHTML = '';
}

function onWizGrantAppTypeChange() {
    var env = document.getElementById('w-grantEnv').value;
    var prod = document.getElementById('w-grantProduct').value;
    var appType = document.getElementById('w-grantAppType').value;
    showGrantHosts(env, prod, appType);
    document.getElementById('w-grantList').innerHTML = '';
}

function showGrantHosts(env, prod, appType) {
    var container = document.getElementById('w-grantExistingHosts');
    var list = document.getElementById('w-grantHostsList');
    if(!env || !prod){ container.style.display='none'; return; }
    var pk = wizCtx.orgName+'/'+env+'/'+prod;
    var hosts = (wizCtx.existing.hosts[pk]||[]).filter(function(h){
        return !appType || h.appType === appType;
    });
    if(hosts.length > 0){
        container.style.display = 'block';
        var html = '<table><tr><th>App Type</th><th>Host ID</th></tr>';
        hosts.forEach(function(h){
            html += '<tr><td><span class="badge badge-ok">'+esc(h.appType)+'</span></td><td><code>'+esc(h.hostId)+'</code></td></tr>';
        });
        html += '</table>';
        list.innerHTML = html;
    } else { container.style.display = 'none'; }
}

function showGrantResources(env, prod) {
    var container = document.getElementById('w-grantExistingResources');
    var list = document.getElementById('w-grantResourcesList');
    if(!env || !prod){ container.style.display='none'; return; }
    var pk = wizCtx.orgName+'/'+env+'/'+prod;
    var resources = wizCtx.existing.resources[pk] || [];
    if(resources.length > 0){
        container.style.display = 'block';
        var html = '<table><tr><th>Type</th><th>Name</th></tr>';
        resources.forEach(function(r){
            html += '<tr><td><span class="badge badge-ok">'+esc(r.type)+'</span></td><td><code>'+esc(r.name)+'</code></td></tr>';
        });
        html += '</table>';
        list.innerHTML = html;
    } else { container.style.display = 'none'; }
}

function getFieldsForType(resType) {
    if(wizRefData.resourcetypes) {
        var match = wizRefData.resourcetypes.find(function(rt){ return rt.name === resType; });
        if(match && match.fields) {
            return match.fields.split(',').map(function(f){ return f.trim(); }).filter(function(f){ return f; });
        }
    }
    var defaults = {
        'dbs': ['host-name','username','password','port','database-name'],
        'kafka': ['bootstrap-servers','sasl-username','sasl-password'],
        'api': ['key','secret','webhook-secret'],
        'smtp': ['host','port','username','password'],
        'ldap': ['url','bind-dn','bind-password'],
        'oauth': ['client-id','client-secret','token-url'],
        'certs': ['cert','key','ca-bundle']
    };
    return defaults[resType] || ['value'];
}

// ============================================
// Utilities
// ============================================
function esc(s) {
    if(!s) return '';
    return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

function ctxBar(stepNum) {
    var el = document.getElementById('ctx-' + stepNum);
    if (!el) return;
    var parts = ['<strong>Org:</strong> ' + wizCtx.orgName];
    if (wizCtx.authMethod) {
        var authLabel = wizCtx.authMethod === 'authn' ? 'API Key' : 'authn-jwt/' + wizCtx.authMethod;
        parts.push('<strong>Auth:</strong> ' + authLabel);
    }
    if (wizCtx.environments.length) parts.push('<strong>Envs:</strong> ' + wizCtx.environments.join(', '));
    if (wizCtx.productEnv && wizCtx.productName) parts.push('<strong>Product:</strong> ' + wizCtx.productEnv + '/' + wizCtx.productName);
    if (wizCtx.appTypes.length) parts.push('<strong>App Types:</strong> ' + wizCtx.appTypes.join(', '));
    el.innerHTML = parts.join(' &nbsp;|&nbsp; ');
}

function appTypeOptions() {
    return wizCtx.appTypes.map(function(t) {
        return '<option value="' + t + '">' + t + '</option>';
    }).join('');
}

function dbOptions() {
    return wizCtx.databases.map(function(d) {
        return '<option value="' + d + '">' + d + '</option>';
    }).join('');
}

function hostOptions() {
    return wizCtx.hosts.map(function(h) {
        return '<option value="' + h.id + '">' + h.id + '</option>';
    }).join('');
}

function getChecked(cls) {
    var vals = [];
    document.querySelectorAll('.' + cls + ':checked').forEach(function(cb) { vals.push(cb.value); });
    return vals;
}

function apiPost(url, body) {
    return fetch(url, { method: 'POST', headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(body) }).then(function(r) { return r.json(); });
}

function stepResult(step, msg, type) {
    var el = document.getElementById('res-' + step);
    if(!el) return;
    el.className = 'step-result ' + type;
    el.textContent = typeof msg === 'string' ? msg : JSON.stringify(msg, null, 2);
}

// ============================================
// Wizard Init & Navigation
// ============================================
function initWizard() {
    var prog = document.getElementById('wizProgress');
    for (var i = 0; i < totalSteps; i++) {
        var dot = document.createElement('span');
        dot.className = 'step-dot';
        dot.textContent = (i+1) + '. ' + stepTitles[i];
        dot.dataset.step = i;
        dot.onclick = function() { showStep(parseInt(this.dataset.step)); };
        prog.appendChild(dot);
    }
    showStep(0);
    loadRefDataAdmin();
    loadWizRefData();
}

function showStep(n) {
    currentStep = n;
    document.querySelectorAll('.step-panel').forEach(function(el) { el.classList.remove('active'); });
    document.getElementById('step-' + n).classList.add('active');
    document.querySelectorAll('.step-dot').forEach(function(el, idx) {
        el.classList.remove('active');
        if (stepState[idx] === 'done') el.className = 'step-dot done';
        else if (stepState[idx] === 'error') el.className = 'step-dot error';
        else el.className = 'step-dot';
    });
    var activeDot = document.querySelectorAll('.step-dot')[n];
    if (activeDot) activeDot.classList.add('active');
    document.getElementById('btnPrev').style.display = n === 0 ? 'none' : '';
    document.getElementById('btnNext').textContent = n === totalSteps - 1 ? 'Finish' : 'Next Step';
    ctxBar(n);

    // Refresh step-specific content
    if(n === 0) { loadRefDataAdmin(); }
    if(n === 5) { populateEnvDropdown(); }
    if(n === 6) {
        populateStepEnvDropdown('w-resEnv');
        populateStepProductDropdown('w-resEnv', 'w-resProduct');
        onWizResProductChange();
    }
    if(n === 7) {
        populateStepEnvDropdown('w-hostEnv');
        populateStepProductDropdown('w-hostEnv', 'w-hostProduct');
        onWizHostProductChange();
    }
    if(n === 8) {
        populateStepEnvDropdown('w-grantEnv');
        populateStepProductDropdown('w-grantEnv', 'w-grantProduct');
        onWizGrantProductChange();
    }
}

function nextStep() { if (currentStep < totalSteps - 1) showStep(currentStep + 1); }
function prevStep() { if (currentStep > 0) showStep(currentStep - 1); }

// ============================================
// Step Execution
// ============================================
function executeStep(step) {
    stepResult(step, 'Executing...', 'info');
    switch(step) {
        case 1: executeConnectionCheck(); break;
        case 2: executeRootPolicy(); break;
        case 3: executeJwtAuth(); break;
        case 4: executeEnvironments(); break;
        case 5: executeProduct(); break;
        case 6: executeResources(); break;
        case 7: executeHosts(); break;
        case 8: executeAccessSecrets(); break;
        case 9: executeSummary(); break;
    }
}

function executeConnectionCheck() {
    fetch('/api/status').then(function(r) { return r.json(); }).then(function(data) {
        if (data.reachable) {
            stepState[1] = 'done';
            stepResult(1, 'Connected to Conjur!\nURL: ' + data.conjurUrl + '\nAccount: ' + data.account + '\n\nDiscovering existing resources...', 'ok');
            loadExisting();
        } else {
            stepState[1] = 'error';
            stepResult(1, 'Conjur is unreachable.', 'err');
        }
        showStep(currentStep);
    }).catch(function(e) { stepState[1] = 'error'; stepResult(1, 'Failed: ' + e.message, 'err'); showStep(currentStep); });
}

function executeRootPolicy() {
    wizCtx.orgName = document.getElementById('w-orgName').value.trim() || 'nimbus';
    apiPost('/api/setup/root', {orgName: wizCtx.orgName}).then(function(data) {
        if (data.error) { stepState[2] = 'error'; stepResult(2, data.error, 'err'); }
        else { stepState[2] = 'done'; stepResult(2, data, 'ok'); }
        showStep(currentStep);
    }).catch(function(e) { stepState[2] = 'error'; stepResult(2, e.message, 'err'); showStep(currentStep); });
}

function onAuthMethodChange() {
    var method = document.getElementById('w-authMethod').value;
    wizCtx.authMethod = method;
    var jwtConfig = document.getElementById('w-jwtConfig');
    var authnInfo = document.getElementById('w-authnInfo');
    var btn = document.getElementById('w-authBtn');
    if (method === 'authn') {
        jwtConfig.style.display = 'none';
        authnInfo.style.display = 'block';
        btn.textContent = 'Confirm API Key Authentication';
    } else {
        jwtConfig.style.display = 'block';
        authnInfo.style.display = 'none';
        btn.textContent = 'Setup JWT Authenticator';
        if (method === 'openshift') {
            document.getElementById('w-jwksUri').value = 'https://openshift.default.svc/.well-known/openid-configuration';
        } else {
            document.getElementById('w-jwksUri').value = 'https://kubernetes.default.svc/openid/v1/jwks';
        }
    }
    document.querySelectorAll('.w-hostK8sFields').forEach(function(el) {
        el.style.display = (method === 'authn') ? 'none' : '';
    });
}

function executeJwtAuth() {
    var method = document.getElementById('w-authMethod').value;
    wizCtx.authMethod = method;

    if (method === 'authn') {
        stepState[3] = 'done';
        stepResult(3, 'Authentication method: API Key (authn)\nHosts will authenticate using their Conjur API key.\nNo JWT authenticator configuration needed.', 'ok');
        showStep(currentStep);
        return;
    }

    var body = {
        serviceId: method,
        jwksUri: document.getElementById('w-jwksUri').value,
        tokenAppProperty: document.getElementById('w-tokenAppProp').value,
        identityPath: document.getElementById('w-identityPath').value,
        issuer: document.getElementById('w-issuer').value,
        audience: document.getElementById('w-audience').value
    };
    apiPost('/api/authenticators/jwt/setup', body).then(function(data) {
        if (data.error) { stepState[3] = 'error'; stepResult(3, data.error, 'err'); }
        else { stepState[3] = 'done'; stepResult(3, data, 'ok'); }
        showStep(currentStep);
    }).catch(function(e) { stepState[3] = 'error'; stepResult(3, e.message, 'err'); showStep(currentStep); });
}

function executeEnvironments() {
    var allEnvs = getChecked('w-env');
    if (allEnvs.length === 0) { stepResult(4, 'Select at least one environment', 'err'); return; }
    var existingEnvs = wizCtx.existing.envs[wizCtx.orgName] || [];
    var newEnvs = allEnvs.filter(function(e){ return existingEnvs.indexOf(e) === -1; });
    wizCtx.environments = allEnvs;

    if(newEnvs.length === 0){
        stepState[4] = 'done';
        stepResult(4, 'All selected environments already exist in Conjur: ' + allEnvs.join(', '), 'ok');
        populateEnvDropdown();
        showStep(currentStep);
        return;
    }

    apiPost('/api/setup/environments', {orgName: wizCtx.orgName, environments: newEnvs}).then(function(data) {
        if (data.error) { stepState[4] = 'error'; stepResult(4, data.error, 'err'); }
        else {
            stepState[4] = 'done';
            var msg = typeof data === 'string' ? data : JSON.stringify(data, null, 2);
            if(existingEnvs.length > 0) msg += '\n\nAlready existing: ' + existingEnvs.join(', ');
            stepResult(4, msg, 'ok');
            newEnvs.forEach(function(e){
                if(!wizCtx.existing.envs[wizCtx.orgName]) wizCtx.existing.envs[wizCtx.orgName] = [];
                wizCtx.existing.envs[wizCtx.orgName].push(e);
            });
            populateEnvDropdown();
        }
        showStep(currentStep);
    }).catch(function(e) { stepState[4] = 'error'; stepResult(4, e.message, 'err'); showStep(currentStep); });
}

function executeProduct() {
    wizCtx.productEnv = document.getElementById('w-prodEnv').value;
    wizCtx.productName = document.getElementById('w-productName').value.trim();
    wizCtx.appTypes = getChecked('w-appType');
    wizCtx.resourceTypes = getChecked('w-resType');
    if (!wizCtx.productName) { stepResult(5, 'Product name is required', 'err'); return; }
    var body = {
        orgName: wizCtx.orgName, environment: wizCtx.productEnv,
        productName: wizCtx.productName, appTypes: wizCtx.appTypes, resourceTypes: wizCtx.resourceTypes
    };
    apiPost('/api/setup/product', body).then(function(data) {
        if (data.error) { stepState[5] = 'error'; stepResult(5, data.error, 'err'); }
        else {
            stepState[5] = 'done'; stepResult(5, data, 'ok');
            var pk = wizCtx.orgName+'/'+wizCtx.productEnv;
            if(!wizCtx.existing.prods[pk]) wizCtx.existing.prods[pk] = [];
            if(wizCtx.existing.prods[pk].indexOf(wizCtx.productName)===-1) wizCtx.existing.prods[pk].push(wizCtx.productName);
            var apk = wizCtx.orgName+'/'+wizCtx.productEnv+'/'+wizCtx.productName;
            wizCtx.existing.appTypes[apk] = wizCtx.appTypes.slice();
            wizCtx.existing.resTypes[apk] = wizCtx.resourceTypes.slice();
            addDbRow(); addHostRow(); addGrantRow();
        }
        showStep(currentStep);
    }).catch(function(e) { stepState[5] = 'error'; stepResult(5, e.message, 'err'); showStep(currentStep); });
}

function executeResources() {
    var env = document.getElementById('w-resEnv') ? document.getElementById('w-resEnv').value : wizCtx.productEnv;
    var prod = document.getElementById('w-resProduct') ? document.getElementById('w-resProduct').value : wizCtx.productName;
    if(!env || !prod) { stepResult(6, 'Select an environment and product first.', 'err'); return; }
    wizCtx.productEnv = env;
    wizCtx.productName = prod;

    var results = [];
    var promises = [];
    var base = { orgName: wizCtx.orgName, environment: env, product: prod };
    wizCtx.databases = [];

    document.querySelectorAll('.w-db-row').forEach(function(row) {
        var name = row.querySelector('.w-dbName').value.trim();
        if (!name) return;
        wizCtx.databases.push(name);
        var body = Object.assign({}, base, { resourceName: name, resourceType: 'dbs' });
        var secrets = {};
        var fields = row.querySelectorAll('.w-db-secret');
        fields.forEach(function(f) { if (f.value.trim()) secrets[f.dataset.key] = f.value.trim(); });
        if (Object.keys(secrets).length > 0) body.initialSecrets = secrets;
        promises.push(apiPost('/api/resources/database', body)
            .then(function(d) { results.push('DB ' + name + ': ' + (d.message || d.error || JSON.stringify(d))); }));
    });

    document.querySelectorAll('.w-kafkaName').forEach(function(el) {
        if (el.value.trim()) {
            var name = el.value.trim();
            promises.push(apiPost('/api/resources/kafka', Object.assign({}, base, { resourceName: name }))
                .then(function(d) { results.push('Kafka ' + name + ': ' + (d.message || d.error)); }));
        }
    });

    document.querySelectorAll('#w-infraList .form-row').forEach(function(row) {
        var type = row.querySelector('.w-infraType');
        var name = row.querySelector('.w-infraName');
        if (type && name && name.value.trim()) {
            promises.push(apiPost('/api/resources/infrastructure',
                Object.assign({}, base, { resourceType: type.value, resourceName: name.value.trim() }))
                .then(function(d) { results.push(type.value + ' ' + name.value + ': ' + (d.message || d.error)); }));
        }
    });

    if (promises.length === 0) { stepResult(6, 'No new resources to register.', 'err'); return; }
    Promise.all(promises).then(function() {
        stepState[6] = 'done'; stepResult(6, results.join('\n'), 'ok');
        loadExisting();
        showStep(currentStep);
    }).catch(function(e) {
        stepState[6] = 'error'; stepResult(6, results.join('\n') + '\nError: ' + e.message, 'err'); showStep(currentStep);
    });
}

function executeHosts() {
    var env = document.getElementById('w-hostEnv') ? document.getElementById('w-hostEnv').value : wizCtx.productEnv;
    var prod = document.getElementById('w-hostProduct') ? document.getElementById('w-hostProduct').value : wizCtx.productName;
    if(!env || !prod) { stepResult(7, 'Select an environment and product first.', 'err'); return; }
    wizCtx.productEnv = env;
    wizCtx.productName = prod;

    var results = [];
    var promises = [];
    wizCtx.hosts = [];
    document.querySelectorAll('#w-hostList .form-row').forEach(function(row) {
        var hostId = row.querySelector('.w-hostId');
        var appType = row.querySelector('.w-hostAppType');
        var ns = row.querySelector('.w-hostNs');
        var sa = row.querySelector('.w-hostSa');
        if (!hostId || !hostId.value.trim()) return;
        var body = {
            orgName: wizCtx.orgName, environment: env, product: prod,
            appType: appType ? appType.value : wizCtx.appTypes[0] || 'nims',
            hostId: hostId.value.trim(),
            namespace: ns ? ns.value : 'apps',
            serviceAccount: sa && sa.value.trim() ? sa.value.trim() : null,
            authMethod: wizCtx.authMethod
        };
        promises.push(apiPost('/api/hosts/register', body).then(function(d) {
            var msg = hostId.value + ': ' + (d.message || d.error || JSON.stringify(d));
            if (d.apiKey) { msg += '\n  API Key: ' + d.apiKey + ' (SAVE THIS!)'; }
            wizCtx.hosts.push({ id: hostId.value.trim(), apiKey: d.apiKey || null, appType: appType ? appType.value : '' });
            results.push(msg);
        }));
    });
    if (promises.length === 0) { stepResult(7, 'No new hosts to register.', 'err'); return; }
    Promise.all(promises).then(function() {
        stepState[7] = 'done'; stepResult(7, results.join('\n\n'), 'ok');
        loadExisting();
        showStep(currentStep);
    }).catch(function(e) {
        stepState[7] = 'error'; stepResult(7, results.join('\n\n') + '\nError: ' + e.message, 'err'); showStep(currentStep);
    });
}

function executeAccessSecrets() {
    var env = document.getElementById('w-grantEnv') ? document.getElementById('w-grantEnv').value : wizCtx.productEnv;
    var prod = document.getElementById('w-grantProduct') ? document.getElementById('w-grantProduct').value : wizCtx.productName;
    if(!env || !prod) { stepResult(8, 'Select an environment and product first.', 'err'); return; }

    var results = [];
    var promises = [];
    document.querySelectorAll('#w-grantList .form-row').forEach(function(row) {
        var hostSel = row.querySelector('.w-grantHost');
        var resSel = row.querySelector('.w-grantResource');
        if (!hostSel || !hostSel.value || !resSel || !resSel.value) return;
        var hostParts = hostSel.value.split('/');
        var resParts = resSel.value.split('/');
        var body = {
            orgName: wizCtx.orgName, environment: env, product: prod,
            appType: hostParts[0],
            hostId: hostParts.slice(1).join('/'),
            targetType: resParts[0],
            targetResource: resParts.slice(1).join('/')
        };
        promises.push(apiPost('/api/access/grant', body).then(function(d) {
            results.push('Grant ' + hostSel.value + ' -> ' + resSel.value + ': ' + (d.message || d.error));
        }));
    });

    if (promises.length === 0) {
        stepResult(8, 'No grants to apply.', 'info');
        stepState[8] = 'done'; showStep(currentStep); return;
    }
    Promise.all(promises).then(function() {
        stepState[8] = 'done'; stepResult(8, results.join('\n'), 'ok'); showStep(currentStep);
    }).catch(function(e) {
        stepState[8] = 'error'; stepResult(8, results.join('\n') + '\n' + e.message, 'err'); showStep(currentStep);
    });
}

function executeSummary() {
    var results = [];
    var kinds = ['policy', 'variable', 'host', 'webservice', 'group', 'layer'];
    var promises = kinds.map(function(kind) {
        return fetch('/api/resources?kind=' + kind).then(function(r) { return r.json().catch(function(){ return []; }); })
            .then(function(d) { results.push(kind + ': ' + (Array.isArray(d) ? d.length : '?')); })
            .catch(function() { results.push(kind + ': error'); });
    });
    Promise.all(promises).then(function() {
        var summary = 'Setup Wizard Complete!\n========================\n\n' + results.join('\n');
        stepState[9] = 'done'; stepResult(9, summary, 'ok');
        var statsHtml = '<div class="summary">';
        results.forEach(function(r) {
            var parts = r.split(': ');
            statsHtml += '<div class="stat"><div class="num">' + parts[1] + '</div><div class="label">' + parts[0] + '</div></div>';
        });
        document.getElementById('summaryStats').innerHTML = statsHtml + '</div>';
        showStep(currentStep);
    });
}

// ============================================
// Dynamic Row Helpers
// ============================================
function addDbRow() {
    var html = '<div class="w-db-row" style="border:1px solid #eee;padding:10px;margin:8px 0;border-radius:6px;">'
        + '<div class="form-row"><div class="form-group"><label>DB Name</label>'
        + '<input type="text" class="w-dbName" placeholder="orders-db" /></div>'
        + '<button class="danger" onclick="this.parentElement.parentElement.remove()" style="height:32px;padding:4px 12px;font-size:0.85em;align-self:end;">X</button></div>'
        + '<div class="form-row" style="margin-top:4px;">'
        + '<div class="form-group"><label>Host Name</label><input class="w-db-secret" data-key="host-name" placeholder="optional" style="min-width:140px;"></div>'
        + '<div class="form-group"><label>Username</label><input class="w-db-secret" data-key="username" placeholder="optional" style="min-width:120px;"></div>'
        + '<div class="form-group"><label>Password</label><input class="w-db-secret" data-key="password" type="password" placeholder="optional" style="min-width:120px;"></div>'
        + '<div class="form-group"><label>Port</label><input class="w-db-secret" data-key="port" placeholder="optional" style="min-width:80px;"></div>'
        + '<div class="form-group"><label>DB Name</label><input class="w-db-secret" data-key="database-name" placeholder="optional" style="min-width:120px;"></div>'
        + '</div></div>';
    document.getElementById('w-dbList').insertAdjacentHTML('beforeend', html);
}

function addKafkaRow() {
    var html = '<div class="form-row"><div class="form-group"><label>Cluster Name</label>'
        + '<input type="text" class="w-kafkaName" placeholder="main-cluster" /></div>'
        + '<button class="danger" onclick="this.parentElement.remove()" style="height:32px;padding:4px 12px;font-size:0.85em;">X</button></div>';
    document.getElementById('w-kafkaList').insertAdjacentHTML('beforeend', html);
}

function addInfraRow() {
    var env = document.getElementById('w-resEnv') ? document.getElementById('w-resEnv').value : wizCtx.productEnv;
    var prod = document.getElementById('w-resProduct') ? document.getElementById('w-resProduct').value : wizCtx.productName;
    var pk = wizCtx.orgName + '/' + env + '/' + prod;
    var resTypes = wizCtx.existing.resTypes[pk] || wizCtx.resourceTypes || [];
    var infraTypes = resTypes.filter(function(t){ return t !== 'dbs' && t !== 'kafka'; });
    if(infraTypes.length === 0) infraTypes = ['api','smtp','ldap','oauth','certs'];
    var typeOpts = infraTypes.map(function(t){ return '<option value="'+t+'">'+t+'</option>'; }).join('');
    var html = '<div class="form-row"><div class="form-group"><label>Type</label>'
        + '<select class="w-infraType">' + typeOpts + '</select></div>'
        + '<div class="form-group"><label>Name</label><input type="text" class="w-infraName" placeholder="resource-name" /></div>'
        + '<button class="danger" onclick="this.parentElement.remove()" style="height:32px;padding:4px 12px;font-size:0.85em;">X</button></div>';
    document.getElementById('w-infraList').insertAdjacentHTML('beforeend', html);
}

function addHostRow() {
    var atOpts = appTypeOptions() || '<option value="nims">nims</option><option value="batch">batch</option>';
    var k8sDisplay = (wizCtx.authMethod === 'authn') ? 'none' : '';
    var html = '<div class="form-row" style="align-items:end;">'
        + '<div class="form-group"><label>Host ID</label><input type="text" class="w-hostId" placeholder="app1" /></div>'
        + '<div class="form-group"><label>App Type</label><select class="w-hostAppType">' + atOpts + '</select></div>'
        + '<div class="form-group w-hostK8sFields" style="display:' + k8sDisplay + '"><label>Namespace</label><input type="text" class="w-hostNs" value="apps" style="min-width:120px;" /></div>'
        + '<div class="form-group w-hostK8sFields" style="display:' + k8sDisplay + '"><label>Service Account</label><input type="text" class="w-hostSa" placeholder="auto" style="min-width:140px;" /></div>'
        + '<button class="danger" onclick="this.parentElement.remove()" style="height:32px;padding:4px 12px;font-size:0.85em;">X</button></div>';
    document.getElementById('w-hostList').insertAdjacentHTML('beforeend', html);
}

function addGrantRow() {
    var env = document.getElementById('w-grantEnv') ? document.getElementById('w-grantEnv').value : wizCtx.productEnv;
    var prod = document.getElementById('w-grantProduct') ? document.getElementById('w-grantProduct').value : wizCtx.productName;
    var appType = document.getElementById('w-grantAppType') ? document.getElementById('w-grantAppType').value : '';
    var pk = wizCtx.orgName + '/' + env + '/' + prod;
    var hosts = (wizCtx.existing.hosts[pk]||[]).filter(function(h){
        return !appType || h.appType === appType;
    });
    var hostOpts = '';
    if(hosts.length === 0){
        hostOpts = '<option value="">-- no hosts --</option>';
    } else {
        hosts.forEach(function(h){
            hostOpts += '<option value="'+esc(h.appType)+'/'+esc(h.hostId)+'">'+esc(h.appType)+'/'+esc(h.hostId)+'</option>';
        });
    }
    var resources = wizCtx.existing.resources[pk] || [];
    var resOpts = '';
    if(resources.length === 0){
        resOpts = '<option value="">-- no resources --</option>';
    } else {
        resources.forEach(function(r){
            resOpts += '<option value="'+esc(r.type)+'/'+esc(r.name)+'">'+esc(r.type)+'/'+esc(r.name)+'</option>';
        });
    }
    var html = '<div class="form-row" style="align-items:end;">'
        + '<div class="form-group"><label>Host</label><select class="w-grantHost">' + hostOpts + '</select></div>'
        + '<div class="form-group"><label>Resource</label><select class="w-grantResource">' + resOpts + '</select></div>'
        + '<button class="danger" onclick="this.parentElement.remove()" style="height:32px;padding:4px 12px;font-size:0.85em;">X</button></div>';
    document.getElementById('w-grantList').insertAdjacentHTML('beforeend', html);
}

initWizard();
