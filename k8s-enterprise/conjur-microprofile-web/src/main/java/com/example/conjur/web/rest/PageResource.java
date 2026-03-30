package com.example.conjur.web.rest;

import com.example.conjur.web.client.ConjurApiClient;
import com.example.conjur.web.client.model.DbCredentials;
import com.example.conjur.web.client.model.SecretEntry;
import com.example.conjur.web.client.model.StatusInfo;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;

@Path("/")
@RequestScoped
public class PageResource {

    @Inject
    @RestClient
    private ConjurApiClient api;

    private static final String CSS = """
            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                   max-width: 960px; margin: 0 auto; padding: 20px;
                   background: #f5f5f5; color: #333; }
            h1 { color: #1a1a2e; border-bottom: 2px solid #16213e; padding-bottom: 10px; }
            h2 { color: #16213e; margin-top: 0; }
            nav { background: #16213e; padding: 10px 20px; border-radius: 8px; margin-bottom: 20px; }
            nav a { color: #e0e0e0; text-decoration: none; margin-right: 20px; font-weight: 500; }
            nav a:hover, nav a.active { color: #fff; text-decoration: underline; }
            .card { background: #fff; border-radius: 8px; padding: 20px; margin: 16px 0;
                    box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
            table { border-collapse: collapse; width: 100%%; }
            th, td { text-align: left; padding: 10px 14px; border-bottom: 1px solid #eee; }
            th { background: #f0f0f0; font-weight: 600; }
            .badge { display: inline-block; padding: 3px 12px; border-radius: 12px;
                     font-size: 0.85em; font-weight: 600; }
            .badge-ok  { background: #d4edda; color: #155724; }
            .badge-err { background: #f8d7da; color: #721c24; }
            .badge-warn { background: #fff3cd; color: #856404; }
            code.masked { cursor: pointer; background: #333; color: #333;
                          padding: 2px 8px; border-radius: 3px; user-select: none; }
            code.masked.revealed { background: #e9ecef; color: #333; }
            .form-row { display: flex; gap: 12px; align-items: end; flex-wrap: wrap; margin: 12px 0; }
            .form-group { display: flex; flex-direction: column; }
            .form-group label { font-weight: 600; margin-bottom: 4px; font-size: 0.9em; }
            .form-group input { padding: 8px 12px; border: 1px solid #ccc; border-radius: 4px; min-width: 200px; }
            button { padding: 8px 20px; background: #0f3460; color: #fff; border: none;
                     border-radius: 4px; cursor: pointer; font-size: 0.95em; height: 38px; }
            button:hover { background: #16213e; }
            button.danger { background: #c0392b; }
            button.danger:hover { background: #96281b; }
            .result { margin-top: 10px; padding: 12px; background: #e9ecef; border-radius: 4px;
                      font-family: monospace; white-space: pre-wrap; display: none; font-size: 0.9em; }
            .result.error { background: #f8d7da; color: #721c24; }
            .result.success { background: #d4edda; color: #155724; }
            .alert { padding: 12px 16px; border-radius: 6px; margin: 10px 0; font-weight: 500; }
            .alert-warning { background: #fff3cd; color: #856404; border: 1px solid #ffc107; }
            .alert-info { background: #d1ecf1; color: #0c5460; border: 1px solid #17a2b8; }
            .summary { display: flex; gap: 20px; flex-wrap: wrap; }
            .summary .stat { text-align: center; padding: 16px 24px; background: #16213e;
                             color: #fff; border-radius: 8px; min-width: 120px; }
            .summary .stat .num { font-size: 2em; font-weight: 700; }
            .summary .stat .label { font-size: 0.85em; opacity: 0.8; }
            """;

    private static final String NAV = """
            <nav>
                <a href="/" id="nav-home">Dashboard</a>
                <a href="/databases" id="nav-db">Databases</a>
                <a href="/apps" id="nav-apps">Applications</a>
                <a href="/secrets" id="nav-secrets">All Secrets</a>
                <a href="/onboard" id="nav-onboard">Bulk Onboard</a>
            </nav>
            """;

