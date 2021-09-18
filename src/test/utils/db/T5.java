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



public class T5 {

	public static void main(String[] args) throws Exception {
		DB db=DB.create("jdbc:mysql://localhost:3306/?autoReconnect=true&allowMultiQueries=true&cacheResultSetMetadata=true&emptyStringsConvertToZero=false&useInformationSchema=true&useServerPrepStmts=true&rewriteBatchedStatements=true", "business", "business");
		AsyncEngine engine=AsyncEngine.create();
		engine.register("shoveIt", createShoveItBackend(db));

		try {
			//DB db=DB.create("jdbc:mysql://localhost:3306/?autoReconnect=true&allowMultiQueries=true&cacheResultSetMetadata=true&emptyStringsConvertToZero=false&useInformationSchema=true&useServerPrepStmts=true&rewriteBatchedStatements=true", "business", "business");
			//DB db=DB.create("jdbc:mysql://192.168.1.2:6666/?autoReconnect=true&allowMultiQueries=true&cacheResultSetMetadata=true&emptyStringsConvertToZero=false&useInformationSchema=true&useServerPrepStmts=true", "business", "business");
			//DB db=DB.create("jdbc:mysql://192.168.1.2:6666/?autoReconnect=true&allowMultiQueries=true&cacheResultSetMetadata=true&emptyStringsConvertToZero=false&useInformationSchema=true&useServerPrepStmts=true&rewriteBatchedStatements=true", "business", "business");
	//		DB db=DB.create("jdbc:oracle:thin:@(DESCRIPTION=(sdu=32000)(ADDRESS=(PROTOCOL=TCP)(HOST=192.168.1.2)(PORT=1521))(CONNECT_DATA=(SID=orcl)(SERVER=DEDICATED)))", "business", "business");
	//		DB db=DB.create("jdbc:oracle:thin:@(DESCRIPTION=(sdu=32000)(ADDRESS=(PROTOCOL=TCP)(HOST=192.168.1.2)(PORT=1521))(CONNECT_DATA=(SID=orcl)(SERVER=DEDICATED)))", "model", "oracle");
			//db.setBatchSize(2);
			
			db.update("drop table if exists  workdb.t1 ");
			db.update("create table workdb.t1 (obj_id int unsigned not null,"
					+ "attr_id smallint unsigned not null,"
					+ "val MEDIUMTEXT not null,"
					+ "primary key(obj_id,attr_id)"
					+ ",index(attr_id,val(20))"
					//+ ") CHARACTER SET latin1");
					+ ") CHARACTER SET utf8 "
					);
					//+ "partition by hash(attr_id) partitions 8");
			db.setBatchSize(4096);
			//db.setBatchSize(1024);
			//db.setBatchSize(1);
			//db.setBatchSize(2048);
			//db.setMaxConnections(2);
			
			
			CompletionCallback callback=new CompletionCallback() {
				public void errored(Workload workload, Throwable e, Object[] args) {
					e.printStackTrace();
					System.exit(1);
				}
				public void completed(Workload workload, Object ret, Object[] args) {}
			};
			
			for (long i=0;i<20000000;++i) {
				
				//engine.call("shoveIt", Integer.MAX_VALUE+i, i+1, i+2);
				//engine.callWithCallback("shoveIt", callback,(long)Integer.MAX_VALUE+i, (int)(i & 0x0F), "meaningless #"+i);
				engine.callWithCallback("shoveIt", callback,i, i & 0x0F, "meaningless #"+i);
				//engine.callWithCallback("shoveIt", callback,i, i & 0x0F, "无意义/meaningless/бессмысленный #"+i);
			}
				
			
			long last=engine.last();
			System.out.println("Last: "+last);
			long complete=engine.completeLast().get();
			System.out.println("Complete: "+complete);
			System.out.println("done");
		} finally {
			db.close();
			se.shutdownNow();
		}
	}

	private static ServiceBackend createShoveItBackend(final DB db) {
		ServiceBackend backend=new ServiceBackend() {
			public void process(final List<Request> bulk) throws Exception {
				//String threadName=Thread.currentThread().getName();
				//System.out.println(threadName+" : "+bulk.size());
				db.commit(new StatementBlock<Void>() {
					public Void execute(ConnectionWrap cw) throws SQLException, InterruptedException {
						cw.batchInsert("insert into workdb.t1 values (?,?,?)", bulk);
						return null;
					}
				});
		
				reportAdd(bulk.size());
			}
			public int getMaxBulkSize() {return db.getBatchSize();}
			public long getWorkerReleaseTimeout() {return 30000;}//30 sec
			public int getMaxWorkers() {return db.getMaxConnections();}
			public int getMaxQueuedRequests() {return 100000;}
		};
		return backend;
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
