create table schools (
    id varchar(36) primary key, name varchar(200) not null,
    school_year integer not null check (school_year between 1 and 9999),
    grade integer not null check (grade between 1 and 12), revision bigint not null default 0
);
create table app_users (
    id varchar(36) primary key, school_id varchar(36) not null references schools(id),
    username varchar(100) not null unique, password_hash varchar(100) not null,
    role varchar(10) not null check (role in ('ADMIN','TEACHER')),
    unique(school_id,id)
);
create table areas (
    id varchar(36) primary key, school_id varchar(36) not null references schools(id),
    name varchar(200) not null, owner_id varchar(36) not null,
    shared_edit boolean not null default false,
    foreign key(school_id,owner_id) references app_users(school_id,id),
    unique(school_id,id), unique(school_id,name)
);
create table topics (
    id varchar(36) primary key, school_id varchar(36) not null, area_id varchar(36) not null,
    name varchar(500) not null, unit integer not null check(unit between 1 and 99),
    legacy_id varchar(300), foreign key(school_id,area_id) references areas(school_id,id),
    unique(school_id,id), unique(school_id,legacy_id)
);
create table schedules (
    id varchar(36) primary key, school_id varchar(36) not null, area_id varchar(36) not null,
    weekday integer not null check(weekday between 1 and 7), start_time varchar(5) not null, end_time varchar(5) not null,
    foreign key(school_id,area_id) references areas(school_id,id), check(start_time < end_time)
);
create table calendar_blocks (
    id varchar(36) primary key, school_id varchar(36) not null references schools(id), area_id varchar(36),
    label varchar(200) not null, kind varchar(20) not null check(kind in ('EXAM','VACATION','HOLIDAY','OTHER')),
    start_date date not null, end_date date not null, check(start_date <= end_date),
    foreign key(school_id,area_id) references areas(school_id,id)
);
create table planned_sessions (
    id varchar(36) primary key, school_id varchar(36) not null, topic_id varchar(36) not null,
    assignment varchar(20), status varchar(20), exact_date varchar(10), notes text,
    version bigint not null default 0,
    foreign key(school_id,topic_id) references topics(school_id,id), unique(school_id,topic_id)
);
create table import_history (
    id varchar(36) primary key, school_id varchar(36) not null references schools(id),
    user_id varchar(36) not null, imported_at timestamp with time zone not null,
    affected_topics integer not null, revision bigint not null,
    foreign key(school_id,user_id) references app_users(school_id,id)
);
create index session_school on planned_sessions(school_id);
create index blocks_school_dates on calendar_blocks(school_id,start_date,end_date);
