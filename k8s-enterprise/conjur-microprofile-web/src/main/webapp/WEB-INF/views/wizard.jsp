<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<t:layout title="Setup Wizard" activeNav="nav-wizard" pageScript="wizard.js">

    <div class="wizard-progress" id="wizProgress"></div>

    <!-- Step 0: Reference Data -->
    <div class="card step-panel" id="step-0">
        <h2>Step 1: Reference Data</h2>
        <p>Manage the catalogs used by the wizard dropdowns: organizations, environments, products, app types, and resource types.</p>
        <div id="refdata-content">

            <div class="ref-section">
                <h4>Organizations</h4>
                <div id="ref-organizations"><p>Loading...</p></div>
                <div class="ref-add-row">
                    <input id="ref-add-organizations-name" placeholder="Name" style="min-width:180px;" />
                    <input id="ref-add-organizations-desc" placeholder="Description (optional)" style="min-width:260px;" />
                    <button onclick="addRefItem('organizations')">Add</button>
                </div>
                <div id="ref-add-organizations-result" class="result"></div>
            </div>

            <div class="ref-section">
                <h4>Environments</h4>
                <div id="ref-environments"><p>Loading...</p></div>
                <div class="ref-add-row">
                    <input id="ref-add-environments-name" placeholder="Name" style="min-width:180px;" />
                    <input id="ref-add-environments-desc" placeholder="Description (optional)" style="min-width:260px;" />
                    <button onclick="addRefItem('environments')">Add</button>
                </div>
                <div id="ref-add-environments-result" class="result"></div>
            </div>

            <div class="ref-section">
                <h4>Products</h4>
                <div id="ref-products"><p>Loading...</p></div>
                <div class="ref-add-row">
                    <input id="ref-add-products-name" placeholder="Name" style="min-width:180px;" />
                    <input id="ref-add-products-desc" placeholder="Description (optional)" style="min-width:260px;" />
                    <button onclick="addRefItem('products')">Add</button>
                </div>
                <div id="ref-add-products-result" class="result"></div>
            </div>

            <div class="ref-section">
                <h4>App Types</h4>
                <div id="ref-apptypes"><p>Loading...</p></div>
                <div class="ref-add-row">
                    <input id="ref-add-apptypes-name" placeholder="Name" style="min-width:180px;" />
                    <input id="ref-add-apptypes-desc" placeholder="Description (optional)" style="min-width:260px;" />
                    <button onclick="addRefItem('apptypes')">Add</button>
                </div>
                <div id="ref-add-apptypes-result" class="result"></div>
            </div>

            <div class="ref-section">
                <h4>Resource Types</h4>
                <div id="ref-resourcetypes"><p>Loading...</p></div>
                <div class="ref-add-row">
                    <input id="ref-add-resourcetypes-name" placeholder="Name" style="min-width:140px;" />
                    <input id="ref-add-resourcetypes-desc" placeholder="Description" style="min-width:200px;" />
                    <input id="ref-add-resourcetypes-fields" placeholder="Fields (comma-separated)" style="min-width:300px;" />
                    <button onclick="addRefItem('resourcetypes')">Add</button>
                </div>
                <div id="ref-add-resourcetypes-result" class="result"></div>
            </div>

        </div>
    </div>

    <!-- Step 1: Connection Check -->
    <div class="card step-panel" id="step-1">
        <h2>Step 2: Connection Check</h2>
        <p>Verify connectivity to the Conjur REST API backend. Also discovers existing resources.</p>
        <button onclick="executeStep(1)">Check Connection</button>
        <div class="step-result" id="res-1"></div>
    </div>

    <!-- Step 2: Root Policy -->
    <div class="card step-panel" id="step-2">
        <h2>Step 3: Root Policy</h2>
        <p>Creates the top-level root policy. Select an existing organization or create a new one.</p>
        <div id="w-existingOrgs" style="display:none;">
            <div class="existing-section">
                <h4>Existing Organizations in Conjur</h4>
                <div id="w-orgBadges"></div>
                <p style="font-size:0.85em;color:#666;margin-top:8px;">Click an organization to use it, or create a new one below.</p>
            </div>
        </div>
        <div class="form-row">
            <div class="form-group">
                <label>Organization Name</label>
                <input type="text" id="w-orgName" value="nimbus" />
            </div>
        </div>
        <button onclick="executeStep(2)">Create Root Policy</button>
        <div class="step-result" id="res-2"></div>
    </div>

    <!-- Step 3: Authentication -->
    <div class="card step-panel" id="step-3">
        <h2>Step 4: Authentication</h2>
        <p>Select the authentication method for your applications.</p>
        <div class="form-row">
            <div class="form-group">
                <label>Authentication Method</label>
                <select id="w-authMethod" onchange="onAuthMethodChange()">
                    <option value="authn">authn (API Key)</option>
                    <option value="kubernetes" selected>authn-jwt/kubernetes</option>
                    <option value="openshift">authn-jwt/openshift</option>
                </select>
            </div>
        </div>
        <div id="w-authnInfo" style="display:none;">
            <div class="info-box" style="background:#e8f5e9;padding:12px 16px;border-radius:6px;border:1px solid #a5d6a7;margin:8px 0;">
                <strong>API Key Authentication (authn)</strong><br/>
                Applications authenticate using their Conjur host API key.<br/>
                No JWT authenticator setup needed &mdash; hosts receive an API key upon registration.
            </div>
        </div>
        <div id="w-jwtConfig">
            <div class="form-row">
                <div class="form-group">
                    <label>JWKS URI</label>
                    <input type="text" id="w-jwksUri" value="https://kubernetes.default.svc/openid/v1/jwks" style="min-width:400px;" />
                </div>
            </div>
            <div class="form-row">
                <div class="form-group">
                    <label>Token App Property</label>
                    <input type="text" id="w-tokenAppProp" value="sub" />
                </div>
                <div class="form-group">
                    <label>Identity Path</label>
                    <input type="text" id="w-identityPath" value="apps" placeholder="Conjur policy path prefix for host identity" />
                </div>
            </div>
            <div class="form-row">
                <div class="form-group">
                    <label>Issuer</label>
                    <input type="text" id="w-issuer" value="https://kubernetes.default.svc.cluster.local" />
                </div>
                <div class="form-group">
                    <label>Audience</label>
                    <input type="text" id="w-audience" value="https://kubernetes.default.svc.cluster.local" />
                </div>
            </div>
        </div>
        <button onclick="executeStep(3)" id="w-authBtn">Setup JWT Authenticator</button>
        <div class="step-result" id="res-3"></div>
    </div>

    <!-- Step 4: Environments -->
    <div class="card step-panel" id="step-4">
        <h2>Step 5: Environments</h2>
        <div class="ctx-bar" id="ctx-4"></div>
        <div class="form-group">
            <label>Select Environments</label>
            <div class="cb-group" id="w-env-group">
                <p style="color:#666;font-size:0.85em;">Loading from reference data...</p>
            </div>
        </div>
        <button onclick="executeStep(4)">Create Environments</button>
        <div class="step-result" id="res-4"></div>
    </div>

    <!-- Step 5: Product Setup -->
    <div class="card step-panel" id="step-5">
        <h2>Step 6: Product Setup</h2>
        <div class="ctx-bar" id="ctx-5"></div>
        <div class="form-row">
            <div class="form-group">
                <label>Environment</label>
                <select id="w-prodEnv" onchange="onWizProdEnvChange()"></select>
            </div>
        </div>
        <div id="w-existingProducts" style="display:none;">
            <div class="existing-section">
                <h4>Existing Products</h4>
                <div id="w-existingProductsList"></div>
            </div>
        </div>
        <h3 style="margin-top:12px;">Create New Product</h3>
        <div class="form-row">
            <div class="form-group">
                <label>Product Name</label>
                <input type="text" id="w-productName" placeholder="e.g. product1" />
            </div>
        </div>
        <div class="form-group">
            <label>App Types</label>
            <div class="cb-group" id="w-appType-group">
                <p style="color:#666;font-size:0.85em;">Loading from reference data...</p>
            </div>
        </div>
        <div class="form-group" style="margin-top:8px;">
            <label>Resource Types</label>
            <div class="cb-group" id="w-resType-group">
                <p style="color:#666;font-size:0.85em;">Loading from reference data...</p>
            </div>
        </div>
        <button onclick="executeStep(5)">Create Product</button>
        <div class="step-result" id="res-5"></div>
    </div>

    <!-- Step 6: Resources -->
    <div class="card step-panel" id="step-6">
        <h2>Step 7: Register Resources</h2>
        <div class="ctx-bar" id="ctx-6"></div>
        <div class="form-row">
            <div class="form-group">
                <label>Environment</label>
                <select id="w-resEnv" onchange="onWizResEnvChange()"></select>
            </div>
            <div class="form-group">
                <label>Product</label>
                <select id="w-resProduct" onchange="onWizResProductChange()"></select>
            </div>
        </div>
        <div id="w-existingResources" style="display:none;">
            <div class="existing-section">
                <h4>Existing Resources</h4>
                <div id="w-existingResourcesList"></div>
            </div>
        </div>

        <div id="w-dbSection">
            <h3 style="margin-top:16px;">Add New Databases</h3>
            <div id="w-dbList"></div>
            <button onclick="addDbRow()" style="background:#6c757d;height:30px;font-size:0.85em;">+ Add Database</button>
        </div>

        <div id="w-kafkaSection">
            <h3 style="margin-top:16px;">Add New Kafka Clusters</h3>
            <div id="w-kafkaList"></div>
            <button onclick="addKafkaRow()" style="background:#6c757d;height:30px;font-size:0.85em;">+ Add Kafka</button>
        </div>

        <div id="w-infraSection">
            <h3 style="margin-top:16px;">Add New Infrastructure</h3>
            <div id="w-infraList"></div>
            <button onclick="addInfraRow()" style="background:#6c757d;height:30px;font-size:0.85em;">+ Add Infrastructure</button>
        </div>

        <div style="margin-top:16px;">
            <button onclick="executeStep(6)">Register All Resources</button>
        </div>
        <div class="step-result" id="res-6"></div>
    </div>

    <!-- Step 7: App Hosts -->
    <div class="card step-panel" id="step-7">
        <h2>Step 8: Register Application Hosts</h2>
        <div class="ctx-bar" id="ctx-7"></div>
        <div class="form-row">
            <div class="form-group">
                <label>Environment</label>
                <select id="w-hostEnv" onchange="onWizHostEnvChange()"></select>
            </div>
            <div class="form-group">
                <label>Product</label>
                <select id="w-hostProduct" onchange="onWizHostProductChange()"></select>
            </div>
        </div>
        <div id="w-existingHosts" style="display:none;">
            <div class="existing-section">
                <h4>Existing Hosts</h4>
                <div id="w-existingHostsList"></div>
            </div>
        </div>
        <h3 style="margin-top:12px;">Add New Hosts</h3>
        <div id="w-hostList"></div>
        <button onclick="addHostRow()" style="background:#6c757d;height:30px;font-size:0.85em;">+ Add Host</button>
        <div style="margin-top:16px;">
            <button onclick="executeStep(7)">Register All Hosts</button>
        </div>
        <div class="step-result" id="res-7"></div>
    </div>

    <!-- Step 8: Access & Secrets -->
    <div class="card step-panel" id="step-8">
        <h2>Step 9: Access Grants & Secrets</h2>
        <div class="ctx-bar" id="ctx-8"></div>
        <div class="form-row">
            <div class="form-group">
                <label>Environment</label>
                <select id="w-grantEnv" onchange="onWizGrantEnvChange()"></select>
            </div>
            <div class="form-group">
                <label>Product</label>
                <select id="w-grantProduct" onchange="onWizGrantProductChange()"></select>
            </div>
            <div class="form-group">
                <label>App Type</label>
                <select id="w-grantAppType" onchange="onWizGrantAppTypeChange()"></select>
            </div>
        </div>
        <div id="w-grantExistingHosts" style="display:none;">
            <div class="existing-section">
                <h4>Hosts (filtered by App Type)</h4>
                <div id="w-grantHostsList"></div>
            </div>
        </div>
        <div id="w-grantExistingResources" style="display:none;">
            <div class="existing-section">
                <h4>Resources in Product</h4>
                <div id="w-grantResourcesList"></div>
            </div>
        </div>

        <h3 style="margin-top:12px;">Access Grants</h3>
        <div id="w-grantList"></div>
        <button onclick="addGrantRow()" style="background:#6c757d;height:30px;font-size:0.85em;">+ Add Grant</button>

        <div style="margin-top:16px;">
            <button onclick="executeStep(8)">Apply Grants</button>
        </div>
        <div class="step-result" id="res-8"></div>
    </div>

    <!-- Step 9: Summary -->
    <div class="card step-panel" id="step-9">
        <h2>Step 10: Summary & Verification</h2>
        <p>Verify all created resources in Conjur.</p>
        <button onclick="executeStep(9)">Verify Setup</button>
        <div class="step-result" id="res-9"></div>
        <div id="summaryStats" style="margin-top:16px;"></div>
    </div>

    <div class="step-nav" id="stepNav">
        <button class="secondary" onclick="prevStep()" id="btnPrev">Previous</button>
        <button onclick="nextStep()" id="btnNext">Next Step</button>
    </div>

</t:layout>
