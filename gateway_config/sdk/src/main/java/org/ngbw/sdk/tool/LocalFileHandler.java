package org.ngbw.sdk.tool;

import java.util.Date;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.ngbw.sdk.api.tool.FileHandler;

public class LocalFileHandler implements FileHandler {

	private static final Log log = LogFactory.getLog(LocalFileHandler.class);

	public void close() {;}


	public boolean configure(Map<String, String> cfg) {
		//TODO well there might be things to add like workspaces ect.
		return isConfigured();
	}

	public boolean isConfigured() {
		return true;
	}

	public boolean exists(final String path)
	{
		return (new File(path)).exists();
	}

	public void createDirectory(String directory) {
		if (directory == null)
			throw new NullPointerException("Directory cannot be null!");
		File dir = new File(directory);
		if (dir.mkdir() == false)
		{
			log.debug("Unable to create directory " + dir.getAbsolutePath());
			//throw new RuntimeException("The submitted 'directory' could not be created!");
			throw new RuntimeException("LocalFileHandler was unable to create:'" + dir.getAbsolutePath() + "'");
		}
	}

	public void removeDirectory(String directory, boolean deleteContent)
	{
		if (directory == null)
			throw new NullPointerException("Directory cannot be null!");
		File dir = new File(directory);
		log.debug("absolute path is " + dir.getAbsolutePath());
		if (dir.isFile())
			throw new RuntimeException("The submitted 'directory' " + directory + " is actually a file!");
		if (dir.exists() == false)
			throw new RuntimeException("The submitted 'directory' " + directory + " does not exist!");
		if (dir.isDirectory() == false)
			throw new RuntimeException("The submitted 'directory' " + directory + " is not a directory!");
		if (dir.listFiles().length > 0 && deleteContent == false)
			throw new RuntimeException("The submitted 'directory' " + directory + " is not empty!");
		boolean retval;
		for(File myfile : dir.listFiles())
		{
			if (myfile.isDirectory())
			{
				removeDirectory(myfile.getAbsolutePath(), deleteContent);
			} else
			{
				retval = myfile.delete();
				if (retval == false)
				{
					log.error("failed to remove file " + myfile.getAbsolutePath());
				}
			}
		}
		if (dir.listFiles().length > 0)
		{
			log.error("dir " + dir.getAbsolutePath() + " isn't empty.  One of the files or subdirs of this directory may still be open.");
		}
		if (dir.delete() == false)
			throw new RuntimeException("The submitted 'directory' " + directory + " could not be deleted!");
	}

	public List<String> listSubdirectories(String directory) throws IOException, Exception {
		if (directory == null)
			throw new NullPointerException("Directory cannot be null!");
		List<HashMap> files = getFileList(directory);
		List<String>  subdirNames = new ArrayList<String>();
		for (HashMap filehash : files) {

			if (Boolean.valueOf(filehash.get("isDirectory").toString()))
				subdirNames.add(filehash.get("filename").toString());
		}

		return subdirNames;
	}

	public List<String> listFiles(String directory) throws IOException, Exception {
		if (directory == null)
			throw new NullPointerException("Directory cannot be null!");
		File dir = new File(directory);
		if (dir.exists() == false)
			throw new RuntimeException(directory + " does not exist!");
		if (dir.isFile())
			throw new RuntimeException(directory + " is a file, not a directory!");
		List<HashMap> files = getFileList(directory);
		List<String>  fileNames = new ArrayList<String>();
		for ( HashMap filehash : files )
			fileNames.add(filehash.get("filename").toString());

		return fileNames;
	}

	public List<FileHandler.FileAttributes> list(String directory) throws IOException, Exception {
		if (directory == null)
			throw new NullPointerException("Directory cannot be null!");
		File dir = new File(directory);
		if (dir.exists() == false)
			throw new RuntimeException(directory + " does not exist!");
		if (dir.isFile())
			throw new RuntimeException(directory + " is a file, not a directory!");

		List<HashMap> files = getFileList(directory);
		List<FileHandler.FileAttributes>  attributes = new ArrayList<FileHandler.FileAttributes>();
		for ( HashMap filehash : files )
		{
			FileHandler.FileAttributes fa = new FileHandler.FileAttributes();
			long mtimeLong;

			fa.filename = filehash.get("filename").toString();
			log.debug("fa.filename: " + fa.filename);
			fa.isDirectory = Boolean.valueOf(filehash.get("isDirectory").toString());
			log.debug("fa.isDirectory: " + fa.isDirectory);
			fa.size = Long.valueOf(filehash.get("size").toString());
			log.debug("fa.size: " + fa.size);
			mtimeLong = Long.valueOf(filehash.get("mtime").toString());
			//fa.mtime = filehash.get("mtime");
			fa.mtime = new Date(mtimeLong);
			log.debug("fa.mtime: " + fa.mtime);

			attributes.add(fa);
		}
		return attributes;
	}

