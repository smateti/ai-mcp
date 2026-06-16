<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<jsp:include page="header.jsp" />

<div class="page-header">
    <h1>Build History &mdash; ${appName}</h1>
    <a href="${pageContext.request.contextPath}/" class="btn btn-secondary">Back to Applications</a>
</div>

<!-- Trigger form -->
<div class="trigger-panel">
    <form action="${pageContext.request.contextPath}/trigger" method="post" class="inline-form">
        <input type="hidden" name="appName" value="${appName}" />
        <label for="env-select">Environment:</label>
        <select name="environment" id="env-select" class="select-env">
            <option value="dev" ${environment == 'dev' ? 'selected' : ''}>dev</option>
            <option value="staging" ${environment == 'staging' ? 'selected' : ''}>staging</option>
            <option value="prod" ${environment == 'prod' ? 'selected' : ''}>prod</option>
        </select>
        <label for="instance-select">Instance:</label>
        <select name="gitlabInstance" id="instance-select" class="select-env">
            <option value="source">Source</option>
            <option value="target">Target</option>
        </select>
        <button type="submit" class="btn btn-primary">Trigger New Build</button>
    </form>
</div>

<!-- Build history table -->
<c:choose>
    <c:when test="${empty builds}">
        <div class="empty-state">
            <p>No builds found for ${appName}.</p>
        </div>
    </c:when>
    <c:otherwise>
        <table class="data-table">
            <thead>
                <tr>
                    <th>Pipeline ID</th>
                    <th>Instance</th>
                    <th>Environment</th>
                    <th>Status</th>
                    <th>Triggered At</th>
                    <th>Duration</th>
                    <th>Actions</th>
                </tr>
            </thead>
            <tbody>
                <c:forEach var="build" items="${builds}">
                    <tr>
                        <td>
                            <a href="${pageContext.request.contextPath}/jobs?pipelineId=${build.getJsonNumber('pipelineId').longValue()}&appName=${appName}">
                                #${build.getJsonNumber("pipelineId").longValue()}
                            </a>
                        </td>
                        <td><span class="badge badge-type">${build.getString("gitlabInstance", "source")}</span></td>
                        <td><span class="tag">${build.getString("environment", "—")}</span></td>
                        <td>
                            <span class="status status-${build.getString('status', 'unknown')}">
                                ${build.getString("status", "unknown")}
                            </span>
                        </td>
                        <td>${build.getString("triggeredAt", "—")}</td>
                        <td>
                            <c:choose>
                                <c:when test="${build.containsKey('duration') && !build.isNull('duration')}">
                                    ${String.format("%.1f", build.getJsonNumber("duration").doubleValue())}s
                                </c:when>
                                <c:otherwise>—</c:otherwise>
                            </c:choose>
                        </td>
                        <td>
                            <a href="${pageContext.request.contextPath}/jobs?pipelineId=${build.getJsonNumber('pipelineId').longValue()}&appName=${appName}"
                               class="btn btn-small">Jobs</a>
                            <c:if test="${not empty build.getString('webUrl', '')}">
                                <a href="${build.getString('webUrl')}" target="_blank"
                                   class="btn btn-small btn-secondary">GitLab</a>
                            </c:if>
                        </td>
                    </tr>
                </c:forEach>
            </tbody>
        </table>
    </c:otherwise>
</c:choose>

<jsp:include page="footer.jsp" />
