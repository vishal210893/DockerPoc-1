-- Drop tables if they exist (useful for create-drop)
DROP TABLE IF EXISTS employee_skills;
DROP TABLE IF EXISTS employee;

-- Create Employee table
CREATE TABLE employee
(
    id BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(255) NOT NULL,
    last_name  VARCHAR(255) NOT NULL,
    -- Embedded Address fields
    street     VARCHAR(255) NOT NULL,
    city       VARCHAR(255) NOT NULL,
    zip_code   VARCHAR(255) NOT NULL,
    -- Embedded ContactInfo fields
    email      VARCHAR(255) NOT NULL,
    phone      VARCHAR(255) NOT NULL
);

-- Create join table for Employee skills (@ElementCollection)
CREATE TABLE employee_skills
(
    employee_id BIGINT       NOT NULL,
    skill       VARCHAR(255) NOT NULL,
    FOREIGN KEY (employee_id) REFERENCES employee (id)
);