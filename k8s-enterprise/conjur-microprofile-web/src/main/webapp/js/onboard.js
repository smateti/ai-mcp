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
        var lines = e.target.result.split('\n').filter(function(l) { return l.trim(); });
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
    var csv = 'app_name,host_id,api_key,k8s_secret,status,conjur_secrets_mapping\n';
    lastResults.apps.forEach(function(app) {
        csv += [app.name, app.hostId, app.apiKey||'', app.k8sSecret||'',
                app.status||'', app.conjurSecretsMapping||''].join(',') + '\n';
    });
    var blob = new Blob([csv], {type:'text/csv'});
    var a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'conjur-onboarding-results.csv';
    a.click();
}
