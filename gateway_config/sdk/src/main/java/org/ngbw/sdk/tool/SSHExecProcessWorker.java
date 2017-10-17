/*
 * SSHExecProcessWorker.java
 */

/*
	These scripts be available on the remote host.  The exact path to the script is specified in the tool-registry:

		- "rc" this is sourced to set path and other env vars before running any
		remote command

		- "submit" is run to submit the job.  It's invoked: submit url commandline
		It looks for scheduler options in scheduler.conf in the working dir.  Returns 0, with jobid=<jobid> on 
		stdout if job submitted.  Returns 1, with multiline error message on stdout otherwise.

		- "check" is run to see which jobs are finished.  It expects to recieve a list of job ids on stdin, one 
		per line.  Returns on stdout, the subset of those jobs that are no longer running or queued.

		- "cancel" is run to cancel a submitted/running job.
*/
package org.ngbw.sdk.tool;

import java.io.IOException;
import java.io.PrintStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Date;
import java.io.FileNotFoundException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ngbw.sdk.api.tool.FileHandler;
import org.ngbw.sdk.common.util.SSHExecProcessRunner;
import org.ngbw.sdk.database.RunningTask;
import org.ngbw.sdk.database.User;
import org.ngbw.sdk.WorkbenchException;

/**
 * SSHExecProcessWorker
 * 
 *
 * @author Terri Liebowitz Schwartz 
 *
 */
public class SSHExecProcessWorker extends BaseProcessWorker 
{
	private static final String STDOUT = "stdout.txt";
	private static final String STDERR = "stderr.txt";
	private static final String SUBMIT = "submit";
	private static final String CHECK = "check";
	private static final String CANCEL  = "cancel";
	private static final String ACCOUNTGROUP = "accountGroup";
	private static final String LOGIN = "login";
	private static final String RUNNER = "runner";

	private static final Log m_log = LogFactory.getLog(SSHExecProcessWorker.class.getName());

	// m_accountGroup comes from the tool registry entry for the resource.  It's optional.  The
	// other parameters here are all mandatory.
	private String m_submit;
	private String m_check;
	private String m_cancel;
	private String m_jobID;
	private String m_login;
	private String m_accountGroup;
	private Class<SSHExecProcessRunner> m_runnerClass;

	/** 
		Constructors
	*/
	public SSHExecProcessWorker(RunningTask rt) 
		throws PropertyException, IOException, InstantiationException, 
		IllegalAccessException, ClassNotFoundException, Exception
	{
		super(rt);
		initializeThisClass();
	}

	public SSHExecProcessWorker(String jobhandle) 
		throws PropertyException, IOException, InstantiationException, 
		IllegalAccessException, ClassNotFoundException, Exception
	{
		super(jobhandle);
		initializeThisClass();
	}
	

	public String submitImpl() throws Exception
	{
		String jobId = submitJob(m_chargeNumber);
		return jobId;
		
	}


	/**
		Receives a list of jobs that were submitted on this host, finds out which ones
		are done, and returns that list.
	*/
	public List<RunningTask> checkJobsOnResource(List<RunningTask> running_tasks) throws Exception
	{
		SSHExecProcessRunner runner = null;
		PrintStream stdin = null;
		int exitStatus;
		try
		{
			runner = getProcessRunner();
			String finalCommand  = (m_rc == null) ? "" : ("source " + m_rc + "; ");
			finalCommand += m_check;

			m_log.debug("Running remote command:" + finalCommand);
			runner.start(finalCommand);
			stdin = new PrintStream(runner.getStdin());

			m_log.debug("Writing to remote command's stdin:");
			for (RunningTask rt : running_tasks)
			{
				// send list of running task id's to remote script
				if (rt.getRemoteJobId() != null)
				{
					stdin.printf("%s\n", rt.getRemoteJobId());
					m_log.debug(rt.getRemoteJobId());
				} 
			}
			stdin.flush();
			stdin.close();
			stdin = null;
			m_log.debug("Waiting for remote to finish.");
			exitStatus = runner.waitForExit();
			if (exitStatus == 0)
			{
				m_log.debug("Remote returned exit status 0");
				return filterRunningTaskList(running_tasks, runner.getStdOut());
			} else
			{
				m_log.debug("Returned:" + exitStatus + ". Stdout=" + runner.getStdOut() + " Stderr=" +
					runner.getStdErr());
			}
		} 
		finally 
		{
			if (stdin != null)
			{
				try { stdin.close(); } catch (Exception e) {;}
			}
			if (runner != null)
			{
				runner.close();
			}
		}
		m_log.debug("Returning empty list of running tasks.");
		return new ArrayList<RunningTask>();
	}

