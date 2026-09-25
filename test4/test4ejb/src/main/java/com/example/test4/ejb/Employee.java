package com.example.test4.ejb;

import java.io.Serializable;

/**
 * Simple employee POJO. Serializable so it can cross the remote EJB boundary.
 */
public class Employee implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private String firstName;
    private String lastName;
    private String department;
    private double salary;

    public Employee() {
    }

    public Employee(int id, String firstName, String lastName, String department, double salary) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.department = department;
        this.salary = salary;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public double getSalary() { return salary; }
    public void setSalary(double salary) { this.salary = salary; }

    @Override
    public String toString() {
        return String.format("Employee{id=%d, name='%s %s', dept='%s', salary=%.2f}",
                id, firstName, lastName, department, salary);
    }
}
