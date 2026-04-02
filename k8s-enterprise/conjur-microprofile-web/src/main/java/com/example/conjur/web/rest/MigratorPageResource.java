package com.example.conjur.web.rest;

import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/migrator")
@RequestScoped
public class MigratorPageResource {

    private static final String MIGRATOR_SCRIPT = """
            var migData = {};
            var refData = {};
            var currentTab = 'create-app';

            function switchTab(tab) {
                document.querySelectorAll('.tab-btn').forEach(function(b){b.classList.remove('active');});
                document.querySelectorAll('.tab-panel').forEach(function(p){p.style.display='none';});
                document.getElementById('btn-'+tab).classList.add('active');
                document.getElementById('panel-'+tab).style.display='block';
                currentTab = tab;
                if (tab==='my-apps' && !migData.apps) loadMyApps();
                if (tab==='manage-vars' && !migData.vars) loadVars();
                if (tab==='grants') loadGrantContext();
            }

            // ===== Reference Data =====
            function loadRefData() {
                fetch('/api/refdata/all').then(function(r){return r.json();})
                .then(function(data){
                    refData = data;
                    var orgs = (data.organizations||[]).map(function(i){return i.name;});
                    var envs = (data.environments||[]).map(function(i){return i.name;});
                    var prods = (data.products||[]).map(function(i){return i.name;});
                    var ats = (data.apptypes||[]).map(function(i){return i.name;});
                    ['app','api','grant','var'].forEach(function(pfx){
                        fillSelect(pfx+'-org', orgs);
                        fillSelect(pfx+'-env', envs);
                        fillSelect(pfx+'-product', prods);
                    });
                    fillSelect('app-type', ats);
                }).catch(function(e){ console.error('RefData load error:',e); });
            }

            function fillSelect(id, items, ph) {
                var sel = document.getElementById(id);
                if(!sel) return;
                sel.innerHTML = '<option value="">'+(ph||'-- Select --')+'</option>';
                items.forEach(function(i){ sel.innerHTML += '<option value="'+esc(i)+'">'+esc(i)+'</option>'; });
            }

            // ===== Tab 1: Create App =====
            function createApp() {
                var org = document.getElementById('app-org').value;
                var env = document.getElementById('app-env').value;
                var prod = document.getElementById('app-product').value;
                var appType = document.getElementById('app-type').value;
                var hostId = document.getElementById('app-hostid').value.trim();
                var ns = document.getElementById('app-namespace').value.trim() || 'apps';
                var sa = document.getElementById('app-sa').value.trim() || (hostId + '-sa');

                if (!org || !env || !prod || !appType || !hostId) {
                    alert('Org, Environment, Product, App Type, and Host ID are required');
                    return;
                }

                var body = { orgName: org, environment: env, product: prod, appType: appType,
                             hostId: hostId, namespace: ns, serviceAccount: sa };

                document.getElementById('app-result').style.display = 'block';
                document.getElementById('app-result').className = 'result';
                document.getElementById('app-result').textContent = 'Registering host...';

                apiCall('POST', '/api/hosts/register', body)
                    .then(function(d) {
                        if (d.error) {
                            showResult('app-result', d.error, true);
                            return;
                        }
                        var msg = 'Host registered!\\n\\nHost ID: ' + (d.hostId || d.name)
                                + '\\nAPI Key: ' + (d.apiKey || 'N/A')
                                + '\\n\\n*** SAVE THE API KEY - IT IS SHOWN ONLY ONCE ***';

                        var createK8s = document.getElementById('app-create-k8s').checked;
                        if (createK8s && d.apiKey) {
                            var hostPath = org + '/' + env + '/products/' + prod + '/apps/' + appType + '/' + hostId;
                            var secretsMapping = document.getElementById('app-secrets-mapping').value.trim();
                            var secretName = 'conjur-' + hostId;
                            return apiCall('POST', '/api/cicd/k8s-secret', {
                                secretName: secretName,
                                namespace: ns,
                                hostPath: hostPath,
                                apiKey: d.apiKey,
                                conjurSecrets: secretsMapping
                            }).then(function(k) {
                                msg += '\\n\\nK8s Secret: ' + (k.secretName || 'failed') + ' in namespace ' + ns;
                                showResult('app-result', msg, false);
                            }).catch(function(e) {
                                msg += '\\n\\nK8s Secret creation failed: ' + e.message;
                                showResult('app-result', msg, false);
                            });
                        } else {
                            showResult('app-result', msg, false);
                        }
                    })
                    .catch(function(e) { showResult('app-result', e.message, true); });
            }

            // ===== Tab 2: Create API Resource =====
            function createApiResource() {
                var org = document.getElementById('api-org').value;
                var env = document.getElementById('api-env').value;
                var prod = document.getElementById('api-product').value;
                var name = document.getElementById('api-name').value.trim();
                if (!org || !env || !prod || !name) { alert('All fields are required'); return; }

                var body = { orgName: org, environment: env, product: prod,
                             resourceType: 'api', resourceName: name };
                var secrets = {};
                var cid = document.getElementById('api-clientid').value.trim();
                var csec = document.getElementById('api-clientsecret').value.trim();
                var whk = document.getElementById('api-webhook').value.trim();
                if (cid) secrets['key'] = cid;
                if (csec) secrets['secret'] = csec;
                if (whk) secrets['webhook-secret'] = whk;
                if (Object.keys(secrets).length > 0) body.initialSecrets = secrets;

                apiCall('POST', '/api/resources/infrastructure', body)
                    .then(function(d) { showResult('api-result', d, !!d.error); })
                    .catch(function(e) { showResult('api-result', e.message, true); });
            }

            // ===== Tab 3: Grant Access =====
            var grantHosts = [];
            var grantDbs = [];

            function loadGrantContext() {
                var org = document.getElementById('grant-org').value;
                var env = document.getElementById('grant-env').value;
                var prod = document.getElementById('grant-product').value;
                if (!org || !env || !prod) return;
                var basePath = org+'/'+env+'/products/'+prod;
                fetch('/api/resources?kind=host&search='+encodeURIComponent(basePath+'/apps'))
                .then(function(r){return r.json();})
                .then(function(data){
                    grantHosts = [];
                    (Array.isArray(data)?data:[]).forEach(function(r){
                        var id = (r.id||'').split(':').pop();
                        var m = id.match(/\\/apps\\/([^\\/]+)\\/(.+)$/);
                        if(m) grantHosts.push({ appType:m[1], hostId:m[2], full:id });
                    });
                    updateGrantHostDropdowns();
                }).catch(function(){});
                fetch('/api/resources?kind=variable&search='+encodeURIComponent(basePath+'/resources/dbs'))
                .then(function(r){return r.json();})
                .then(function(data){
                    var dbSet = {};
                    (Array.isArray(data)?data:[]).forEach(function(r){
                        var path = (r.id||'').split(':').pop();
                        var m = path.match(/\\/resources\\/dbs\\/([^\\/]+)\\//);
                        if(m) dbSet[m[1]]=true;
                    });
                    grantDbs = Object.keys(dbSet).sort();
                    updateGrantDbDropdowns();
                }).catch(function(){});
            }

            function updateGrantHostDropdowns() {
                document.querySelectorAll('.gr-host-select').forEach(function(sel){
                    var cur = sel.value;
                    sel.innerHTML = '<option value="">-- Select Host --</option>';
                    grantHosts.forEach(function(h){
                        sel.innerHTML += '<option value="'+esc(h.appType+'|'+h.hostId)+'">'+esc(h.appType+'/'+h.hostId)+'</option>';
                    });
                    if(cur) sel.value = cur;
                });
            }
            function updateGrantDbDropdowns() {
                document.querySelectorAll('.gr-db-select').forEach(function(sel){
                    var cur = sel.value;
                    sel.innerHTML = '<option value="">-- Select DB --</option>';
                    grantDbs.forEach(function(d){
                        sel.innerHTML += '<option value="'+esc(d)+'">'+esc(d)+'</option>';
                    });
                    if(cur) sel.value = cur;
                });
            }

            var grantCount = 0;
            function addGrantRow() {
                grantCount++;
                var html = '<div class="grant-row" id="grant-' + grantCount + '">'
                    + '<div class="form-row">'
                    + '<div class="form-group"><label>Host</label>'
                    + '<select class="gr-host-select" style="min-width:200px;"><option value="">-- Select Host --</option></select></div>'
                    + '<div class="form-group"><label>Grant Type</label>'
                    + '<select class="gr-type" onchange="toggleGrantTarget(this)">'
                    + '<option value="db">Database</option><option value="shared">Shared Resources</option>'
                    + '<option value="jwt">JWT Authenticator</option></select></div>'
                    + '<div class="form-group gr-target-group"><label>Target</label>'
                    + '<select class="gr-db-select" style="min-width:200px;"><option value="">-- Select DB --</option></select></div>'
                    + '<div class="form-group gr-jwt-group" style="display:none;"><label>JWT Service ID</label>'
                    + '<input class="gr-jwt-svc" placeholder="kubernetes" style="min-width:150px;"></div>'
                    + '<button class="danger" onclick="document.getElementById(\\'grant-' + grantCount + '\\').remove()" style="align-self:end">X</button>'
                    + '</div></div>';
                document.getElementById('grant-rows').insertAdjacentHTML('beforeend', html);
                updateGrantHostDropdowns();
                updateGrantDbDropdowns();
            }

            function toggleGrantTarget(sel) {
                var row = sel.closest('.grant-row');
                var dbGroup = row.querySelector('.gr-target-group');
                var jwtGroup = row.querySelector('.gr-jwt-group');
                dbGroup.style.display = sel.value==='db' ? 'flex' : 'none';
                jwtGroup.style.display = sel.value==='jwt' ? 'flex' : 'none';
            }

            function submitGrants() {
                var org = document.getElementById('grant-org').value;
                var env = document.getElementById('grant-env').value;
                var prod = document.getElementById('grant-product').value;
                if (!org || !env || !prod) { alert('Org, Environment, and Product are required'); return; }

                var rows = document.querySelectorAll('.grant-row');
                if (rows.length === 0) { alert('Add at least one grant'); return; }

                var promises = [];
                rows.forEach(function(row) {
                    var hostVal = row.querySelector('.gr-host-select').value;
                    if(!hostVal) return;
                    var parts = hostVal.split('|');
                    var appType = parts[0];
                    var hostId = parts[1];
                    var grantType = row.querySelector('.gr-type').value;
                    var body = { orgName: org, environment: env, product: prod,
                                 appType: appType, hostId: hostId, targetType: grantType };
                    if (grantType==='db') {
                        var db = row.querySelector('.gr-db-select').value;
                        if(db) body.targetResource = db;
                    } else if (grantType==='jwt') {
                        var svc = row.querySelector('.gr-jwt-svc').value.trim();
                        if(svc) body.targetResource = svc;
                    }
                    promises.push(apiCall('POST', '/api/access/grant', body));
                });

                if(promises.length===0){ alert('Select a host for at least one grant row'); return; }
                Promise.all(promises)
                    .then(function(results) { showResult('grant-result', results, false); })
                    .catch(function(e) { showResult('grant-result', e.message, true); });
            }

            // ===== Tab 4: Manage Variables =====
            function loadVars() {
                var search = document.getElementById('var-search').value.trim();
                var url = '/api/resources?kind=variable';
                if (search) url += '&search=' + encodeURIComponent(search);
                document.getElementById('var-list').innerHTML = '<p>Loading...</p>';
                fetch(url).then(function(r) { return r.json(); })
                    .then(function(data) {
                        migData.vars = data;
                        renderVarTable(data);
                    })
                    .catch(function(e) { document.getElementById('var-list').innerHTML = '<p class="badge badge-err">' + e.message + '</p>'; });
            }

            function renderVarTable(data) {
                if (!data || data.length === 0) {
                    document.getElementById('var-list').innerHTML = '<p>No variables found.</p>';
                    return;
                }
                var html = '<table><tr><th>Variable ID</th><th>Owner</th><th>Action</th></tr>';
                data.forEach(function(r) {
                    var id = r.id ? r.id.split(':').pop() : '';
                    var owner = r.owner ? r.owner.split(':').pop() : '';
                    html += '<tr><td><code>' + esc(id) + '</code></td><td>' + esc(owner) + '</td>'
                          + '<td><button onclick="promptSetValue(\\'' + escJs(id) + '\\')">Set Value</button></td></tr>';
                });
                html += '</table>';
                document.getElementById('var-list').innerHTML = html;
            }

            function promptSetValue(varId) {
                var val = prompt('Enter value for ' + varId);
                if (val === null) return;
                apiCall('POST', '/api/secrets/bulk', { secrets: [{ variableId: varId, value: val }] })
                    .then(function(d) { showResult('var-result', d, !!d.errors && d.errors.length > 0); })
                    .catch(function(e) { showResult('var-result', e.message, true); });
            }

            function createVars() {
                var branch = document.getElementById('var-branch').value.trim();
                var org = document.getElementById('var-org').value;
                var env = document.getElementById('var-env').value;
                var prod = document.getElementById('var-product').value;
                var names = document.getElementById('var-names').value.trim();
                if (!names) { alert('Variable names are required'); return; }

                var body = { variables: names.split(',').map(function(s) { return s.trim(); }).filter(Boolean) };
                if (branch) {
                    body.branch = branch;
                } else if (org && env && prod) {
                    body.orgName = org; body.environment = env; body.product = prod;
                    var rt = document.getElementById('var-restype').value;
                    if (rt) body.resourceType = rt;
                } else {
                    alert('Provide either a branch path or org/env/product'); return;
                }

                apiCall('POST', '/api/resources/variable', body)
                    .then(function(d) { showResult('var-create-result', d, !!d.error); })
                    .catch(function(e) { showResult('var-create-result', e.message, true); });
            }

            // ===== Tab 5: My Apps =====
            function loadMyApps() {
                document.getElementById('app-list').innerHTML = '<p>Loading hosts...</p>';
                fetch('/api/resources?kind=host')
                    .then(function(r) { return r.json(); })
                    .then(function(data) {
                        migData.apps = data;
                        renderApps(data);
                    })
                    .catch(function(e) { document.getElementById('app-list').innerHTML = '<p class="badge badge-err">' + e.message + '</p>'; });
            }

            function renderApps(data) {
                if (!data || data.length === 0) {
                    document.getElementById('app-list').innerHTML = '<p>No apps found. Create one using the "Create App" tab.</p>';
                    return;
                }
                var html = '<table><tr><th>Host ID</th><th>Owner</th><th>Annotations</th><th>Actions</th></tr>';
                data.forEach(function(r) {
                    var id = r.id ? r.id.split(':').pop() : '';
                    var owner = r.owner ? r.owner.split(':').pop() : '';
                    var annot = '';
                    if (r.annotations) {
                        r.annotations.forEach(function(a) {
                            annot += esc(a.name) + '=' + esc(a.value) + ' ';
                        });
                    }
                    html += '<tr><td><code>' + esc(id) + '</code></td><td>' + esc(owner) + '</td>'
                          + '<td><small>' + annot + '</small></td>'
                          + '<td><button onclick="showAppDetail(\\'' + escJs(r.id || id) + '\\')">Details</button></td></tr>';
                });
                html += '</table>';
                document.getElementById('app-list').innerHTML = html;
            }

            function showAppDetail(roleId) {
                var parts = roleId.split(':');
                var kind = parts.length > 1 ? parts[1] : 'host';
                var encodedId = parts.length > 2 ? encodeURIComponent(parts[2]) : encodeURIComponent(parts[0]);
                document.getElementById('app-detail').style.display = 'block';
                document.getElementById('app-detail').innerHTML = '<p>Loading role details...</p>';

                fetch('/api/resources/role/' + kind + ':' + encodedId)
                    .then(function(r) { return r.json(); })
                    .then(function(data) {
                        var html = '<h4>Role: ' + esc(roleId) + '</h4>';
                        if (data.members && data.members.length > 0) {
                            html += '<h5>Members:</h5><ul>';
                            data.members.forEach(function(m) { html += '<li>' + esc(m.member || m.role || JSON.stringify(m)) + '</li>'; });
                            html += '</ul>';
                        }
                        if (data.memberships && data.memberships.length > 0) {
                            html += '<h5>Member Of:</h5><ul>';
                            data.memberships.forEach(function(m) { html += '<li>' + esc(m.role || JSON.stringify(m)) + '</li>'; });
                            html += '</ul>';
                        }
                        if ((!data.members || data.members.length === 0) && (!data.memberships || data.memberships.length === 0)) {
                            html += '<p>No membership data available.</p>';
                        }
                        document.getElementById('app-detail').innerHTML = html;
                    })
                    .catch(function(e) {
                        document.getElementById('app-detail').innerHTML = '<p class="badge badge-err">Error: ' + e.message + '</p>';
                    });
            }

            function esc(s) { if (!s) return ''; return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }
            function escJs(s) { return s.replace(/\\\\/g,'\\\\\\\\').replace(/'/g,"\\\\'"); }

            // Init
            loadRefData();
            addGrantRow();
            """;

