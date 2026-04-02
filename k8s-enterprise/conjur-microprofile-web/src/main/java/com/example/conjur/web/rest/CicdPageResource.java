package com.example.conjur.web.rest;

import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/cicd")
@RequestScoped
public class CicdPageResource {

    private static final String CICD_SCRIPT = """
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

            // ===== Tab 1: K8s Secrets =====
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
                          + '<td><button class="danger" onclick="deleteSecret(\\'' + escJs(s.namespace) + '\\', \\'' + escJs(s.name) + '\\')">Delete</button></td></tr>';
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

            // ===== Tab 2: Create K8s Secret =====
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

            // ===== Tab 3: Init Container Guide =====
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
                return 'apiVersion: apps/v1\\n'
                    + 'kind: Deployment\\n'
                    + 'metadata:\\n'
                    + '  name: my-app\\n'
                    + '  namespace: apps\\n'
                    + 'spec:\\n'
                    + '  replicas: 1\\n'
                    + '  selector:\\n'
                    + '    matchLabels:\\n'
                    + '      app: my-app\\n'
                    + '  template:\\n'
                    + '    metadata:\\n'
                    + '      labels:\\n'
                    + '        app: my-app\\n'
                    + '    spec:\\n'
                    + '      serviceAccountName: my-app-sa\\n'
                    + '      initContainers:\\n'
                    + '      - name: conjur-secrets-init\\n'
                    + '        image: localhost:30500/conjur-secrets-init:latest\\n'
                    + '        env:\\n'
                    + '        - name: CONJUR_APPLIANCE_URL\\n'
                    + '          valueFrom:\\n'
                    + '            secretKeyRef:\\n'
                    + '              name: conjur-my-app    # created by CI/CD tab\\n'
                    + '              key: CONJUR_APPLIANCE_URL\\n'
                    + '        - name: CONJUR_ACCOUNT\\n'
                    + '          valueFrom:\\n'
                    + '            secretKeyRef:\\n'
                    + '              name: conjur-my-app\\n'
                    + '              key: CONJUR_ACCOUNT\\n'
                    + '        - name: CONJUR_AUTHN_LOGIN\\n'
                    + '          valueFrom:\\n'
                    + '            secretKeyRef:\\n'
                    + '              name: conjur-my-app\\n'
                    + '              key: CONJUR_AUTHN_LOGIN\\n'
                    + '        - name: CONJUR_AUTHN_API_KEY\\n'
                    + '          valueFrom:\\n'
                    + '            secretKeyRef:\\n'
                    + '              name: conjur-my-app\\n'
                    + '              key: CONJUR_AUTHN_API_KEY\\n'
                    + '        - name: CONJUR_SECRETS\\n'
                    + '          value: "db.url=nimbus/dev/.../dbs/db1/url,db.password=nimbus/dev/.../dbs/db1/password"\\n'
                    + '        volumeMounts:\\n'
                    + '        - name: conjur-secrets\\n'
                    + '          mountPath: /conjur/secrets\\n'
                    + '      containers:\\n'
                    + '      - name: my-app\\n'
                    + '        image: localhost:30500/my-app:latest\\n'
                    + '        ports:\\n'
                    + '        - containerPort: 9080\\n'
                    + '        volumeMounts:\\n'
                    + '        - name: conjur-secrets\\n'
                    + '          mountPath: /conjur/secrets\\n'
                    + '          readOnly: true\\n'
                    + '      volumes:\\n'
                    + '      - name: conjur-secrets\\n'
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
            function escJs(s) { return s.replace(/\\\\/g,'\\\\\\\\').replace(/'/g,"\\\\'"); }

            // Auto-load secrets
            loadK8sSecrets();
            """;

