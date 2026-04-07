<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<t:layout title="Bulk Onboard" activeNav="nav-onboard" pageScript="onboard.js">
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
        <textarea id="jsonInput" rows="12" style="width:100%;font-family:monospace;padding:10px;border:1px solid #ccc;border-radius:4px;">{
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
</t:layout>
