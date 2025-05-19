-- Insert sample employees
INSERT INTO employee (first_name, last_name, street, city, zip_code, email, phone)
VALUES ('John', 'Doe', '123 Main St', 'Anytown', '12345', 'john.doe@example.com', '555-1234'),
       ('Jane', 'Smith', '456 Oak Ave', 'Otherville', '67890', 'jane.smith@example.com', '555-5678');

-- Insert skills for John Doe (assuming ID 1)
INSERT INTO employee_skills (employee_id, skill)
VALUES (1, 'Java'),
       (1, 'Spring Boot'),
       (1, 'SQL');

-- Insert skills for Jane Smith (assuming ID 2)
INSERT INTO employee_skills (employee_id, skill)
VALUES (2, 'Python'),
       (2, 'Django'),
       (2, 'AWS');