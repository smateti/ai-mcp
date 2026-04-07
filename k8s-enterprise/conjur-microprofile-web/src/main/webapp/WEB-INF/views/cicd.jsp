<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<t:layout title="CI/CD Pipeline" activeNav="nav-cicd" pageScript="cicd.js">
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
            <textarea id="k8s-secrets" placeholder="db.url=nimbus/environments/dev/products/product1/resources/dbs/db1/url,db.password=..."></textarea>
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
</t:layout>
