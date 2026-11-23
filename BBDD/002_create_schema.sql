
BEGIN;
CREATE SCHEMA qori;
REVOKE ALL ON SCHEMA qori FROM PUBLIC, anon, authenticated;
CREATE DOMAIN qori.positive_amount AS numeric(19,2)
 CHECK (VALUE > 0 AND VALUE <> 'NaN'::numeric);
CREATE DOMAIN qori.nonnegative_amount AS numeric(19,2)
 CHECK (VALUE >= 0 AND VALUE <> 'NaN'::numeric);
CREATE DOMAIN qori.currency_code AS varchar(3) CHECK (VALUE IN ('PEN','USD'));

CREATE TABLE qori.profiles (
 user_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE RESTRICT,
 display_name varchar(120) NOT NULL CHECK (btrim(display_name) <> ''),
 preferred_language varchar(2) NOT NULL DEFAULT 'es' CHECK (preferred_language IN ('es','en')),
 preferred_currency qori.currency_code NOT NULL DEFAULT 'PEN',
 timezone varchar(80) NOT NULL DEFAULT 'America/Lima',
 status varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE'))
);
CREATE TABLE qori.accounts (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 user_id uuid NOT NULL REFERENCES qori.profiles(user_id),
 name varchar(120) NOT NULL CHECK (btrim(name) <> ''),
 type varchar(20) NOT NULL CHECK (type IN ('CASH','BANK','DIGITAL_WALLET')),
 currency qori.currency_code NOT NULL,
 opening_balance qori.nonnegative_amount NOT NULL DEFAULT 0,
 opening_on date NOT NULL,
 status varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','ARCHIVED')),
 archived_at timestamptz,
 UNIQUE(id,user_id),
 CHECK ((status = 'ARCHIVED') = (archived_at IS NOT NULL))
);
CREATE TABLE qori.categories (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 user_id uuid REFERENCES qori.profiles(user_id),
 name varchar(100) NOT NULL CHECK (btrim(name) <> ''),
 type varchar(10) NOT NULL CHECK (type IN ('INCOME','EXPENSE')),
 icon varchar(50), is_system boolean NOT NULL DEFAULT false,
 status varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','ARCHIVED')),
 archived_at timestamptz,
 CHECK (is_system = (user_id IS NULL)),
 CHECK ((status = 'ARCHIVED') = (archived_at IS NOT NULL))
);
CREATE TABLE qori.transactions (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 user_id uuid NOT NULL REFERENCES qori.profiles(user_id),
 account_id uuid NOT NULL,
 category_id uuid NOT NULL REFERENCES qori.categories(id),
 type varchar(10) NOT NULL CHECK (type IN ('INCOME','EXPENSE')),
 amount qori.positive_amount NOT NULL, occurred_on date NOT NULL,
 description varchar(250),
 status varchar(20) NOT NULL CHECK (status IN ('PENDING','CONFIRMED','CANCELLED')),
 source varchar(30) NOT NULL CHECK (source IN ('MANUAL','RECURRING','GROUP_EXPENSE','GROUP_SETTLEMENT')),
 confirmed_at timestamptz, cancelled_at timestamptz,
 idempotency_key varchar(120) NOT NULL CHECK (btrim(idempotency_key) <> ''),
 request_hash varchar(64) NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
 UNIQUE(user_id,idempotency_key),
 FOREIGN KEY(account_id,user_id) REFERENCES qori.accounts(id,user_id),
 CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL)),
 CHECK (status <> 'CONFIRMED' OR confirmed_at IS NOT NULL),
 CHECK (status <> 'PENDING' OR confirmed_at IS NULL)
);
CREATE TABLE qori.internal_transfers (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 user_id uuid NOT NULL REFERENCES qori.profiles(user_id),
 from_account_id uuid NOT NULL, to_account_id uuid NOT NULL,
 amount qori.positive_amount NOT NULL, occurred_on date NOT NULL,
 description varchar(250),
 status varchar(20) NOT NULL CHECK (status IN ('PENDING','CONFIRMED','CANCELLED')),
 confirmed_at timestamptz, cancelled_at timestamptz,
 idempotency_key varchar(120) NOT NULL CHECK (btrim(idempotency_key) <> ''),
 request_hash varchar(64) NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
 UNIQUE(user_id,idempotency_key), CHECK (from_account_id <> to_account_id),
 FOREIGN KEY(from_account_id,user_id) REFERENCES qori.accounts(id,user_id),
 FOREIGN KEY(to_account_id,user_id) REFERENCES qori.accounts(id,user_id),
 CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL)),
 CHECK (status <> 'CONFIRMED' OR confirmed_at IS NOT NULL),
 CHECK (status <> 'PENDING' OR confirmed_at IS NULL)
);
CREATE TABLE qori.recurring_schedules (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 user_id uuid NOT NULL REFERENCES qori.profiles(user_id), account_id uuid NOT NULL,
 category_id uuid NOT NULL REFERENCES qori.categories(id),
 operation_type varchar(10) NOT NULL CHECK (operation_type IN ('INCOME','EXPENSE')),
 amount qori.positive_amount NOT NULL, description varchar(250),
 frequency varchar(10) NOT NULL CHECK (frequency IN ('DAILY','WEEKLY','MONTHLY','YEARLY')),
 interval_count smallint NOT NULL CHECK (interval_count BETWEEN 1 AND 120),
 starts_on date NOT NULL, ends_on date, next_due_on date NOT NULL,
 status varchar(20) NOT NULL CHECK (status IN ('ACTIVE','PAUSED','CANCELLED','COMPLETED')),
 FOREIGN KEY(account_id,user_id) REFERENCES qori.accounts(id,user_id),
 CHECK (ends_on IS NULL OR ends_on >= starts_on), CHECK (next_due_on >= starts_on)
);
CREATE TABLE qori.recurring_occurrences (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 schedule_id uuid NOT NULL REFERENCES qori.recurring_schedules(id),
 scheduled_for date NOT NULL,
 status varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','CONFIRMED','SKIPPED','CANCELLED')),
 transaction_id uuid UNIQUE REFERENCES qori.transactions(id), reviewed_at timestamptz,
 UNIQUE(schedule_id,scheduled_for),
 CHECK ((status = 'CONFIRMED') = (transaction_id IS NOT NULL)),
 CHECK ((status = 'PENDING') = (reviewed_at IS NULL))
);
CREATE TABLE qori.budgets (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 user_id uuid NOT NULL REFERENCES qori.profiles(user_id),
 category_id uuid REFERENCES qori.categories(id), month_start date NOT NULL,
 currency qori.currency_code NOT NULL, amount qori.positive_amount NOT NULL,
 alert_threshold_percent smallint NOT NULL CHECK (alert_threshold_percent BETWEEN 1 AND 100),
 status varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
 deactivated_at timestamptz, UNIQUE(id,user_id),
 CHECK (extract(day FROM month_start) = 1),
 CHECK ((status = 'INACTIVE') = (deactivated_at IS NOT NULL))
);
CREATE TABLE qori.goals (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 user_id uuid NOT NULL REFERENCES qori.profiles(user_id),
 name varchar(120) NOT NULL CHECK (btrim(name) <> ''),
 target_amount qori.positive_amount NOT NULL, currency qori.currency_code NOT NULL,
 target_date date,
 status varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','PAUSED','COMPLETED','CANCELLED')),
 completed_at timestamptz,
 CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL))
);
CREATE TABLE qori.goal_reservations (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 goal_id uuid NOT NULL REFERENCES qori.goals(id),
 account_id uuid NOT NULL REFERENCES qori.accounts(id),
 amount qori.positive_amount NOT NULL, reserved_on date NOT NULL,
 status varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','REVERSED')),
 note varchar(250), reversed_at timestamptz,
 idempotency_key varchar(120) NOT NULL CHECK (btrim(idempotency_key) <> ''),
 request_hash varchar(64) NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
 UNIQUE(account_id,idempotency_key),
 CHECK ((status = 'REVERSED') = (reversed_at IS NOT NULL))
);
CREATE TABLE qori.financial_groups (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 owner_id uuid NOT NULL REFERENCES qori.profiles(user_id),
 name varchar(120) NOT NULL CHECK (btrim(name) <> ''), description varchar(500),
 status varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','ARCHIVED')),
 archived_at timestamptz,
 CHECK ((status = 'ARCHIVED') = (archived_at IS NOT NULL))
);
CREATE TABLE qori.group_members (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 group_id uuid NOT NULL REFERENCES qori.financial_groups(id),
 user_id uuid NOT NULL REFERENCES qori.profiles(user_id),
 role varchar(10) NOT NULL CHECK (role IN ('OWNER','MEMBER')),
 status varchar(20) NOT NULL CHECK (status IN ('INVITED','ACTIVE','DECLINED','LEFT')),
 invited_by_user_id uuid NOT NULL REFERENCES qori.profiles(user_id),
 invited_at timestamptz NOT NULL DEFAULT now(), joined_at timestamptz,
 responded_at timestamptz, left_at timestamptz,
 UNIQUE(group_id,user_id), UNIQUE(id,group_id),
 CHECK ((status = 'LEFT') = (left_at IS NOT NULL)),
 CHECK (status <> 'ACTIVE' OR joined_at IS NOT NULL)
);
CREATE TABLE qori.group_expenses (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 group_id uuid NOT NULL REFERENCES qori.financial_groups(id), paid_by_member_id uuid NOT NULL,
 personal_transaction_id uuid NOT NULL UNIQUE REFERENCES qori.transactions(id),
 split_mode varchar(10) NOT NULL CHECK (split_mode IN ('EQUAL','MANUAL')),
 description varchar(250) NOT NULL CHECK (btrim(description) <> ''), cancelled_at timestamptz,
 FOREIGN KEY(paid_by_member_id,group_id) REFERENCES qori.group_members(id,group_id)
);
CREATE TABLE qori.group_expense_splits (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 group_expense_id uuid NOT NULL REFERENCES qori.group_expenses(id),
 member_id uuid NOT NULL REFERENCES qori.group_members(id),
 amount qori.nonnegative_amount NOT NULL, UNIQUE(group_expense_id,member_id)
);
CREATE TABLE qori.group_settlements (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 group_id uuid NOT NULL REFERENCES qori.financial_groups(id),
 from_member_id uuid NOT NULL, to_member_id uuid NOT NULL,
 amount qori.positive_amount NOT NULL, currency qori.currency_code NOT NULL,
 settled_on date NOT NULL,
 payer_transaction_id uuid NOT NULL UNIQUE REFERENCES qori.transactions(id),
 receiver_transaction_id uuid UNIQUE REFERENCES qori.transactions(id),
 payer_confirmed_at timestamptz NOT NULL, receiver_confirmed_at timestamptz,
 note varchar(250), idempotency_key varchar(120) NOT NULL CHECK (btrim(idempotency_key) <> ''),
 request_hash varchar(64) NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
 UNIQUE(from_member_id,idempotency_key),
 FOREIGN KEY(from_member_id,group_id) REFERENCES qori.group_members(id,group_id),
 FOREIGN KEY(to_member_id,group_id) REFERENCES qori.group_members(id,group_id),
 CHECK (from_member_id <> to_member_id),
 CHECK (payer_transaction_id IS DISTINCT FROM receiver_transaction_id),
 CHECK ((receiver_transaction_id IS NULL) = (receiver_confirmed_at IS NULL))
);
CREATE TABLE qori.settlement_allocations (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 settlement_id uuid NOT NULL REFERENCES qori.group_settlements(id),
 expense_split_id uuid NOT NULL REFERENCES qori.group_expense_splits(id),
 amount qori.positive_amount NOT NULL, UNIQUE(settlement_id,expense_split_id)
);
CREATE TABLE qori.financial_notifications (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 user_id uuid NOT NULL REFERENCES qori.profiles(user_id), budget_id uuid NOT NULL,
 threshold_percent smallint NOT NULL CHECK (threshold_percent BETWEEN 1 AND 100),
 spent_amount qori.nonnegative_amount NOT NULL, budget_amount qori.positive_amount NOT NULL,
 currency qori.currency_code NOT NULL, read_at timestamptz,
 UNIQUE(budget_id,threshold_percent),
 FOREIGN KEY(budget_id,user_id) REFERENCES qori.budgets(id,user_id)
);
CREATE TABLE qori.audit_logs (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 actor_user_id uuid REFERENCES qori.profiles(user_id), action varchar(80) NOT NULL,
 entity_type varchar(60) NOT NULL, entity_id uuid, request_id uuid NOT NULL,
 before_values jsonb, after_values jsonb
);

