<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:include page="header.jsp" />

<h1>Registered Applications</h1>

<c:choose>
    <c:when test="${empty applications}">
        <div class="empty-state">
            <p>No applications found. Make sure the Build Trigger Service is running.</p>
        </div>
    </c:when>
    <c:otherwise>
        <div class="card-grid">
            <c:forEach var="app" items="${applications}">
                <div class="card">
                    <h2 class="card-title">${app.getString("name")}</h2>
                    <span class="badge badge-type">${app.getString("type")}</span>

                    <div class="card-section">
                        <h3>Environments</h3>
                        <div class="tag-list">
                            <c:forEach var="env" items="${app.getJsonArray('environments')}">
                                <span class="tag">${fn:replace(env, '"', '')}</span>
                            </c:forEach>
                        </div>
                    </div>

                    <div class="card-section">
                        <h3>Services</h3>
                        <ul class="service-list">
                            <c:forEach var="svc" items="${app.getJsonArray('services')}">
                                <li>${fn:replace(svc, '"', '')}</li>
                            </c:forEach>
                        </ul>
                    </div>

                    <div class="card-actions">
                        <form action="${pageContext.request.contextPath}/trigger" method="post" class="inline-form">
                            <input type="hidden" name="appName" value="${app.getString('name')}" />
                            <select name="environment" class="select-env">
                                <c:forEach var="env" items="${app.getJsonArray('environments')}">
                                    <c:set var="envStr" value="${fn:replace(env, '\"', '')}" />
                                    <option value="${envStr}">${envStr}</option>
                                </c:forEach>
                            </select>
                            <select name="gitlabInstance" class="select-env">
                                <option value="source">Source</option>
                                <option value="target">Target</option>
                            </select>
                            <button type="submit" class="btn btn-primary">Trigger Build</button>
                        </form>
                        <a href="${pageContext.request.contextPath}/history?appName=${app.getString('name')}"
                           class="btn btn-secondary">View History</a>
                    </div>
                </div>
            </c:forEach>
        </div>
    </c:otherwise>
</c:choose>

<jsp:include page="footer.jsp" />
