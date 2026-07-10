CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE sf_organizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    slug TEXT NOT NULL UNIQUE,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    logo_url TEXT,
    primary_color TEXT NOT NULL DEFAULT '#1D4ED8',
    secondary_color TEXT NOT NULL DEFAULT '#0F766E',
    accent_color TEXT NOT NULL DEFAULT '#F59E0B',
    certificate_template JSONB NOT NULL DEFAULT '{}'::jsonb,
    settings JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sf_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES sf_organizations(id) ON DELETE CASCADE,
    email TEXT NOT NULL,
    full_name TEXT NOT NULL,
    password_hash TEXT,
    role TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    locale TEXT NOT NULL DEFAULT 'en',
    timezone TEXT NOT NULL DEFAULT 'UTC',
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, email)
);

CREATE TABLE sf_departments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES sf_organizations(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    code TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, name)
);

CREATE TABLE sf_batches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES sf_organizations(id) ON DELETE CASCADE,
    department_id UUID REFERENCES sf_departments(id) ON DELETE SET NULL,
    name TEXT NOT NULL,
    code TEXT,
    starts_on DATE,
    ends_on DATE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, name)
);

CREATE TABLE sf_candidate_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES sf_organizations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES sf_users(id) ON DELETE CASCADE,
    department_id UUID REFERENCES sf_departments(id) ON DELETE SET NULL,
    batch_id UUID REFERENCES sf_batches(id) ON DELETE SET NULL,
    external_ref TEXT,
    phone TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, user_id)
);

CREATE TABLE sf_subjects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID REFERENCES sf_organizations(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    slug TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, slug)
);

CREATE TABLE sf_questions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES sf_organizations(id) ON DELETE CASCADE,
    subject_id UUID REFERENCES sf_subjects(id) ON DELETE SET NULL,
    current_version_id UUID,
    code TEXT NOT NULL,
    type TEXT NOT NULL,
    difficulty TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'DRAFT',
    tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    expected_time_seconds INTEGER NOT NULL DEFAULT 60,
    default_marks NUMERIC(8,2) NOT NULL DEFAULT 1,
    negative_marks NUMERIC(8,2) NOT NULL DEFAULT 0,
    usage_count BIGINT NOT NULL DEFAULT 0,
    created_by UUID REFERENCES sf_users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, code)
);

CREATE TABLE sf_question_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id UUID NOT NULL REFERENCES sf_questions(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    title TEXT NOT NULL,
    prompt TEXT NOT NULL,
    options JSONB NOT NULL DEFAULT '[]'::jsonb,
    correct_answer JSONB NOT NULL DEFAULT '{}'::jsonb,
    explanation TEXT,
    reference_links JSONB NOT NULL DEFAULT '[]'::jsonb,
    scoring JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by UUID REFERENCES sf_users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (question_id, version_number)
);

ALTER TABLE sf_questions
    ADD CONSTRAINT fk_sf_questions_current_version
    FOREIGN KEY (current_version_id) REFERENCES sf_question_versions(id) ON DELETE SET NULL;

CREATE TABLE sf_question_approvals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id UUID NOT NULL REFERENCES sf_questions(id) ON DELETE CASCADE,
    version_id UUID NOT NULL REFERENCES sf_question_versions(id) ON DELETE CASCADE,
    status TEXT NOT NULL,
    submitted_by UUID REFERENCES sf_users(id) ON DELETE SET NULL,
    reviewed_by UUID REFERENCES sf_users(id) ON DELETE SET NULL,
    review_notes TEXT,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_at TIMESTAMPTZ
);

CREATE TABLE sf_assessment_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES sf_organizations(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    description TEXT,
    blueprint JSONB NOT NULL DEFAULT '{}'::jsonb,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID REFERENCES sf_users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, name)
);

