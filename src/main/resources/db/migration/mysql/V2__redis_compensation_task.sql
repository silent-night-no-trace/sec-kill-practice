create table redis_compensation_task (
    id bigint not null auto_increment,
    event_id bigint not null,
    user_id varchar(64) not null,
    source varchar(32) not null,
    status varchar(16) not null,
    attempt_count int not null,
    last_error_message varchar(255),
    next_retry_at datetime(6) not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    constraint uk_redis_comp_task_event_user unique (event_id, user_id)
) engine=InnoDB;

create index idx_redis_comp_task_status_next_retry on redis_compensation_task(status, next_retry_at);