    private static final String SCRIPT_UTILS = """
            function showResult(id, data, isError) {
                var el = document.getElementById(id);
                el.style.display = 'block';
                el.className = 'result ' + (isError ? 'error' : 'success');
                el.textContent = typeof data === 'string' ? data : JSON.stringify(data, null, 2);
            }
            function apiCall(method, url, body) {
                var opts = { method: method, headers: {'Content-Type': 'application/json'} };
                if (body) opts.body = JSON.stringify(body);
                return fetch(url, opts).then(function(r) { return r.json(); });
            }
            document.querySelectorAll('.masked').forEach(function(el) {
                el.addEventListener('click', function() { this.classList.toggle('revealed'); });
            });
            """;

    // ==================== Dashboard ====================

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String dashboard() {
        StatusInfo status;
        int secretCount = 0, dbCount = 0, appCount = 0;
        boolean reachable = false;
        try {
            status = api.getStatus();
            reachable = status.isReachable();
            secretCount = status.getKnownSecrets() != null ? status.getKnownSecrets().size() : 0;
            dbCount = status.getKnownDatabases() != null ? status.getKnownDatabases().size() : 0;
            appCount = status.getKnownApps() != null ? status.getKnownApps().size() : 0;
        } catch (Exception e) {
            status = new StatusInfo();
            status.setConjurUrl("Error connecting to REST API");
        }

        String statusBadge = reachable
                ? "<span class=\"badge badge-ok\">Connected</span>"
                : "<span class=\"badge badge-err\">Unreachable</span>";

        return page("Dashboard", "nav-home", """
                <div class="card">
                    <h2>Conjur Vault Status</h2>
                    <table>
                        <tr><td style="width:150px"><strong>Server</strong></td><td>%s</td></tr>
                        <tr><td><strong>Account</strong></td><td>%s</td></tr>
                        <tr><td><strong>Status</strong></td><td>%s</td></tr>
                    </table>
                </div>
                <div class="summary">
                    <div class="stat"><div class="num">%d</div><div class="label">Databases</div></div>
                    <div class="stat"><div class="num">%d</div><div class="label">Secrets</div></div>
                    <div class="stat"><div class="num">%d</div><div class="label">Applications</div></div>
                </div>
                """.formatted(
                esc(status.getConjurUrl()), esc(status.getAccount()), statusBadge,
                dbCount, secretCount, appCount));
    }

    // ==================== Databases ====================

