# Following script will truncate all tables in the database
# Author: Mona Wong
# Date: July 3, 2019
truncate application_preferences;
truncate applications;
truncate cached_items;
truncate data_records;
truncate folder_preferences;
truncate folders;
truncate groups;
truncate item_metadata;
truncate job_events;
truncate job_stats;
truncate oauth_profile;
truncate record_fields;
truncate running_tasks;
truncate running_tasks_parameters;
truncate sso;
truncate task_input_source_documents;
truncate task_log_messages;
truncate task_properties;
truncate tgusage;
truncate tool_parameters;
truncate transfer_record;
truncate user_group_lookup;
truncate user_preferences;
truncate userdata;
truncate userdata_dir;
truncate users;
# The following tables are order-dependent and cannot use truncate
delete from source_documents;
alter table source_documents auto_increment = 1;
delete from task_input_parameters;
alter table task_input_parameters auto_increment = 1;
delete from task_output_source_documents;
alter table task_output_source_documents auto_increment = 1;
delete from task_output_parameters;
alter table task_output_parameters auto_increment = 1;
delete from tasks;
alter table tasks auto_increment = 1;
select 'Done.  Remember to delete all the data in globus_transfer!' AS '';
