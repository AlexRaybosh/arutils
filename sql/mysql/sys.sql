

drop table if exists seq;
drop table if exists system_process_lsof;
drop table if exists system_process;
drop table if exists cluster_member;
drop table if exists web_host;
drop table if exists env_config;
drop table if exists env;
drop table if exists word;


CREATE TABLE if not exists seq (
  name varchar(200) NOT NULL,
  value bigint NOT NULL,
  last_ms bigint DEFAULT NULL,
  PRIMARY KEY (name)
) engine=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;



create table if not exists env (
	id int not null,
    name varchar(64) not null,
    last_ms bigint not null,
    primary key (id),
    unique index env_name_idx(name)
) engine=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

create table if not exists env_config (
	env_id int not null,
	position int not null default 0,
    config varchar(64) not null,
    meta mediumtext not null,
    last_ms bigint not null,
    primary key (env_id,position),
    unique index env_name_idx(config),
    constraint env_config_env_fk foreign key (env_id) references env(id)
) engine=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;



insert into env values (1, 'localdev1', unix_timestamp()*1000);
insert into env values (2, 'localdev2', unix_timestamp()*1000);
insert into env values (3, 'localdev3', unix_timestamp()*1000);
insert into env values (4, 'localdev4', unix_timestamp()*1000);
insert into env values (5, 'localdev5', unix_timestamp()*1000);
insert into env values (100, 'dev', unix_timestamp()*1000);
insert into env values (200, 'test', unix_timestamp()*1000);
insert into env values (300, 'beta',  unix_timestamp()*1000);
insert into env values (400, 'prod', unix_timestamp()*1000);

insert into env_config (env_id,position,config,meta,last_ms) values (1,0,'my_entry','{\"some value\":\"some override\"}',unix_timestamp()*1000);



create table if not exists cluster_member (
	id int not null,
    hostname varchar(300) not null,
    member_type varchar(64) not null,
    tcp_port int not null,
	meta mediumtext not null,
    env_id int not null default 0,
    last_ms bigint not null,
    primary key (id),
    unique index cluster_member_idx (hostname, tcp_port),
    index cluster_member_env_idx (env_id),
    constraint cluster_member_env_fk foreign key (env_id) references env(id) 
) engine=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

INSERT INTO cluster_member (id, hostname, member_type, tcp_port, meta, env_id, last_ms) VALUES (1, 'z440.localdomain', 'WEBSERVER', 8080, '{}', 1, unix_timestamp()*1000);



create table if not exists system_process (
	id bigint not null,
    is_active bool not null,
    env_id int null,
    hostname varchar(300) not null,
    pid bigint null,
    cmd mediumtext null,
    cluster_member_id int null,
    start_ms bigint not null,
    ping_ms  bigint not null,
    dead_ms  bigint null,
    primary key (id),
    index process_dead_idx (is_active, dead_ms),
    index process_cluster_idx (cluster_member_id, is_active),
    constraint system_process_cluster_member_fk foreign key (cluster_member_id) references cluster_member(id),
    constraint system_process_env_fk foreign key (env_id) references env(id)
) engine=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;



drop table if exists word_dictionary;
create table if not exists word_dictionary (
	id int not null,
    word varchar(700) not null,
    last_ms bigint null,
    primary key (id),
    unique index word_dictionary_idx (word)
) engine=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;


