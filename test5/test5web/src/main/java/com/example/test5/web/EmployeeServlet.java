package com.example.test5.web;

import com.example.test4.ejb.Employee;
import com.example.test4.ejb.EmployeeRemote;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servlet that calls the test4 EmployeeBean remotely via IIOP/Remote EJB.
 * test4 server runs on port 9083 with IIOP endpoint on port 2809.
 */
@WebServlet(urlPatterns = {"/employees"})
public class EmployeeServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(EmployeeServlet.class.getName());

    /**
     * JNDI name for the remote EJB lookup via IIOP CosNaming.
     *
     * Format: corbaname::host:port#ejb/global/ear-name/ejb-module-name/bean-class!remote-interface-fqn
     */
    private static final String JNDI_NAME =
            "corbaname::localhost:2809#ejb/global/test4ear/test4ejb/EmployeeBean!com.example.test4.ejb.EmployeeRemote";

    /**
     * Looks up the remote EmployeeRemote EJB via IIOP.
     * Uses PortableRemoteObject.narrow() via reflection because javax.rmi
     * was removed from the JDK in Java 11, but OpenLiberty provides the
     * class at runtime through the ejbRemote-3.2 feature.
     */
    private EmployeeRemote lookupEmployeeBean() throws Exception {
        Context ctx = new InitialContext();
        Object obj = ctx.lookup(JNDI_NAME);
        // PortableRemoteObject.narrow() is required to convert the CORBA stub
        // into a proxy that implements the remote interface.
        Class<?> proClass = Class.forName("javax.rmi.PortableRemoteObject");
        Method narrowMethod = proClass.getMethod("narrow", Object.class, Class.class);
        return (EmployeeRemote) narrowMethod.invoke(null, obj, EmployeeRemote.class);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("text/html;charset=UTF-8");
        PrintWriter out = resp.getWriter();

        String action = req.getParameter("action");

        out.println("<!DOCTYPE html>");
        out.println("<html><head><title>Employee Manager</title>");
        out.println("<style>");
        out.println("body { font-family: Arial, sans-serif; margin: 40px; }");
        out.println("table { border-collapse: collapse; width: 70%; }");
        out.println("th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }");
        out.println("th { background-color: #336699; color: white; }");
        out.println("form { margin: 20px 0; padding: 15px; background: #f5f5f5; width: 65%; }");
        out.println("form input, form button { padding: 6px; margin: 4px; }");
        out.println(".msg-ok { color: green; } .msg-err { color: red; }");
        out.println(".actions a { margin-right: 8px; color: #336699; }");
        out.println("</style>");
        out.println("</head><body>");
        out.println("<h1>Employee Manager</h1>");
        out.println("<p><em>test5web (port 9082) &rarr; IIOP/Remote EJB &rarr; test4ejb (port 2809) &rarr; H2 JDBC</em></p>");

        try {
            EmployeeRemote employeeService = lookupEmployeeBean();

            // Process actions
            if ("add".equals(action)) {
                handleAdd(req, out, employeeService);
            } else if ("delete".equals(action)) {
                handleDelete(req, out, employeeService);
            }

            // List all employees via remote EJB call
            List<Employee> employees = employeeService.findAllEmployees();

            out.println("<h2>Employees (" + employees.size() + ")</h2>");

            if (employees.isEmpty()) {
                out.println("<p>No employees found.</p>");
            } else {
                out.println("<table>");
                out.println("<tr><th>ID</th><th>First Name</th><th>Last Name</th>" +
                        "<th>Department</th><th>Salary</th><th>Action</th></tr>");
                for (Employee emp : employees) {
                    out.printf("<tr><td>%d</td><td>%s</td><td>%s</td><td>%s</td><td>$%.2f</td>" +
                                    "<td class='actions'><a href='employees?action=delete&id=%d'>Delete</a></td></tr>%n",
                            emp.getId(),
                            emp.getFirstName(),
                            emp.getLastName(),
                            emp.getDepartment() != null ? emp.getDepartment() : "",
                            emp.getSalary(),
                            emp.getId());
                }
                out.println("</table>");
            }
        } catch (NamingException e) {
            LOG.log(Level.SEVERE, "Failed to look up remote EmployeeBean via IIOP", e);
            out.println("<p class='msg-err'><strong>Cannot reach test4 EJB server via IIOP.</strong><br/>");
            out.println("Make sure test4ear Liberty server is running with IIOP endpoint on port 2809.<br/>");
            out.println("JNDI name: " + JNDI_NAME + "<br/>");
            out.println("Error: " + e.getMessage() + "</p>");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to call remote EmployeeBean", e);
            out.println("<p class='msg-err'><strong>Error calling remote EJB.</strong><br/>");
            out.println("Error: " + e.getMessage() + "</p>");
        }

        // Add employee form
        out.println("<h3>Add Employee</h3>");
        out.println("<form method='get' action='employees'>");
        out.println("<input type='hidden' name='action' value='add'/>");
        out.println("First Name: <input type='text' name='firstName' required/><br/>");
        out.println("Last Name: <input type='text' name='lastName' required/><br/>");
        out.println("Department: <input type='text' name='department'/><br/>");
        out.println("Salary: <input type='text' name='salary' value='50000'/><br/>");
        out.println("<button type='submit'>Add Employee</button>");
        out.println("</form>");

        // Quick-add sample data
        out.println("<h3>Quick Add Sample Data</h3>");
        out.println("<div class='actions'>");
        out.println("<a href='employees?action=add&firstName=John&lastName=Smith&department=Engineering&salary=85000'>Add John Smith</a> | ");
        out.println("<a href='employees?action=add&firstName=Jane&lastName=Doe&department=Marketing&salary=72000'>Add Jane Doe</a> | ");
        out.println("<a href='employees?action=add&firstName=Bob&lastName=Wilson&department=Finance&salary=91000'>Add Bob Wilson</a>");
        out.println("</div>");

        out.println("</body></html>");
    }

    private void handleAdd(HttpServletRequest req, PrintWriter out, EmployeeRemote employeeService) {
        String firstName = req.getParameter("firstName");
        String lastName = req.getParameter("lastName");
        String department = req.getParameter("department");
        String salaryStr = req.getParameter("salary");
        if (firstName != null && lastName != null) {
            try {
                double salary = salaryStr != null ? Double.parseDouble(salaryStr) : 0;
                employeeService.createEmployee(firstName, lastName,
                        department != null ? department : "", salary);
                out.println("<p class='msg-ok'>Added " + firstName + " " + lastName + ".</p>");
            } catch (Exception e) {
                out.println("<p class='msg-err'>Error: " + e.getMessage() + "</p>");
            }
        }
    }

    private void handleDelete(HttpServletRequest req, PrintWriter out, EmployeeRemote employeeService) {
        String idStr = req.getParameter("id");
        if (idStr != null) {
            try {
                employeeService.deleteEmployee(Integer.parseInt(idStr));
                out.println("<p class='msg-ok'>Deleted employee #" + idStr + ".</p>");
            } catch (Exception e) {
                out.println("<p class='msg-err'>Error: " + e.getMessage() + "</p>");
            }
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        doGet(req, resp);
    }
}
