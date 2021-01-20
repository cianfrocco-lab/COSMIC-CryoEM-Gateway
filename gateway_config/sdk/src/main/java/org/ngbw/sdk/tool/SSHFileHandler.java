/*
 * SSHFileHandler.java
 */
package org.ngbw.sdk.tool;


import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;
import java.util.HashMap;
import java.util.Properties;
import java.lang.Long;
import java.lang.Boolean;
import java.lang.Process;
import java.security.MessageDigest;
import javax.xml.bind.DatatypeConverter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ngbw.sdk.WorkbenchException;
import org.ngbw.sdk.api.tool.FileHandler;
import org.ngbw.sdk.core.io.SSLConnectionManager;
import org.ngbw.sdk.common.util.SSHExecProcessRunner;


/**
 *
 * @author Roland H. Niedner
 * @author Terri Liebowitz Schwartz
 * @author Paul Hoover
 *
 */
class SSHFileHandler implements FileHandler {

	private static final String sshCommand = "ssh -o StrictHostKeyChecking=no ";
	private static final Log m_log = LogFactory.getLog(SSHFileHandler.class.getName());
	private static final String FILEHOST = "fileHost";
	private static final String LOGIN = "login";
	private static final String REMOTESTAT = "stat";

	private String m_host;
	private String host;
	private String username;
	private File keyfile;
	private String connectString;

	// constructors


	protected SSHFileHandler()
	{
		m_host = null;
	}

	protected SSHFileHandler(String host)
	{
		m_host = host;
	}


	// public methods
	public void close() {;}


	public boolean configure(Map<String, String> cfg)
	{
		if (cfg != null && cfg.containsKey(FILEHOST)) 
		{
			try {
				m_host = cfg.get(FILEHOST);
				m_log.debug("Configured host: " + m_host);
	
				this.host = SSLConnectionManager.getInstance().getHost(m_host);
				this.username = SSLConnectionManager.getInstance().getUsername(m_host);
				this.keyfile = SSLConnectionManager.getInstance().getKeyfile(m_host);
				this.connectString = "-i " + this.keyfile.getAbsolutePath()  + " " +
				this.username + "@" + this.host;
				m_log.debug("Configured SSHExecProcessRunner from ssl.properties for " + m_host +
				", connectString=" + this.connectString);
                        }
                        catch(Exception e)
                        {
                                m_log.error("", e);
                        }
		} else
		{
			m_log.error("Missing parameter: " + FILEHOST);
		}

		return isConfigured();
	}

	public boolean isConfigured()
	{
		return m_host != null;
	}

	public void createDirectory(String directory) throws IOException, InterruptedException, Exception
	{
		SSHExecProcessRunner pr=null;

		m_log.debug("Creating Directory: " + directory);

		pr = getProcessRunner();

		int exitCode = pr.run("mkdir --mode=0750 '" + directory + "'");
		if (exitCode != 0){
			m_log.debug("mkdir --mode=0750 '" + directory + "' failed with exitCode " + exitCode);
			m_log.debug("stdout: " + pr.getStdOut());
			m_log.debug("stderr: " + pr.getStdErr());
			throw new IOException("mkdir --mode=0750 '" + directory + "' failed with exitCode " + exitCode);
		}

		m_log.debug("Created Directory: " + directory);
		pr.close();
	}

	public boolean exists(String path) throws Exception
	{
		try {
			getAttribute(path, REMOTESTAT);

			return true;
		}
		catch (IOException ioErr) {
			return false;
		}
	}

	public Date getMTime(String fileName) throws IOException, Exception
	{
		return new Date(Long.parseLong(getAttribute(fileName, REMOTESTAT + " --format=%Y").trim()) * 1000L);
	}

	public Long getMTimeLong(String fileName) throws IOException, Exception
	{
		//return new Date(Long.parseLong(getAttribute(fileName, REMOTESTAT + " --format=%Y").trim()) * 1000L);
		return Long.parseLong(getAttribute(fileName, REMOTESTAT + " --format=%Y").trim()) * 1000L;
	}

	public long getSize(String fileName) throws IOException, Exception
	{
		return Long.parseLong(getAttribute(fileName, REMOTESTAT + " --format=%s "));
	}

	public boolean isDirectory(String fileName) throws IOException, Exception
	{
		return "directory".equals(getAttribute(fileName, REMOTESTAT + " --format=%F ").trim());
	}

