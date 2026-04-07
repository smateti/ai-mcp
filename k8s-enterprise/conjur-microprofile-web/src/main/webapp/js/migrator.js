var migData = {};
var liveCtx = {};
var currentTab = 'create-app';

function switchTab(tab) {
    document.querySelectorAll('.tab-btn').forEach(function(b){b.classList.remove('active');});
    document.querySelectorAll('.tab-panel').forEach(function(p){p.style.display='none';});
    document.getElementById('btn-'+tab).classList.add('active');
    document.getElementById('panel-'+tab).style.display='block';
    currentTab = tab;
    if (tab==='my-apps' && !migData.apps) loadMyApps();
    if (tab==='manage-vars' && !migData.vars) loadVars();
}

function loadLiveDropdowns() {
    loadLiveContext(function(ctx) {
        liveCtx = ctx;
        var prefixes = ['app', 'api', 'grant', 'var'];
        prefixes.forEach(function(pfx) {
            fillLiveSelect(pfx + '-org', ctx.orgs);
            fillLiveSelect(pfx + '-env', []);
            fillLiveSelect(pfx + '-product', []);
            var orgEl = document.getElementById(pfx + '-org');
            var envEl = document.getElementById(pfx + '-env');
            var prodEl = document.getElementById(pfx + '-product');
            if (orgEl) orgEl.onchange = function() { cascadeMig(pfx, 'org'); };
            if (envEl) envEl.onchange = function() { cascadeMig(pfx, 'env'); };
            if (prodEl) prodEl.onchange = function() { cascadeMig(pfx, 'product'); };
        });
        fillLiveSelect('app-type', []);
        // Auto-select if only one org
        if (ctx.orgs.length === 1) {
            prefixes.forEach(function(pfx) {
                var el = document.getElementById(pfx + '-org');
                if (el) { el.value = ctx.orgs[0]; cascadeMig(pfx, 'org'); }
            });
        }
    });
}

function cascadeMig(pfx, level) {
    var org = document.getElementById(pfx + '-org').value;
    var envEl = document.getElementById(pfx + '-env');
    var prodEl = document.getElementById(pfx + '-product');
    var typeEl = document.getElementById(pfx + '-type');
    var resTypeEl = document.getElementById(pfx + '-restype');

    if (level === 'org') {
        var envs = org ? (liveCtx.envs[org] || []) : [];
        fillLiveSelect(pfx + '-env', envs);
        fillLiveSelect(pfx + '-product', []);
        if (typeEl) fillLiveSelect(pfx + '-type', []);
        if (resTypeEl) fillLiveSelect(pfx + '-restype', []);
        if (envs.length === 1 && envEl) { envEl.value = envs[0]; cascadeMig(pfx, 'env'); }
        return;
    }

    if (level === 'env') {
        var env = envEl ? envEl.value : '';
        var prods = (org && env) ? (liveCtx.prods[org + '/' + env] || []) : [];
        fillLiveSelect(pfx + '-product', prods);
        if (typeEl) fillLiveSelect(pfx + '-type', []);
        if (resTypeEl) fillLiveSelect(pfx + '-restype', []);
        if (prods.length === 1 && prodEl) { prodEl.value = prods[0]; cascadeMig(pfx, 'product'); }
        return;
    }

    if (level === 'product') {
        var env2 = envEl ? envEl.value : '';
        var prod = prodEl ? prodEl.value : '';
        var pk = org + '/' + env2 + '/' + prod;
        if (typeEl) {
            var types = (org && env2 && prod) ? (liveCtx.appTypes[pk] || []) : [];
            fillLiveSelect(pfx + '-type', types);
        }
        if (resTypeEl) {
            var rts = (org && env2 && prod) ? (liveCtx.resTypes[pk] || []) : [];
            fillLiveSelect(pfx + '-restype', rts);
        }
        // For grants tab, auto-load resources
        if (pfx === 'grant' && org && env2 && prod) loadGrantContext();
    }
}

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
            var msg = 'Host registered!\n\nHost ID: ' + (d.hostId || d.name)
                    + '\nAPI Key: ' + (d.apiKey || 'N/A')
                    + '\n\n*** SAVE THE API KEY - IT IS SHOWN ONLY ONCE ***';

            var createK8s = document.getElementById('app-create-k8s').checked;
            if (createK8s && d.apiKey) {
                var hostPath = org + '/environments/' + env + '/products/' + prod + '/apps/' + appType + '/' + hostId;
                var secretName = 'conjur-' + hostId;
                return apiCall('POST', '/api/cicd/k8s-secret', {
                    secretName: secretName,
                    namespace: ns,
                    hostPath: hostPath,
                    apiKey: d.apiKey
                }).then(function(k) {
                    msg += '\n\nK8s Secret: ' + (k.secretName || 'failed') + ' in namespace ' + ns;
                    showResult('app-result', msg, false);
                }).catch(function(e) {
                    msg += '\n\nK8s Secret creation failed: ' + e.message;
                    showResult('app-result', msg, false);
                });
            } else {
                showResult('app-result', msg, false);
            }
        })
        .catch(function(e) { showResult('app-result', e.message, true); });
}

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

