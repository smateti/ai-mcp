<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<jsp:include page="header.jsp" />

<div class="page-header">
    <h1>Pipeline #${pipelineId} &mdash; Jobs</h1>
    <c:if test="${not empty appName}">
        <a href="${pageContext.request.contextPath}/history?appName=${appName}"
           class="btn btn-secondary">Back to History</a>
    </c:if>
</div>

<c:choose>
    <c:when test="${empty jobs}">
        <div class="empty-state">
            <p>No jobs found for pipeline #${pipelineId}.</p>
        </div>
    </c:when>
    <c:otherwise>
        <table class="data-table">
            <thead>
                <tr>
                    <th>Job ID</th>
                    <th>Name</th>
                    <th>Stage</th>
                    <th>Status</th>
                    <th>Duration</th>
                    <th>Link</th>
                </tr>
            </thead>
            <tbody>
                <c:forEach var="job" items="${jobs}">
                    <tr>
                        <td>${job.getJsonNumber("id").longValue()}</td>
                        <td><strong>${job.getString("name", "—")}</strong></td>
                        <td>${job.getString("stage", "—")}</td>
                        <td>
                            <span class="status status-${job.getString('status', 'unknown')}">
                                ${job.getString("status", "unknown")}
                            </span>
                        </td>
                        <td>
                            <c:choose>
                                <c:when test="${job.containsKey('duration') && !job.isNull('duration')}">
                                    ${String.format("%.1f", job.getJsonNumber("duration").doubleValue())}s
                                </c:when>
                                <c:otherwise>—</c:otherwise>
                            </c:choose>
                        </td>
                        <td>
                            <c:if test="${not empty job.getString('webUrl', '')}">
                                <a href="${job.getString('webUrl')}" target="_blank"
                                   class="btn btn-small btn-secondary">View in GitLab</a>
                            </c:if>
                        </td>
                    </tr>
                </c:forEach>
            </tbody>
        </table>
    </c:otherwise>
</c:choose>

<jsp:include page="footer.jsp" />
