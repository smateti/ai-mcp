package com.example.conjur.web.rest;

import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/wizard")
@RequestScoped
public class WizardPageResource {

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String wizard() {
        return PageTemplates.page("Setup Wizard", "nav-wizard", WIZARD_HTML + "<script>" + WIZARD_SCRIPT + "</script>");
    }

    private static final String WIZARD_HTML = """
            <style>
                .wizard-progress { display: flex; gap: 4px; margin-bottom: 20px; flex-wrap: wrap; }
                .wizard-progress .step-dot {
                    padding: 6px 14px; border-radius: 16px; font-size: 0.82em; font-weight: 600;
                    background: #e0e0e0; color: #666; cursor: pointer; white-space: nowrap;
                    transition: all 0.2s;
                }
                .wizard-progress .step-dot.active { background: #0f3460; color: #fff; }
                .wizard-progress .step-dot.done { background: #28a745; color: #fff; }
                .wizard-progress .step-dot.error { background: #dc3545; color: #fff; }
                .step-panel { display: none; }
                .step-panel.active { display: block; }
                .step-nav { display: flex; gap: 12px; margin-top: 16px; }
                .step-nav button.secondary { background: #6c757d; }
                .step-result { margin-top: 12px; padding: 12px; border-radius: 4px;
                               font-family: monospace; white-space: pre-wrap; font-size: 0.88em;
                               display: none; max-height: 300px; overflow-y: auto; }
                .step-result.ok { display: block; background: #d4edda; color: #155724; }
                .step-result.err { display: block; background: #f8d7da; color: #721c24; }
                .step-result.info { display: block; background: #d1ecf1; color: #0c5460; }
                .cb-group { display: flex; gap: 16px; flex-wrap: wrap; margin: 8px 0; }
                .cb-group label { display: flex; align-items: center; gap: 6px; font-weight: 500; }
                .secret-row { display: flex; gap: 8px; align-items: center; margin: 4px 0; }
                .secret-row input, .secret-row select { padding: 6px 10px; border: 1px solid #ccc; border-radius: 4px; }
                .secret-row input.var-id { width: 320px; }
                .secret-row input.var-val { width: 240px; }
                .ctx-bar { background: #e9ecef; padding: 8px 14px; border-radius: 6px; margin-bottom: 12px;
                           font-size: 0.9em; color: #333; }
                .ctx-bar strong { color: #0f3460; }
                .exists-badge { display: inline-block; background: #d4edda; color: #155724; padding: 2px 8px;
                                border-radius: 4px; font-size: 0.75em; margin-left: 4px; font-weight: 600; }
                .existing-section { margin: 12px 0; padding: 12px; background: #f0f7ff; border: 1px solid #b8daff;
                                    border-radius: 6px; }
                .existing-section h4 { margin: 0 0 8px 0; color: #004085; font-size: 0.95em; }
                .org-pill { display: inline-block; padding: 6px 16px; margin: 4px; border-radius: 20px;
                            background: #28a745; color: #fff; font-weight: 600; cursor: pointer; font-size: 0.9em; }
                .org-pill:hover { background: #218838; }
            </style>

            <div class="wizard-progress" id="wizProgress"></div>

            <!-- Step 0: Connection Check -->
            <div class="card step-panel" id="step-0">
                <h2>Step 1: Connection Check</h2>
                <p>Verify connectivity to the Conjur REST API backend. Also discovers existing resources.</p>
                <button onclick="executeStep(0)">Check Connection</button>
                <div class="step-result" id="res-0"></div>
            </div>

            <!-- Step 1: Root Policy -->
            <div class="card step-panel" id="step-1">
                <h2>Step 2: Root Policy</h2>
                <p>Creates the top-level root policy. Select an existing organization or create a new one.</p>
                <div id="w-existingOrgs" style="display:none;">
                    <div class="existing-section">
                        <h4>Existing Organizations in Conjur</h4>
                        <div id="w-orgBadges"></div>
                        <p style="font-size:0.85em;color:#666;margin-top:8px;">Click an organization to use it, or create a new one below.</p>
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label>Organization Name</label>
                        <input type="text" id="w-orgName" value="nimbus" />
                    </div>
                </div>
                <button onclick="executeStep(1)">Create Root Policy</button>
                <div class="step-result" id="res-1"></div>
            </div>

            <!-- Step 2: JWT Authenticator -->
            <div class="card step-panel" id="step-2">
                <h2>Step 3: JWT Authenticator</h2>
                <p>Creates the <code>authn-jwt</code> authenticator with JWKS configuration.</p>
                <div class="form-row">
                    <div class="form-group">
                        <label>Service ID</label>
                        <select id="w-jwtServiceId">
                            <option value="kubernetes">kubernetes</option>
                            <option value="openshift">openshift</option>
                        </select>
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label>JWKS URI</label>
                        <input type="text" id="w-jwksUri" value="https://kubernetes.default.svc/openid/v1/jwks" style="min-width:400px;" />
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label>Token App Property</label>
                        <input type="text" id="w-tokenAppProp" value="sub" />
                    </div>
                    <div class="form-group">
                        <label>Identity Path</label>
                        <input type="text" id="w-identityPath" value="apps" placeholder="Conjur policy path prefix for host identity" />
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label>Issuer</label>
                        <input type="text" id="w-issuer" value="https://kubernetes.default.svc.cluster.local" />
                    </div>
                    <div class="form-group">
                        <label>Audience</label>
                        <input type="text" id="w-audience" value="https://kubernetes.default.svc.cluster.local" />
                    </div>
                </div>
                <button onclick="executeStep(2)">Setup JWT Authenticator</button>
                <div class="step-result" id="res-2"></div>
            </div>

            <!-- Step 3: Environments -->
            <div class="card step-panel" id="step-3">
                <h2>Step 4: Environments</h2>
                <div class="ctx-bar" id="ctx-3"></div>
                <div class="form-group">
                    <label>Select Environments</label>
                    <div class="cb-group" id="w-env-group">
                        <p style="color:#666;font-size:0.85em;">Loading from reference data...</p>
                    </div>
                </div>
                <button onclick="executeStep(3)">Create Environments</button>
                <div class="step-result" id="res-3"></div>
            </div>

            <!-- Step 4: Product Setup -->
            <div class="card step-panel" id="step-4">
                <h2>Step 5: Product Setup</h2>
                <div class="ctx-bar" id="ctx-4"></div>
                <div class="form-row">
                    <div class="form-group">
                        <label>Environment</label>
                        <select id="w-prodEnv" onchange="onWizProdEnvChange()"></select>
                    </div>
                </div>
                <div id="w-existingProducts" style="display:none;">
                    <div class="existing-section">
                        <h4>Existing Products</h4>
                        <div id="w-existingProductsList"></div>
                    </div>
                </div>
                <h3 style="margin-top:12px;">Create New Product</h3>
                <div class="form-row">
                    <div class="form-group">
                        <label>Product Name</label>
                        <input type="text" id="w-productName" placeholder="e.g. product1" />
                    </div>
                </div>
                <div class="form-group">
                    <label>App Types</label>
                    <div class="cb-group" id="w-appType-group">
                        <p style="color:#666;font-size:0.85em;">Loading from reference data...</p>
                    </div>
                </div>
                <div class="form-group" style="margin-top:8px;">
                    <label>Resource Types</label>
                    <div class="cb-group" id="w-resType-group">
                        <p style="color:#666;font-size:0.85em;">Loading from reference data...</p>
                    </div>
                </div>
                <button onclick="executeStep(4)">Create Product</button>
                <div class="step-result" id="res-4"></div>
            </div>

            <!-- Step 5: Resources -->
            <div class="card step-panel" id="step-5">
                <h2>Step 6: Register Resources</h2>
                <div class="ctx-bar" id="ctx-5"></div>
                <div class="form-row">
                    <div class="form-group">
                        <label>Environment</label>
                        <select id="w-resEnv" onchange="onWizResEnvChange()"></select>
                    </div>
                    <div class="form-group">
                        <label>Product</label>
                        <select id="w-resProduct" onchange="onWizResProductChange()"></select>
                    </div>
                </div>
                <div id="w-existingResources" style="display:none;">
                    <div class="existing-section">
                        <h4>Existing Resources</h4>
                        <div id="w-existingResourcesList"></div>
                    </div>
                </div>

                <div id="w-dbSection">
                    <h3 style="margin-top:16px;">Add New Databases</h3>
                    <div id="w-dbList"></div>
                    <button onclick="addDbRow()" style="background:#6c757d;height:30px;font-size:0.85em;">+ Add Database</button>
                </div>

                <div id="w-kafkaSection">
                    <h3 style="margin-top:16px;">Add New Kafka Clusters</h3>
                    <div id="w-kafkaList"></div>
                    <button onclick="addKafkaRow()" style="background:#6c757d;height:30px;font-size:0.85em;">+ Add Kafka</button>
                </div>

                <div id="w-infraSection">
                    <h3 style="margin-top:16px;">Add New Infrastructure</h3>
                    <div id="w-infraList"></div>
                    <button onclick="addInfraRow()" style="background:#6c757d;height:30px;font-size:0.85em;">+ Add Infrastructure</button>
                </div>

                <div style="margin-top:16px;">
                    <button onclick="executeStep(5)">Register All Resources</button>
                </div>
                <div class="step-result" id="res-5"></div>
            </div>

            <!-- Step 6: App Hosts -->
            <div class="card step-panel" id="step-6">
                <h2>Step 7: Register Application Hosts</h2>
                <div class="ctx-bar" id="ctx-6"></div>
                <div class="form-row">
                    <div class="form-group">
                        <label>Environment</label>
                        <select id="w-hostEnv" onchange="onWizHostEnvChange()"></select>
                    </div>
                    <div class="form-group">
                        <label>Product</label>
                        <select id="w-hostProduct" onchange="onWizHostProductChange()"></select>
                    </div>
                </div>
                <div id="w-existingHosts" style="display:none;">
                    <div class="existing-section">
                        <h4>Existing Hosts</h4>
                        <div id="w-existingHostsList"></div>
                    </div>
                </div>
                <h3 style="margin-top:12px;">Add New Hosts</h3>
                <div id="w-hostList"></div>
                <button onclick="addHostRow()" style="background:#6c757d;height:30px;font-size:0.85em;">+ Add Host</button>
                <div style="margin-top:16px;">
                    <button onclick="executeStep(6)">Register All Hosts</button>
                </div>
                <div class="step-result" id="res-6"></div>
            </div>

            <!-- Step 7: Access & Secrets -->
            <div class="card step-panel" id="step-7">
                <h2>Step 8: Access Grants & Secrets</h2>
                <div class="ctx-bar" id="ctx-7"></div>
                <div class="form-row">
                    <div class="form-group">
                        <label>Environment</label>
                        <select id="w-grantEnv" onchange="onWizGrantEnvChange()"></select>
                    </div>
                    <div class="form-group">
                        <label>Product</label>
                        <select id="w-grantProduct" onchange="onWizGrantProductChange()"></select>
                    </div>
                    <div class="form-group">
                        <label>App Type</label>
                        <select id="w-grantAppType" onchange="onWizGrantAppTypeChange()"></select>
                    </div>
                </div>
                <div id="w-grantExistingHosts" style="display:none;">
                    <div class="existing-section">
                        <h4>Hosts (filtered by App Type)</h4>
                        <div id="w-grantHostsList"></div>
                    </div>
                </div>
                <div id="w-grantExistingResources" style="display:none;">
                    <div class="existing-section">
                        <h4>Resources in Product</h4>
                        <div id="w-grantResourcesList"></div>
                    </div>
                </div>

                <h3 style="margin-top:12px;">Access Grants</h3>
                <div id="w-grantList"></div>
                <button onclick="addGrantRow()" style="background:#6c757d;height:30px;font-size:0.85em;">+ Add Grant</button>

                <h3 style="margin-top:16px;">Initial Secrets</h3>
                <div id="w-secretList"></div>
                <button onclick="addSecretRow()" style="background:#6c757d;height:30px;font-size:0.85em;">+ Add Secret</button>

                <div style="margin-top:16px;">
                    <button onclick="executeStep(7)">Apply Grants & Set Secrets</button>
                </div>
                <div class="step-result" id="res-7"></div>
            </div>

            <!-- Step 8: Summary -->
            <div class="card step-panel" id="step-8">
                <h2>Step 9: Summary & Verification</h2>
                <p>Verify all created resources in Conjur.</p>
                <button onclick="executeStep(8)">Verify Setup</button>
                <div class="step-result" id="res-8"></div>
                <div id="summaryStats" style="margin-top:16px;"></div>
            </div>

            <div class="step-nav" id="stepNav">
                <button class="secondary" onclick="prevStep()" id="btnPrev">Previous</button>
                <button onclick="nextStep()" id="btnNext">Next Step</button>
            </div>
            """;