var grantHosts = [];
var grantResources = {};
var grantAuthenticators = [];

var resTypeLabels = {
    dbs: 'Databases', kafka: 'Kafka Clusters', api: 'API Resources',
    ldap: 'LDAP', smtp: 'SMTP', oauth: 'OAuth Providers', certs: 'Certificates'
};
var resTypeOrder = ['dbs', 'kafka', 'api', 'ldap', 'smtp', 'oauth', 'certs'];

function loadGrantContext() {
    var org = document.getElementById('grant-org').value;
    var env = document.getElementById('grant-env').value;
    var prod = document.getElementById('grant-product').value;
    if (!org || !env || !prod) {
        document.getElementById('grant-sections').innerHTML = '';
        document.getElementById('grant-jwt-section').style.display = 'none';
        return;
    }
    var basePath = org + '/environments/' + env + '/products/' + prod;
    document.getElementById('grant-loading').style.display = 'block';
    document.getElementById('grant-sections').innerHTML = '';

    var hostP = fetch('/api/resources?kind=host&search=' + encodeURIComponent(basePath + '/apps'))
        .then(function(r) { return r.json(); })
        .then(function(data) {
            grantHosts = [];
            (Array.isArray(data) ? data : []).forEach(function(r) {
                var id = (r.id || '').split(':').pop();
                var m = id.match(/\/apps\/([^\/]+)\/(.+)$/);
                if (m) grantHosts.push({ appType: m[1], hostId: m[2] });
            });
        });

    var resP = fetch('/api/resources?kind=variable&search=' + encodeURIComponent(basePath + '/resources'))
        .then(function(r) { return r.json(); })
        .then(function(data) {
            grantResources = {};
            (Array.isArray(data) ? data : []).forEach(function(r) {
                var path = (r.id || '').split(':').pop();
                var m = path.match(/\/resources\/([^\/]+)\/([^\/]+)\//);
                if (m) {
                    if (!grantResources[m[1]]) grantResources[m[1]] = {};
                    grantResources[m[1]][m[2]] = true;
                }
            });
            Object.keys(grantResources).forEach(function(type) {
                grantResources[type] = Object.keys(grantResources[type]).sort();
            });
        });

    var authP = fetch('/api/resources?kind=webservice&search=' + encodeURIComponent('conjur/authn-jwt'))
        .then(function(r) { return r.json(); })
        .then(function(data) {
            grantAuthenticators = [];
            (Array.isArray(data) ? data : []).forEach(function(r) {
                var id = (r.id || '').split(':').pop();
                var m = id.match(/^conjur\/authn-jwt\/([^\/]+)$/);
                if (m) grantAuthenticators.push(m[1]);
            });
        });

    Promise.all([hostP, resP, authP]).then(function() {
        document.getElementById('grant-loading').style.display = 'none';
        renderGrantSections();
    }).catch(function(e) {
        document.getElementById('grant-loading').style.display = 'none';
        document.getElementById('grant-sections').innerHTML =
            '<p class="badge badge-err">Error: ' + esc(e.message) + '</p>';
    });
}

function renderGrantSections() {
    var hostsSection = document.getElementById('grant-hosts-section');
    var resourcesSection = document.getElementById('grant-resources-section');
    var jwtSection = document.getElementById('grant-jwt-section');

    // Render Hosts section
    if (grantHosts.length === 0) {
        hostsSection.style.display = 'block';
        document.getElementById('grant-host-list').innerHTML =
            '<p class="grant-empty">No hosts found. Register hosts first in the "Create App" tab.</p>';
        document.getElementById('grant-apptype-filter').innerHTML =
            '<option value="">All App Types</option>';
        resourcesSection.style.display = 'none';
        jwtSection.style.display = 'none';
        return;
    }

    hostsSection.style.display = 'block';

    // Populate app type filter
    var appTypes = {};
    grantHosts.forEach(function(h) { appTypes[h.appType] = true; });
    var filterEl = document.getElementById('grant-apptype-filter');
    filterEl.innerHTML = '<option value="">All App Types</option>';
    Object.keys(appTypes).sort().forEach(function(t) {
        filterEl.innerHTML += '<option value="' + esc(t) + '">' + esc(t) + '</option>';
    });

    // Render host checkboxes
    var hostHtml = '';
    grantHosts.forEach(function(h) {
        hostHtml += '<label><input type="checkbox" class="grant-host-cb"'
            + ' data-apptype="' + esc(h.appType) + '" data-hostid="' + esc(h.hostId) + '"> '
            + esc(h.appType) + '/' + esc(h.hostId) + '</label>';
    });
    document.getElementById('grant-host-list').innerHTML = hostHtml;

    // Render Resources section
    var hasResources = false;
    var allTypes = resTypeOrder.slice();
    Object.keys(grantResources).forEach(function(t) {
        if (allTypes.indexOf(t) === -1) allTypes.push(t);
    });

    var resHtml = '';
    allTypes.forEach(function(type) {
        var resources = grantResources[type];
        if (!resources || resources.length === 0) return;
        hasResources = true;
        var label = resTypeLabels[type] || type;

        resHtml += '<div class="grant-resource-card"><h5>' + esc(label) + '</h5>'
            + '<div class="grant-host-grid">';
        resources.forEach(function(resName) {
            resHtml += '<label><input type="checkbox" class="grant-res-cb"'
                + ' data-type="' + esc(type) + '" data-resource="' + esc(resName) + '"> '
                + esc(resName) + '</label>';
        });
        resHtml += '</div></div>';
    });

    if (!hasResources) {
        resHtml = '<p class="grant-empty">No resources found. Create resources first.</p>';
    }

    document.getElementById('grant-resource-list').innerHTML = resHtml;
    resourcesSection.style.display = 'block';

    // JWT section
    if (grantAuthenticators.length > 0) {
        jwtSection.style.display = 'block';
        var sel = document.getElementById('grant-jwt-svc');
        sel.innerHTML = '';
        grantAuthenticators.forEach(function(svc) {
            sel.innerHTML += '<option value="' + esc(svc) + '">authn-jwt/' + esc(svc) + '</option>';
        });
    } else {
        jwtSection.style.display = 'none';
    }
}

function filterGrantHosts() {
    var filter = document.getElementById('grant-apptype-filter').value;
    document.querySelectorAll('.grant-host-cb').forEach(function(cb) {
        var label = cb.parentElement;
        if (!filter || cb.getAttribute('data-apptype') === filter) {
            label.style.display = '';
        } else {
            label.style.display = 'none';
            cb.checked = false;
        }
    });
}

function toggleAllHosts(checked) {
    var filter = document.getElementById('grant-apptype-filter').value;
    document.querySelectorAll('.grant-host-cb').forEach(function(cb) {
        if (!filter || cb.getAttribute('data-apptype') === filter) {
            cb.checked = checked;
        }
    });
}

function submitGrants() {
    var org = document.getElementById('grant-org').value;
    var env = document.getElementById('grant-env').value;
    var prod = document.getElementById('grant-product').value;
    if (!org || !env || !prod) { alert('Org, Environment, and Product are required'); return; }

    // Collect selected hosts
    var selectedHosts = [];
    document.querySelectorAll('.grant-host-cb:checked').forEach(function(cb) {
        selectedHosts.push({ appType: cb.getAttribute('data-apptype'), hostId: cb.getAttribute('data-hostid') });
    });

    // Collect selected resources
    var selectedResources = [];
    document.querySelectorAll('.grant-res-cb:checked').forEach(function(cb) {
        selectedResources.push({ type: cb.getAttribute('data-type'), resource: cb.getAttribute('data-resource') });
    });

    var promises = [];

    // Resource grants: each selected host × each selected resource
    selectedHosts.forEach(function(h) {
        selectedResources.forEach(function(r) {
            promises.push(apiCall('POST', '/api/access/grant', {
                orgName: org, environment: env, product: prod,
                appType: h.appType, hostId: h.hostId,
                targetType: r.type, targetResource: r.resource
            }));
        });
    });

    // JWT grants: each selected host × selected authenticator
    var jwtSvc = document.getElementById('grant-jwt-svc').value;
    if (jwtSvc && selectedHosts.length > 0) {
        selectedHosts.forEach(function(h) {
            promises.push(apiCall('POST', '/api/access/grant', {
                orgName: org, environment: env, product: prod,
                appType: h.appType, hostId: h.hostId,
                targetType: 'jwt', targetResource: jwtSvc
            }));
        });
    }

    if (promises.length === 0) { alert('Select at least one host and one resource (or JWT authenticator)'); return; }
    showResult('grant-result', 'Submitting ' + promises.length + ' grant(s)...', false);
    Promise.all(promises)
        .then(function(results) {
            var msgs = results.map(function(r) { return r.message || r.error || JSON.stringify(r); });
            showResult('grant-result', msgs.join('\n'), false);
        })
        .catch(function(e) { showResult('grant-result', e.message, true); });
}

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
              + '<td><button onclick="promptSetValue(\'' + escJs(id) + '\')">Set Value</button></td></tr>';
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
              + '<td><button onclick="showAppDetail(\'' + escJs(r.id || id) + '\')">Details</button></td></tr>';
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
function escJs(s) { return s.replace(/\\/g,'\\\\').replace(/'/g,"\\'"); }

// Init
loadLiveDropdowns();