	public List<FileAttributes> list(String directory) throws IOException, Exception
	{
		List<HashMap> files = getFileList(directory);
		List<FileHandler.FileAttributes> attributes = new ArrayList<FileHandler.FileAttributes>();

		for ( HashMap filehash : files ) {
			FileHandler.FileAttributes fa = new FileHandler.FileAttributes();
			long mtimeLong;

			fa.filename = filehash.get("filename").toString();
			m_log.debug("fa.filename: " + fa.filename);
			fa.isDirectory = Boolean.valueOf(filehash.get("isDirectory").toString());
			m_log.debug("fa.isDirectory: " + fa.isDirectory);
			fa.size = Long.valueOf(filehash.get("size").toString());
			m_log.debug("fa.size: " + fa.size);
			mtimeLong = Long.valueOf(filehash.get("mtime").toString());
			//fa.mtime = filehash.get("mtime");
			fa.mtime = new Date(mtimeLong);
			m_log.debug("fa.mtime: " + fa.mtime);

			attributes.add(fa);
		}

		return attributes;
	}

	public List<String> listFiles(String directory) throws IOException, Exception
	{
		List<HashMap> files = getFileList(directory);
		List<String> names = new ArrayList<String>();

		for ( HashMap filehash : files )
			names.add(filehash.get("filename").toString());

		return names;
	}

	public Map<String, List<String>> listFilesByExtension(String directory) throws IOException, Exception
	{
		List<HashMap> files = getFileList(directory);
		Map<String, List<String>> fileNamesMap = new TreeMap<String, List<String>>();

		for ( HashMap filehash : files) {
			String name = filehash.get("filename").toString();
			int dotChar = name.lastIndexOf('.');

			if (dotChar == -1)
				continue;

			String extension = name.substring(dotChar + 1);
			List<String> fileList = fileNamesMap.get(extension);

			if (fileList == null) {
				fileList = new ArrayList<String>();

				fileNamesMap.put(extension, fileList);
			}

			fileList.add(name);
		}

		return fileNamesMap;
	}

	public List<String> listSubdirectories(String directory) throws IOException, Exception
	{
		List<HashMap> files = getFileList(directory);
		List<String> names = new ArrayList<String>();

		for (HashMap filehash : files) {

			if (Boolean.valueOf(filehash.get("isDirectory").toString()))
				names.add(filehash.get("filename").toString());
		}

		return names;
	}

	public void moveDirectory(String directoryName, String newDirectoryName) throws IOException, Exception
	{
		moveFile(directoryName, newDirectoryName);
	}

	public void moveFile(String fileName, String newFileName) throws IOException, Exception
	{
		m_log.debug("Moving, before getConnection(),  " + fileName + " to " + newFileName+ " via " + m_host);
		SSHExecProcessRunner pr=null;

		try {
			pr = getProcessRunner();

			m_log.debug("Moving " + fileName + " to " + newFileName+ " via " + m_host);

			int exitCode = pr.run("mv '" + fileName + "' '" + newFileName + "'");
			if (exitCode != 0){
				m_log.debug("mv '" + fileName + "' '" + newFileName + "' failed with exitCode " + exitCode);
				m_log.debug("stdout: " + pr.getStdOut());
				m_log.debug("stderr: " + pr.getStdErr());
				throw new IOException("mv '" + fileName + "' '" + newFileName + "' failed with exitCode " + exitCode);
			}
		}
		finally {
			pr.close();
		}
	}

	public InputStream  readFile(String fileName) throws IOException, Exception
	{
		return getInputStream(fileName);
	}



	public InputStream getInputStream(String fileName) throws Exception
	{
		m_log.debug("getInputStream for fileName: " + fileName);
		SSHExecProcessRunner pr=null;

		try 
		{
			pr = getProcessRunner();
			String command = sshCommand + " " + connectString + " " + "cat " + fileName;
			Process process = Runtime.getRuntime().exec(command);
			return process.getInputStream();
		}
		catch (IOException ioErr) 
		{
			pr.close();
			throw ioErr;
		}
	}

	public void removeDirectory(String directoryPath, boolean deleteContent) throws IOException, Exception
	{
		m_log.debug("removeDirectory  for directoryPath: " + directoryPath);


		removeDirectoryRecursive(directoryPath, deleteContent);

		m_log.debug("Removed " + directoryPath);
	}

