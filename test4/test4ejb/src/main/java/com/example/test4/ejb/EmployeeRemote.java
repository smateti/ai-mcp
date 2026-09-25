package com.example.test4.ejb;

import javax.ejb.Remote;
import java.util.List;

@Remote
public interface EmployeeRemote {

    void createEmployee(String firstName, String lastName, String department, double salary);

    Employee findEmployee(int id);

    List<Employee> findAllEmployees();

    void updateEmployee(int id, String firstName, String lastName, String department, double salary);

    void deleteEmployee(int id);
}