    @GET
    @Path("/databases")
    @Produces(MediaType.TEXT_HTML)
    public String databases() {
        StatusInfo status;
        try { status = api.getStatus(); } catch (Exception e) { status = new StatusInfo(); }

        StringBuilder dbRows = new StringBuilder();
        if (status.getKnownDatabases() != null) {
            for (String db : status.getKnownDatabases()) {
                try {
                    DbCredentials creds = api.getDbCredentials(db);
                    dbRows.append("<tr><td><strong>%s</strong></td><td>%s</td>".formatted(esc(db), esc(creds.getUsername())));
                    dbRows.append("<td><code class=\"masked\">%s</code></td>".formatted(esc(creds.getPassword())));
                    dbRows.append("<td><span class=\"badge badge-ok\">Active</span></td></tr>\n");
                } catch (Exception e) {
                    dbRows.append("<tr><td><strong>%s</strong></td><td colspan=\"3\"><span class=\"badge badge-err\">Error: %s</span></td></tr>\n"
                            .formatted(esc(db), esc(e.getMessage())));
                }
            }
        }

        return page("Databases", "nav-db", """
                <div class="card">
                    <h2>Database Credentials</h2>
                    <p><em>Click password to reveal. Convention: db/{name}-uid and db/{name}-pwd in Conjur.</em></p>
                    <table>
                        <tr><th>Database</th><th>Username</th><th>Password</th><th>Status</th></tr>
                        %s
                    </table>
                </div>

                <div class="card">
                    <h2>Update Credentials</h2>
                    <div class="form-row">
                        <div class="form-group">
                            <label>Database Name</label>
                            <input type="text" id="updDbName" placeholder="empdb"/>
                        </div>
                        <div class="form-group">
                            <label>Username</label>
                            <input type="text" id="updDbUid" placeholder="db2inst1"/>
                        </div>
                        <div class="form-group">
                            <label>Password</label>
                            <input type="password" id="updDbPwd" placeholder="new-password"/>
                        </div>
                        <button onclick="updateDbCreds()">Update</button>
                    </div>
                    <div id="updResult" class="result"></div>
                </div>

                <div class="card">
                    <h2>Register New Database</h2>
                    <p>Creates credential variables in Conjur vault (db/{name}-uid and db/{name}-pwd).</p>
                    <div class="form-row">
                        <div class="form-group">
                            <label>Database Name</label>
                            <input type="text" id="newDbName" placeholder="mydb"/>
                        </div>
                        <button onclick="registerDb()">Register Database</button>
                    </div>
                    <div id="regDbResult" class="result"></div>
                </div>

                <script>
                %s
                function updateDbCreds() {
                    var db = document.getElementById('updDbName').value;
                    var uid = document.getElementById('updDbUid').value;
                    var pwd = document.getElementById('updDbPwd').value;
                    if (!db || !uid || !pwd) { showResult('updResult', 'All fields are required', true); return; }
                    apiCall('PUT', '/api/secrets/db/' + db, {username: uid, password: pwd})
                        .then(function(d) { showResult('updResult', d); setTimeout(function(){location.reload();}, 1500); })
                        .catch(function(e) { showResult('updResult', e.message, true); });
                }
                function registerDb() {
                    var name = document.getElementById('newDbName').value;
                    if (!name) { showResult('regDbResult', 'Database name is required', true); return; }
                    apiCall('POST', '/api/policies/register-database', {name: name})
                        .then(function(d) { showResult('regDbResult', d); setTimeout(function(){location.reload();}, 2000); })
                        .catch(function(e) { showResult('regDbResult', e.message, true); });
                }
                </script>
                """.formatted(dbRows.toString(), SCRIPT_UTILS));
    }

    // ==================== Applications ====================

    @GET
    @Path("/apps")
    @Produces(MediaType.TEXT_HTML)
    public String apps() {
        StatusInfo status;
        try { status = api.getStatus(); } catch (Exception e) { status = new StatusInfo(); }

        StringBuilder appRows = new StringBuilder();
        if (status.getKnownApps() != null) {
            for (String app : status.getKnownApps()) {
                appRows.append("<tr><td><strong>%s</strong></td><td>host/apps/%s</td><td><span class=\"badge badge-ok\">Registered</span></td></tr>\n"
                        .formatted(esc(app), esc(app)));
            }
        }

        return page("Applications", "nav-apps", """
                <div class="card">
                    <h2>Registered Applications</h2>
                    <p><em>Applications that have host identities in Conjur and can read database secrets.</em></p>
                    <table>
                        <tr><th>Application</th><th>Host Identity</th><th>Status</th></tr>
                        %s
                    </table>
                </div>

                <div class="card">
                    <h2>Register New Application</h2>
                    <div class="alert alert-warning">
                        The API key is shown <strong>only once</strong> after registration. Save it immediately.
                    </div>
                    <div class="form-row">
                        <div class="form-group">
                            <label>Application Name</label>
                            <input type="text" id="newAppName" placeholder="my-service"/>
                        </div>
                        <button onclick="registerApp()">Register Application</button>
                    </div>
                    <div id="regAppResult" class="result"></div>
                </div>

                <script>
                %s
                function registerApp() {
                    var name = document.getElementById('newAppName').value;
                    if (!name) { showResult('regAppResult', 'Application name is required', true); return; }
                    apiCall('POST', '/api/policies/register-app', {name: name})
                        .then(function(d) {
                            if (d.apiKey) {
                                showResult('regAppResult',
                                    'Application registered!\\n\\nHost: ' + d.hostId +
                                    '\\nAPI Key: ' + d.apiKey +
                                    '\\n\\nSAVE THIS API KEY NOW - it will not be shown again.');
                            } else {
                                showResult('regAppResult', d);
                            }
                        })
                        .catch(function(e) { showResult('regAppResult', e.message, true); });
                }
                </script>
                """.formatted(appRows.toString(), SCRIPT_UTILS));
    }

    // ==================== All Secrets ====================

