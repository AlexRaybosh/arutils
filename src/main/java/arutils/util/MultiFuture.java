package arutils.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class MultiFuture implements Future<List<? extends Object>> {

	private final List<Future<? extends Object>> list=new ArrayList<>();
	
	public MultiFuture() {}
	
	public MultiFuture(Future<? extends Object>...args) {
		for (Future<? extends Object> a : args) {
			list.add(a);
		}
	}
	public void add(Future<? extends Object> a ) {
		list.add(a);
	}
	
	@Override
	public boolean cancel(boolean c) {
		boolean r=true;
		for (Future<? extends Object> f : list) {
			if (!f.cancel(c)) r=false;
		}
		return r;
	}

	@Override
	public List<? extends Object> get() throws InterruptedException, ExecutionException {
		List<Object> ret=new ArrayList<>();
		for (Future<? extends Object> f : list) {
			ret.add(f.get());
		}
		return ret;
	}

	@Override
	public List<? extends Object> get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		//throw new RuntimeException("TODO: implement");
		return get();
	}

	@Override
	public boolean isCancelled() {
		boolean r=true;
		for (Future<? extends Object> f : list) {
			if (!f.isCancelled()) r=false;
		}
		return r;
	}

	@Override
	public boolean isDone() {
		boolean r=true;
		for (Future<? extends Object> f : list) {
			if (!f.isDone()) r=false;
		}
		return r;
	}

}
