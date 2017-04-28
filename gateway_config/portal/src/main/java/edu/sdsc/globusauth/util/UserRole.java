package edu.sdsc.globusauth.util;

/**
 * Created by cyoun on 10/18/16.
 */
public enum UserRole {
    ADMIN,
    TEACHER,
    STUDENT,
    GUEST,
    STANDARD,
    TESTER,
    REST_END_USER_UMBRELLA,
    REST_USER,

    // These are obsolete, but keeping them in the enum in case we still have them in the db.
    REST_END_USER_DIRECT,
    REST_ADMIN;

    public boolean isRestEndUser()
    {
        return this == REST_END_USER_UMBRELLA || this == REST_USER;
    }

	/*
	public boolean isCipresUser()
	{
		return !isRestEndUser() && (this != REST_ADMIN);
	}
	*/
}