	public void removeFile(String fileName) throws IOException, Exception
	{
		m_log.debug("removeFile  for fileName: " + fileName);
		SSHExecProcessRunner pr=null;

		try {
			pr = getProcessRunner();

			int exitCode = pr.run("rm '" + fileName + "'");

			if (exitCode != 0) {
				m_log.debug("rm '" + fileName + "' failed with exitCode " + exitCode);
				m_log.debug("stdout: " + pr.getStdOut());
				m_log.debug("stderr: " + pr.getStdErr());
				throw new IOException("rm '" + fileName + "' failed with exitCode " + exitCode);
			}

			m_log.debug("Removed " + fileName);
		}
		catch (InterruptedException e) {
			throw new InterruptedException(e.getMessage());
		}
		finally {
			pr.close();
		}
	}

	public void writeFile(String fileName, String content) throws IOException, Exception
	{
		writeFile(fileName, content.getBytes());
	}

	public void writeFile(String fileName, byte[] content) throws IOException, Exception
	{
		writeFile(fileName, new ByteArrayInputStream(content));
	}

	public void writeFile(String fileName, File file) throws IOException, Exception
	{
		writeFile(fileName, new BufferedInputStream(new FileInputStream(file)));
	}

	// private methods


        protected SSHExecProcessRunner getProcessRunner() //throws Exception
        {
                SSHExecProcessRunner runner;
		runner = new SSHExecProcessRunner();
                HashMap<String, String> cfg = new HashMap<String, String>();
                cfg.put(LOGIN, m_host);
                if (runner.configure(cfg))
                {
                        return runner;
                }
                throw new WorkbenchException("Process Runner Not configured.");
        }

	private String getAttribute(String fileName, String statline) throws IOException, Exception
	{
		m_log.debug("getAttribute  for fileName: '" + fileName + "' with statline: " + statline);
		//login1.stampede2(12)$ stat --format="%s %Y %F" ngbw
		//4096 1505412724 directory
		//https://stackoverflow.com/questions/2887658/java-popen-like-function
		//Process process = Runtime.getRuntime().exec("your command");
		//blocking in webapp?
		int exitCode=0;
		SSHExecProcessRunner pr=null;

		try {
			
			//int exitCode = pr.run("stat --format=\"%s %Y %F\" '" + fileName);
			pr = getProcessRunner();
			exitCode = pr.run(statline + " '" + fileName + "'");
			m_log.debug("exitCode is " + exitCode + ". Output is " + pr.getStdOut());
			if (exitCode != 0) {
				m_log.debug("stdout: " + pr.getStdOut());
				m_log.debug("stderr: " + pr.getStdErr());
				throw new IOException("getAttributes() for file: '" + fileName + "' failed! with exitCode: " + exitCode);
			} else {
				return pr.getStdOut().trim();
			}
		}
		catch (IOException e)
		{
			pr.close();
			throw new IOException("getAttributes() for file: " + fileName + " failed! with exitCode: " + exitCode);
		}
	}

	private List<HashMap> getFileList(String directory) throws IOException, Exception
	{
		List<HashMap> fileEntries = new ArrayList<HashMap>();
		int exitCode=0;
		List<String> filenamelist = new ArrayList<String>();
		SSHExecProcessRunner pr=null;
		try {
			pr = getProcessRunner();
			exitCode = pr.run("ls -a -1 '" + directory + "'");
			if (exitCode != 0) {
				m_log.debug("ls -a -i '" + directory + "' failed with exitCode: " + exitCode);
				m_log.debug("stdout: " + pr.getStdOut());
				m_log.debug("stderr: " + pr.getStdErr());
				throw new IOException("ls -a -i '" + directory + "' failed with exitCode: " + exitCode);
			}
			String[] filenames = pr.getStdOut().split("\n");
			for (String filestring : filenames) {
				filenamelist.add(filestring);
			}
		}
		catch(IOException e)
		{
			m_log.debug("ls -a -i " + directory + " failed with exitCode: " + exitCode);
			pr.close();
			throw new IOException("ls -a -i " + directory + " failed with exitCode: " + exitCode);
		}
		for (String filestring : filenamelist) {
			HashMap file = new HashMap();
			
			String fileName = filestring;
			file.put("filename", fileName);
			file.put("isDirectory", isDirectory(directory + "/" + fileName));
			file.put("mtime", getMTimeLong(directory + "/" + fileName));
			file.put("size", getSize(directory + "/" + fileName));

			if(!fileName.equals(".") && !fileName.equals(".."))
				fileEntries.add(file);
		}

		return fileEntries;
	}


