var explorerData = {};

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
}

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
                '</td><td><button onclick="showRoleDetail(\'' + escJs(roleId) +
                '\')" style="height:28px;padding:2px 12px;font-size:0.85em;">Members</button></td></tr>';
    });
    layers.forEach(function(r) {
        var shortId = r.id ? r.id.split(':').pop() : '';
        var owner = r.owner ? r.owner.split(':').pop() : '';
        var roleId = r.id ? r.id.substring(r.id.indexOf(':') + 1) : '';
        html += '<tr><td><span class="badge badge-warn">Layer</span></td><td><code>' + esc(shortId) +
                '</code></td><td>' + esc(owner) +
                '</td><td><button onclick="showRoleDetail(\'' + escJs(roleId) +
                '\')" style="height:28px;padding:2px 12px;font-size:0.85em;">Members</button></td></tr>';
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

function esc(s) {
    if (!s) return '';
    return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

function escJs(s) {
    return s.replace(/\\/g,'\\\\').replace(/'/g,"\\'");
}

// Initialize first tab
loadResources('policies', 'policy');
