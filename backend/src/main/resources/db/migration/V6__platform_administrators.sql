alter table app_users add constraint app_users_id_role_unique unique(id,role);

create table platform_administrators (
    user_id varchar(36) primary key,
    user_role varchar(10) not null default 'ADMIN' check (user_role = 'ADMIN'),
    foreign key(user_id,user_role) references app_users(id,role)
);
