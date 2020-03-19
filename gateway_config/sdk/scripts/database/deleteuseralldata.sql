# IMPORTANT: you MUST change the user_id values X below for the correct value;
# Following script will delete a user's data, including account and data
# Author: Mona Wong
# Date: July 3, 2019
#select user_id, username, first_name from users;
delete from users where user_id=X;
#select profile_id, user_id, username, link_username from oauth_profile;
delete from oauth_profile where user_id=X;
#select folder_id, user_id, enclosing_folder_id, label from folders;
delete from folders where user_id=X;
delete from userdata where user_id=X;
delete from userdata_dir where user_id=X;
delete from tasks where user_id=X;
delete from transfer_record where user_id=X;
delete from user_preferences where user_id=X;
# Display the user_id to group_id mapping...
select * from user_group_lookup where user_id=X;
delete from user_group_lookup where user_id=X;
# Now inform user to manually delete the group
select 'Manually delete the above entry with delete from groups where group_id=whatever;' AS '';
#select group_id, groupname from groups where user_id=X;
select 'Now you MUST delete the user Globus transfer directory;' AS '';
