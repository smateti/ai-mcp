<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<t:layout title="Sample App" activeNav="nav-sample" pageScript="sample-app.js">

    <div class="card">
        <h2>Deploy Sample App with Conjur Init Container</h2>
        <p>Deploy <strong>db2-microprofile-app</strong> to Kubernetes using the JWT authenticator
           init container pattern. The init container fetches secrets from Conjur and writes them
           to a shared volume. No Conjur SDK in the application.</p>

        <div class="sa-config">
            <div class="form-group">
                <label>App Name</label>
                <input type="text" id="sa-appName" value="db2-sample" />
            </div>
            <div class="form-group">
                <label>Namespace</label>
                <input type="text" id="sa-namespace" value="apps" />
            </div>
            <div class="form-group">
                <label>Node Port</label>
                <input type="number" id="sa-nodePort" value="30088" />
            </div>
            <div class="form-group">
                <label>JWT Service ID</label>
                <input type="text" id="sa-jwtServiceId" value="kubernetes" />
            </div>
            <div class="form-group">
                <label>Environment</label>
                <select id="sa-env" onchange="onSaEnvChange()"></select>
            </div>
            <div class="form-group">
                <label>Product</label>
                <select id="sa-product" onchange="onSaProductChange()"></select>
            </div>
            <div class="form-group sa-full">
                <label>Secret Mappings <span style="font-weight:normal;color:#666;">(property=conjur/path, one per line)</span></label>
                <textarea id="sa-mappings" class="sa-mappings" placeholder="db.host-name=nimbus/environments/dev/products/payments/resources/dbs/payments-db/host-name"></textarea>
            </div>
        </div>

        <div class="sa-actions">
            <button onclick="doDeploy()">Deploy</button>
            <button onclick="doStatus()" style="background:#17a2b8;">Check Status</button>
            <button onclick="doLogs('conjur-init')" style="background:#6c757d;">Init Logs</button>
            <button onclick="doLogs(document.getElementById('sa-appName').value)" style="background:#6c757d;">App Logs</button>
            <button onclick="doUndeploy()" class="danger">Undeploy</button>
        </div>

        <div id="sa-result" class="sa-status" style="display:none;"></div>
        <div id="sa-pods"></div>
    </div>

</t:layout>
