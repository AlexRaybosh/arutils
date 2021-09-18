package async;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import arutils.async.AsyncEngine;
import arutils.async.Request;
import arutils.async.ServiceBackend;
import arutils.async.Workload;

public class T3 {

	public static void main(String[] args) throws Exception {
		AsyncEngine engine=AsyncEngine.create();
		engine.register("MySquare", createMySquareBackend());
		
		final Workload w1=engine.createWorkload();
		final Workload w2=engine.createWorkload();
		
		ExecutorService es=Executors.newCachedThreadPool();
		for (int i=0;i<100;++i) {
			final int ii=i;
			es.submit(new Runnable() {public void run() {try {w1.callNoLimit("MySquare", ii);} catch (InterruptedException e) {e.printStackTrace();}}});
			es.submit(new Runnable() {public void run() {try {w2.call("MySquare", -ii);} catch (InterruptedException e) {e.printStackTrace();}}});			
		}
		
		w1.last();
		w2.last();
		
		System.out.printf("--> W1 Sent done signal, #submitted=%d,  #completed=%d\n",w1.getNumberOfSubmittedCalls(),w1.getNumberOfCompletedCalls());
		System.out.printf("--> W2 Sent done signal, #submitted=%d,  #completed=%d\n",w2.getNumberOfSubmittedCalls(),w2.getNumberOfCompletedCalls());
		

		Future<Long> end1 = w1.completeLast();
		Future<Long> end2 = w2.completeLast();
		System.out.println("<-- W1: Done processing #requests="+end1.get());
		System.out.println("<-- W2: Done processing #requests="+end2.get());
//		engine.shutdown();
		es.shutdown();
		
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
				Thread.sleep(100);
				
			}
			
			@Override
			public int getMaxBulkSize() {
				return 4;
			}

			@Override
			public long getWorkerReleaseTimeout() {return 1;}

			@Override
			public int getMaxWorkers() {return 3;}

			@Override
			public int getMaxQueuedRequests() {return 1;}
		};
		return backend;
	}

}