    @GET
    @Path("/secrets")
    @Produces(MediaType.TEXT_HTML)
    public String secrets() {
        List<SecretEntry> entries;
        try { entries = api.listSecrets(); } catch (Exception e) { entries = List.of(); }

        StringBuilder rows = new StringBuilder();
        for (SecretEntry entry : entries) {
            String statusBadge = entry.isFound()
                    ? "<span class=\"badge badge-ok\">Set</span>"
                    : "<span class=\"badge badge-warn\">Not Set</span>";
            String val = entry.getValue() != null ? entry.getValue() : "";
            rows.append("<tr><td><code>%s</code></td><td><code class=\"masked\">%s</code></td><td>%s</td></tr>\n"
                    .formatted(esc(entry.getVariableId()), esc(val), statusBadge));
        }

        return page("All Secrets", "nav-secrets", """
                <div class="card">
                    <h2>Conjur Secrets</h2>
                    <p><em>Click value to reveal. All known variables in Conjur vault.</em></p>
                    <table>
                        <tr><th>Variable ID</th><th>Value</th><th>Status</th></tr>
                        %s
                    </table>
                </div>

                <div class="card">
                    <h2>Update Secret</h2>
                    <div class="form-row">
                        <div class="form-group">
                            <label>Variable ID</label>
                            <input type="text" id="secVarId" placeholder="db.empdb-uid"/>
                        </div>
                        <div class="form-group">
                            <label>New Value</label>
                            <input type="text" id="secValue" placeholder="new-value"/>
                        </div>
                        <button onclick="updateSecret()">Update</button>
                    </div>
                    <div id="secResult" class="result"></div>
                </div>

                <script>
                %s
                function updateSecret() {
                    var varId = document.getElementById('secVarId').value;
                    var val = document.getElementById('secValue').value;
                    if (!varId || !val) { showResult('secResult', 'Both fields are required', true); return; }
                    apiCall('PUT', '/api/secrets/' + varId.replace(/\\//g, '.'), {value: val})
                        .then(function(d) { showResult('secResult', d); setTimeout(function(){location.reload();}, 1500); })
                        .catch(function(e) { showResult('secResult', e.message, true); });
                }
                </script>
                """.formatted(rows.toString(), SCRIPT_UTILS));
    }

    // ==================== Bulk Onboarding ====================