CREATE TABLE sf_assessments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES sf_organizations(id) ON DELETE CASCADE,
    template_id UUID REFERENCES sf_assessment_templates(id) ON DELETE SET NULL,
    title TEXT NOT NULL,
    description TEXT,
    status TEXT NOT NULL DEFAULT 'DRAFT',
    duration_minutes INTEGER NOT NULL DEFAULT 60,
    passing_percentage NUMERIC(5,2) NOT NULL DEFAULT 60,
    start_at TIMESTAMPTZ,
    end_at TIMESTAMPTZ,
    grace_period_minutes INTEGER NOT NULL DEFAULT 0,
    candidate_limit INTEGER,
    shuffle_questions BOOLEAN NOT NULL DEFAULT TRUE,
    show_result_immediately BOOLEAN NOT NULL DEFAULT FALSE,
    created_by UUID REFERENCES sf_users(id) ON DELETE SET NULL,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sf_assessment_sections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assessment_id UUID NOT NULL REFERENCES sf_assessments(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    instructions TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    duration_minutes INTEGER,
    total_marks NUMERIC(8,2) NOT NULL DEFAULT 0,
    rules JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sf_assessment_questions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assessment_id UUID NOT NULL REFERENCES sf_assessments(id) ON DELETE CASCADE,
    section_id UUID REFERENCES sf_assessment_sections(id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES sf_questions(id) ON DELETE RESTRICT,
    question_version_id UUID NOT NULL REFERENCES sf_question_versions(id) ON DELETE RESTRICT,
    marks NUMERIC(8,2) NOT NULL DEFAULT 1,
    negative_marks NUMERIC(8,2) NOT NULL DEFAULT 0,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (assessment_id, question_id)
);

CREATE TABLE sf_assessment_invitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES sf_organizations(id) ON DELETE CASCADE,
    assessment_id UUID NOT NULL REFERENCES sf_assessments(id) ON DELETE CASCADE,
    candidate_user_id UUID NOT NULL REFERENCES sf_users(id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL UNIQUE,
    status TEXT NOT NULL DEFAULT 'PENDING',
    email_status TEXT NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    opened_at TIMESTAMPTZ,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (assessment_id, candidate_user_id)
);

CREATE TABLE sf_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES sf_organizations(id) ON DELETE CASCADE,
    assessment_id UUID NOT NULL REFERENCES sf_assessments(id) ON DELETE CASCADE,
    invitation_id UUID REFERENCES sf_assessment_invitations(id) ON DELETE SET NULL,
    candidate_user_id UUID NOT NULL REFERENCES sf_users(id) ON DELETE CASCADE,
    status TEXT NOT NULL DEFAULT 'NOT_STARTED',
    started_at TIMESTAMPTZ,
    submitted_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    score NUMERIC(8,2),
    percentage NUMERIC(5,2),
    passed BOOLEAN,
    suspicious_score NUMERIC(5,2) NOT NULL DEFAULT 0,
    ip_address TEXT,
    user_agent TEXT,
    device_fingerprint TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (assessment_id, candidate_user_id)
);

CREATE TABLE sf_attempt_answers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    attempt_id UUID NOT NULL REFERENCES sf_attempts(id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES sf_questions(id) ON DELETE RESTRICT,
    question_version_id UUID NOT NULL REFERENCES sf_question_versions(id) ON DELETE RESTRICT,
    answer JSONB NOT NULL DEFAULT '{}'::jsonb,
    status TEXT NOT NULL DEFAULT 'UNANSWERED',
    awarded_marks NUMERIC(8,2),
    evaluator_notes TEXT,
    flagged_for_review BOOLEAN NOT NULL DEFAULT FALSE,
    auto_saved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (attempt_id, question_id)
);

CREATE TABLE sf_attempt_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    attempt_id UUID NOT NULL REFERENCES sf_attempts(id) ON DELETE CASCADE,
    event_type TEXT NOT NULL,
    severity TEXT NOT NULL DEFAULT 'INFO',
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sf_score_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    attempt_id UUID NOT NULL REFERENCES sf_attempts(id) ON DELETE CASCADE,
    total_marks NUMERIC(8,2) NOT NULL,
    awarded_marks NUMERIC(8,2) NOT NULL,
    percentage NUMERIC(5,2) NOT NULL,
    passed BOOLEAN NOT NULL,
    topic_breakdown JSONB NOT NULL DEFAULT '{}'::jsonb,
    difficulty_breakdown JSONB NOT NULL DEFAULT '{}'::jsonb,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (attempt_id)
);

