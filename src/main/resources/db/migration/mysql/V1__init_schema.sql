create table seckill_event (
    id bigint not null auto_increment,
    name varchar(128) not null,
    start_time datetime(6) not null,
    end_time datetime(6) not null,
    total_stock int not null,
    available_stock int not null,
    version bigint not null default 0,
    primary key (id)
) engine=InnoDB;

create table purchase_order (
    id bigint not null auto_increment,
    order_no varchar(64) not null,
    event_id bigint not null,
    user_id varchar(64) not null,
    created_at datetime(6) not null,
    primary key (id),
    constraint uk_purchase_order_order_no unique (order_no),
    constraint uk_purchase_order_event_user unique (event_id, user_id),
    constraint fk_purchase_order_event foreign key (event_id) references seckill_event(id)
) engine=InnoDB;

create index idx_purchase_order_event_created_at on purchase_order(event_id, created_at);

create table seckill_purchase_request (
    id bigint not null auto_increment,
    request_id varchar(64) not null,
    event_id bigint not null,
    user_id varchar(64) not null,
    status varchar(16) not null,
    redis_reserved bit not null,
    failure_code varchar(64),
    failure_message varchar(255),
    order_id bigint,
    order_no varchar(64),
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    constraint uk_request_request_id unique (request_id),
    constraint uk_request_event_user unique (event_id, user_id)
) engine=InnoDB;

create index idx_request_status_updated_at on seckill_purchase_request(status, updated_at);
create index idx_request_event_created_at on seckill_purchase_request(event_id, created_at);