    private static final String MIGRATOR_HTML = """
            <style>
                .tab-bar { display: flex; gap: 0; margin-bottom: 0; flex-wrap: wrap; }
                .tab-btn { padding: 10px 20px; background: #e0e0e0; border: none; cursor: pointer;
                           font-weight: 600; border-radius: 8px 8px 0 0; color: #555; font-size: 0.9em; }
                .tab-btn.active { background: #fff; color: #16213e; }
                .tab-panel { display: none; background: #fff; padding: 20px; border-radius: 0 8px 8px 8px;
                             box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                #app-detail { display: none; margin-top: 16px; padding: 16px; background: #f8f9fa;
                              border-radius: 8px; border: 1px solid #dee2e6; }
                .grant-row { border-bottom: 1px solid #eee; padding-bottom: 8px; margin-bottom: 8px; }
                textarea { width: 100%%; min-height: 60px; font-family: monospace; padding: 8px;
                           border: 1px solid #ccc; border-radius: 4px; }
            </style>

            <h2>Migrator Console</h2>
            <p>Create apps, manage API resources, grant access, and manage variables.</p>

            <div class="tab-bar">
                <button class="tab-btn active" id="btn-create-app" onclick="switchTab('create-app')">Create App</button>
                <button class="tab-btn" id="btn-create-api" onclick="switchTab('create-api')">Create API Resource</button>
                <button class="tab-btn" id="btn-grants" onclick="switchTab('grants')">Grant Access</button>
                <button class="tab-btn" id="btn-manage-vars" onclick="switchTab('manage-vars')">Manage Variables</button>
                <button class="tab-btn" id="btn-my-apps" onclick="switchTab('my-apps')">My Apps</button>
            </div>

            <!-- Tab 1: Create App -->
            <div class="tab-panel" id="panel-create-app" style="display:block">
                <h3>Register Application Host</h3>
                <div class="form-row">
                    <div class="form-group"><label>Organization</label>
                        <select id="app-org"><option value="">-- Select --</option></select></div>
                    <div class="form-group"><label>Environment</label>
                        <select id="app-env"><option value="">-- Select --</option></select></div>
                    <div class="form-group"><label>Product</label>
                        <select id="app-product"><option value="">-- Select --</option></select></div>
                </div>
                <div class="form-row">
                    <div class="form-group"><label>App Type</label>
                        <select id="app-type"><option value="">-- Select --</option></select></div>
                    <div class="form-group"><label>Host ID</label>
                        <input id="app-hostid" placeholder="app1"></div>
                    <div class="form-group"><label>Namespace</label>
                        <input id="app-namespace" placeholder="apps" value="apps"></div>
                    <div class="form-group"><label>Service Account</label>
                        <input id="app-sa" placeholder="auto: hostId-sa"></div>
                </div>
                <div style="margin: 12px 0;">
                    <label><input type="checkbox" id="app-create-k8s" checked> Create K8s Secret automatically</label>
                </div>
                <div class="form-group" style="margin-bottom: 12px;">
                    <label>CONJUR_SECRETS mapping (optional)</label>
                    <textarea id="app-secrets-mapping" placeholder="db.url=nimbus/dev/products/product1/resources/dbs/db1/host-name,db.password=nimbus/dev/products/product1/resources/dbs/db1/password"></textarea>
                </div>
                <button onclick="createApp()">Register Host</button>
                <div class="alert alert-warning" style="margin-top: 12px;">
                    The API key is shown only once. Save it immediately!
                </div>
                <div id="app-result" class="result"></div>
            </div>

            <!-- Tab 2: Create API Resource -->
            <div class="tab-panel" id="panel-create-api">
                <h3>Register API Resource</h3>
                <div class="form-row">
                    <div class="form-group"><label>Organization</label>
                        <select id="api-org"><option value="">-- Select --</option></select></div>
                    <div class="form-group"><label>Environment</label>
                        <select id="api-env"><option value="">-- Select --</option></select></div>
                    <div class="form-group"><label>Product</label>
                        <select id="api-product"><option value="">-- Select --</option></select></div>
                    <div class="form-group"><label>API Name</label>
                        <input id="api-name" placeholder="stripe"></div>
                </div>
                <h4>Credentials (optional — set now or later)</h4>
                <div class="form-row">
                    <div class="form-group"><label>Client ID / Key</label><input id="api-clientid"></div>
                    <div class="form-group"><label>Client Secret</label><input id="api-clientsecret" type="password"></div>
                    <div class="form-group"><label>Webhook Secret</label><input id="api-webhook" type="password"></div>
                </div>
                <button onclick="createApiResource()">Create API Resource</button>
                <div id="api-result" class="result"></div>
            </div>

            <!-- Tab 3: Grant Access -->
            <div class="tab-panel" id="panel-grants">
                <h3>Grant Access</h3>
                <div class="form-row">
                    <div class="form-group"><label>Organization</label>
                        <select id="grant-org"><option value="">-- Select --</option></select></div>
                    <div class="form-group"><label>Environment</label>
                        <select id="grant-env"><option value="">-- Select --</option></select></div>
                    <div class="form-group"><label>Product</label>
                        <select id="grant-product" onchange="loadGrantContext()"><option value="">-- Select --</option></select></div>
                </div>
                <div id="grant-rows"></div>
                <div style="margin: 12px 0;">
                    <button onclick="addGrantRow()">+ Add Grant</button>
                    <button onclick="submitGrants()">Submit All Grants</button>
                </div>
                <div id="grant-result" class="result"></div>
            </div>

            <!-- Tab 4: Manage Variables -->
            <div class="tab-panel" id="panel-manage-vars">
                <h3>Search Variables</h3>
                <div class="form-row">
                    <div class="form-group"><label>Search</label><input id="var-search" placeholder="e.g. resources/dbs"></div>
                    <button onclick="loadVars()">Search</button>
                </div>
                <div id="var-list"></div>
                <div id="var-result" class="result"></div>

                <hr style="margin: 24px 0;">
                <h3>Create Variables</h3>
                <div class="form-row">
                    <div class="form-group"><label>Branch (full path)</label>
                        <input id="var-branch" placeholder="nimbus/dev/products/product1/resources/api"></div>
                </div>
                <p style="margin: 4px 0; color: #666; font-size: 0.9em;">— or use dropdowns —</p>
                <div class="form-row">
                    <div class="form-group"><label>Organization</label>
                        <select id="var-org"><option value="">-- Select --</option></select></div>
                    <div class="form-group"><label>Environment</label>
                        <select id="var-env"><option value="">-- Select --</option></select></div>
                    <div class="form-group"><label>Product</label>
                        <select id="var-product"><option value="">-- Select --</option></select></div>
                    <div class="form-group"><label>Resource Type</label>
                        <select id="var-restype">
                            <option value="">-- Optional --</option>
                            <option value="dbs">dbs</option>
                            <option value="kafka">kafka</option>
                            <option value="api">api</option>
                            <option value="ldap">ldap</option>
                            <option value="smtp">smtp</option>
                            <option value="oauth">oauth</option>
                            <option value="certs">certs</option>
                        </select></div>
                </div>
                <div class="form-group" style="margin: 12px 0;">
                    <label>Variable Names (comma-separated)</label>
                    <input id="var-names" placeholder="client-id, client-secret, token-url">
                </div>
                <button onclick="createVars()">Create Variables</button>
                <div id="var-create-result" class="result"></div>
            </div>

            <!-- Tab 5: My Apps -->
            <div class="tab-panel" id="panel-my-apps">
                <div id="app-list"><p>Click to load apps...</p></div>
                <div id="app-detail"></div>
            </div>
            """;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String migrator() {
        return PageTemplates.page("Migrator Console", "nav-migrator",
                MIGRATOR_HTML + "<script>" + PageTemplates.SCRIPT_UTILS + MIGRATOR_SCRIPT + "</script>");
    }
}