    private static final String WIZARD_SCRIPT = """
            var currentStep = 0;
            var totalSteps = 9;
            var stepState = {};
            var stepTitles = [
                'Connection', 'Root Policy', 'JWT Auth', 'Environments',
                'Product', 'Resources', 'Hosts', 'Access & Secrets', 'Summary'
            ];
            var wizRefData = {};

            // === Wizard Context (persists across steps) ===
            var wizCtx = {
                orgName: 'nimbus',
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
                    var m = p.match(/^([^\\/]+)\\/([^\\/]+)\\/products/);
                    if(m){
                        if(!wizCtx.existing.envs[m[1]]) wizCtx.existing.envs[m[1]] = [];
                        if(wizCtx.existing.envs[m[1]].indexOf(m[2])===-1) wizCtx.existing.envs[m[1]].push(m[2]);
                    }
                });

                paths.forEach(function(p){
                    var m = p.match(/^([^\\/]+)\\/([^\\/]+)\\/products\\/([^\\/]+)$/);
                    if(m){
                        var k = m[1]+'/'+m[2];
                        if(!wizCtx.existing.prods[k]) wizCtx.existing.prods[k] = [];
                        if(wizCtx.existing.prods[k].indexOf(m[3])===-1) wizCtx.existing.prods[k].push(m[3]);
                    }
                });

                paths.forEach(function(p){
                    var m = p.match(/^([^\\/]+)\\/([^\\/]+)\\/products\\/([^\\/]+)\\/apps\\/([^\\/]+)$/);
                    if(m){
                        var k = m[1]+'/'+m[2]+'/'+m[3];
                        if(!wizCtx.existing.appTypes[k]) wizCtx.existing.appTypes[k] = [];
                        if(wizCtx.existing.appTypes[k].indexOf(m[4])===-1) wizCtx.existing.appTypes[k].push(m[4]);
                    }
                });

                paths.forEach(function(p){
                    var m = p.match(/^([^\\/]+)\\/([^\\/]+)\\/products\\/([^\\/]+)\\/resources\\/([^\\/]+)$/);
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
                    var m = path.match(/^([^\\/]+)\\/([^\\/]+)\\/products\\/([^\\/]+)\\/apps\\/([^\\/]+)\\/(.+)/);
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
                    var m = path.match(/^([^\\/]+)\\/([^\\/]+)\\/products\\/([^\\/]+)\\/resources\\/([^\\/]+)\\/([^\\/]+)\\//);
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
                // Step 1: existing orgs
                if(wizCtx.existing.orgs.length > 0){
                    document.getElementById('w-existingOrgs').style.display = 'block';
                    var html = '';
                    wizCtx.existing.orgs.forEach(function(org){
                        html += '<span class="org-pill" onclick="selectExistingOrg(\\''+esc(org)+'\\')">'+esc(org)+' \\u2713</span>';
                    });
                    document.getElementById('w-orgBadges').innerHTML = html;
                    document.getElementById('w-orgName').value = wizCtx.existing.orgs[0];
                    wizCtx.orgName = wizCtx.existing.orgs[0];
                }
            }

            function selectExistingOrg(orgName) {
                wizCtx.orgName = orgName;
                document.getElementById('w-orgName').value = orgName;
                stepState[1] = 'done';
                stepResult(1, 'Using existing organization: ' + orgName, 'ok');
                showStep(currentStep);
            }

            function selectExistingProduct(env, product) {
                var pk = wizCtx.orgName + '/' + env + '/' + product;
                wizCtx.productEnv = env;
                wizCtx.productName = product;
                wizCtx.appTypes = wizCtx.existing.appTypes[pk] || [];
                wizCtx.resourceTypes = wizCtx.existing.resTypes[pk] || [];
                stepState[4] = 'done';
                stepResult(4, 'Using existing product: ' + env + '/' + product
                    + '\\nApp Types: ' + (wizCtx.appTypes.join(', ')||'none')
                    + '\\nResource Types: ' + (wizCtx.resourceTypes.join(', ')||'none'), 'ok');
                showStep(currentStep);
            }

            // ============================================
            // Load Reference Data
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

            // Step 4: env change => show existing products
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
                            + '<td><button onclick="selectExistingProduct(\\''+esc(env)+'\\',\\''+esc(p)+'\\')" '
                            + 'style="height:28px;padding:2px 14px;font-size:0.85em;background:#28a745;">Select</button></td></tr>';
                    });
                    html += '</table>';
                    list.innerHTML = html;
                } else {
                    container.style.display = 'none';
                }
            }

            // Step 5: env/product change => show existing resources
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

            // Step 6: env/product change => show existing hosts
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
            // Step 7: Access & Grants Handlers
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
                document.getElementById('w-secretList').innerHTML = '';
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
                if(n === 4) { populateEnvDropdown(); }
                if(n === 5) {
                    populateStepEnvDropdown('w-resEnv');
                    populateStepProductDropdown('w-resEnv', 'w-resProduct');
                    onWizResProductChange();
                }
                if(n === 6) {
                    populateStepEnvDropdown('w-hostEnv');
                    populateStepProductDropdown('w-hostEnv', 'w-hostProduct');
                    onWizHostProductChange();
                }
                if(n === 7) {
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
                    case 0: executeConnectionCheck(); break;
                    case 1: executeRootPolicy(); break;
                    case 2: executeJwtAuth(); break;
                    case 3: executeEnvironments(); break;
                    case 4: executeProduct(); break;
                    case 5: executeResources(); break;
                    case 6: executeHosts(); break;
                    case 7: executeAccessSecrets(); break;
                    case 8: executeSummary(); break;
                }
            }

            function executeConnectionCheck() {
                fetch('/api/status').then(function(r) { return r.json(); }).then(function(data) {
                    if (data.reachable) {
                        stepState[0] = 'done';
                        stepResult(0, 'Connected to Conjur!\\nURL: ' + data.conjurUrl + '\\nAccount: ' + data.account + '\\n\\nDiscovering existing resources...', 'ok');
                        loadExisting();
                    } else {
                        stepState[0] = 'error';
                        stepResult(0, 'Conjur is unreachable.', 'err');
                    }
                    showStep(currentStep);
                }).catch(function(e) { stepState[0] = 'error'; stepResult(0, 'Failed: ' + e.message, 'err'); showStep(currentStep); });
            }

            function executeRootPolicy() {
                wizCtx.orgName = document.getElementById('w-orgName').value.trim() || 'nimbus';
                apiPost('/api/setup/root', {orgName: wizCtx.orgName}).then(function(data) {
                    if (data.error) { stepState[1] = 'error'; stepResult(1, data.error, 'err'); }
                    else { stepState[1] = 'done'; stepResult(1, data, 'ok'); }
                    showStep(currentStep);
                }).catch(function(e) { stepState[1] = 'error'; stepResult(1, e.message, 'err'); showStep(currentStep); });
            }

            function executeJwtAuth() {
                var body = {
                    serviceId: document.getElementById('w-jwtServiceId').value,
                    jwksUri: document.getElementById('w-jwksUri').value,
                    tokenAppProperty: document.getElementById('w-tokenAppProp').value,
                    identityPath: document.getElementById('w-identityPath').value,
                    issuer: document.getElementById('w-issuer').value,
                    audience: document.getElementById('w-audience').value
                };
                apiPost('/api/authenticators/jwt/setup', body).then(function(data) {
                    if (data.error) { stepState[2] = 'error'; stepResult(2, data.error, 'err'); }
                    else { stepState[2] = 'done'; stepResult(2, data, 'ok'); }
                    showStep(currentStep);
                }).catch(function(e) { stepState[2] = 'error'; stepResult(2, e.message, 'err'); showStep(currentStep); });
            }

            function executeEnvironments() {
                var allEnvs = getChecked('w-env');
                if (allEnvs.length === 0) { stepResult(3, 'Select at least one environment', 'err'); return; }
                var existingEnvs = wizCtx.existing.envs[wizCtx.orgName] || [];
                var newEnvs = allEnvs.filter(function(e){ return existingEnvs.indexOf(e) === -1; });
                wizCtx.environments = allEnvs;

                if(newEnvs.length === 0){
                    stepState[3] = 'done';
                    stepResult(3, 'All selected environments already exist in Conjur: ' + allEnvs.join(', '), 'ok');
                    populateEnvDropdown();
                    showStep(currentStep);
                    return;
                }

                apiPost('/api/setup/environments', {orgName: wizCtx.orgName, environments: newEnvs}).then(function(data) {
                    if (data.error) { stepState[3] = 'error'; stepResult(3, data.error, 'err'); }
                    else {
                        stepState[3] = 'done';
                        var msg = typeof data === 'string' ? data : JSON.stringify(data, null, 2);
                        if(existingEnvs.length > 0) msg += '\\n\\nAlready existing: ' + existingEnvs.join(', ');
                        stepResult(3, msg, 'ok');
                        newEnvs.forEach(function(e){
                            if(!wizCtx.existing.envs[wizCtx.orgName]) wizCtx.existing.envs[wizCtx.orgName] = [];
                            wizCtx.existing.envs[wizCtx.orgName].push(e);
                        });
                        populateEnvDropdown();
                    }
                    showStep(currentStep);
                }).catch(function(e) { stepState[3] = 'error'; stepResult(3, e.message, 'err'); showStep(currentStep); });
            }

            function executeProduct() {
                wizCtx.productEnv = document.getElementById('w-prodEnv').value;
                wizCtx.productName = document.getElementById('w-productName').value.trim();
                wizCtx.appTypes = getChecked('w-appType');
                wizCtx.resourceTypes = getChecked('w-resType');
                if (!wizCtx.productName) { stepResult(4, 'Product name is required', 'err'); return; }
                var body = {
                    orgName: wizCtx.orgName, environment: wizCtx.productEnv,
                    productName: wizCtx.productName, appTypes: wizCtx.appTypes, resourceTypes: wizCtx.resourceTypes
                };
                apiPost('/api/setup/product', body).then(function(data) {
                    if (data.error) { stepState[4] = 'error'; stepResult(4, data.error, 'err'); }
                    else {
                        stepState[4] = 'done'; stepResult(4, data, 'ok');
                        var pk = wizCtx.orgName+'/'+wizCtx.productEnv;
                        if(!wizCtx.existing.prods[pk]) wizCtx.existing.prods[pk] = [];
                        if(wizCtx.existing.prods[pk].indexOf(wizCtx.productName)===-1) wizCtx.existing.prods[pk].push(wizCtx.productName);
                        var apk = wizCtx.orgName+'/'+wizCtx.productEnv+'/'+wizCtx.productName;
                        wizCtx.existing.appTypes[apk] = wizCtx.appTypes.slice();
                        wizCtx.existing.resTypes[apk] = wizCtx.resourceTypes.slice();
                        addDbRow(); addHostRow(); addGrantRow();
                    }
                    showStep(currentStep);
                }).catch(function(e) { stepState[4] = 'error'; stepResult(4, e.message, 'err'); showStep(currentStep); });
            }

            function executeResources() {
                var env = document.getElementById('w-resEnv') ? document.getElementById('w-resEnv').value : wizCtx.productEnv;
                var prod = document.getElementById('w-resProduct') ? document.getElementById('w-resProduct').value : wizCtx.productName;
                if(!env || !prod) { stepResult(5, 'Select an environment and product first.', 'err'); return; }
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

                if (promises.length === 0) { stepResult(5, 'No new resources to register.', 'err'); return; }
                Promise.all(promises).then(function() {
                    stepState[5] = 'done'; stepResult(5, results.join('\\n'), 'ok');
                    loadExisting();
                    showStep(currentStep);
                }).catch(function(e) {
                    stepState[5] = 'error'; stepResult(5, results.join('\\n') + '\\nError: ' + e.message, 'err'); showStep(currentStep);
                });
            }

            function executeHosts() {
                var env = document.getElementById('w-hostEnv') ? document.getElementById('w-hostEnv').value : wizCtx.productEnv;
                var prod = document.getElementById('w-hostProduct') ? document.getElementById('w-hostProduct').value : wizCtx.productName;
                if(!env || !prod) { stepResult(6, 'Select an environment and product first.', 'err'); return; }
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
                        serviceAccount: sa && sa.value.trim() ? sa.value.trim() : null
                    };
                    promises.push(apiPost('/api/hosts/register', body).then(function(d) {
                        var msg = hostId.value + ': ' + (d.message || d.error || JSON.stringify(d));
                        if (d.apiKey) { msg += '\\n  API Key: ' + d.apiKey + ' (SAVE THIS!)'; }
                        wizCtx.hosts.push({ id: hostId.value.trim(), apiKey: d.apiKey || null, appType: appType ? appType.value : '' });
                        results.push(msg);
                    }));
                });
                if (promises.length === 0) { stepResult(6, 'No new hosts to register.', 'err'); return; }
                Promise.all(promises).then(function() {
                    stepState[6] = 'done'; stepResult(6, results.join('\\n\\n'), 'ok');
                    loadExisting();
                    showStep(currentStep);
                }).catch(function(e) {
                    stepState[6] = 'error'; stepResult(6, results.join('\\n\\n') + '\\nError: ' + e.message, 'err'); showStep(currentStep);
                });
            }

            function executeAccessSecrets() {
                var env = document.getElementById('w-grantEnv') ? document.getElementById('w-grantEnv').value : wizCtx.productEnv;
                var prod = document.getElementById('w-grantProduct') ? document.getElementById('w-grantProduct').value : wizCtx.productName;
                if(!env || !prod) { stepResult(7, 'Select an environment and product first.', 'err'); return; }

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

                var secrets = [];
                document.querySelectorAll('#w-secretList .secret-row').forEach(function(row) {
                    var varSel = row.querySelector('.var-id');
                    var varVal = row.querySelector('.var-val');
                    if (varSel && varSel.value && varVal && varVal.value.trim()) {
                        secrets.push({ variableId: varSel.value, value: varVal.value.trim() });
                    }
                });
                if (secrets.length > 0) {
                    promises.push(apiPost('/api/secrets/bulk', { secrets: secrets })
                        .then(function(d) { results.push('Secrets: ' + (d.message || d.error)); }));
                }

                if (promises.length === 0) {
                    stepResult(7, 'No grants or secrets to apply.', 'info');
                    stepState[7] = 'done'; showStep(currentStep); return;
                }
                Promise.all(promises).then(function() {
                    stepState[7] = 'done'; stepResult(7, results.join('\\n'), 'ok'); showStep(currentStep);
                }).catch(function(e) {
                    stepState[7] = 'error'; stepResult(7, results.join('\\n') + '\\n' + e.message, 'err'); showStep(currentStep);
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
                    var summary = 'Setup Wizard Complete!\\n========================\\n\\n' + results.join('\\n');
                    stepState[8] = 'done'; stepResult(8, summary, 'ok');
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
                var html = '<div class="form-row" style="align-items:end;">'
                    + '<div class="form-group"><label>Host ID</label><input type="text" class="w-hostId" placeholder="app1" /></div>'
                    + '<div class="form-group"><label>App Type</label><select class="w-hostAppType">' + atOpts + '</select></div>'
                    + '<div class="form-group"><label>Namespace</label><input type="text" class="w-hostNs" value="apps" style="min-width:120px;" /></div>'
                    + '<div class="form-group"><label>Service Account</label><input type="text" class="w-hostSa" placeholder="auto" style="min-width:140px;" /></div>'
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

            function addSecretRow() {
                var env = document.getElementById('w-grantEnv') ? document.getElementById('w-grantEnv').value : wizCtx.productEnv;
                var prod = document.getElementById('w-grantProduct') ? document.getElementById('w-grantProduct').value : wizCtx.productName;
                var pk = wizCtx.orgName + '/' + env + '/' + prod;
                var prefix = wizCtx.orgName + '/' + env + '/products/' + prod + '/resources/';
                var resources = wizCtx.existing.resources[pk] || [];
                var varOpts = '';
                resources.forEach(function(r) {
                    var fields = getFieldsForType(r.type);
                    fields.forEach(function(f) {
                        varOpts += '<option value="' + prefix + esc(r.type) + '/' + esc(r.name) + '/' + esc(f) + '">'
                            + esc(r.type) + '/' + esc(r.name) + '/' + esc(f) + '</option>';
                    });
                });
                if(!varOpts) varOpts = '<option value="">-- no resources found --</option>';
                var html = '<div class="secret-row">'
                    + '<select class="var-id" style="min-width:360px;">' + varOpts + '</select>'
                    + '<input type="text" class="var-val" placeholder="value" />'
                    + '<button class="danger" onclick="this.parentElement.remove()" style="height:28px;padding:2px 8px;font-size:0.8em;">X</button></div>';
                document.getElementById('w-secretList').insertAdjacentHTML('beforeend', html);
            }

            initWizard();
            """;
}
