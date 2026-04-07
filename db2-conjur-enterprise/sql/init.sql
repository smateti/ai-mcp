-- Application database initialization (DB2 syntax)
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
    ('John', 'Smith', 'john.smith@example.com', 'Engineering', 95000.00, '2023-01-15');
INSERT INTO employees (first_name, last_name, email, department, salary, hire_date) VALUES
    ('Jane', 'Doe', 'jane.doe@example.com', 'Marketing', 85000.00, '2023-03-20');
INSERT INTO employees (first_name, last_name, email, department, salary, hire_date) VALUES
    ('Bob', 'Johnson', 'bob.johnson@example.com', 'Finance', 92000.00, '2022-11-01');
