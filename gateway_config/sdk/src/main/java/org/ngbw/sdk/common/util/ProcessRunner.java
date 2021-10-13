/**
 * 
 * @author Terri Liebowitz Schwartz
 *
 */

/**
	ProcessRunner provides a simple way to run an external command and wait for
	it to complete.  You can retrieve the output and error text as strings and
	get the exit code.  For example:

		ProcessRunner pr = new ProcessRunner(true);
		int exitCode = pr.run("ls",  "-l");
		System.out.println("exitCode is " + exitCode + ". Output is " + pr.getStdOut());

	Or use the runSh() method to pass a string to the bourne shell and execute it.  For
	example:
		exitCode = pr.runSh("echo 'fiddlefaddle\nbiddle' > xxx 2>&1; wc -l xxx; cat xxx; test -f xxx");
*/

package org.ngbw.sdk.common.util;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Future;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ngbw.sdk.common.util.InputStreamCollector;
import org.ngbw.sdk.common.util.SuperString;

public class ProcessRunner
{
	private static final Log log = LogFactory.getLog(ProcessRunner.class.getName());
	int exitStatus;
	Future<String> stdOut;
	Future<String> stdErr;
	boolean mergeStreams;
	File directory;

	public int getExitStatus() { return exitStatus;}

	public String  getStdOut() 
	{ 
		String retval = "";
		try
		{
			retval = stdOut.get(); 
		} 
		catch(Exception e)
		{
			log.error("", e);
		}
		return retval == null ? "" : retval;
	}

	public String  getStdErr() 
	{ 
		String retval = "";
		try
		{
			retval = stdErr.get(); 
		} 
		catch(Exception e)
		{
			log.error("", e);
		}
		return retval == null ? "" : retval;
	}

	public void mergeStreams(boolean mergeStreams) { this.mergeStreams = mergeStreams; }
	public void directory(File directory) { this.directory = directory; }

	public ProcessRunner()
	{
	}

	public ProcessRunner(boolean mergeStreams)
	{
		this.mergeStreams = mergeStreams;
	}

	private ProcessBuilder runInit()
	{
		ProcessBuilder pb = new ProcessBuilder();
		//pb.redirectErrorStream(mergeStreams);
		pb.directory(directory);
		return pb;
	}

	/**
		Can invoke this as either multiple strings as the argument or with an
		array of strings as the argument.
	*/
	public int run(String... command) throws IOException, InterruptedException
	{
		ProcessBuilder pb = runInit();
		pb.command(command);
		return start(pb);
	}

	public int run(List<String> command) throws IOException, InterruptedException
	{
		ProcessBuilder pb = runInit();
		pb.command(command);
		return start(pb);
	}


	/**
		Specify command as a single string containing the arguments (but no shell
		wildcards, redirection, etc.).  Command will be split on whitespace.
	*/
	public int run(String command) throws IOException, InterruptedException
	{
		String[] commandArray = command.split("\\s+");
		ProcessBuilder pb = runInit();
		pb.command(commandArray);
		return start(pb);
	}

	public int runSh(String command) throws IOException, InterruptedException
	{
		ProcessBuilder pb = runInit();
		pb.command("/bin/sh", "-c", command);
		return start(pb);
	}


	private int start(ProcessBuilder pb) throws IOException, InterruptedException
	{
		String command = SuperString.valueOf(pb.command(), ' ').toString();
		log.debug("Waiting for:  " + command);
		Process p = pb.start();
			// start throws io exception if the command can't be found/run.

		stdOut = InputStreamCollector.readInputStream(p.getInputStream());
		stdErr = InputStreamCollector.readInputStream(p.getErrorStream());
		exitStatus = p.waitFor();
		log.debug("Completed :  " + command);
		return exitStatus;
	}

	public static void main(String args[]) throws Exception
	{
		// Initialize using varargs with stdout and stderr merged
		ProcessRunner pr = new ProcessRunner(true);
		int exitCode;

		/*
		exitCode = pr.run("cat",  "-x");
		System.out.println("exitCode is " + exitCode + ". Output is " + pr.getStdOut());

		// Initialize using varargs with stdout and stderr separate
		pr.mergeStreams(false);
		exitCode = pr.run("cat",  "-x");
		System.out.println("exitCode is " + exitCode + ". Output is " + pr.getStdOut());

		// Initialize using array of strings 
		String[] commandArray = {"ls", "-l"};
		pr.mergeStreams(true);
		exitCode = pr.run(commandArray);
		System.out.println("exitCode is " + exitCode + ". Output is " + pr.getStdOut());

		exitCode = pr.run("ls   -l  ");
		System.out.println("exitCode is " + exitCode + ". Output is " + pr.getStdOut());

		exitCode = pr.run("ls   -l  *");
		System.out.println("exitCode is " + exitCode + ". Output is " + pr.getStdOut());

		exitCode = pr.runSh("ls   -l  *");
		System.out.println("exitCode is " + exitCode + ". Output is " + pr.getStdOut());
		*/

		exitCode = pr.runSh("echo 'fiddlefaddle\nbiddle' > xxx 2>&1; wc -l xxx; cat xxx; test -f xxx");
		System.out.println("exitCode is " + exitCode + ". Output is " + pr.getStdOut());

		exitCode = pr.runSh("echo testing again &&  test -b xxx");
		System.out.println("exitCode is " + exitCode + ". Output is " + pr.getStdOut());
	}

}
