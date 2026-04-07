<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<t:layout title="Dashboard" activeNav="nav-home">
    <div class="card">
        <h2>Conjur Vault Status</h2>
        <c:if test="${not empty error}">
            <div class="alert alert-warning">${error}</div>
        </c:if>
        <table>
            <tr><td style="width:150px"><strong>Server</strong></td>
                <td><c:out value="${status.conjurUrl}" default="Unknown"/></td></tr>
            <tr><td><strong>Account</strong></td>
                <td><c:out value="${status.account}" default="Unknown"/></td></tr>
            <tr><td><strong>Status</strong></td>
                <td>
                    <c:choose>
                        <c:when test="${status.reachable}">
                            <span class="badge badge-ok">Connected</span>
                        </c:when>
                        <c:otherwise>
                            <span class="badge badge-err">Unreachable</span>
                        </c:otherwise>
                    </c:choose>
                </td></tr>
        </table>
    </div>
    <div class="summary">
        <div class="stat"><div class="num">${dbCount}</div><div class="label">Databases</div></div>
        <div class="stat"><div class="num">${secretCount}</div><div class="label">Secrets</div></div>
        <div class="stat"><div class="num">${appCount}</div><div class="label">Applications</div></div>
    </div>
</t:layout>
