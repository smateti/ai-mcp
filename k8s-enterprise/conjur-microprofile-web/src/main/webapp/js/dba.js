var dbaData = {};
var liveCtx = {};
var currentTab = 'databases';
var DB_FIELDS = ['host-name','username','password','port','database-name'];

function switchTab(tab) {
    document.querySelectorAll('.tab-btn').forEach(function(b){b.classList.remove('active');});
    document.querySelectorAll('.tab-panel').forEach(function(p){p.style.display='none';});
    document.getElementById('btn-'+tab).classList.add('active');
    document.getElementById('panel-'+tab).style.display='block';
    currentTab = tab;
    if (tab==='databases' && !dbaData.databases) loadDatabases();
}

function loadLiveDropdowns() {
    loadLiveContext(function(ctx) {
        liveCtx = ctx;
        fillLiveSelect('db-org', ctx.orgs);
        fillLiveSelect('db-env', []);
        fillLiveSelect('db-product', []);
        var orgEl = document.getElementById('db-org');
        var envEl = document.getElementById('db-env');
        var prodEl = document.getElementById('db-product');
        if (orgEl) orgEl.onchange = function() { cascadeDba('org'); };
        if (envEl) envEl.onchange = function() { cascadeDba('env'); };
        if (prodEl) prodEl.onchange = function() { cascadeDba('product'); };
        if (ctx.orgs.length === 1) {
            orgEl.value = ctx.orgs[0]; cascadeDba('org');
        }
    });
}

function cascadeDba(level) {
    var org = document.getElementById('db-org').value;
    var envEl = document.getElementById('db-env');
    var prodEl = document.getElementById('db-product');
    if (level === 'org') {
        var envs = org ? (liveCtx.envs[org] || []) : [];
        fillLiveSelect('db-env', envs);
        fillLiveSelect('db-product', []);
        if (envs.length === 1) { envEl.value = envs[0]; cascadeDba('env'); }
    } else if (level === 'env') {
        var env = envEl.value;
        var prods = (org && env) ? (liveCtx.prods[org + '/' + env] || []) : [];
        fillLiveSelect('db-product', prods);
        if (prods.length === 1) { prodEl.value = prods[0]; cascadeDba('product'); }
    }
}

function loadDatabases() {
    document.getElementById('db-list').innerHTML = '<p>Loading databases...</p>';
    fetch('/api/resources?kind=variable&search=resources/dbs')
    .then(function(r){return r.json();})
    .then(function(data){
        dbaData.databases = data;
        renderDatabases(data);
    })
    .catch(function(e){
        document.getElementById('db-list').innerHTML = '<p class="badge badge-err">Error: '+e.message+'</p>';
    });
}

function renderDatabases(data) {
    var groups = {};
    data.forEach(function(r){
        var id = r.id||'';
        var parts = id.split(':');
        var path = parts.length>2 ? parts[2] : parts[parts.length-1];
        var match = path.match(/(.+)\/resources\/dbs\/([^\/]+)\/(.+)/);
        if(match){
            var key = match[1]+'/resources/dbs/'+match[2];
            if(!groups[key]) groups[key]={ name:match[2], prefix:match[1], vars:[] };
            groups[key].vars.push(match[3]);
        }
    });
    var keys = Object.keys(groups);
    if(keys.length===0){
        document.getElementById('db-list').innerHTML = '<p>No databases found. Create one using the "Create Database" tab.</p>';
        return;
    }
    var html = '<table><tr><th>Database</th><th>Path</th><th>Variables</th><th>Actions</th></tr>';
    keys.forEach(function(k){
        var g = groups[k];
        html += '<tr><td><strong>'+esc(g.name)+'</strong></td>'
            +'<td><code>'+esc(g.prefix)+'</code></td>'
            +'<td>'+g.vars.length+'</td>'
            +'<td><button onclick="manageDb(\''+escJs(k)+'\',\''+escJs(g.name)+'\')">Manage</button></td></tr>';
    });
    html += '</table>';
    document.getElementById('db-list').innerHTML = html;
    populateDbDropdown(groups);
}

