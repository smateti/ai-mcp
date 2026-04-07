<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<t:layout title="Migrator Console" activeNav="nav-migrator" pageScript="migrator.js">
    <h2>Migrator Console</h2>
    <p>Create apps, manage API resources, grant access, and manage variables.</p>

    <div class="tab-bar">
        <button class="tab-btn active" id="btn-create-app" onclick="switchTab('create-app')">Create App</button>
        <button class="tab-btn" id="btn-create-api" onclick="switchTab('create-api')">Create API Resource</button>
        <button class="tab-btn" id="btn-grants" onclick="switchTab('grants')">Grant Access</button>
        <button class="tab-btn" id="btn-manage-vars" onclick="switchTab('manage-vars')">Manage Variables</button>
        <button class="tab-btn" id="btn-my-apps" onclick="switchTab('my-apps')">My Apps</button>
    </div>

    <!-- Tab 1: Create App -->
    <div class="tab-panel" id="panel-create-app" style="display:block">
        <h3>Register Application Host</h3>
        <div class="form-row">
            <div class="form-group"><label>Organization</label>
                <select id="app-org"><option value="">-- Select --</option></select></div>
            <div class="form-group"><label>Environment</label>
                <select id="app-env"><option value="">-- Select --</option></select></div>
            <div class="form-group"><label>Product</label>
                <select id="app-product"><option value="">-- Select --</option></select></div>
        </div>
        <div class="form-row">
            <div class="form-group"><label>App Type</label>
                <select id="app-type"><option value="">-- Select --</option></select></div>
            <div class="form-group"><label>Host ID</label>
                <input id="app-hostid" placeholder="app1"></div>
            <div class="form-group"><label>Namespace</label>
                <input id="app-namespace" placeholder="apps" value="apps"></div>
            <div class="form-group"><label>Service Account</label>
                <input id="app-sa" placeholder="auto: hostId-sa"></div>
        </div>
        <div style="margin: 12px 0;">
            <label><input type="checkbox" id="app-create-k8s" checked> Create K8s Secret automatically</label>
        </div>
        <button onclick="createApp()">Register Host</button>
        <div class="alert alert-warning" style="margin-top: 12px;">
            The API key is shown only once. Save it immediately!
        </div>
        <div id="app-result" class="result"></div>
    </div>

    <!-- Tab 2: Create API Resource -->
    <div class="tab-panel" id="panel-create-api">
        <h3>Register API Resource</h3>
        <div class="form-row">
            <div class="form-group"><label>Organization</label>
                <select id="api-org"><option value="">-- Select --</option></select></div>
            <div class="form-group"><label>Environment</label>
                <select id="api-env"><option value="">-- Select --</option></select></div>
            <div class="form-group"><label>Product</label>
                <select id="api-product"><option value="">-- Select --</option></select></div>
            <div class="form-group"><label>API Name</label>
                <input id="api-name" placeholder="stripe"></div>
        </div>
        <h4>Credentials (optional -- set now or later)</h4>
        <div class="form-row">
            <div class="form-group"><label>Client ID / Key</label><input id="api-clientid"></div>
            <div class="form-group"><label>Client Secret</label><input id="api-clientsecret" type="password"></div>
            <div class="form-group"><label>Webhook Secret</label><input id="api-webhook" type="password"></div>
        </div>
        <button onclick="createApiResource()">Create API Resource</button>
        <div id="api-result" class="result"></div>
    </div>

    <!-- Tab 3: Grant Access -->
    <div class="tab-panel" id="panel-grants">
        <h3>Grant Access</h3>
        <p>Select a product to discover its hosts and resources, then grant per-resource access.</p>
        <div class="form-row">
            <div class="form-group"><label>Organization</label>
                <select id="grant-org"><option value="">-- Select --</option></select></div>
            <div class="form-group"><label>Environment</label>
                <select id="grant-env"><option value="">-- Select --</option></select></div>
            <div class="form-group"><label>Product</label>
                <select id="grant-product"><option value="">-- Select --</option></select></div>
        </div>
        <div id="grant-loading" style="display:none;"><p>Loading resources and hosts...</p></div>
        <div id="grant-sections"></div>
        <div id="grant-jwt-section" style="display:none;">
            <div class="grant-type-section">
                <h4>JWT Authenticator</h4>
                <div class="form-row" style="align-items:end;">
                    <div class="form-group"><label>Authenticator</label>
                        <select id="grant-jwt-svc" style="min-width:220px;">
                            <option value="">-- Loading --</option>
                        </select></div>
                </div>
                <div id="grant-jwt-hosts" class="grant-host-grid"></div>
            </div>
        </div>
        <div style="margin: 16px 0;">
            <button onclick="submitGrants()">Submit All Grants</button>
        </div>
        <div id="grant-result" class="result"></div>
    </div>

    <!-- Tab 4: Manage Variables -->
    <div class="tab-panel" id="panel-manage-vars">
        <h3>Search Variables</h3>
        <div class="form-row">
            <div class="form-group"><label>Search</label><input id="var-search" placeholder="e.g. resources/dbs"></div>
            <button onclick="loadVars()">Search</button>
        </div>
        <div id="var-list"></div>
        <div id="var-result" class="result"></div>

        <hr style="margin: 24px 0;">
        <h3>Create Variables</h3>
        <div class="form-row">
            <div class="form-group"><label>Branch (full path)</label>
                <input id="var-branch" placeholder="nimbus/dev/products/product1/resources/api"></div>
        </div>
        <p style="margin: 4px 0; color: #666; font-size: 0.9em;">-- or use dropdowns --</p>
        <div class="form-row">
            <div class="form-group"><label>Organization</label>
                <select id="var-org"><option value="">-- Select --</option></select></div>
            <div class="form-group"><label>Environment</label>
                <select id="var-env"><option value="">-- Select --</option></select></div>
            <div class="form-group"><label>Product</label>
                <select id="var-product"><option value="">-- Select --</option></select></div>
            <div class="form-group"><label>Resource Type</label>
                <select id="var-restype"><option value="">-- Select Product first --</option></select></div>
        </div>
        <div class="form-group" style="margin: 12px 0;">
            <label>Variable Names (comma-separated)</label>
            <input id="var-names" placeholder="client-id, client-secret, token-url">
        </div>
        <button onclick="createVars()">Create Variables</button>
        <div id="var-create-result" class="result"></div>
    </div>

    <!-- Tab 5: My Apps -->
    <div class="tab-panel" id="panel-my-apps">
        <div id="app-list"><p>Click to load apps...</p></div>
        <div id="app-detail"></div>
    </div>
</t:layout>