CREATE TABLE sf_certificates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES sf_organizations(id) ON DELETE CASCADE,
    attempt_id UUID NOT NULL REFERENCES sf_attempts(id) ON DELETE CASCADE,
    certificate_code TEXT NOT NULL UNIQUE,
    certificate_url TEXT,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sf_email_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES sf_organizations(id) ON DELETE CASCADE,
    template_type TEXT NOT NULL,
    subject TEXT NOT NULL,
    body_html TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, template_type)
);

CREATE TABLE sf_notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES sf_organizations(id) ON DELETE CASCADE,
    user_id UUID REFERENCES sf_users(id) ON DELETE CASCADE,
    type TEXT NOT NULL,
    title TEXT NOT NULL,
    body TEXT,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sf_webhook_endpoints (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES sf_organizations(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    url TEXT NOT NULL,
    secret_hash TEXT NOT NULL,
    events JSONB NOT NULL DEFAULT '[]'::jsonb,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sf_webhook_deliveries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint_id UUID NOT NULL REFERENCES sf_webhook_endpoints(id) ON DELETE CASCADE,
    event_type TEXT NOT NULL,
    payload JSONB NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    next_attempt_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sf_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID REFERENCES sf_organizations(id) ON DELETE CASCADE,
    actor_user_id UUID REFERENCES sf_users(id) ON DELETE SET NULL,
    action TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id UUID,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    ip_address TEXT,
    user_agent TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sf_operations_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID REFERENCES sf_organizations(id) ON DELETE CASCADE,
    job_type TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING',
    progress INTEGER NOT NULL DEFAULT 0,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_message TEXT,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sf_users_org_email ON sf_users(organization_id, email);
CREATE INDEX idx_sf_questions_org_status ON sf_questions(organization_id, status);
CREATE INDEX idx_sf_questions_search ON sf_question_versions USING GIN (to_tsvector('english', coalesce(title, '') || ' ' || coalesce(prompt, '')));
CREATE INDEX idx_sf_assessments_org_status ON sf_assessments(organization_id, status);
CREATE INDEX idx_sf_invitations_token ON sf_assessment_invitations(token_hash);
CREATE INDEX idx_sf_attempts_live ON sf_attempts(assessment_id, status);
CREATE INDEX idx_sf_attempt_events_attempt ON sf_attempt_events(attempt_id, occurred_at);
CREATE INDEX idx_sf_audit_org_created ON sf_audit_logs(organization_id, created_at DESC);

INSERT INTO sf_subjects (name, slug) VALUES
('Linux', 'linux'),
('Unix', 'unix'),
('Shell', 'shell'),
('Bash', 'bash'),
('Splunk', 'splunk'),
('Java', 'java'),
('Spring Boot', 'spring-boot'),
('Spring MVC', 'spring-mvc'),
('Spring Security', 'spring-security'),
('Hibernate', 'hibernate'),
('JPA', 'jpa'),
('SQL', 'sql'),
('Oracle', 'oracle'),
('PostgreSQL', 'postgresql'),
('Kafka', 'kafka'),
('IBM MQ', 'ibm-mq'),
('REST API', 'rest-api'),
('Microservices', 'microservices'),
('Docker', 'docker'),
('Kubernetes', 'kubernetes'),
('Git', 'git'),
('GitHub', 'github'),
('Jenkins', 'jenkins'),
('Maven', 'maven'),
('Gradle', 'gradle'),
('AWS', 'aws'),
('Azure', 'azure'),
('GCP', 'gcp'),
('New Relic', 'new-relic'),
('Grafana', 'grafana'),
('Prometheus', 'prometheus'),
('ServiceNow', 'servicenow'),
('ITIL', 'itil'),
('Apigee', 'apigee'),
('IBM FileNet', 'ibm-filenet'),
('PCF', 'pcf'),
('RabbitMQ', 'rabbitmq'),
('Redis', 'redis'),
('JSON', 'json'),
('XML', 'xml'),
('XPath', 'xpath'),
('Swagger', 'swagger'),
('Postman', 'postman'),
('REST Assured', 'rest-assured'),
('JUnit', 'junit'),
('Mockito', 'mockito'),
('DSA', 'dsa'),
('OOP', 'oop'),
('Collections', 'collections'),
('Exception Handling', 'exception-handling'),
('Streams', 'streams'),
('Multithreading', 'multithreading'),
('Design Patterns', 'design-patterns');
