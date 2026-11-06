-- The tasklist looks up the first task of a process instance it just started.
CREATE INDEX user_task_process_instance ON user_task (process_instance_key);