	public Map<String, List<String>> listFilesByExtension(String directory) throws IOException, Exception {
		if (directory == null)
			throw new NullPointerException("Directory cannot be null!");
		File dir = new File(directory);
		if (dir.exists() == false)
			throw new RuntimeException(directory + " does not exist!");
		if (dir.isFile())
			throw new RuntimeException(directory + " is a file, not a directory!");
		List<HashMap> files = getFileList(directory);
		Map<String, List<String>> fileNamesMap = new HashMap<String, List<String>>();

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

	private List<HashMap> getFileList(String directory) throws IOException, Exception
	{
		List<HashMap> fileEntries = new ArrayList<HashMap>();
		int exitCode=0;
		List<String> filenamelist = new ArrayList<String>();
		try {

            Process process = Runtime.getRuntime().exec(new String[]{"/bin/bash", "-c","cd '" + directory + "' ; find -type f | cut --characters=3- | sort"});
			//exitCode = pr.run("(cd '" + directory + "' ; find -type f | cut --characters=3- | sort)");
			log.debug("(cd  '" + directory + "' ; find -type f | cut --characters=3- | sort)");

            StringBuilder out = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String filestring;
            while ((filestring = reader.readLine()) != null) {
				filenamelist.add(filestring);
                out.append(filestring).append('\n');
            }


            exitCode = process.waitFor();

			if (exitCode != 0) {
				log.debug("(cd  '" + directory + "' ; find -type f | cut --characters=3- | sort) failed with exitCode: " + exitCode);
				log.debug("stdout: " + out.toString());

				throw new IOException("(cd  '" + directory + "' ; find -type f | cut --characters=3- | sort) failed with exitCode: " + exitCode);
			}
            
			//String[] filenames = pr.getStdOut().split("\n");
			//for (String filestring : filenames) {
		    //		filenamelist.add(filestring);
			//} 
		}
		catch(IOException e)
		{
			log.debug("(cd '" + directory + "' ; find -type f | cut --characters=3- | sort) failed with exitCode: " + exitCode);

			throw new IOException("(cd '" + directory + "' ; find -type f | cut --characters=3- | sort) failed with exitCode: " + exitCode);
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

	public void removeFile(String file) {
		if (file == null)
			throw new NullPointerException("File cannot be null!");
		File myfile = new File(file);
		if (myfile.isDirectory())
			throw new RuntimeException("The submitted 'file' " + file + " is actually a directory!");
		if (myfile.exists() == false)
			throw new RuntimeException("The submitted 'file' " + file + " does not exist!");
		if (myfile.delete() == false)
			throw new RuntimeException("The submitted 'file' " + file + " could not be deleted!");
	}



	public InputStream readFile(String fileName) 
			throws FileNotFoundException, Exception 
	{
		return getInputStream(fileName);
	}


	public InputStream getInputStream(String fileName) throws Exception
	{
		return new FileInputStream(fileName);
	}

	public void writeFile(String fileName, String content) {
		if (fileName == null)
			throw new NullPointerException("File name cannot be null!");
		if (content == null)
			throw new NullPointerException("Content cannot be null!");
		FileWriter fw = null;
		try {
			fw = new FileWriter(fileName);
			fw.append(content);
		} catch (IOException e) {
			throw new RuntimeException("Can't write to file " + fileName, e);
		} finally {
			try { if(fw != null) fw.close(); } catch (IOException e) { ;}
		}
	}

	public void writeFile(String fileName, byte[] content) {
		if (fileName == null)
			throw new NullPointerException("File name cannot be null!");
		if (content == null)
			throw new NullPointerException("Content cannot be null!");
	    FileOutputStream fos = null;
	    try {
	      fos = new FileOutputStream(fileName);
	      fos.write(content);

	    } catch (IOException e) {
			throw new RuntimeException("Can't write to file " + fileName, e);
		} finally
		{
			try
			{
				if(fos != null)
				{
					fos.close();
				}
			}
			catch (IOException e) {  ; }
		}
	}

	public void writeFile(String newFileName, File file) throws Exception
	{
		if (newFileName == null)
			throw new NullPointerException("New file name name cannot be null!");
		if (file == null)
			throw new NullPointerException("File cannot be null!");
		try {
			writeFile(newFileName, readFile(file.getPath()));
		} catch (FileNotFoundException e) {
			throw new RuntimeException(file + " does not exist!", e);
		}
	}

	public void writeFile(String fileName, InputStream inStream) throws IOException
	{
		OutputStream outStream = new BufferedOutputStream(new FileOutputStream(fileName));

		try {
			byte[] readBuffer = new byte[8192];
			int bytesRead;

			while ((bytesRead = inStream.read(readBuffer, 0, readBuffer.length)) >= 0)
				outStream.write(readBuffer, 0, bytesRead);
		}
		finally {
			outStream.close();
		}
	}

	public void moveDirectory(String directoryName, String newDirectoryName) {
		if (directoryName == null)
			throw new NullPointerException("Old directory name name cannot be null!");
		if (newDirectoryName == null)
			throw new NullPointerException("New directory name name cannot be null!");
		File directory = new File(directoryName) ;
		if (directory.exists() == false)
			throw new NullPointerException(directory + " does not exist!");
		if (directory.canRead() == false)
			throw new NullPointerException(directory + " cannot be read!");
		File targetDirectory = new File(newDirectoryName) ;
		//directory.renameTo(targetDirectory);
		try
		{
			Files.move(directory.toPath(), targetDirectory.toPath());
		}
		catch(Exception e)
		{
			log.error("MOVE DIRECTORY ERROR: ", e);
		}
	}

	public void moveFile(String fileName, String newFileName) {
		if (fileName == null)
			throw new NullPointerException("Old directory name name cannot be null!");
		if (newFileName == null)
			throw new NullPointerException("New directory name name cannot be null!");
		File orgFile = new File(fileName) ;
		if (orgFile.exists() == false)
			throw new NullPointerException(orgFile + " does not exist!");
		if (orgFile.canRead() == false)
			throw new NullPointerException(orgFile + " cannot be read!");
		File targetFile = new File(newFileName) ;
		orgFile.renameTo(targetFile);
	}

	public boolean isDirectory(String filename)
	{
		return new File(filename).isDirectory();
	}

	public long getSize(String filename)
	{
		return new File(filename).length();
	}

	public Date getMTime(String filename)
	{
		return new Date(new File(filename).lastModified());
	}

    public Long getMTimeLong(String filename) throws IOException, Exception
    {
		return (new File(filename).lastModified());
    }
}

