<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<t:layout title="Databases" activeNav="nav-db" pageScript="dashboard.js">
    <div class="card">
        <h2>Database Credentials</h2>
        <p><em>Click password to reveal. Convention: dbs/{name}-uid and dbs/{name}-pwd in Conjur.</em></p>
        <table>
            <tr><th>Database</th><th>Username</th><th>Password</th><th>Status</th></tr>
            <c:forEach var="db" items="${databases}">
                <tr>
                    <td><strong><c:out value="${db.name}"/></strong></td>
                    <c:choose>
                        <c:when test="${db.status == 'active'}">
                            <td><c:out value="${db.username}"/></td>
                            <td><code class="masked"><c:out value="${db.password}"/></code></td>
                            <td><span class="badge badge-ok">Active</span></td>
                        </c:when>
                        <c:otherwise>
                            <td colspan="3"><span class="badge badge-err">Error: <c:out value="${db.error}"/></span></td>
                        </c:otherwise>
                    </c:choose>
                </tr>
            </c:forEach>
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
        <p>Creates credential variables in Conjur vault (dbs/{name}-uid and dbs/{name}-pwd).</p>
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
    function updateDbCreds() {
        var db = document.getElementById('updDbName').value;
        var uid = document.getElementById('updDbUid').value;
        var pwd = document.getElementById('updDbPwd').value;
        if (!db || !uid || !pwd) { showResult('updResult', 'All fields are required', true); return; }
        apiCall('PUT', '/api/secrets/dbs/' + db, {username: uid, password: pwd})
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
</t:layout>
