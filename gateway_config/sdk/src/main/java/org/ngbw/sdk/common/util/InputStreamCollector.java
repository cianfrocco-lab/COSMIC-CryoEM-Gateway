/*
 * InputStreamCollector.java
 */
package org.ngbw.sdk.common.util;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;


/**
 * 
 * @author Paul Hoover
 *
 */
public class InputStreamCollector extends Thread implements Future<String> {

	private final static int MAX_SIZE = 40 * 1024;
	private enum State {
		RUNNING,
		SUCCEEDED,
		FAILED
	}


	private final ReentrantLock m_stateLock = new ReentrantLock();
	private final Condition m_finished = m_stateLock.newCondition();
	private final InputStream m_stream;
	private State m_state = State.RUNNING;
	private String m_result;
	private Throwable m_error;


	public static Future<String> readInputStream(InputStream stream)
	{
		InputStreamCollector collector = new InputStreamCollector(stream);

		collector.start();

		return collector;
	}

	private InputStreamCollector(InputStream stream)
	{
		assert(stream != null);

		m_stream = stream;
	}

	@Override
	public void run()
	{
		BufferedReader reader = null; 
		try {
			InputStreamReader streamReader = new InputStreamReader(m_stream);
			reader = new BufferedReader(streamReader);
			StringBuilder resultBuilder = new StringBuilder();
			String line;
			int size = 0;

			while ((line = reader.readLine()) != null) {
				size += line.length();
				if (size > MAX_SIZE)
				{
					resultBuilder.append("... Truncated.  File too large ...\n");
				} else
				{
					resultBuilder.append(line);
					resultBuilder.append("\n");
				}
			}

			finishTask(resultBuilder.toString());
		}
		catch (IOException ioErr) {
			reportError(ioErr);
		}
		finally
		{
			if (reader != null)
			{
				try { reader.close(); } catch(Exception e) { ; }
			}
		}
	}

	// private methods

	private void finishTask(String result)
	{
		m_stateLock.lock();

		try {
			assert(m_state == State.RUNNING);

			m_state = State.SUCCEEDED;
			m_result = result;

			m_finished.signalAll();
		}
		finally {
			m_stateLock.unlock();
		}
	}

	private void reportError(Throwable error)
	{
		m_stateLock.lock();

		try {
			assert(m_state == State.RUNNING);

			m_state = State.FAILED;
			m_error = error;

			m_finished.signalAll();
		}
		finally {
			m_stateLock.unlock();
		}
	}

	// Future implementation

	public boolean cancel(boolean mayInterruptIfRunning)
	{
		return false;
	}

	public String get() throws ExecutionException, InterruptedException
	{
		m_stateLock.lock();

		try {
			if (m_state == State.RUNNING)
				m_finished.await();

			if (m_state == State.FAILED)
				throw new ExecutionException(m_error);

			return m_result;
		}
		finally {
			m_stateLock.unlock();
		}
	}

	public String get(long timeout, TimeUnit unit) throws ExecutionException, InterruptedException, TimeoutException
	{
		m_stateLock.lock();

		try {
			if (m_state == State.RUNNING && !m_finished.await(timeout, unit))
				throw new TimeoutException();

			if (m_state == State.FAILED)
				throw new ExecutionException(m_error);

			return m_result;
		}
		finally {
			m_stateLock.unlock();
		}
	}

	public boolean isCancelled()
	{
		return false;
	}

	public boolean isDone()
	{
		m_stateLock.lock();

		try {
			return m_state == State.SUCCEEDED || m_state == State.FAILED;
		}
		finally {
			m_stateLock.unlock();
		}
	}
}
