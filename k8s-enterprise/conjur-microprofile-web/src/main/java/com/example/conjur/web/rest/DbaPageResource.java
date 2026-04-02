package com.example.conjur.web.rest;

import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/dba")
@RequestScoped
public class DbaPageResource {

    private static final String DBA_SCRIPT = """
            var dbaData = {};
            var refData = {};
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

            // ===== Reference Data for Dropdowns =====
            function loadRefData() {
                fetch('/api/refdata/all').then(function(r){return r.json();})
                .then(function(data){
                    refData = data;
                    fillSelect('db-org', (data.organizations||[]).map(function(i){return i.name;}));
                    fillSelect('db-env', (data.environments||[]).map(function(i){return i.name;}));
                    fillSelect('db-product', (data.products||[]).map(function(i){return i.name;}));
                }).catch(function(e){ console.error('RefData load error:',e); });
            }

            function fillSelect(id, items, ph) {
                var sel = document.getElementById(id);
                if(!sel) return;
                sel.innerHTML = '<option value="">'+(ph||'-- Select --')+'</option>';
                items.forEach(function(i){ sel.innerHTML += '<option value="'+esc(i)+'">'+esc(i)+'</option>'; });
            }

            // ===== My Databases Tab =====
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
                    var match = path.match(/(.+)\\/resources\\/dbs\\/([^\\/]+)\\/(.+)/);
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
                        +'<td><button onclick="manageDb(\\''+escJs(k)+'\\',\\''+escJs(g.name)+'\\')">Manage</button></td></tr>';
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
                    fetch('/api/secrets/'+varPath.replace(/\\//g,'.'))
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

            // ===== Create Database =====
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

            // ===== Set Credentials =====
            function loadCredentials() {
                var fullPath = document.getElementById('cred-db-select').value;
                if(!fullPath){ alert('Select a database first'); return; }
                DB_FIELDS.forEach(function(v){
                    var input = document.getElementById('cred-'+v);
                    if(input) input.value = '';
                    fetch('/api/secrets/'+(fullPath+'/'+v).replace(/\\//g,'.'))
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
            function escJs(s){ return s.replace(/\\\\/g,'\\\\\\\\').replace(/'/g,"\\\\'"); }

            loadRefData();
            loadDatabases();
            """;

    private static final String DBA_HTML = """
            <style>
                .tab-bar { display:flex; gap:0; margin-bottom:0; }
                .tab-btn { padding:10px 24px; background:#e0e0e0; border:none; cursor:pointer;
                           font-weight:600; border-radius:8px 8px 0 0; color:#555; }
                .tab-btn.active { background:#fff; color:#16213e; }
                .tab-panel { display:none; background:#fff; padding:20px; border-radius:0 8px 8px 8px;
                             box-shadow:0 2px 4px rgba(0,0,0,0.1); }
                #db-detail { display:none; margin-top:16px; padding:16px; background:#f8f9fa;
                             border-radius:8px; border:1px solid #dee2e6; }
            </style>

            <h2>DBA Console</h2>
            <p>Manage databases — create resources, set credentials, and view access.</p>

            <div class="tab-bar">
                <button class="tab-btn active" id="btn-databases" onclick="switchTab('databases')">My Databases</button>
                <button class="tab-btn" id="btn-create" onclick="switchTab('create')">Create Database</button>
                <button class="tab-btn" id="btn-credentials" onclick="switchTab('credentials')">Set Credentials</button>
            </div>

            <div class="tab-panel" id="panel-databases" style="display:block">
                <div id="db-list"><p>Loading...</p></div>
                <div id="db-detail"></div>
            </div>

            <div class="tab-panel" id="panel-create">
                <h3>Register New Database</h3>
                <div class="form-row">
                    <div class="form-group"><label>Organization</label>
                        <select id="db-org"><option value="">-- Select --</option></select></div>
                    <div class="form-group"><label>Environment</label>
                        <select id="db-env"><option value="">-- Select --</option></select></div>
                    <div class="form-group"><label>Product</label>
                        <select id="db-product"><option value="">-- Select --</option></select></div>
                    <div class="form-group"><label>Database Name</label>
                        <input id="db-name" placeholder="orders-db"></div>
                </div>
                <h4>Initial Credentials (optional)</h4>
                <div class="form-row">
                    <div class="form-group"><label>Host Name</label>
                        <input id="db-init-host-name" placeholder="dbhost.example.com"></div>
                    <div class="form-group"><label>Username</label>
                        <input id="db-init-username" placeholder="app_user"></div>
                    <div class="form-group"><label>Password</label>
                        <input id="db-init-password" type="password" placeholder="secret"></div>
                </div>
                <div class="form-row">
                    <div class="form-group"><label>Port</label>
                        <input id="db-init-port" placeholder="1521"></div>
                    <div class="form-group"><label>Database Name</label>
                        <input id="db-init-database-name" placeholder="ORCL"></div>
                </div>
                <button onclick="createDatabase()">Create Database</button>
                <div id="create-db-result" class="result"></div>
            </div>

            <div class="tab-panel" id="panel-credentials">
                <h3>Set / Update Credentials</h3>
                <div class="form-row">
                    <div class="form-group"><label>Select Database</label>
                        <select id="cred-db-select"><option value="">-- Select Database --</option></select>
                    </div>
                    <button onclick="loadCredentials()">Load Current</button>
                </div>
                <div class="form-row">
                    <div class="form-group"><label>Host Name</label><input id="cred-host-name"></div>
                    <div class="form-group"><label>Username</label><input id="cred-username"></div>
                    <div class="form-group"><label>Password</label><input id="cred-password" type="password"></div>
                </div>
                <div class="form-row">
                    <div class="form-group"><label>Port</label><input id="cred-port"></div>
                    <div class="form-group"><label>Database Name</label><input id="cred-database-name"></div>
                </div>
                <button onclick="saveCredentials()">Save Credentials</button>
                <div id="cred-result" class="result"></div>
            </div>
            """;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String dba() {
        return PageTemplates.page("DBA Console", "nav-dba",
                DBA_HTML + "<script>" + PageTemplates.SCRIPT_UTILS + DBA_SCRIPT + "</script>");
    }
}
