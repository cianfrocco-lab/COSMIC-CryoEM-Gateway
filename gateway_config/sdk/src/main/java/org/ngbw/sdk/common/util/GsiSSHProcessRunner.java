/**
 * 
 * @author Terri Liebowitz Schwartz
 *
 */

package org.ngbw.sdk.common.util;

import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ngbw.sdk.tool.GlobusConfig;
import org.ngbw.sdk.tool.GlobusCred;


/**
	Extends SSHExecProcessRunner to run remote commands using gsissh.

	Calls GlobusCred.getInstance().get() to make sure the cert file has
	an unexpired proxy.  Also locks the certfile for reading before running the 
	command and unlocks the certfile when the command finishes.  We lock it for 
	reading because gsissh itself doesn't lock it and other cipres processes that 
	use gridftp will obtain a write lock and update it when the cert is found to be old.  

	Terri, Wed Jan 30 13:32:44 PST 2013: not sure what I was thinking with the file locking.
	We're seeing OverlappingFileLockException frequently, I think because multiple threads
	are trying to lock the cert file at the same time and that isn't allowed.  I'm going to 
	remove the locking.  I suppose that means that the gsissh command may occassionally fail 
	because another process (the portal, loadResultsD, a cron job ...) modifes the cert file 
	while gsissh reads it OR maybe gsissh locks it?  Either way I think we'll see that failure a lot
	less often than we're seeing this exception.

*/
public class GsiSSHProcessRunner extends SSHExecProcessRunner
{
	private static final Log log = LogFactory.getLog(GsiSSHProcessRunner.class.getName());
	private String gCommand = "ssh -o BatchMode=yes";

	/**
		Constructors
	*/
	public GsiSSHProcessRunner()
	{
		this.sshCommand = gCommand;
		Map<String, String> m = new HashMap();
		//m.put("X509_USER_PROXY", GlobusConfig.getProxyFile());
		setEnv(m);
		//log.debug("Initialized GsiSSHProcessRunner with X509_USER_PROXY = " + GlobusConfig.getProxyFile());
	}

	public boolean configure(Map<String, String> cfg)
	{
		if (cfg != null)
		{
			if (cfg.containsKey(LOGIN))
			{
				this.connectString = cfg.get(LOGIN);
				log.debug("Configured GsiSSHProcessRunner with " + LOGIN + "=" + this.connectString);
			} else
			{
				log.debug("missing configuration parameter: " + LOGIN);
			}
		} else
		{
			log.debug("missing configuration parameters");
		}
		return isConfigured();
	}

	public boolean isConfigured()
	{
		return this.connectString != null; 
	}


	/**
		Public Methods
	*/
	public void start(String command) throws IOException, InterruptedException, Exception
	{
		//GlobusCred.getInstance().get();
		super.start(command);
	}

	public int run(String command) throws IOException, InterruptedException, Exception
	{
		//GlobusCred.getInstance().get();
		return super.run(command);
	}

	public int waitForExit() throws Exception
	{
		int retval = super.waitForExit();
		return retval;
	}

	public void close()
	{
	}

	/**
		Private Methods
	*/


	/**
		Main method for testing only
	*/
	public static void main(String[] args) 
	{
		GsiSSHProcessRunner runner = null; 
		try
		{
			/*
			runner = new GsiSSHProcessRunner("tg804218@lonestar.tacc.utexas.edu",
				"/tmp/terri");
			runner = new GsiSSHProcessRunner("tg804218@lonestar.tacc.utexas.edu",
				GlobusConfig.getProxyFile());
			*/
			runner = new GsiSSHProcessRunner();
			Map cfg = new HashMap<String, String>();
			cfg.put("login", "cipres@trestles.sdsc.edu");
			if (!runner.configure(cfg))
			{
				throw new Exception("Not configured.");
			}

			runner.start("cat");
			PrintStream stdin = new PrintStream(runner.getStdin());
			stdin.printf("hi there\n");
			stdin.printf("bye now\n");
			stdin.flush();
			stdin.close();

			int exitCode = runner.waitForExit();
			if (exitCode == 0)
			{
				System.out.printf("stdout=%s\n", runner.getStdOut());
			} else
			{
				System.out.printf("exitCode=%d\n", exitCode);
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			if (runner != null)
			{
				runner.close();
			}
		}
	}
}