CREATE FUNCTION qori.touch_updated_at() RETURNS trigger LANGUAGE plpgsql
 SET search_path = pg_catalog AS $$
BEGIN NEW.updated_at = clock_timestamp(); RETURN NEW; END;
$$;
DO $$
DECLARE t record;
BEGIN
 FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = 'qori' LOOP
  EXECUTE format('ALTER TABLE qori.%I ADD COLUMN created_at timestamptz NOT NULL DEFAULT now()',t.tablename);
  EXECUTE format('ALTER TABLE qori.%I ENABLE ROW LEVEL SECURITY',t.tablename);
  IF t.tablename NOT IN ('group_expense_splits','group_settlements','settlement_allocations','financial_notifications','audit_logs') THEN
   EXECUTE format('ALTER TABLE qori.%I ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now()',t.tablename);
   EXECUTE format('CREATE TRIGGER touch_updated_at BEFORE UPDATE ON qori.%I FOR EACH ROW EXECUTE FUNCTION qori.touch_updated_at()',t.tablename);
  END IF;
 END LOOP;
END $$;
CREATE UNIQUE INDEX accounts_active_name ON qori.accounts(user_id,currency,lower(btrim(name))) WHERE status='ACTIVE';
CREATE UNIQUE INDEX categories_user_name ON qori.categories(user_id,type,lower(btrim(name))) WHERE NOT is_system AND status='ACTIVE';
CREATE UNIQUE INDEX categories_system_name ON qori.categories(type,lower(btrim(name))) WHERE is_system AND status='ACTIVE';
CREATE UNIQUE INDEX budgets_general ON qori.budgets(user_id,month_start,currency) WHERE category_id IS NULL;
CREATE UNIQUE INDEX budgets_category ON qori.budgets(user_id,month_start,currency,category_id) WHERE category_id IS NOT NULL;
CREATE UNIQUE INDEX groups_one_owner ON qori.group_members(group_id) WHERE role='OWNER';
CREATE INDEX transactions_user_date ON qori.transactions(user_id,occurred_on);
CREATE INDEX transactions_account_status ON qori.transactions(account_id,status,occurred_on);
CREATE INDEX transactions_category_date ON qori.transactions(category_id,occurred_on);
CREATE INDEX transfers_from ON qori.internal_transfers(from_account_id,status,occurred_on);
CREATE INDEX transfers_to ON qori.internal_transfers(to_account_id,status,occurred_on);
CREATE INDEX recurring_due ON qori.recurring_schedules(status,next_due_on);
CREATE INDEX reservations_account ON qori.goal_reservations(account_id,status);
CREATE INDEX reservations_goal ON qori.goal_reservations(goal_id,status);
CREATE INDEX memberships_user ON qori.group_members(user_id,status);
CREATE INDEX expenses_group ON qori.group_expenses(group_id,created_at);
CREATE INDEX splits_member ON qori.group_expense_splits(member_id);
CREATE INDEX settlements_pair ON qori.group_settlements(group_id,from_member_id,to_member_id,currency);
CREATE INDEX allocations_split ON qori.settlement_allocations(expense_split_id);
CREATE INDEX notifications_user ON qori.financial_notifications(user_id,created_at);
CREATE INDEX notifications_unread ON qori.financial_notifications(user_id,created_at) WHERE read_at IS NULL;
CREATE INDEX audit_entity ON qori.audit_logs(entity_type,entity_id,created_at);
CREATE INDEX audit_actor ON qori.audit_logs(actor_user_id,created_at);
REVOKE ALL ON ALL TABLES IN SCHEMA qori FROM PUBLIC, anon, authenticated;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA qori FROM PUBLIC, anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA qori REVOKE ALL ON TABLES FROM PUBLIC, anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA qori REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC, anon, authenticated;
COMMIT;
