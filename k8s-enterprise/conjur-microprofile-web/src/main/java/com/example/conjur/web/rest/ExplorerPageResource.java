package com.example.conjur.web.rest;

import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/explorer")
@RequestScoped
public class ExplorerPageResource {

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String explorer() {
        return PageTemplates.page("Explorer", "nav-explorer",
                EXPLORER_HTML + "<script>" + PageTemplates.SCRIPT_UTILS + EXPLORER_SCRIPT + "</script>");
    }

    private static final String EXPLORER_SCRIPT = """
            var explorerData = {};
            var refData = {};
            var RES_FIELD_MAP = {};

            function switchTab(tabName) {
                document.querySelectorAll('.tab-content').forEach(function(el) { el.classList.remove('active'); });
                document.querySelectorAll('.explorer-tabs .tab').forEach(function(el) { el.classList.remove('active'); });
                document.getElementById('tab-' + tabName).classList.add('active');
                document.querySelector('[data-tab="' + tabName + '"]').classList.add('active');

                if (tabName === 'policies' && !explorerData.policies) loadResources('policies', 'policy');
                if (tabName === 'hosts' && !explorerData.hosts) loadResources('hosts', 'host');
                if (tabName === 'variables' && !explorerData.variables) loadResources('variables', 'variable');
                if (tabName === 'groups' && !explorerData.groups) loadGroupsAndLayers();
                if (tabName === 'webservices' && !explorerData.webservices) loadResources('webservices', 'webservice');
                if (tabName === 'grants' && !explorerData.grants) loadGrants();
                if (tabName === 'manage' && !explorerData.manageLoaded) {
                    explorerData.manageLoaded = true;
                    loadRefData();
                }
                if (tabName === 'refdata' && !explorerData.refLoaded) {
                    explorerData.refLoaded = true;
                    loadRefDataAdmin();
                }
            }

            // ===== Reference Data for Dropdowns =====
            function loadRefData() {
                fetch('/api/refdata/all').then(function(r){return r.json();})
                .then(function(data){
                    refData = data;
                    buildResFieldMap(data);
                    fillSelect('mgmt-orgName', (data.organizations||[]).map(function(i){return i.name;}));
                    fillSelect('mgmt-at-org', (data.organizations||[]).map(function(i){return i.name;}));
                    fillSelect('mgmt-prod-org', (data.organizations||[]).map(function(i){return i.name;}));
                    fillSelect('mgmt-res-org', (data.organizations||[]).map(function(i){return i.name;}));
                    fillSelect('mgmt-envName', (data.environments||[]).map(function(i){return i.name;}));
                    fillSelect('mgmt-prod-env', (data.environments||[]).map(function(i){return i.name;}));
                    fillSelect('mgmt-at-env', (data.environments||[]).map(function(i){return i.name;}));
                    fillSelect('mgmt-res-env', (data.environments||[]).map(function(i){return i.name;}));
                    fillSelect('mgmt-at-product', (data.products||[]).map(function(i){return i.name;}));
                    fillSelect('mgmt-res-product', (data.products||[]).map(function(i){return i.name;}));
                    fillResTypeSelect('mgmt-res-type', data.resourcetypes||[]);
                    fillAppTypeCheckboxes(data.apptypes||[]);
                    fillResTypeCheckboxes(data.resourcetypes||[]);
                }).catch(function(e){ console.error('RefData load error:',e); });
            }

            function buildResFieldMap(data) {
                RES_FIELD_MAP = {};
                (data.resourcetypes||[]).forEach(function(rt){
                    if(rt.fields) RES_FIELD_MAP[rt.name] = rt.fields.split(',').map(function(s){return s.trim();});
                });
            }

            function fillSelect(id, items, ph) {
                var sel = document.getElementById(id);
                if(!sel) return;
                sel.innerHTML = '<option value="">'+(ph||'-- Select --')+'</option>';
                items.forEach(function(i){ sel.innerHTML += '<option value="'+esc(i)+'">'+esc(i)+'</option>'; });
            }

            function fillResTypeSelect(id, rtItems) {
                var sel = document.getElementById(id);
                if(!sel) return;
                sel.innerHTML = '<option value="">-- Select --</option>';
                rtItems.forEach(function(rt){
                    sel.innerHTML += '<option value="'+esc(rt.name)+'">'+esc(rt.name)+'</option>';
                });
            }

            function fillAppTypeCheckboxes(items) {
                var container = document.getElementById('mgmt-prod-apptypes-cb');
                if(!container) return;
                container.innerHTML = '';
                items.forEach(function(at){
                    container.innerHTML += '<label><input type="checkbox" class="mgmt-prod-apptype" value="'+esc(at.name)+'"> '+esc(at.name)+'</label>';
                });
            }

            function fillResTypeCheckboxes(items) {
                var container = document.getElementById('mgmt-prod-restypes-cb');
                if(!container) return;
                container.innerHTML = '';
                items.forEach(function(rt){
                    var checked = rt.name==='dbs' ? ' checked' : '';
                    container.innerHTML += '<label><input type="checkbox" class="mgmt-prod-restype" value="'+esc(rt.name)+'"'+checked+'> '+esc(rt.name)+'</label>';
                });
            }

            // ===== Reference Data Admin =====
            function loadRefDataAdmin() {
                fetch('/api/refdata/all').then(function(r){return r.json();})
                .then(function(data){
                    refData = data;
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
                    html += '<td><button class="danger" onclick="deleteRefItem(\\''+escJs(type)+'\\','+item.id+')" '
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
                        if(explorerData.manageLoaded) loadRefData();
                    }
                })
                .catch(function(e){ showResult('ref-add-'+type+'-result', e.message, true); });
            }

            function deleteRefItem(type, id) {
                if(!confirm('Delete this '+type.replace(/s$/,'')+' item?')) return;
                fetch('/api/refdata/'+type+'/'+id, { method:'DELETE' })
                .then(function(r){return r.json();})
                .then(function(d){
                    loadRefDataAdmin();
                    if(explorerData.manageLoaded) loadRefData();
                })
                .catch(function(e){ alert('Delete failed: '+e.message); });
            }

            // ===== Resource & Policy Tables =====
            function loadResources(tabKey, kind) {
                document.getElementById('table-' + tabKey).innerHTML = '<p>Loading...</p>';
                fetch('/api/resources?kind=' + kind)
                    .then(function(r) { return r.json(); })
                    .then(function(data) {
                        explorerData[tabKey] = data;
                        renderResourceTable(tabKey, data);
                    })
                    .catch(function(e) {
                        document.getElementById('table-' + tabKey).innerHTML =
                            '<p style="color:red;">Error: ' + e.message + '</p>';
                    });
            }

            function renderResourceTable(tabKey, data) {
                if (!Array.isArray(data) || data.length === 0) {
                    document.getElementById('table-' + tabKey).innerHTML = '<p>No resources found.</p>';
                    return;
                }
                var html = '<table><tr><th>ID</th><th>Owner</th><th>Policy</th><th>Created</th></tr>';
                data.forEach(function(r) {
                    var shortId = r.id ? r.id.split(':').pop() : '';
                    var owner = r.owner ? r.owner.split(':').pop() : '';
                    var policy = r.policy ? r.policy.split(':').pop() : '';
                    var created = r.created_at ? r.created_at.substring(0, 19) : '';
                    html += '<tr><td><code>' + esc(shortId) + '</code></td><td>' + esc(owner) +
                            '</td><td>' + esc(policy) + '</td><td>' + created + '</td></tr>';
                });
                html += '</table><p style="font-size:0.85em;color:#666;">Total: ' + data.length + '</p>';
                document.getElementById('table-' + tabKey).innerHTML = html;
            }

            function searchResources(tabKey, kind) {
                var query = document.getElementById('search-' + tabKey).value;
                if (query.length > 0 && query.length < 2) return;
                var url = '/api/resources?kind=' + kind;
                if (query) url += '&search=' + encodeURIComponent(query);
                fetch(url).then(function(r) { return r.json(); }).then(function(data) {
                    explorerData[tabKey] = data;
                    renderResourceTable(tabKey, data);
                });
            }

            function loadGroupsAndLayers() {
                document.getElementById('table-groups').innerHTML = '<p>Loading...</p>';
                Promise.all([
                    fetch('/api/resources?kind=group').then(function(r) { return r.json(); }),
                    fetch('/api/resources?kind=layer').then(function(r) { return r.json(); })
                ]).then(function(results) {
                    var groups = Array.isArray(results[0]) ? results[0] : [];
                    var layers = Array.isArray(results[1]) ? results[1] : [];
                    explorerData.groups = groups.concat(layers);
                    renderGroupsTable(groups, layers);
                }).catch(function(e) {
                    document.getElementById('table-groups').innerHTML =
                        '<p style="color:red;">Error: ' + e.message + '</p>';
                });
            }

            function renderGroupsTable(groups, layers) {
                var html = '<table><tr><th>Type</th><th>ID</th><th>Owner</th><th>Actions</th></tr>';
                groups.forEach(function(r) {
                    var shortId = r.id ? r.id.split(':').pop() : '';
                    var owner = r.owner ? r.owner.split(':').pop() : '';
                    var roleId = r.id ? r.id.substring(r.id.indexOf(':') + 1) : '';
                    html += '<tr><td><span class="badge badge-ok">Group</span></td><td><code>' + esc(shortId) +
                            '</code></td><td>' + esc(owner) +
                            '</td><td><button onclick="showRoleDetail(\\'' + escJs(roleId) +
                            '\\')" style="height:28px;padding:2px 12px;font-size:0.85em;">Members</button></td></tr>';
                });
                layers.forEach(function(r) {
                    var shortId = r.id ? r.id.split(':').pop() : '';
                    var owner = r.owner ? r.owner.split(':').pop() : '';
                    var roleId = r.id ? r.id.substring(r.id.indexOf(':') + 1) : '';
                    html += '<tr><td><span class="badge badge-warn">Layer</span></td><td><code>' + esc(shortId) +
                            '</code></td><td>' + esc(owner) +
                            '</td><td><button onclick="showRoleDetail(\\'' + escJs(roleId) +
                            '\\')" style="height:28px;padding:2px 12px;font-size:0.85em;">Members</button></td></tr>';
                });
                html += '</table><p style="font-size:0.85em;color:#666;">Groups: ' + groups.length + ', Layers: ' + layers.length + '</p>';
                document.getElementById('table-groups').innerHTML = html;
            }

            function showRoleDetail(roleId) {
                var el = document.getElementById('role-detail');
                el.style.display = 'block';
                document.getElementById('role-detail-name').textContent = roleId;
                document.getElementById('role-detail-content').innerHTML = '<p>Loading...</p>';

                fetch('/api/resources/role/' + encodeURIComponent(roleId))
                    .then(function(r) { return r.json(); })
                    .then(function(data) {
                        var html = '';
                        if (data.members && data.members.length > 0) {
                            html += '<h4>Members (' + data.members.length + ')</h4><ul>';
                            data.members.forEach(function(m) {
                                var memberId = m.member || m.role || (typeof m === 'string' ? m : JSON.stringify(m));
                                html += '<li><code>' + esc(memberId) + '</code>';
                                if (m.admin) html += ' <span class="badge badge-warn">admin</span>';
                                html += '</li>';
                            });
                            html += '</ul>';
                        }
                        if (data.memberships && data.memberships.length > 0) {
                            html += '<h4>Member Of (' + data.memberships.length + ')</h4><ul>';
                            data.memberships.forEach(function(m) {
                                var role = m.role || (typeof m === 'string' ? m : JSON.stringify(m));
                                html += '<li><code>' + esc(role) + '</code></li>';
                            });
                            html += '</ul>';
                        }
                        if (!html) html = '<p>No membership data available.</p>';
                        document.getElementById('role-detail-content').innerHTML = html;
                    })
                    .catch(function(e) {
                        document.getElementById('role-detail-content').innerHTML =
                            '<p style="color:red;">Error: ' + e.message + '</p>';
                    });
            }

            function loadGrants() {
                document.getElementById('table-grants').innerHTML = '<p>Loading...</p>';
                fetch('/api/resources')
                    .then(function(r) { return r.json(); })
                    .then(function(data) {
                        if (!Array.isArray(data)) data = [];
                        explorerData.grants = data;
                        var html = '<table><tr><th>Resource</th><th>Role</th><th>Privilege</th></tr>';
                        var count = 0;
                        data.forEach(function(r) {
                            if (r.permissions && r.permissions.length > 0) {
                                r.permissions.forEach(function(p) {
                                    var shortId = r.id ? r.id.split(':').pop() : '';
                                    var role = p.role ? p.role.split(':').pop() : '';
                                    html += '<tr><td><code>' + esc(shortId) + '</code></td><td>' +
                                            esc(role) + '</td><td><span class="badge badge-ok">' +
                                            esc(p.privilege) + '</span></td></tr>';
                                    count++;
                                });
                            }
                        });
                        if (count === 0) html += '<tr><td colspan="3">No grants found.</td></tr>';
                        html += '</table><p style="font-size:0.85em;color:#666;">Total grants: ' + count + '</p>';
                        document.getElementById('table-grants').innerHTML = html;
                    })
                    .catch(function(e) {
                        document.getElementById('table-grants').innerHTML =
                            '<p style="color:red;">Error: ' + e.message + '</p>';
                    });
            }

            // ===== Manage Tab Functions =====

            function addEnvironment() {
                var org = document.getElementById('mgmt-orgName').value;
                var env = document.getElementById('mgmt-envName').value;
                if (!org || !env) { showResult('addEnvResult', 'Organization and environment are required', true); return; }
                apiCall('POST', '/api/setup/environments', {orgName: org, environments: [env]})
                    .then(function(d) {
                        if (d.error) showResult('addEnvResult', d.error, true);
                        else showResult('addEnvResult', d);
                    })
                    .catch(function(e) { showResult('addEnvResult', e.message, true); });
            }

            function addAppType() {
                var org = document.getElementById('mgmt-at-org').value;
                var env = document.getElementById('mgmt-at-env').value;
                var product = document.getElementById('mgmt-at-product').value;
                var appType = document.getElementById('mgmt-at-apptype').value.trim();
                if (!org || !env || !product || !appType) {
                    showResult('addAppTypeResult', 'All fields are required', true); return;
                }
                apiCall('POST', '/api/setup/apptype', {
                    orgName: org, environment: env, product: product, appType: appType
                }).then(function(d) {
                    if (d.error) showResult('addAppTypeResult', d.error, true);
                    else showResult('addAppTypeResult', d);
                }).catch(function(e) { showResult('addAppTypeResult', e.message, true); });
            }

            function addProduct() {
                var org = document.getElementById('mgmt-prod-org').value;
                var env = document.getElementById('mgmt-prod-env').value;
                var prodName = document.getElementById('mgmt-prod-name').value.trim();
                if (!org || !env || !prodName) {
                    showResult('addProductResult', 'Organization, environment, and product name are required', true); return;
                }
                var appTypes = [];
                document.querySelectorAll('.mgmt-prod-apptype:checked').forEach(function(cb){ appTypes.push(cb.value); });
                var resTypes = [];
                document.querySelectorAll('.mgmt-prod-restype:checked').forEach(function(cb){ resTypes.push(cb.value); });
                if (appTypes.length===0) { showResult('addProductResult', 'At least one app type is required', true); return; }
                if (resTypes.length===0) { showResult('addProductResult', 'At least one resource type is required', true); return; }

                apiCall('POST', '/api/setup/product', {
                    orgName: org, environment: env, productName: prodName,
                    appTypes: appTypes, resourceTypes: resTypes
                }).then(function(d) {
                    if (d.error) showResult('addProductResult', d.error, true);
                    else showResult('addProductResult', d);
                }).catch(function(e) { showResult('addProductResult', e.message, true); });
            }

            function onResTypeChange() {
                var resType = document.getElementById('mgmt-res-type').value;
                var container = document.getElementById('mgmt-res-fields');
                if (!resType || !RES_FIELD_MAP[resType]) {
                    container.innerHTML = '';
                    return;
                }
                var fields = RES_FIELD_MAP[resType];
                var html = '<h4>Initial Values (optional)</h4><div class="form-row" style="flex-wrap:wrap;">';
                fields.forEach(function(f){
                    var inputType = (f.indexOf('password')>=0 || f.indexOf('secret')>=0) ? 'password' : 'text';
                    html += '<div class="form-group"><label>'+esc(f)+'</label>'
                        +'<input id="mgmt-res-val-'+f+'" type="'+inputType+'" placeholder="'+esc(f)+'"></div>';
                });
                html += '</div>';
                container.innerHTML = html;
            }

            function addResource() {
                var org = document.getElementById('mgmt-res-org').value;
                var env = document.getElementById('mgmt-res-env').value;
                var prod = document.getElementById('mgmt-res-product').value;
                var resType = document.getElementById('mgmt-res-type').value;
                var resName = document.getElementById('mgmt-res-name').value.trim();
                if (!org||!env||!prod||!resType||!resName) {
                    showResult('addResourceResult', 'All fields are required', true); return;
                }
                var body = { orgName:org, environment:env, product:prod, resourceType:resType, resourceName:resName };
                var secrets = {};
                if (RES_FIELD_MAP[resType]) {
                    RES_FIELD_MAP[resType].forEach(function(f){
                        var el = document.getElementById('mgmt-res-val-'+f);
                        if (el && el.value.trim()) secrets[f] = el.value.trim();
                    });
                }
                if (Object.keys(secrets).length>0) body.initialSecrets = secrets;

                var endpoint;
                if (resType==='dbs') endpoint = '/api/resources/database';
                else if (resType==='kafka') endpoint = '/api/resources/kafka';
                else endpoint = '/api/resources/infrastructure';

                apiCall('POST', endpoint, body)
                .then(function(d){
                    if (d.error) showResult('addResourceResult', d.error, true);
                    else showResult('addResourceResult', d);
                })
                .catch(function(e){ showResult('addResourceResult', e.message, true); });
            }

            function esc(s) {
                if (!s) return '';
                return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
            }

            function escJs(s) {
                return s.replace(/\\\\/g,'\\\\\\\\').replace(/'/g,"\\\\'");
            }

            // Initialize first tab
            loadResources('policies', 'policy');
            """;

