var cicdData = {};
var currentTab = 'secrets';

function switchTab(tab) {
    document.querySelectorAll('.tab-btn').forEach(function(b) { b.classList.remove('active'); });
    document.querySelectorAll('.tab-panel').forEach(function(p) { p.style.display = 'none'; });
    document.getElementById('btn-' + tab).classList.add('active');
    document.getElementById('panel-' + tab).style.display = 'block';
    currentTab = tab;
    if (tab === 'secrets' && !cicdData.secrets) loadK8sSecrets();
    if (tab === 'guide') loadStatusForGuide();
}

function loadK8sSecrets() {
    var ns = document.getElementById('secret-ns').value.trim() || 'apps';
    document.getElementById('secret-list').innerHTML = '<p>Loading K8s secrets...</p>';
    fetch('/api/cicd/k8s-secrets?namespace=' + encodeURIComponent(ns))
        .then(function(r) { return r.json(); })
        .then(function(data) {
            cicdData.secrets = data;
            renderSecrets(data);
        })
        .catch(function(e) {
            document.getElementById('secret-list').innerHTML = '<p class="badge badge-err">Error: ' + e.message + '</p>';
        });
}

function renderSecrets(data) {
    if (!data || data.length === 0) {
        document.getElementById('secret-list').innerHTML = '<p>No Conjur-managed K8s secrets found in this namespace.</p>';
        return;
    }
    var html = '<table><tr><th>Secret Name</th><th>App</th><th>Host Path</th><th>Created</th><th>Actions</th></tr>';
    data.forEach(function(s) {
        html += '<tr><td><strong>' + esc(s.name) + '</strong></td>'
              + '<td>' + esc(s.app) + '</td>'
              + '<td><code>' + esc(s.hostPath) + '</code></td>'
              + '<td>' + esc(s.created) + '</td>'
              + '<td><button class="danger" onclick="deleteSecret(\'' + escJs(s.namespace) + '\', \'' + escJs(s.name) + '\')">Delete</button></td></tr>';
    });
    html += '</table>';
    document.getElementById('secret-list').innerHTML = html;
}

function deleteSecret(ns, name) {
    if (!confirm('Delete K8s secret ' + ns + '/' + name + '?')) return;
    apiCall('DELETE', '/api/cicd/k8s-secret/' + encodeURIComponent(ns) + '/' + encodeURIComponent(name))
        .then(function(d) {
            showResult('secret-result', d, !!d.error);
            cicdData.secrets = null;
            loadK8sSecrets();
        })
        .catch(function(e) { showResult('secret-result', e.message, true); });
}

function createK8sSecret() {
    var name = document.getElementById('k8s-name').value.trim();
    var ns = document.getElementById('k8s-ns').value.trim() || 'apps';
    var hostPath = document.getElementById('k8s-hostpath').value.trim();
    var apiKey = document.getElementById('k8s-apikey').value.trim();
    var secrets = document.getElementById('k8s-secrets').value.trim();

    if (!name || !hostPath || !apiKey) {
        alert('Secret Name, Host Path, and API Key are required');
        return;
    }

    apiCall('POST', '/api/cicd/k8s-secret', {
        secretName: name, namespace: ns, hostPath: hostPath,
        apiKey: apiKey, conjurSecrets: secrets
    })
    .then(function(d) {
        showResult('create-secret-result', d, !!d.error);
        if (!d.error) { cicdData.secrets = null; }
    })
    .catch(function(e) { showResult('create-secret-result', e.message, true); });
}

var guideLoaded = false;
function loadStatusForGuide() {
    if (guideLoaded) return;
    fetch('/api/status')
        .then(function(r) { return r.json(); })
        .then(function(d) {
            var url = d.conjurUrl || 'http://conjur-server.conjur-system.svc.cluster.local';
            var acct = d.account || 'nimbusConjurAccount';
            document.getElementById('guide-conjur-url').textContent = url;
            document.getElementById('guide-conjur-account').textContent = acct;

            var yaml = generateDeploymentYaml(url, acct);
            document.getElementById('guide-yaml').textContent = yaml;
            guideLoaded = true;
        })
        .catch(function() {});
}

function generateDeploymentYaml(url, account) {
    return 'apiVersion: apps/v1\n'
        + 'kind: Deployment\n'
        + 'metadata:\n'
        + '  name: my-app\n'
        + '  namespace: apps\n'
        + 'spec:\n'
        + '  replicas: 1\n'
        + '  selector:\n'
        + '    matchLabels:\n'
        + '      app: my-app\n'
        + '  template:\n'
        + '    metadata:\n'
        + '      labels:\n'
        + '        app: my-app\n'
        + '    spec:\n'
        + '      serviceAccountName: my-app-sa\n'
        + '      initContainers:\n'
        + '      - name: conjur-secrets-init\n'
        + '        image: localhost:30500/conjur-secrets-init:latest\n'
        + '        env:\n'
        + '        - name: CONJUR_APPLIANCE_URL\n'
        + '          valueFrom:\n'
        + '            secretKeyRef:\n'
        + '              name: conjur-my-app    # created by CI/CD tab\n'
        + '              key: CONJUR_APPLIANCE_URL\n'
        + '        - name: CONJUR_ACCOUNT\n'
        + '          valueFrom:\n'
        + '            secretKeyRef:\n'
        + '              name: conjur-my-app\n'
        + '              key: CONJUR_ACCOUNT\n'
        + '        - name: CONJUR_AUTHN_LOGIN\n'
        + '          valueFrom:\n'
        + '            secretKeyRef:\n'
        + '              name: conjur-my-app\n'
        + '              key: CONJUR_AUTHN_LOGIN\n'
        + '        - name: CONJUR_AUTHN_API_KEY\n'
        + '          valueFrom:\n'
        + '            secretKeyRef:\n'
        + '              name: conjur-my-app\n'
        + '              key: CONJUR_AUTHN_API_KEY\n'
        + '        - name: CONJUR_SECRETS\n'
        + '          value: "db.url=nimbus/environments/dev/.../dbs/db1/url,db.password=nimbus/environments/dev/.../dbs/db1/password"\n'
        + '        volumeMounts:\n'
        + '        - name: conjur-secrets\n'
        + '          mountPath: /conjur/secrets\n'
        + '      containers:\n'
        + '      - name: my-app\n'
        + '        image: localhost:30500/my-app:latest\n'
        + '        ports:\n'
        + '        - containerPort: 9080\n'
        + '        volumeMounts:\n'
        + '        - name: conjur-secrets\n'
        + '          mountPath: /conjur/secrets\n'
        + '          readOnly: true\n'
        + '      volumes:\n'
        + '      - name: conjur-secrets\n'
        + '        emptyDir: {}';
}

function copyYaml() {
    var text = document.getElementById('guide-yaml').textContent;
    navigator.clipboard.writeText(text).then(function() {
        var btn = document.getElementById('copy-btn');
        btn.textContent = 'Copied!';
        setTimeout(function() { btn.textContent = 'Copy YAML'; }, 2000);
    });
}

function esc(s) { if (!s) return ''; return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }
function escJs(s) { return s.replace(/\\/g,'\\\\').replace(/'/g,"\\'"); }

// Auto-load secrets
loadK8sSecrets();
