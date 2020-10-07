/*
 * SFTPFileHandler.java
 */
package org.ngbw.sdk.tool;


import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ngbw.sdk.WorkbenchException;
import org.ngbw.sdk.api.tool.FileHandler;
import org.ngbw.sdk.core.io.SSLConnectionManager;

import com.trilead.ssh2.Connection;
import com.trilead.ssh2.SFTPException;
import com.trilead.ssh2.SFTPv3Client;
import com.trilead.ssh2.SFTPv3DirectoryEntry;
import com.trilead.ssh2.SFTPv3FileAttributes;
import com.trilead.ssh2.SFTPv3FileHandle;


/**
 *
 * @author Roland H. Niedner
 * @author Terri Liebowitz Schwartz
 * @author Paul Hoover
 *
 */
public class SFTPFileHandler implements FileHandler {

	private static final Log m_log = LogFactory.getLog(SFTPFileHandler.class.getName());
	private static final String FILEHOST = "fileHost";
	private static SFTPv3FileAttributes attributes_640 = new SFTPv3FileAttributes();
	static 
	{ 
		attributes_640.permissions = 00640;
	}

	private String m_host;


	// constructors


	public SFTPFileHandler()
	{
		m_host = null;
	}

	public SFTPFileHandler(String host)
	{
		m_host = host;
	}


	// public methods
	public void close() {;}


