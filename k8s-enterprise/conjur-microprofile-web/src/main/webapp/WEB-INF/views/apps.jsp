<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<t:layout title="Applications" activeNav="nav-apps">
    <div class="card">
        <h2>Registered Applications</h2>
        <p><em>Applications that have host identities in Conjur and can read database secrets.</em></p>
        <table>
            <tr><th>Application</th><th>Host Identity</th><th>Status</th></tr>
            <c:forEach var="app" items="${apps}">
                <tr>
                    <td><strong><c:out value="${app}"/></strong></td>
                    <td>host/apps/<c:out value="${app}"/></td>
                    <td><span class="badge badge-ok">Registered</span></td>
                </tr>
            </c:forEach>
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
    function registerApp() {
        var name = document.getElementById('newAppName').value;
        if (!name) { showResult('regAppResult', 'Application name is required', true); return; }
        apiCall('POST', '/api/policies/register-app', {name: name})
            .then(function(d) {
                if (d.apiKey) {
                    showResult('regAppResult',
                        'Application registered!\n\nHost: ' + d.hostId +
                        '\nAPI Key: ' + d.apiKey +
                        '\n\nSAVE THIS API KEY NOW - it will not be shown again.');
                } else {
                    showResult('regAppResult', d);
                }
            })
            .catch(function(e) { showResult('regAppResult', e.message, true); });
    }
    </script>
</t:layout>