    private static final String EXPLORER_HTML = """
            <style>
                .explorer-tabs { display: flex; gap: 4px; margin-bottom: 16px; flex-wrap: wrap; }
                .explorer-tabs .tab {
                    padding: 8px 16px; border-radius: 6px 6px 0 0; font-weight: 600;
                    background: #e0e0e0; color: #666; cursor: pointer; border: none; font-size: 0.9em;
                }
                .explorer-tabs .tab.active { background: #0f3460; color: #fff; }
                .tab-content { display: none; }
                .tab-content.active { display: block; }
                .search-bar { margin-bottom: 12px; }
                .search-bar input { padding: 8px 12px; border: 1px solid #ccc; border-radius: 4px; width: 300px; }
                .role-detail { margin-top: 12px; padding: 16px; background: #f8f9fa; border-radius: 4px;
                               border: 1px solid #dee2e6; display: none; }
                .role-detail ul { margin: 4px 0; padding-left: 24px; }
                .role-detail li { margin: 2px 0; }
                .cb-group { display: flex; gap: 16px; flex-wrap: wrap; margin: 8px 0; }
                .cb-group label { font-weight: normal; display: flex; align-items: center; gap: 4px; }
                .ref-section { margin-bottom: 16px; padding: 12px; background: #f8f9fa; border-radius: 6px; border: 1px solid #dee2e6; }
                .ref-section h4 { margin: 0 0 8px 0; color: #0f3460; }
                .ref-add-row { display: flex; gap: 8px; align-items: end; margin-top: 8px; flex-wrap: wrap; }
                .ref-add-row input { padding: 6px 10px; border: 1px solid #ccc; border-radius: 4px; }
            </style>

            <div class="explorer-tabs">
                <button class="tab active" data-tab="policies" onclick="switchTab('policies')">Policies</button>
                <button class="tab" data-tab="hosts" onclick="switchTab('hosts')">Hosts</button>
                <button class="tab" data-tab="variables" onclick="switchTab('variables')">Variables</button>
                <button class="tab" data-tab="groups" onclick="switchTab('groups')">Groups & Layers</button>
                <button class="tab" data-tab="webservices" onclick="switchTab('webservices')">Webservices</button>
                <button class="tab" data-tab="grants" onclick="switchTab('grants')">Grants</button>
                <button class="tab" data-tab="manage" onclick="switchTab('manage')">Manage</button>
                <button class="tab" data-tab="refdata" onclick="switchTab('refdata')">Reference Data</button>
            </div>

            <!-- Policies tab -->
            <div class="tab-content active" id="tab-policies">
                <div class="card">
                    <h2>Policies</h2>
                    <div class="search-bar">
                        <input type="text" id="search-policies" placeholder="Search policies..."
                               onkeyup="searchResources('policies','policy')" />
                    </div>
                    <div id="table-policies"><p>Loading...</p></div>
                </div>
            </div>

            <!-- Hosts tab -->
            <div class="tab-content" id="tab-hosts">
                <div class="card">
                    <h2>Hosts</h2>
                    <div class="search-bar">
                        <input type="text" id="search-hosts" placeholder="Search hosts..."
                               onkeyup="searchResources('hosts','host')" />
                    </div>
                    <div id="table-hosts"><p>Click tab to load...</p></div>
                </div>
            </div>

            <!-- Variables tab -->
            <div class="tab-content" id="tab-variables">
                <div class="card">
                    <h2>Variables</h2>
                    <div class="search-bar">
                        <input type="text" id="search-variables" placeholder="Search variables..."
                               onkeyup="searchResources('variables','variable')" />
                    </div>
                    <div id="table-variables"><p>Click tab to load...</p></div>
                </div>
            </div>

            <!-- Groups & Layers tab -->
            <div class="tab-content" id="tab-groups">
                <div class="card">
                    <h2>Groups & Layers</h2>
                    <p>Click <strong>Members</strong> to view role membership details.</p>
                    <div id="table-groups"><p>Click tab to load...</p></div>
                    <div class="role-detail" id="role-detail">
                        <h3>Role: <span id="role-detail-name" style="font-family:monospace;"></span></h3>
                        <div id="role-detail-content"></div>
                    </div>
                </div>
            </div>

            <!-- Webservices tab -->
            <div class="tab-content" id="tab-webservices">
                <div class="card">
                    <h2>Webservices</h2>
                    <div class="search-bar">
                        <input type="text" id="search-webservices" placeholder="Search webservices..."
                               onkeyup="searchResources('webservices','webservice')" />
                    </div>
                    <div id="table-webservices"><p>Click tab to load...</p></div>
                </div>
            </div>

            <!-- Grants tab -->
            <div class="tab-content" id="tab-grants">
                <div class="card">
                    <h2>Access Grants & Permissions</h2>
                    <p>Permissions extracted from Conjur resource definitions.</p>
                    <div id="table-grants"><p>Click tab to load...</p></div>
                </div>
            </div>

            <!-- Manage tab -->
            <div class="tab-content" id="tab-manage">
                <div class="card">
                    <h2>Add Environment</h2>
                    <p>Add a new environment branch to an existing organization in Conjur.</p>
                    <div class="form-row">
                        <div class="form-group">
                            <label>Organization</label>
                            <select id="mgmt-orgName"><option value="">-- Select --</option></select>
                        </div>
                        <div class="form-group">
                            <label>Environment</label>
                            <select id="mgmt-envName"><option value="">-- Select --</option></select>
                        </div>
                        <button onclick="addEnvironment()">Add Environment</button>
                    </div>
                    <div id="addEnvResult" class="result"></div>
                </div>

                <div class="card">
                    <h2>Add Product</h2>
                    <p>Create a product branch with app types and resource types under an environment.</p>
                    <div class="form-row">
                        <div class="form-group">
                            <label>Organization</label>
                            <select id="mgmt-prod-org"><option value="">-- Select --</option></select>
                        </div>
                        <div class="form-group">
                            <label>Environment</label>
                            <select id="mgmt-prod-env"><option value="">-- Select --</option></select>
                        </div>
                        <div class="form-group">
                            <label>Product Name</label>
                            <input type="text" id="mgmt-prod-name" placeholder="e.g. payments" />
                        </div>
                    </div>
                    <div>
                        <label style="font-weight:600;font-size:0.9em;">App Types</label>
                        <div class="cb-group" id="mgmt-prod-apptypes-cb">
                            <p style="color:#666;font-size:0.85em;">Loading from reference data...</p>
                        </div>
                    </div>
                    <div style="margin-top:8px;">
                        <label style="font-weight:600;font-size:0.9em;">Resource Types</label>
                        <div class="cb-group" id="mgmt-prod-restypes-cb">
                            <p style="color:#666;font-size:0.85em;">Loading from reference data...</p>
                        </div>
                    </div>
                    <button onclick="addProduct()">Create Product</button>
                    <div id="addProductResult" class="result"></div>
                </div>

                <div class="card">
                    <h2>Add Resource</h2>
                    <p>Add a named resource (DB, Kafka, API, etc.) with auto-created variables and readers group.</p>
                    <div class="form-row">
                        <div class="form-group">
                            <label>Organization</label>
                            <select id="mgmt-res-org"><option value="">-- Select --</option></select>
                        </div>
                        <div class="form-group">
                            <label>Environment</label>
                            <select id="mgmt-res-env"><option value="">-- Select --</option></select>
                        </div>
                        <div class="form-group">
                            <label>Product</label>
                            <select id="mgmt-res-product"><option value="">-- Select --</option></select>
                        </div>
                    </div>
                    <div class="form-row">
                        <div class="form-group">
                            <label>Resource Type</label>
                            <select id="mgmt-res-type" onchange="onResTypeChange()">
                                <option value="">-- Select --</option>
                            </select>
                        </div>
                        <div class="form-group">
                            <label>Resource Name</label>
                            <input type="text" id="mgmt-res-name" placeholder="e.g. orders-db" />
                        </div>
                    </div>
                    <div id="mgmt-res-fields"></div>
                    <button onclick="addResource()">Create Resource</button>
                    <div id="addResourceResult" class="result"></div>
                </div>

                <div class="card">
                    <h2>Add App Type</h2>
                    <p>Add a new application type branch to an existing product in Conjur.</p>
                    <div class="form-row">
                        <div class="form-group">
                            <label>Organization</label>
                            <select id="mgmt-at-org"><option value="">-- Select --</option></select>
                        </div>
                        <div class="form-group">
                            <label>Environment</label>
                            <select id="mgmt-at-env"><option value="">-- Select --</option></select>
                        </div>
                        <div class="form-group">
                            <label>Product</label>
                            <select id="mgmt-at-product"><option value="">-- Select --</option></select>
                        </div>
                        <div class="form-group">
                            <label>App Type</label>
                            <input type="text" id="mgmt-at-apptype" placeholder="e.g. springboot" />
                        </div>
                        <button onclick="addAppType()">Add App Type</button>
                    </div>
                    <div id="addAppTypeResult" class="result"></div>
                </div>
            </div>

            <!-- Reference Data tab -->
            <div class="tab-content" id="tab-refdata">
                <div class="card">
                    <h2>Reference Data Management</h2>
                    <p>Manage independent catalogs used across all wizards and consoles. Changes here are reflected in all dropdown menus.</p>
                    <div id="refdata-content">

                        <div class="ref-section">
                            <h4>Organizations</h4>
                            <div id="ref-organizations"><p>Loading...</p></div>
                            <div class="ref-add-row">
                                <input id="ref-add-organizations-name" placeholder="Name" style="min-width:180px;" />
                                <input id="ref-add-organizations-desc" placeholder="Description (optional)" style="min-width:260px;" />
                                <button onclick="addRefItem('organizations')">Add</button>
                            </div>
                            <div id="ref-add-organizations-result" class="result"></div>
                        </div>

                        <div class="ref-section">
                            <h4>Environments</h4>
                            <div id="ref-environments"><p>Loading...</p></div>
                            <div class="ref-add-row">
                                <input id="ref-add-environments-name" placeholder="Name" style="min-width:180px;" />
                                <input id="ref-add-environments-desc" placeholder="Description (optional)" style="min-width:260px;" />
                                <button onclick="addRefItem('environments')">Add</button>
                            </div>
                            <div id="ref-add-environments-result" class="result"></div>
                        </div>

                        <div class="ref-section">
                            <h4>Products</h4>
                            <div id="ref-products"><p>Loading...</p></div>
                            <div class="ref-add-row">
                                <input id="ref-add-products-name" placeholder="Name" style="min-width:180px;" />
                                <input id="ref-add-products-desc" placeholder="Description (optional)" style="min-width:260px;" />
                                <button onclick="addRefItem('products')">Add</button>
                            </div>
                            <div id="ref-add-products-result" class="result"></div>
                        </div>

                        <div class="ref-section">
                            <h4>App Types</h4>
                            <div id="ref-apptypes"><p>Loading...</p></div>
                            <div class="ref-add-row">
                                <input id="ref-add-apptypes-name" placeholder="Name" style="min-width:180px;" />
                                <input id="ref-add-apptypes-desc" placeholder="Description (optional)" style="min-width:260px;" />
                                <button onclick="addRefItem('apptypes')">Add</button>
                            </div>
                            <div id="ref-add-apptypes-result" class="result"></div>
                        </div>

                        <div class="ref-section">
                            <h4>Resource Types</h4>
                            <div id="ref-resourcetypes"><p>Loading...</p></div>
                            <div class="ref-add-row">
                                <input id="ref-add-resourcetypes-name" placeholder="Name" style="min-width:140px;" />
                                <input id="ref-add-resourcetypes-desc" placeholder="Description" style="min-width:200px;" />
                                <input id="ref-add-resourcetypes-fields" placeholder="Fields (comma-separated)" style="min-width:300px;" />
                                <button onclick="addRefItem('resourcetypes')">Add</button>
                            </div>
                            <div id="ref-add-resourcetypes-result" class="result"></div>
                        </div>

                    </div>
                </div>
            </div>
            """;
}