	public boolean configure(Map<String, String> cfg)
	{
		if (cfg != null && cfg.containsKey(FILEHOST)) 
		{
			m_host = cfg.get(FILEHOST);
			m_log.debug("Configured host: " + m_host);
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

	public void createDirectory(String directory) throws IOException
	{
		Connection conn = getConnection();
		SFTPv3Client client = null;

		try {
			client = new SFTPv3Client(conn);

			m_log.debug("Creating Directory: (" + directory + ")");
			client.mkdir(directory, 0750);

			m_log.debug("Created Directory: " + directory);
		}
		finally {
			if (client != null)
				client.close();

			conn.close();
		}
	}

	public boolean exists(String path)
	{
		try {
			getAttributes(path);

			return true;
		}
		catch (IOException ioErr) {
			return false;
		}
	}

	public Date getMTime(String fileName) throws IOException
	{
		return new Date(getAttributes(fileName).mtime * 1000L);
	}

	public long getSize(String fileName) throws IOException
	{
		return getAttributes(fileName).size;
	}

	public boolean isDirectory(String fileName) throws IOException
	{
		boolean retval;
		try
		{
			return getAttributes(fileName).isDirectory();
		}
		catch (SFTPException sftpErr)
		{
			if (sftpErr.getServerErrorCode() == 2)
			{
				return false;
			}
			throw sftpErr;
		}
	}

	public List<FileAttributes> list(String directory) throws IOException
	{
		List<SFTPv3DirectoryEntry> files = getFileList(directory);
		List<FileHandler.FileAttributes> attributes = new ArrayList<FileHandler.FileAttributes>();

		for (Iterator<SFTPv3DirectoryEntry> elements = files.iterator() ; elements.hasNext() ; ) {
			SFTPv3DirectoryEntry file = elements.next();
			FileHandler.FileAttributes fa = new FileHandler.FileAttributes();

			fa.filename = file.filename.trim();
			//fa.isDirectory = file.attributes.isDirectory();
			if (false){
				//m_log.debug("isDirectory or isRegularFile for: " + fa.filename);
				if (file.attributes.isSymlink()){
					//m_log.debug("isSymlink for: " + fa.filename);
				}
				if (file.attributes.isRegularFile()){
					//m_log.debug("isRegularFile for: " + fa.filename);
				}
				if (file.attributes.isDirectory()){
					//m_log.debug("isDirectory for: " + fa.filename);
				}
				fa.isDirectory = file.attributes.isDirectory();
			} else {
				try {
					List<String> testlist = listFiles(directory + "/" + fa.filename);
					m_log.debug("listFiles succeeded for: " + fa.filename);
					fa.isDirectory = true;
				} catch(IOException e) {
					m_log.debug("listFiles failed for: " + fa.filename);
					fa.isDirectory = false;
				}
			}
			fa.size = file.attributes.size;
			fa.mtime = new Date(file.attributes.mtime * 1000L);

			attributes.add(fa);
		}

		return attributes;
	}

	public List<String> listFiles(String directory) throws IOException
	{
		List<SFTPv3DirectoryEntry> files = getFileList(directory);
		List<String> names = new ArrayList<String>();

		for (Iterator<SFTPv3DirectoryEntry> elements = files.iterator() ; elements.hasNext() ; )
			names.add(elements.next().filename.trim());

		return names;
	}

	public Map<String, List<String>> listFilesByExtension(String directory) throws IOException
	{
		List<SFTPv3DirectoryEntry> files = getFileList(directory);
		Map<String, List<String>> fileNamesMap = new TreeMap<String, List<String>>();

		for (Iterator<SFTPv3DirectoryEntry> elements = files.iterator() ; elements.hasNext() ; ) {
			String name = elements.next().filename.trim();
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

	public List<String> listSubdirectories(String directory) throws IOException
	{
		List<SFTPv3DirectoryEntry> files = getFileList(directory);
		List<String> names = new ArrayList<String>();

		for (Iterator<SFTPv3DirectoryEntry> elements = files.iterator() ; elements.hasNext() ; ) {
			SFTPv3DirectoryEntry file = elements.next();

			if (file.attributes.isDirectory())
				names.add(file.filename.trim());
		}

		return names;
	}

	public void moveDirectory(String directoryName, String newDirectoryName) throws IOException
	{
		moveFile(directoryName, newDirectoryName);
	}

	public void moveFile(String fileName, String newFileName) throws IOException
	{
		Connection conn = getConnection();
		SFTPv3Client client = null;

		try {
			client = new SFTPv3Client(conn);

			m_log.debug("Moving " + fileName + " to " + newFileName+ " via " + m_host);

			client.mv(fileName, newFileName);
		}
		catch(SFTPException sftpErr)
		{
			m_log.debug(sftpErr.getServerErrorMessage() + ": " + sftpErr.getServerErrorCodeVerbose());
			throw sftpErr;
		}
		finally {
			if (client != null)
				client.close();

			conn.close();
		}
	}

	public InputStream  readFile(String fileName) throws IOException, Exception
	{
		return getInputStream(fileName);
	}



	public InputStream getInputStream(String fileName) throws Exception
	{
		Connection conn = getConnection();
		SFTPv3Client client = null;

		try 
		{
			client = new SFTPv3Client(conn);
			return new SFTPInputStream(conn, client, fileName);
		}
		catch (SFTPException sftpErr) 
		{
			if (client != null)
			{
				client.close();
			}
			conn.close();
			// we want to produce behavior consistent with the java.io package, so
			// that consumers can have a uniform way of handling exceptions
			m_log.debug("Filename=" + fileName + ", " + sftpErr.getServerErrorMessage() + ": " + sftpErr.getServerErrorCodeVerbose());
			if (sftpErr.getServerErrorCode() == 2)
			{
				throw new FileNotFoundException(fileName);
			}
			else
			{
				throw sftpErr;
			}
		}
		catch (IOException ioErr) 
		{
			if (client != null)
				client.close();
			conn.close();
			throw ioErr;
		}
	}

	public void removeDirectory(String directoryPath, boolean deleteContent) throws IOException
	{
		Connection conn = getConnection();
		SFTPv3Client client = null;

		try {
			client = new SFTPv3Client(conn);

			removeDirectoryRecursive(directoryPath, deleteContent, client);

			m_log.debug("Removed " + directoryPath);
		}
		finally {
			if (client != null)
				client.close();

			conn.close();
		}
	}

	public void removeFile(String fileName) throws IOException
	{
		Connection conn = getConnection();
		SFTPv3Client client = null;

		try {
			client = new SFTPv3Client(conn);

			client.rm(fileName);

			m_log.debug("Removed " + fileName);
		}
		finally {
			if (client != null)
				client.close();

			conn.close();
		}
	}

	public void writeFile(String fileName, String content) throws IOException
	{
		writeFile(fileName, content.getBytes());
	}

	public void writeFile(String fileName, byte[] content) throws IOException
	{
		writeFile(fileName, new ByteArrayInputStream(content));
	}

	public void writeFile(String fileName, File file) throws IOException
	{
		writeFile(fileName, new BufferedInputStream(new FileInputStream(file)));
	}

	public void writeFile(String fileName, InputStream inStream) throws IOException
	{
		Connection conn = getConnection();
		SFTPv3Client client = null;

		try {
			client = new SFTPv3Client(conn);

			writeFile(client, fileName, inStream);
		}
		finally {
			if (client != null)
				client.close();

			conn.close();
		}
	}


	// private methods


	private Connection getConnection() throws IOException
	{
		Connection conn = SSLConnectionManager.getInstance().getConnection(m_host);

		if (conn == null)
			throw new WorkbenchException("No connection could be acquired from host: " + m_host);

		return conn;
	}

	private SFTPv3FileAttributes getAttributes(String fileName) throws IOException
	{
		Connection conn = getConnection();
		SFTPv3Client client = null;

		try {
			client = new SFTPv3Client(conn);

			return client.stat(fileName);
		}
		finally {
			if (client != null)
				client.close();

			conn.close();
		}
	}

	private List<SFTPv3DirectoryEntry> getFileList(String directory) throws IOException
	{
		Connection conn = getConnection();
		SFTPv3Client client = null;

		try {
			client = new SFTPv3Client(conn);

			return getFileList(client, directory);
		}
		finally {
			if (client != null)
				client.close();

			conn.close();
		}
	}

	private List<SFTPv3DirectoryEntry> getFileList(SFTPv3Client client, String directory) throws IOException
	{
		List<SFTPv3DirectoryEntry> fileEntries = new ArrayList<SFTPv3DirectoryEntry>();
		Vector<?> files = client.ls(directory);

		for (Iterator<?> elements = files.iterator() ; elements.hasNext() ; ) {
			SFTPv3DirectoryEntry file = (SFTPv3DirectoryEntry) elements.next();
			String fileName = file.filename.trim();

			if(!fileName.equals(".") && !fileName.equals(".."))
				fileEntries.add(file);
		}

		return fileEntries;
	}


	private void removeDirectoryRecursive(String directory, boolean deleteContent, SFTPv3Client client) throws IOException
	{
		List<SFTPv3DirectoryEntry> files = getFileList(client, directory);

		if (deleteContent == false)
		{
			if (files.size() > 0)
			{
				m_log.error("Directory is not empty: ");
				for (SFTPv3DirectoryEntry file : files)
				{
					m_log.error(file.filename);
				}
				throw new WorkbenchException("Directory is not empty");
			}
			// we fall thru since we've determined that the directory is empty.
		}
		for(SFTPv3DirectoryEntry file : files)
		{
			String fileName = file.filename;
			if(".".equals(fileName.trim()) == false && "..".equals(fileName.trim()) == false)
			{
				// tl: added this to make this method recursive
				if (file.attributes.isDirectory())
				{
					removeDirectoryRecursive(directory + "/" + fileName, deleteContent, client);
				} else
					// tl: end  added this to make this method recursive
				{
					client.rm(directory + "/" + fileName);
				}
			}
		}
		client.rmdir(directory);
	}

	private void writeFile(SFTPv3Client client, String fileName, InputStream inStream) throws IOException
	{
		SFTPv3FileHandle handle = client.createFile(fileName, attributes_640);

		try {
			byte[] readBuffer = new byte[8192];
			long offset = 0;
			int bytesRead;

			while ((bytesRead = inStream.read(readBuffer, 0, readBuffer.length)) >= 0) {
				client.write(handle, offset, readBuffer, 0, bytesRead);

				offset += bytesRead;
			}
		}
		finally {
			client.closeFile(handle);
		}
	}
}
