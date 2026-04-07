<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<t:layout title="DBA Console" activeNav="nav-dba" pageScript="dba.js">
    <h2>DBA Console</h2>
    <p>Manage databases — create resources, set credentials, and view access.</p>

    <div class="tab-bar">
        <button class="tab-btn active" id="btn-databases" onclick="switchTab('databases')">My Databases</button>
        <button class="tab-btn" id="btn-create" onclick="switchTab('create')">Create Database</button>
        <button class="tab-btn" id="btn-credentials" onclick="switchTab('credentials')">Set Credentials</button>
    </div>

    <div class="tab-panel" id="panel-databases" style="display:block">
        <div id="db-list"><p>Loading...</p></div>
        <div id="db-detail"></div>
    </div>

    <div class="tab-panel" id="panel-create">
        <h3>Register New Database</h3>
        <div class="form-row">
            <div class="form-group"><label>Organization</label>
                <select id="db-org"><option value="">-- Select --</option></select></div>
            <div class="form-group"><label>Environment</label>
                <select id="db-env"><option value="">-- Select --</option></select></div>
            <div class="form-group"><label>Product</label>
                <select id="db-product"><option value="">-- Select --</option></select></div>
            <div class="form-group"><label>Database Name</label>
                <input id="db-name" placeholder="orders-db"></div>
        </div>
        <h4>Initial Credentials (optional)</h4>
        <div class="form-row">
            <div class="form-group"><label>Host Name</label>
                <input id="db-init-host-name" placeholder="dbhost.example.com"></div>
            <div class="form-group"><label>Username</label>
                <input id="db-init-username" placeholder="app_user"></div>
            <div class="form-group"><label>Password</label>
                <input id="db-init-password" type="password" placeholder="secret"></div>
        </div>
        <div class="form-row">
            <div class="form-group"><label>Port</label>
                <input id="db-init-port" placeholder="1521"></div>
            <div class="form-group"><label>Database Name</label>
                <input id="db-init-database-name" placeholder="ORCL"></div>
        </div>
        <button onclick="createDatabase()">Create Database</button>
        <div id="create-db-result" class="result"></div>
    </div>

    <div class="tab-panel" id="panel-credentials">
        <h3>Set / Update Credentials</h3>
        <div class="form-row">
            <div class="form-group"><label>Select Database</label>
                <select id="cred-db-select"><option value="">-- Select Database --</option></select>
            </div>
            <button onclick="loadCredentials()">Load Current</button>
        </div>
        <div class="form-row">
            <div class="form-group"><label>Host Name</label><input id="cred-host-name"></div>
            <div class="form-group"><label>Username</label><input id="cred-username"></div>
            <div class="form-group"><label>Password</label><input id="cred-password" type="password"></div>
        </div>
        <div class="form-row">
            <div class="form-group"><label>Port</label><input id="cred-port"></div>
            <div class="form-group"><label>Database Name</label><input id="cred-database-name"></div>
        </div>
        <button onclick="saveCredentials()">Save Credentials</button>
        <div id="cred-result" class="result"></div>
    </div>
</t:layout>
