-- Core platform schema: users, roles, tokens, jobs, applications, resumes

CREATE TABLE users (
    id uuid PRIMARY KEY,
    email varchar(320) NOT NULL UNIQUE,
    password_hash varchar(255) NOT NULL,
    display_name varchar(200),
    enabled boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE roles (
    id uuid PRIMARY KEY,
    name varchar(50) NOT NULL UNIQUE,
    description varchar(200),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE user_roles (
    user_id uuid NOT NULL,
    role_id uuid NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user_id
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role_id
        FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE RESTRICT
);

CREATE TABLE refresh_tokens (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL,
    token_hash varchar(128) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    revoked boolean NOT NULL DEFAULT false,
    revoked_at timestamptz,
    replaced_by_token_id uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_refresh_tokens_user_id
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_refresh_tokens_replaced_by
        FOREIGN KEY (replaced_by_token_id) REFERENCES refresh_tokens (id)
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);

CREATE TABLE jobs (
    id uuid PRIMARY KEY,
    employer_id uuid NOT NULL,
    title varchar(200) NOT NULL,
    description text NOT NULL,
    status varchar(20) NOT NULL,
    moderation_action varchar(20),
    moderation_reason text,
    moderated_at timestamptz,
    moderated_by uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    published_at timestamptz,
    disabled_at timestamptz,
    CONSTRAINT fk_jobs_employer_id
        FOREIGN KEY (employer_id) REFERENCES users (id),
    CONSTRAINT fk_jobs_moderated_by
        FOREIGN KEY (moderated_by) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_jobs_employer_id ON jobs (employer_id);
CREATE INDEX idx_jobs_status ON jobs (status);

CREATE TABLE applications (
    id uuid PRIMARY KEY,
    job_id uuid NOT NULL,
    seeker_id uuid NOT NULL,
    status varchar(20) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    submitted_at timestamptz,
    reviewed_at timestamptz,
    decided_at timestamptz,
    CONSTRAINT fk_applications_job_id
        FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE CASCADE,
    CONSTRAINT fk_applications_seeker_id
        FOREIGN KEY (seeker_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_applications_job_seeker UNIQUE (job_id, seeker_id)
);

CREATE INDEX idx_applications_job_id ON applications (job_id);
CREATE INDEX idx_applications_seeker_id ON applications (seeker_id);

CREATE TABLE resumes (
    id uuid PRIMARY KEY,
    seeker_id uuid NOT NULL,
    original_filename varchar(255) NOT NULL,
    content_type varchar(100) NOT NULL,
    file_size_bytes bigint NOT NULL,
    storage_location varchar(500),
    processing_status varchar(20) NOT NULL,
    failure_code varchar(50),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    parsed_at timestamptz,
    CONSTRAINT fk_resumes_seeker_id
        FOREIGN KEY (seeker_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_resumes_seeker_id ON resumes (seeker_id);
