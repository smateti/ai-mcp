-- Test database initialization (DB2 syntax)
CREATE TABLE employees (
    id INTEGER GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    department VARCHAR(100),
    salary DECIMAL(10,2) NOT NULL,
    hire_date DATE,
    created_at TIMESTAMP DEFAULT CURRENT TIMESTAMP,
    PRIMARY KEY (id)
);

INSERT INTO employees (first_name, last_name, email, department, salary, hire_date) VALUES
    ('Test', 'Engineer', 'test.engineer@example.com', 'Engineering', 90000.00, '2023-06-01');
INSERT INTO employees (first_name, last_name, email, department, salary, hire_date) VALUES
    ('Test', 'Manager', 'test.manager@example.com', 'Management', 105000.00, '2022-09-15');
INSERT INTO employees (first_name, last_name, email, department, salary, hire_date) VALUES
    ('Test', 'Analyst', 'test.analyst@example.com', 'Analytics', 88000.00, '2023-01-10');