	protected FileHandler getFileHandler() throws Exception
	{
		if (m_local)
		{
			return new LocalFileHandler();
		}
		return toolRegistry.getToolResource(m_rt.getResource()).getFileHandler();
	}

	protected SSHExecProcessRunner getProcessRunner() throws Exception
	{
		SSHExecProcessRunner runner;
		try
		{
			runner = m_runnerClass.newInstance(); 
		}
		catch(Exception e)
		{
			m_log.error("", e);
			throw e;
		}
		HashMap<String, String> cfg = new HashMap<String, String>();
		cfg.put(LOGIN, m_login);
		if (runner.configure(cfg))
		{
			return runner;
		}
		throw new WorkbenchException("Process Runner Not configured.");
	}

	protected InputStream readStdErr(FileHandler handler) throws Exception
	{
		return readFile(m_workingDir + "/" + STDERR, handler);
	}

	protected InputStream  readStdOut(FileHandler handler) throws Exception
	{
		return readFile(m_workingDir + "/" + STDOUT, handler);
	}

	/**
		chargeCode is either user's charge code or null (to use default, i.e. cipres
		community account's allocation on teragrid).

		Returns jobID.
		Can throw an exception if ssh error, submit command times out, or submit returns
		non-zero exit status or stdout of submit command doesn't contain job id.
	*/

	private String submitJob(String chargeCode) throws Exception
	{
		String jobID;
		SSHExecProcessRunner runner = getProcessRunner(); 

		/*
			Todo: This is a temporary hack to deal with intermittent problem where although we've
			successfully staged the input with SFTPFileHandler but we can't cd to the working
			dir with ssh.  Turns out this only happens if I use tscc-login.sdsc.edu which is
			round-robined.  Data takes awhile to show up if you put it there on one node and then
			check on another. Temporary solution is to use either tscc-login1 or tscc-login2 explicitly.
			No node for this hack then.
		*/
		/*
		int attempts = 0;
		boolean worked = false;
		while (attempts < 30)
		{
			if (attempts == 0)
			{
				m_log.debug("Trying this command, up to 30 times: 'hostname; cd " + m_workingDir + "'");
			}
			int retval = runner.run("hostname; cd " + m_workingDir );
			String output = runner.getStdOut(); 
			if (retval != 0)
			{
				m_log.warn("Can't cd to working dir: " + output);
				Thread.sleep(1000);
			} else
			{
				m_log.debug("cd worked! " + runner.getStdOut());
				worked = true;
				break;
			}
			attempts++;
		}
		*/
		/*
			End hack
		*/

		String finalCommand  = (m_rc == null) ? "" : ("source " + m_rc + "; ");
		finalCommand += 	"export WB_JOBID=" + m_jobHandle + "; "  + "cd " + m_workingDir + "; " ;
		finalCommand += m_submit + " ";
		if (chargeCode != null)
		{
			finalCommand += ( "--account " + chargeCode + " ");  
		}
		finalCommand += "--url " + m_url + " ";
		finalCommand += "-- ";
		finalCommand += " '" + m_commandLine + " '";

		m_log.debug("Running ssh command: " + finalCommand);
		int exitStatus = runner.run(finalCommand);
		if (exitStatus == 0)
		{
			m_log.debug("Returned:"+  runner.getStdOut());
			// should contain "jobid=<jobid>"
			jobID = getFirstMatch("jobid=(\\S+).*$", runner.getStdOut());
			if (jobID != null)
			{
				return jobID;
			} 
			m_log.error("Qsub returned exit status 0, but jobid can't be parsed from stdout.");
		} 
		if (exitStatus == 2)
		{
			throw new Exception("Too many tasks are waiting to run.  You can clone your task and try running it again later."); 
		}
		throw new Exception("Error submitting job: " + runner.getStdOut() + ". " + runner.getStdErr());
	}

