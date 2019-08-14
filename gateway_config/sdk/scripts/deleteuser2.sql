# IMPORTANT: you MUST change the user_id values X below for the correct value;
# Following script will delete a user's data, including account and data
# Author: Mona Wong
# Date: July 3, 2019
#select user_id, username, first_name from users;
delete from users where user_id=2;
#select profile_id, user_id, username, link_username from oauth_profile;
delete from oauth_profile where user_id=2;
#select folder_id, user_id, enclosing_folder_id, label from folders;
delete from folders where user_id=2;
# Display the user_id to group_id mapping...
select * from user_group_lookup where user_id=2;
delete from user_group_lookup where user_id=2;
# Now inform user to manually delete the group
select 'Manually delete the above entry with delete from groups where group_id=whatever;' AS '';
select group_id, groupname from groups where group_id=2;