	private void removeDirectoryRecursive(String directory, boolean deleteContent) throws IOException, Exception
	{
		int exitCode;
		SSHExecProcessRunner pr=null;
		try {
	    		pr = getProcessRunner();
	    		List<HashMap> files = getFileList(directory);
	    
	    		if (deleteContent == false)
	    		{
	    			if (files.size() > 0)
	    			{
	    				m_log.error("Directory is not empty: ");
	    				for (HashMap filehash : files)
	    				{
	    					m_log.error(filehash.get("filename").toString());
	    				}
	    				throw new WorkbenchException("Directory is not empty");
	    			}
	    			// we fall thru since we've determined that the directory is empty.
	    		}
	    		for(HashMap file : files)
	    		{
	    			String fileName = file.get("filename").toString();
	    			if(".".equals(fileName.trim()) == false && "..".equals(fileName.trim()) == false)
	    			{
	    				// tl: added this to make this method recursive
	    				if (Boolean.valueOf(file.get("isDirectory").toString()))
	    				{
	    					removeDirectoryRecursive(directory + "/" + fileName, deleteContent);
	    				} else
	    					// tl: end  added this to make this method recursive
	    				{
	    					exitCode = pr.run("rm '" + directory + "/" + fileName + "'");
	    					if (exitCode != 0){
	    						m_log.debug("rm of '" + directory + "/" + fileName + "' failed with exitCode: " + exitCode);
							m_log.debug("stdout: " + pr.getStdOut());
							m_log.debug("stderr: " + pr.getStdErr());
	    						throw new IOException("rm of '" + directory + "/" + fileName + "' failed with exitCode: " + exitCode);
	    					}
	    				}
	    			}
	    		}
	    		exitCode = pr.run("rmdir '" + directory + "'");
	    		if (exitCode != 0){
	    			m_log.debug("rmdir of '" + directory + "' failed with exitCode: " + exitCode);
				m_log.debug("stdout: " + pr.getStdOut());
				m_log.debug("stderr: " + pr.getStdErr());
	    			throw new IOException("rmdir of '" + directory + "' failed with exitCode: " + exitCode);
	    		}

		}
		catch (IOException e)
		{
	    		m_log.debug("rmdir of '" + directory + "' failed");
			pr.close();
	    		throw new IOException("rmdir of " + directory + " failed");
		}
	}

	public void writeFile(String fileName, InputStream inStream) throws IOException, Exception
	{
		OutputStream os=null;
		SSHExecProcessRunner pr=null;

		try {
			byte[] readBuffer = new byte[8192];
			int offset = 0;
			int bytesRead;
			MessageDigest md = MessageDigest.getInstance("MD5");
			pr = getProcessRunner();
			pr.start("cat > " + fileName);
			os = pr.getStdin();

			while ((bytesRead = inStream.read(readBuffer, 0, readBuffer.length)) >= 0) {
				m_log.debug("offset: " + offset + " bytesRead: " + bytesRead + " " + readBuffer);
				os.write(readBuffer, 0, bytesRead);
				md.update(readBuffer, 0, bytesRead);

				offset += bytesRead;
			}
			os.flush();
			os.close();
                        os = null;
                        m_log.debug("Waiting for remote to finish.");
                        int exitCode = pr.waitForExit();

			pr = getProcessRunner();
			pr.run("md5sum '" + fileName + "'");
			String md5sumstring = pr.getStdOut().split("\\s+")[0].trim().toLowerCase();
                        pr.close();
			String localmd5string = DatatypeConverter.printHexBinary(md.digest()).toLowerCase();
			if (! md5sumstring.equals(localmd5string)) {
				m_log.debug("md5sumstring: " + md5sumstring + " not equal to localmd5string: " + localmd5string + " for " + fileName);
				throw new IOException("md5sumstring: " + md5sumstring + " not equal to localmd5string: " + localmd5string + " for " + fileName);
			} else {
				m_log.debug("md5sumstring: " + md5sumstring + " does equal localmd5string: " + localmd5string + " for " + fileName);
			}
                        m_log.debug("Exited after closing " + fileName);
			m_log.debug("stdout: " + pr.getStdOut());
			m_log.debug("stderr: " + pr.getStdErr());
		}
                finally
                {
                        if (os != null)
                        {
                                try { os.close(); } catch (Exception e) {;}
                        }
                        if (pr != null)
                        {
                                pr.close();
                        }
                }
	}
}
