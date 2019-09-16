/*
	SQL to add MAX_UPLOAD_SIZE_GB to users table and set the values to 10.  Note, I
  chose not to use the SQL DEFAULT syntax because I don't want this setting to be
  specified at the database level.  It is currently specified in code..  (See COSMIC2
  GitHub issue #126 for more info.
*/
alter table users add column MAX_UPLOAD_SIZE_GB int(11);
update users set MAX_UPLOAD_SIZE_GB=10;
