/*
 * UserTgAllocation.java
 */
package org.ngbw.utils;


import java.util.List;
import org.ngbw.sdk.database.ConnectionManager;
import org.ngbw.sdk.database.DriverConnectionSource;
import org.ngbw.sdk.database.User;


/**
 *
 * @author Terri Liebowitz Schwartz
 *
 * modified by Kenneth from Terri's UserTgAllocation
 */
public class UserUploadLimit {

	public static void main(String[] args)
	{
		//System.err.println("main: err start");
		//System.out.println("main: out start");
		//String account = null;
		try {

			ConnectionManager.setConnectionSource(new DriverConnectionSource());

			if (args.length < 1) {
				
				System.out.println("usage: UserUploadLimit username limit");
				System.out.println("Users in the system:");
				System.out.println("username	email	uploadlimitgb");
				List<User> userlist = User.findAllUsers();
				for (User user : userlist) {
					System.out.println(user.getUsername() + " " + user.getEmail() + " " + user.getMaxUploadSizeGB());
				}
				throw new Exception("usage: UserUploadLimit username limit");
			}


			User user = User.findUser(args[0]);
			if (user == null) {
				throw new Exception("Couldn't find a user with username " + args[0]);
			}
			int uploadsize = Integer.parseInt(args[1]);
			user.setMaxUploadSizeGB(uploadsize);
			user.save();
/*
			if (args.length > 1)
			{
				account = args[1];
				if (account.equals("NONE") || account.equals("NULL"))
				{
					account = null;
				}
				user.setAccount(User.TERAGRID, account);
				user.save();
			}
			boolean canSubmit = user.canSubmit();
			account = user.getAccount(User.TERAGRID);
			System.out.println("Teragrid account for user '" + args[0] + "' is set to '" +  account + "'");
			System.out.println("Task submission " + (canSubmit ? "enabled" : "disabled"));
*/
		}
		catch (Exception err) {
			System.out.println(err.toString());
			err.printStackTrace(System.err);

			System.exit(-1);
		}
	}
}
