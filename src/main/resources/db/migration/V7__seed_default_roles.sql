-- Seed the roles required by registration and authorization flows.

INSERT INTO roles (id, name, description)
SELECT '11111111-1111-1111-1111-111111111111', 'ADMIN', 'System administrator'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ADMIN');

INSERT INTO roles (id, name, description)
SELECT '22222222-2222-2222-2222-222222222222', 'EMPLOYER', 'Employer account'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'EMPLOYER');

INSERT INTO roles (id, name, description)
SELECT '33333333-3333-3333-3333-333333333333', 'JOB_SEEKER', 'Job seeker account'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'JOB_SEEKER');