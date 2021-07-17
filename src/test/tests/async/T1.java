package tests.async;

import java.util.ArrayList;
import java.util.List;

import arutils.async.AsyncEngine;
import arutils.async.Request;
import arutils.async.Result;
import arutils.async.ServiceBackend;

public class T1 {

	public static void main(String[] args) throws Exception {
		AsyncEngine engine=AsyncEngine.create();
		engine.register("MySquare", createMySquareBackend());
		
		List<Result> results=new ArrayList<>();
		for (int i=0;i<20;++i) {
			Result r=engine.call("MySquare", i);
			results.add(r);
		}
		
		Thread.sleep(500);
		
		for (Result r : results) {
			Object arg=r.getArgs()[0];			
			System.out.println(arg+"*"+arg+" = "+r.get());
		}


	}

	private static ServiceBackend createMySquareBackend() {

		ServiceBackend backend=new ServiceBackend() {
			@Override
			public void process(List<Request> bulk) throws Exception {
				String threadName=Thread.currentThread().getName();
				StringBuilder sb=new StringBuilder(threadName+" : ");
				for (Request r : bulk) {
					Integer num=(Integer)r.getArgs()[0];
					r.setResult(num*num);
					sb.append("\tA=").append(num);
				}
				System.out.println(sb);
				Thread.sleep(500);// <-- simulate IO
			}
			
			@Override
			public int getMaxBulkSize() {
				return 3;
			}

			@Override
			public long getWorkerReleaseTimeout() {return 1;}

			@Override
			public int getMaxWorkers() {return 3;}

			@Override
			public int getMaxQueuedRequests() {return 10;}
		};
		return backend;
	}

}
