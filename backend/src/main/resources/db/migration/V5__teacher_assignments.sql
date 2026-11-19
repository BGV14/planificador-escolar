-- Tutorship grants classroom read access, never additional write privileges.
create table classroom_tutors (
    school_id varchar(36) not null,
    context_id varchar(36) not null,
    user_id varchar(36) not null,
    primary key (school_id, context_id, user_id),
    foreign key (school_id, context_id) references academic_contexts(school_id, id),
    foreign key (school_id, user_id) references app_users(school_id, id)
);
