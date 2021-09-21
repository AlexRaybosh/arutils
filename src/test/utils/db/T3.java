package db;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import arutils.async.AsyncEngine;
import arutils.async.CompletionCallback;
import arutils.async.Request;
import arutils.async.ServiceBackend;
import arutils.async.Workload;
import arutils.db.BatchInputIterator;
import arutils.db.ConnectionWrap;
import arutils.db.DB;
import arutils.db.StatementBlock;



public class T3 {

	public static void main(String[] args) throws Exception {
		DB db=DB.create("jdbc:mysql://localhost:3306/?autoReconnect=true&allowMultiQueries=true&cacheResultSetMetadata=true&emptyStringsConvertToZero=false&useInformationSchema=true&useServerPrepStmts=true&rewriteBatchedStatements=true", "business", "business");
//		DB db=DB.create("jdbc:oracle:thin:@(DESCRIPTION=(sdu=32000)(ADDRESS=(PROTOCOL=TCP)(HOST=192.168.1.2)(PORT=1521))(CONNECT_DATA=(SID=orcl)(SERVER=DEDICATED)))", "business", "business");

		
		db.update("drop table if exists  workdb.t1 ");
		db.update("create table workdb.t1 (obj_id int unsigned not null,attr_id int unsigned not null,val varchar(200) not null,primary key(obj_id,attr_id))");
		db.setBatchSize(4096);

		
		AsyncEngine engine=AsyncEngine.create();
		engine.register("shoveIt", createShoveItBackend(db));
		
		CompletionCallback callback = createCallback();
		
		for (long id=0;id<20000000;++id) {
			
			//engine.call("shoveIt", Integer.MAX_VALUE+i, i+1, i+2);
			long attr_id=id & 0x0F;
			engine.callWithCallback("shoveIt", callback, id,attr_id, "Hello "+id);
		}
			
		
		engine.last();
		engine.completeLast().get();

		db.close();
		se.shutdownNow();
		
		System.out.println("done");
	}



	private static ServiceBackend<Void> createShoveItBackend(final DB db) {
		ServiceBackend<Void> backend=new ServiceBackend<Void>() {
			public void process(final List<Request<Void>> bulk) throws Exception {
				//String threadName=Thread.currentThread().getName();
				//System.out.println(threadName+" : "+bulk.size());
				db.commit(new StatementBlock<Void>() {
					public Void execute(ConnectionWrap cw) throws SQLException, InterruptedException {
						cw.batchInsertRequests("insert into workdb.t1 values (?,?,?)", bulk);
						return null;
					}
				});
		
				reportAdd(bulk.size());
			}
			public int getMaxBulkSize() {return db.getBatchSize();}
			public long getWorkerReleaseTimeout() {return 100;}
			public int getMaxWorkers() {return db.getMaxConnections();}
			public int getMaxQueuedRequests() {return 100000;}
		};
		return backend;
	}
	
	private static CompletionCallback createCallback() {
		CompletionCallback callback=new CompletionCallback() {
			public void errored(Workload workload, Throwable e, Object[] args) {
				e.printStackTrace();
				System.exit(1);
			}
			public void completed(Workload workload, Object ret, Object[] args) {}
		};
		return callback;
	}
	
	
	static ScheduledExecutorService se=Executors.newSingleThreadScheduledExecutor();
	static {
		se.scheduleWithFixedDelay(new Runnable(){
			public void run() {
				if (cnt.get()>0) {
					long num=cnt.get();
					long delta=num-prevNum;
					nextTs=System.currentTimeMillis();
				
					float secs=(nextTs-prevTs)/1000f;
					if (secs>0) { 
						float speed=delta/secs;
						System.out.printf("shoved %d records, speed %.02f rec a second\n", num, speed);
					
						prevTs=nextTs;
						prevNum=num;
					}

				}
			}
		}, 0, 2, TimeUnit.SECONDS);
	}
	static AtomicLong cnt=new AtomicLong();
	static long prevTs=System.currentTimeMillis(), nextTs=-1, prevNum;

	private static void reportAdd(int size) {
		long num=cnt.addAndGet(size);
	}
}
