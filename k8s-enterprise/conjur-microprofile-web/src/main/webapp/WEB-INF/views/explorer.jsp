<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<t:layout title="Explorer" activeNav="nav-explorer" pageScript="explorer.js">
    <div class="explorer-tabs">
        <button class="tab active" data-tab="policies" onclick="switchTab('policies')">Policies</button>
        <button class="tab" data-tab="hosts" onclick="switchTab('hosts')">Hosts</button>
        <button class="tab" data-tab="variables" onclick="switchTab('variables')">Variables</button>
        <button class="tab" data-tab="groups" onclick="switchTab('groups')">Groups & Layers</button>
        <button class="tab" data-tab="webservices" onclick="switchTab('webservices')">Webservices</button>
        <button class="tab" data-tab="grants" onclick="switchTab('grants')">Grants</button>
    </div>

    <!-- Policies tab -->
    <div class="tab-content active" id="tab-policies">
        <div class="card">
            <h2>Policies</h2>
            <div class="search-bar">
                <input type="text" id="search-policies" placeholder="Search policies..."
                       onkeyup="searchResources('policies','policy')" />
            </div>
            <div id="table-policies"><p>Loading...</p></div>
        </div>
    </div>

    <!-- Hosts tab -->
    <div class="tab-content" id="tab-hosts">
        <div class="card">
            <h2>Hosts</h2>
            <div class="search-bar">
                <input type="text" id="search-hosts" placeholder="Search hosts..."
                       onkeyup="searchResources('hosts','host')" />
            </div>
            <div id="table-hosts"><p>Click tab to load...</p></div>
        </div>
    </div>

    <!-- Variables tab -->
    <div class="tab-content" id="tab-variables">
        <div class="card">
            <h2>Variables</h2>
            <div class="search-bar">
                <input type="text" id="search-variables" placeholder="Search variables..."
                       onkeyup="searchResources('variables','variable')" />
            </div>
            <div id="table-variables"><p>Click tab to load...</p></div>
        </div>
    </div>

    <!-- Groups & Layers tab -->
    <div class="tab-content" id="tab-groups">
        <div class="card">
            <h2>Groups & Layers</h2>
            <p>Click <strong>Members</strong> to view role membership details.</p>
            <div id="table-groups"><p>Click tab to load...</p></div>
            <div class="role-detail" id="role-detail">
                <h3>Role: <span id="role-detail-name" style="font-family:monospace;"></span></h3>
                <div id="role-detail-content"></div>
            </div>
        </div>
    </div>

    <!-- Webservices tab -->
    <div class="tab-content" id="tab-webservices">
        <div class="card">
            <h2>Webservices</h2>
            <div class="search-bar">
                <input type="text" id="search-webservices" placeholder="Search webservices..."
                       onkeyup="searchResources('webservices','webservice')" />
            </div>
            <div id="table-webservices"><p>Click tab to load...</p></div>
        </div>
    </div>

    <!-- Grants tab -->
    <div class="tab-content" id="tab-grants">
        <div class="card">
            <h2>Access Grants & Permissions</h2>
            <p>Permissions extracted from Conjur resource definitions.</p>
            <div id="table-grants"><p>Click tab to load...</p></div>
        </div>
    </div>
</t:layout>