    private static final String CICD_HTML = """
            <style>
                .tab-bar { display: flex; gap: 0; margin-bottom: 0; }
                .tab-btn { padding: 10px 24px; background: #e0e0e0; border: none; cursor: pointer;
                           font-weight: 600; border-radius: 8px 8px 0 0; color: #555; }
                .tab-btn.active { background: #fff; color: #16213e; }
                .tab-panel { display: none; background: #fff; padding: 20px; border-radius: 0 8px 8px 8px;
                             box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                textarea { width: 100%%; min-height: 80px; font-family: monospace; padding: 8px;
                           border: 1px solid #ccc; border-radius: 4px; }
                .guide-section { margin: 20px 0; }
                .guide-section h4 { color: #16213e; margin-bottom: 8px; }
                .guide-section p { margin: 4px 0; line-height: 1.6; }
                pre.yaml { background: #1e1e1e; color: #d4d4d4; padding: 16px; border-radius: 8px;
                           overflow-x: auto; font-size: 0.85em; line-height: 1.5; white-space: pre; }
                .info-box { background: #d1ecf1; color: #0c5460; padding: 12px 16px; border-radius: 6px;
                            border: 1px solid #17a2b8; margin: 12px 0; }
            </style>

            <h2>CI/CD Pipeline Console</h2>
            <p>Manage K8s secrets and configure init containers for secret injection.</p>

            <div class="tab-bar">
                <button class="tab-btn active" id="btn-secrets" onclick="switchTab('secrets')">K8s Secrets</button>
                <button class="tab-btn" id="btn-create-secret" onclick="switchTab('create-secret')">Create K8s Secret</button>
                <button class="tab-btn" id="btn-guide" onclick="switchTab('guide')">Init Container Guide</button>
            </div>

            <!-- Tab 1: K8s Secrets -->
            <div class="tab-panel" id="panel-secrets" style="display:block">
                <div class="form-row">
                    <div class="form-group"><label>Namespace</label><input id="secret-ns" value="apps" placeholder="apps"></div>
                    <button onclick="loadK8sSecrets()">Refresh</button>
                </div>
                <div id="secret-list"><p>Loading...</p></div>
                <div id="secret-result" class="result"></div>
            </div>

            <!-- Tab 2: Create K8s Secret -->
            <div class="tab-panel" id="panel-create-secret">
                <h3>Create K8s Secret with Conjur Identity</h3>
                <div class="form-row">
                    <div class="form-group"><label>Secret Name</label><input id="k8s-name" placeholder="conjur-my-app"></div>
                    <div class="form-group"><label>Namespace</label><input id="k8s-ns" value="apps" placeholder="apps"></div>
                </div>
                <div class="form-row">
                    <div class="form-group"><label>Host Path (Conjur identity)</label>
                        <input id="k8s-hostpath" placeholder="nimbus/dev/products/product1/apps/nims/app1" style="min-width:400px"></div>
                </div>
                <div class="form-row">
                    <div class="form-group"><label>API Key</label>
                        <input id="k8s-apikey" type="password" placeholder="Conjur host API key" style="min-width:400px"></div>
                </div>
                <div class="form-group" style="margin: 12px 0;">
                    <label>CONJUR_SECRETS mapping (optional)</label>
                    <textarea id="k8s-secrets" placeholder="db.url=nimbus/dev/products/product1/resources/dbs/db1/url,db.password=..."></textarea>
                </div>
                <button onclick="createK8sSecret()">Create K8s Secret</button>
                <div id="create-secret-result" class="result"></div>
            </div>

            <!-- Tab 3: Init Container Guide -->
            <div class="tab-panel" id="panel-guide">
                <h3>Init Container Secret Injection Guide</h3>

                <div class="guide-section">
                    <h4>How It Works</h4>
                    <p>The <code>conjur-secrets-init</code> init container runs before your application starts.
                       It authenticates to Conjur using the API key stored in the K8s secret, fetches the
                       requested secrets, and writes them to <code>/conjur/secrets/</code> as individual files
                       and a combined <code>.env</code> file.</p>
                </div>

                <div class="info-box">
                    <strong>Conjur URL:</strong> <code id="guide-conjur-url">Loading...</code><br>
                    <strong>Account:</strong> <code id="guide-conjur-account">Loading...</code>
                </div>

                <div class="guide-section">
                    <h4>Workflow</h4>
                    <ol style="line-height: 2;">
                        <li><strong>Admin</strong> sets up policy structure via <a href="/wizard">Setup Wizard</a></li>
                        <li><strong>DBA</strong> creates databases and sets credentials via <a href="/dba">DBA Console</a></li>
                        <li><strong>Migrator</strong> creates app host (gets API key) via <a href="/migrator">Migrator Console</a></li>
                        <li><strong>Migrator</strong> grants app access to database</li>
                        <li><strong>CI/CD</strong> creates K8s secret with app identity (this tab)</li>
                        <li><strong>Init Container</strong> fetches secrets at pod startup</li>
                        <li><strong>App</strong> reads secrets from <code>/conjur/secrets/</code></li>
                    </ol>
                </div>

                <div class="guide-section">
                    <h4>Example Deployment YAML</h4>
                    <button id="copy-btn" onclick="copyYaml()" style="margin-bottom: 8px;">Copy YAML</button>
                    <pre class="yaml" id="guide-yaml">Loading...</pre>
                </div>

                <div class="guide-section">
                    <h4>K8s Secret Contents</h4>
                    <table>
                        <tr><th>Key</th><th>Description</th></tr>
                        <tr><td><code>CONJUR_APPLIANCE_URL</code></td><td>Conjur server URL (in-cluster)</td></tr>
                        <tr><td><code>CONJUR_ACCOUNT</code></td><td>Conjur account name</td></tr>
                        <tr><td><code>CONJUR_AUTHN_LOGIN</code></td><td>Host identity (e.g., <code>host/nimbus/dev/products/.../app1</code>)</td></tr>
                        <tr><td><code>CONJUR_AUTHN_API_KEY</code></td><td>Host API key for authentication</td></tr>
                        <tr><td><code>CONJUR_SECRETS</code></td><td>Comma-separated key=path mappings</td></tr>
                    </table>
                </div>

                <div class="guide-section">
                    <h4>Secret Files Output</h4>
                    <p>The init container writes to <code>/conjur/secrets/</code>:</p>
                    <table>
                        <tr><th>File</th><th>Content</th></tr>
                        <tr><td><code>/conjur/secrets/db.url</code></td><td>Database URL value</td></tr>
                        <tr><td><code>/conjur/secrets/db.password</code></td><td>Database password value</td></tr>
                        <tr><td><code>/conjur/secrets/.env</code></td><td>All secrets as KEY=VALUE pairs</td></tr>
                    </table>
                </div>
            </div>
            """;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String cicd() {
        return PageTemplates.page("CI/CD Pipeline", "nav-cicd",
                CICD_HTML + "<script>" + PageTemplates.SCRIPT_UTILS + CICD_SCRIPT + "</script>");
    }
}
