<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<t:layout title="All Secrets" activeNav="nav-secrets">
    <div class="card">
        <h2>Conjur Secrets</h2>
        <p><em>Click value to reveal. All known variables in Conjur vault.</em></p>
        <table>
            <tr><th>Variable ID</th><th>Value</th><th>Status</th></tr>
            <c:forEach var="entry" items="${secrets}">
                <tr>
                    <td><code><c:out value="${entry.variableId}"/></code></td>
                    <td><code class="masked"><c:out value="${entry.value}" default=""/></code></td>
                    <td>
                        <c:choose>
                            <c:when test="${entry.found}">
                                <span class="badge badge-ok">Set</span>
                            </c:when>
                            <c:otherwise>
                                <span class="badge badge-warn">Not Set</span>
                            </c:otherwise>
                        </c:choose>
                    </td>
                </tr>
            </c:forEach>
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
    function updateSecret() {
        var varId = document.getElementById('secVarId').value;
        var val = document.getElementById('secValue').value;
        if (!varId || !val) { showResult('secResult', 'Both fields are required', true); return; }
        apiCall('PUT', '/api/secrets/' + varId.replace(/\//g, '.'), {value: val})
            .then(function(d) { showResult('secResult', d); setTimeout(function(){location.reload();}, 1500); })
            .catch(function(e) { showResult('secResult', e.message, true); });
    }
    </script>
</t:layout>
