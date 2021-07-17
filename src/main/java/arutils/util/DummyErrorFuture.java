package arutils.util;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class DummyErrorFuture<T> implements Future<T> {
	private final Throwable err;

	public DummyErrorFuture(Throwable err) {
		this.err = err;
	}
	public DummyErrorFuture(String msg) {
		this.err = new RuntimeException(msg);
	}


	@Override
	public boolean cancel(boolean arg0) {
		return false;
	}

	@Override
	public T get() throws InterruptedException, ExecutionException {
		throw new ExecutionException(err);
	}

	@Override
	public T get(long arg0, TimeUnit arg1) throws InterruptedException, ExecutionException, TimeoutException {
		throw new ExecutionException(err);
	}

	@Override
	public boolean isCancelled() {
		return false;
	}

	@Override
	public boolean isDone() {
		return true;
	}
	

}