	public void cancelJob() throws Exception
	{
		if (m_cancel == null)
		{
			m_log.debug("Can't cancel because no cancel program was specified for this resource in tool-registry.cfg.xml.");
			return;
		}
		SSHExecProcessRunner runner = getProcessRunner(); 

		String finalCommand  = (m_rc == null) ? "" : ("source " + m_rc + "; ");
		finalCommand += m_cancel + " -j " + m_rt.getRemoteJobId();
		finalCommand += " -d " + m_workingDir;

		m_log.debug("Running ssh command: " + finalCommand);
		int exitStatus = runner.run(finalCommand);
		if (exitStatus != 0)
		{
			m_log.error("Returned:" + exitStatus + ". Stdout=" + runner.getStdOut() + " Stderr=" +
				runner.getStdErr());
		} else
		{
			m_log.debug("Exit status is 0, that's good.");
		}
	}



	// Return the subset of running_tasks whose ids are in remote_job_ids, where remote_job_ids
	// is a newline separated sequence of ids of finished jobs.
	private List<RunningTask> filterRunningTaskList(List<RunningTask> running_tasks, String remote_job_ids)
	{
		m_log.debug("Got this list of finished jobs (remote job ids): " + remote_job_ids);
		String[] array = remote_job_ids.split("\n");
		List<String> job_ids = java.util.Arrays.asList(array);
		for (Iterator<RunningTask> it = running_tasks.iterator(); it.hasNext(); )
		{
			RunningTask rt = it.next();
			if (!job_ids.contains(rt.getRemoteJobId()))
			{
				it.remove();
			}
		}
		m_log.debug("Returning finished task list of size: " + running_tasks.size());
		return running_tasks;
	}

	public InputStream readFile(String filename, FileHandler handler)  throws Exception
	{
		InputStream is = null;
		int tries = 0;
		try
		{
			while (true)
			{
				try
				{
					tries++;
					is = handler.getInputStream(filename);
					return is;
				}
				catch (FileNotFoundException e)
				{
					// Handler will have closed stream before throwing this.
					if (tries > 1)
					{
						throw e;
					}
					Thread.sleep(1000 * 10);
					m_log.debug("Retrying read of " + filename + ".");
				}
			}
		}
		catch (Exception e)
		{
			m_log.debug("Exception reading:'" + filename , e);
			return null;
		}
	}

	private void initializeThisClass() throws PropertyException, Exception
	{
		Properties p = new Properties();
		p.putAll(m_tool.getToolResource().getParameters());
		setMyMembers(p);
	}


	private void setMyMembers(Properties p) throws PropertyException
	{
		m_submit = p.getProperty(SUBMIT);
		if (m_submit == null)
			throw new PropertyException("required property " + SUBMIT + " is null");
		m_check = p.getProperty(CHECK);
		if (m_check == null)
			throw new PropertyException("required property " + CHECK + " is null");
		m_cancel = p.getProperty(CANCEL);

		m_accountGroup= p.getProperty(ACCOUNTGROUP);

		m_login = p.getProperty(LOGIN);
		if (m_login == null)
		{
			throw new PropertyException("Required property " + LOGIN + " is null.");
		}

		String className = p.getProperty(RUNNER);
		if (className == null)
		{
			throw new PropertyException("Required property " + RUNNER + " is null.");
		}
		try
		{
			m_runnerClass = (Class<SSHExecProcessRunner>)Class.forName(className);
		}
		catch(Exception e)
		{
			m_log.debug("", e);
			throw new RuntimeException("Problem finding class: " + className + ", " + e);
		}
	}

	private String getFirstMatch(String p, String input)
	{
		Pattern pattern = Pattern.compile(p, Pattern.MULTILINE);
		Matcher matcher = pattern.matcher(input);
		while (matcher.find())
		{
			return matcher.group(1);
		}
		return null;
	}


}