function populateDbDropdown(groups) {
    var sel = document.getElementById('cred-db-select');
    sel.innerHTML = '<option value="">-- Select Database --</option>';
    Object.keys(groups).forEach(function(k){
        var g = groups[k];
        sel.innerHTML += '<option value="'+esc(k)+'">'+esc(g.name)+' ('+esc(g.prefix)+')</option>';
    });
}

function manageDb(fullPath, dbName) {
    var html = '<h3>Database: '+esc(dbName)+'</h3><p>Path: <code>'+esc(fullPath)+'</code></p>';
    html += '<table><tr><th>Variable</th><th>Value</th></tr>';
    DB_FIELDS.forEach(function(v){
        html += '<tr><td>'+v+'</td><td id="dbval-'+v+'">Loading...</td></tr>';
    });
    html += '</table><div id="manage-result" class="result"></div>';
    document.getElementById('db-detail').innerHTML = html;
    document.getElementById('db-detail').style.display = 'block';
    DB_FIELDS.forEach(function(v){
        var varPath = fullPath+'/'+v;
        fetch('/api/secrets/'+varPath.replace(/\//g,'.'))
        .then(function(r){return r.json();})
        .then(function(d){
            document.getElementById('dbval-'+v).innerHTML = d.found
                ? '<code class="masked">'+esc(d.value)+'</code>'
                : '<span class="badge badge-warn">Not set</span>';
            document.querySelectorAll('.masked').forEach(function(el){
                el.onclick = function(){ this.classList.toggle('revealed'); };
            });
        })
        .catch(function(){ document.getElementById('dbval-'+v).innerHTML = '<span class="badge badge-warn">Not set</span>'; });
    });
}

function createDatabase() {
    var org = document.getElementById('db-org').value;
    var env = document.getElementById('db-env').value;
    var prod = document.getElementById('db-product').value;
    var name = document.getElementById('db-name').value.trim();
    if(!org||!env||!prod||!name){ alert('All fields are required'); return; }
    var body = { orgName:org, environment:env, product:prod, resourceName:name, resourceType:'dbs' };
    var secrets = {};
    DB_FIELDS.forEach(function(v){
        var val = document.getElementById('db-init-'+v);
        if(val && val.value.trim()) secrets[v] = val.value.trim();
    });
    if(Object.keys(secrets).length>0) body.initialSecrets = secrets;
    apiCall('POST','/api/resources/database',body)
    .then(function(d){
        showResult('create-db-result', d, !!d.error);
        if(!d.error){ dbaData.databases=null; loadDatabases(); }
    })
    .catch(function(e){ showResult('create-db-result', e.message, true); });
}

function loadCredentials() {
    var fullPath = document.getElementById('cred-db-select').value;
    if(!fullPath){ alert('Select a database first'); return; }
    DB_FIELDS.forEach(function(v){
        var input = document.getElementById('cred-'+v);
        if(input) input.value = '';
        fetch('/api/secrets/'+(fullPath+'/'+v).replace(/\//g,'.'))
        .then(function(r){return r.json();})
        .then(function(d){ if(d.found && input) input.value = d.value; })
        .catch(function(){});
    });
}

function saveCredentials() {
    var fullPath = document.getElementById('cred-db-select').value;
    if(!fullPath){ alert('Select a database first'); return; }
    var secrets = [];
    DB_FIELDS.forEach(function(v){
        var input = document.getElementById('cred-'+v);
        if(input && input.value.trim()){
            secrets.push({ variableId: fullPath+'/'+v, value: input.value.trim() });
        }
    });
    if(secrets.length===0){ alert('Enter at least one credential value'); return; }
    apiCall('POST','/api/secrets/bulk',{ secrets: secrets })
    .then(function(d){ showResult('cred-result', d, !!d.errors && d.errors.length>0); })
    .catch(function(e){ showResult('cred-result', e.message, true); });
}

function esc(s){ if(!s) return ''; return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }
function escJs(s){ return s.replace(/\\/g,'\\\\').replace(/'/g,"\\'"); }

loadLiveDropdowns();
loadDatabases();
