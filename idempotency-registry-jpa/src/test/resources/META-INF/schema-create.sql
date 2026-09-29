create table task_log_entry_ (task_id_ varchar(100) not null, process_instance_id_ varchar(100) not null, created_at_ timestamp not null, result_ blob, constraint task_log_entry_pk_ primary key (task_id_));
create index idx_task_log_entry_process_instance_id_ on task_log_entry_ (process_instance_id_);
