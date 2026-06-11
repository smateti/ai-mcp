<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${pageTitle} — Build Console</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap" rel="stylesheet">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<body>
    <header class="top-bar">
        <nav>
            <a href="${pageContext.request.contextPath}/" class="logo">Build Console</a>
            <ul class="nav-links">
                <li><a href="${pageContext.request.contextPath}/">Applications</a></li>
            </ul>
        </nav>
    </header>
    <main class="container">
        <c:if test="${not empty flashMessage}">
            <div class="flash flash-success">${flashMessage}</div>
        </c:if>
        <c:if test="${not empty flashError}">
            <div class="flash flash-error">${flashError}</div>
        </c:if>