    @GET
    @Path("/onboard")
    @Produces(MediaType.TEXT_HTML)
    public String onboard() {
        return page("Bulk Onboard", "nav-onboard", """
                <div class="card">
                    <h2>Bulk Application Onboarding</h2>
                    <p>Register multiple databases and applications with Conjur in a single operation.
                       Per-app K8s Secrets will be created automatically.</p>
                    <div class="alert alert-warning">
                        API keys are shown <strong>only once</strong> after registration. Download the results CSV immediately.
                    </div>
                </div>

                <div class="card">
                    <h2>Option 1: JSON Input</h2>
                    <p>Paste the onboarding JSON below:</p>
                    <textarea id="jsonInput" rows="12" style="width:100%%;font-family:monospace;padding:10px;border:1px solid #ccc;border-radius:4px;">{
  "apps": [
    {
      "name": "order-service",
      "namespace": "apps",
      "databases": ["orderdb"],
      "extraSecrets": {}
    },
    {
      "name": "inventory-service",
      "namespace": "apps",
      "databases": ["inventorydb"],
      "extraSecrets": {}
    }
  ]
}</textarea>
                    <div style="margin-top:12px;">
                        <button onclick="runOnboard()">Onboard Applications</button>
                    </div>
                    <div id="onboardResult" class="result"></div>
                </div>

                <div class="card">
                    <h2>Option 2: CSV Import</h2>
                    <p>Upload a CSV file with columns: <code>app_name,namespace,databases,extra_secrets,node_port</code></p>
                    <p style="font-size:0.9em;color:#666;">Example: <code>order-service,apps,orderdb,stripe.api.key=creds/stripe-api-key,30091</code></p>
                    <div class="form-row">
                        <input type="file" id="csvFile" accept=".csv" />
                        <button onclick="importCsv()">Import &amp; Convert to JSON</button>
                    </div>
                </div>

                <div class="card" id="resultsCard" style="display:none;">
                    <h2>Onboarding Results</h2>
                    <div id="resultsTable"></div>
                    <div style="margin-top:12px;">
                        <button onclick="downloadCsv()">Download Results CSV</button>
                    </div>
                </div>

                <script>
                %s
                var lastResults = null;

                function runOnboard() {
                    var json;
                    try { json = JSON.parse(document.getElementById('jsonInput').value); }
                    catch(e) { showResult('onboardResult', 'Invalid JSON: ' + e.message, true); return; }

                    showResult('onboardResult', 'Onboarding in progress...');
                    apiCall('POST', '/api/onboard/full', json)
                        .then(function(data) {
                            lastResults = data;
                            showResult('onboardResult', data);
                            showResultsTable(data);
                        })
                        .catch(function(e) { showResult('onboardResult', 'Error: ' + e.message, true); });
                }

                function importCsv() {
                    var file = document.getElementById('csvFile').files[0];
                    if (!file) { alert('Select a CSV file first'); return; }
                    var reader = new FileReader();
                    reader.onload = function(e) {
                        var lines = e.target.result.split('\\n').filter(function(l) { return l.trim(); });
                        var apps = [];
                        for (var i = 1; i < lines.length; i++) {
                            var cols = lines[i].split(',');
                            if (cols.length < 2) continue;
                            var entry = {
                                name: cols[0].trim(),
                                namespace: cols[1].trim() || 'apps',
                                databases: cols[2] ? cols[2].trim().split(';').filter(Boolean) : [],
                                extraSecrets: {}
                            };
                            if (cols[3]) {
                                cols[3].trim().split(';').forEach(function(pair) {
                                    var kv = pair.split('=');
                                    if (kv.length === 2) entry.extraSecrets[kv[0]] = kv[1];
                                });
                            }
                            apps.push(entry);
                        }
                        document.getElementById('jsonInput').value = JSON.stringify({apps: apps}, null, 2);
                    };
                    reader.readAsText(file);
                }

                function showResultsTable(data) {
                    if (!data.apps) return;
                    var card = document.getElementById('resultsCard');
                    card.style.display = 'block';
                    var html = '<table><tr><th>App</th><th>Host ID</th><th>API Key</th><th>K8s Secret</th><th>Status</th></tr>';
                    data.apps.forEach(function(app) {
                        html += '<tr><td>' + (app.name||'') + '</td><td>' + (app.hostId||'') +
                                '</td><td><code class="masked">' + (app.apiKey||'N/A') +
                                '</code></td><td>' + (app.k8sSecret||'') +
                                '</td><td>' + (app.status||'') + '</td></tr>';
                    });
                    html += '</table>';
                    document.getElementById('resultsTable').innerHTML = html;
                    document.querySelectorAll('.masked').forEach(function(el) {
                        el.addEventListener('click', function() { this.classList.toggle('revealed'); });
                    });
                }

                function downloadCsv() {
                    if (!lastResults || !lastResults.apps) return;
                    var csv = 'app_name,host_id,api_key,k8s_secret,status,conjur_secrets_mapping\\n';
                    lastResults.apps.forEach(function(app) {
                        csv += [app.name, app.hostId, app.apiKey||'', app.k8sSecret||'',
                                app.status||'', app.conjurSecretsMapping||''].join(',') + '\\n';
                    });
                    var blob = new Blob([csv], {type:'text/csv'});
                    var a = document.createElement('a');
                    a.href = URL.createObjectURL(blob);
                    a.download = 'conjur-onboarding-results.csv';
                    a.click();
                }
                </script>
                """.formatted(SCRIPT_UTILS));
    }

    // ==================== Helpers ====================

    private String page(String title, String activeNav, String body) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>%s - ConjurAdmin</title>
                    <style>%s</style>
                </head>
                <body>
                    <h1>Conjur Admin Console</h1>
                    %s
                    %s
                    <script>
                    var nav = document.getElementById('%s');
                    if (nav) nav.className = 'active';
                    </script>
                </body>
                </html>
                """.formatted(esc(title), CSS, NAV, body, activeNav);
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
