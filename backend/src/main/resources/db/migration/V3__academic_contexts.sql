-- Append-only migration: historical grade means PRIMARY, never infer SECONDARY.
create table academic_contexts (
 id varchar(36) primary key, school_id varchar(36) not null references schools(id),
 school_year integer not null check(school_year between 1900 and 2200),
 level varchar(12) not null check(level in ('INITIAL','PRIMARY','SECONDARY')),
 stage_kind varchar(5) not null, stage_value integer not null,
 revision bigint not null default 0, legacy_default boolean not null default false,
 unique(school_id,id), unique(school_id,school_year,level,stage_value),
 check ((level='INITIAL' and stage_kind='AGE' and stage_value between 0 and 5)
 or (level='PRIMARY' and stage_kind='GRADE' and stage_value between 1 and 6)
 or (level='SECONDARY' and stage_kind='GRADE' and stage_value between 1 and 5))
);
-- This insert deliberately fails for ambiguous historical grades or unsupported years.
insert into academic_contexts(id,school_id,school_year,level,stage_kind,stage_value,revision,legacy_default)
 select id,id,school_year,'PRIMARY','GRADE',grade,revision,true from schools;
alter table areas rename to areas_v2;
alter table topics rename to topics_v2;
alter table schedules rename to schedules_v2;
alter table calendar_blocks rename to calendar_blocks_v2;
alter table planned_sessions rename to planned_sessions_v2;
alter table import_history rename to import_history_v2;
create table areas (
    id varchar(36) primary key, context_id varchar(36) not null, school_id varchar(36) not null references schools(id),
    name varchar(200) not null, owner_id varchar(36) not null,
    shared_edit boolean not null default false,
    foreign key(school_id,owner_id) references app_users(school_id,id),
    unique(school_id,context_id,id), unique(school_id,context_id,name),
    foreign key(school_id,context_id) references academic_contexts(school_id,id)
);
create table topics (
    id varchar(36) primary key, context_id varchar(36) not null, school_id varchar(36) not null, area_id varchar(36) not null,
    name varchar(500) not null, unit integer not null check(unit between 1 and 99),
    legacy_id varchar(300), foreign key(school_id,context_id,area_id) references areas(school_id,context_id,id),
    unique(school_id,context_id,id), unique(school_id,context_id,legacy_id),
    foreign key(school_id,context_id) references academic_contexts(school_id,id)
);
create table schedules (
    id varchar(36) primary key, context_id varchar(36) not null, school_id varchar(36) not null, area_id varchar(36) not null,
    weekday integer not null check(weekday between 1 and 7), start_time varchar(5) not null, end_time varchar(5) not null,
    foreign key(school_id,context_id,area_id) references areas(school_id,context_id,id), check(start_time < end_time),
    foreign key(school_id,context_id) references academic_contexts(school_id,id)
);
create table calendar_blocks (
    id varchar(36) primary key, context_id varchar(36) not null, school_id varchar(36) not null references schools(id), area_id varchar(36),
    label varchar(200) not null, kind varchar(20) not null check(kind in ('EXAM','VACATION','HOLIDAY','OTHER')),
    start_date date not null, end_date date not null, check(start_date <= end_date),
    foreign key(school_id,context_id,area_id) references areas(school_id,context_id,id),
    foreign key(school_id,context_id) references academic_contexts(school_id,id)
);
create table planned_sessions (
    id varchar(36) primary key, context_id varchar(36) not null, school_id varchar(36) not null, topic_id varchar(36) not null,
    assignment varchar(20), status varchar(20), exact_date varchar(10), notes text,
    version bigint not null default 0,
    foreign key(school_id,context_id,topic_id) references topics(school_id,context_id,id), unique(school_id,context_id,topic_id),
    foreign key(school_id,context_id) references academic_contexts(school_id,id)
);
create table import_history (
    id varchar(36) primary key, context_id varchar(36) not null, school_id varchar(36) not null references schools(id),
    user_id varchar(36) not null, imported_at timestamp with time zone not null,
    affected_topics integer not null, revision bigint not null,
    foreign key(school_id,user_id) references app_users(school_id,id),
    foreign key(school_id,context_id) references academic_contexts(school_id,id)
);
insert into areas(context_id,id,school_id,name,owner_id,shared_edit) select school_id,id,school_id,name,owner_id,shared_edit from areas_v2;
insert into topics(context_id,id,school_id,area_id,name,unit,legacy_id) select school_id,id,school_id,area_id,name,unit,legacy_id from topics_v2;
insert into schedules(context_id,id,school_id,area_id,weekday,start_time,end_time) select school_id,id,school_id,area_id,weekday,start_time,end_time from schedules_v2;
insert into calendar_blocks(context_id,id,school_id,area_id,label,kind,start_date,end_date) select school_id,id,school_id,area_id,label,kind,start_date,end_date from calendar_blocks_v2;
insert into planned_sessions(context_id,id,school_id,topic_id,assignment,status,exact_date,notes,version) select school_id,id,school_id,topic_id,assignment,status,exact_date,notes,version from planned_sessions_v2;
insert into import_history(context_id,id,school_id,user_id,imported_at,affected_topics,revision) select school_id,id,school_id,user_id,imported_at,affected_topics,revision from import_history_v2;
drop table import_history_v2;
drop table planned_sessions_v2;
drop table calendar_blocks_v2;
drop table schedules_v2;
drop table topics_v2;
drop table areas_v2;
create index session_context on planned_sessions(school_id,context_id);
create index blocks_context_dates on calendar_blocks(school_id,context_id,start_date,end_date);
