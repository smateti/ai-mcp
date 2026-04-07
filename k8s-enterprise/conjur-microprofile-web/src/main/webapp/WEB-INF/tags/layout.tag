<%@ tag description="Page layout" pageEncoding="UTF-8" %>
<%@ attribute name="title" required="true" %>
<%@ attribute name="activeNav" required="true" %>
<%@ attribute name="pageScript" required="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${title} - ConjurAdmin</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<body>
    <h1>Conjur Admin Console</h1>
    <nav>
        <a href="${pageContext.request.contextPath}/" id="nav-home">Dashboard</a>
        <a href="${pageContext.request.contextPath}/dba" id="nav-dba">DBA Console</a>
        <a href="${pageContext.request.contextPath}/migrator" id="nav-migrator">Migrator</a>
        <a href="${pageContext.request.contextPath}/cicd" id="nav-cicd">CI/CD</a>
        <a href="${pageContext.request.contextPath}/explorer" id="nav-explorer">Explorer</a>
        <a href="${pageContext.request.contextPath}/wizard" id="nav-wizard">Setup Wizard</a>
        <a href="${pageContext.request.contextPath}/sample-app" id="nav-sample">Sample App</a>
    </nav>
    <jsp:doBody/>
    <script src="${pageContext.request.contextPath}/js/utils.js"></script>
    <c:if test="${not empty pageScript}">
        <script src="${pageContext.request.contextPath}/js/${pageScript}"></script>
    </c:if>
    <script>
        var nav = document.getElementById('${activeNav}');
        if (nav) nav.className = 'active';
    </script>
</body>
</html>
