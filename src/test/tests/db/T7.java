package tests.db;

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
import arutils.db.ConnectionWrap;
import arutils.db.DB;
import arutils.db.StatementBlock;



public class T7 {

	public static void main(String[] args) throws Exception {
		final DB db=DB.create("jdbc:mysql://localhost:3306/?autoReconnect=true&allowMultiQueries=true&cacheResultSetMetadata=true&emptyStringsConvertToZero=false&useInformationSchema=true&useServerPrepStmts=true&rewriteBatchedStatements=true", "business", "business");
		//final DB db=DB.create("jdbc:mysql://192.168.1.2:6666/?autoReconnect=true&allowMultiQueries=true&cacheResultSetMetadata=true&emptyStringsConvertToZero=false&useInformationSchema=true&useServerPrepStmts=true&rewriteBatchedStatements=true", "business", "business");		
		AsyncEngine engine=AsyncEngine.create();
		engine.register("shoveIt", createShoveItBackend(db));
		
		try {
			//DB db=DB.create("jdbc:mysql://192.168.1.2:6666/?autoReconnect=true&allowMultiQueries=true&cacheResultSetMetadata=true&emptyStringsConvertToZero=false&useInformationSchema=true&useServerPrepStmts=true", "business", "business");
	//		DB db=DB.create("jdbc:oracle:thin:@(DESCRIPTION=(sdu=32000)(ADDRESS=(PROTOCOL=TCP)(HOST=192.168.1.2)(PORT=1521))(CONNECT_DATA=(SID=orcl)(SERVER=DEDICATED)))", "business", "business");
	//		DB db=DB.create("jdbc:oracle:thin:@(DESCRIPTION=(sdu=32000)(ADDRESS=(PROTOCOL=TCP)(HOST=192.168.1.2)(PORT=1521))(CONNECT_DATA=(SID=orcl)(SERVER=DEDICATED)))", "model", "oracle");
			//db.setBatchSize(2);
			
			db.update("drop table if exists  workdb.t0 ");
			db.update("create table workdb.t0 (id int unsigned not null,v int unsigned not null,primary key(id))");
			db.setBatchSize(4096);
			db.setMaxConnections(40);
			final Object sk=db.addInitSqlWithCleanup(false,
					"create temporary table tempdb.X (id int unsigned ,v int unsigned not null) engine=MEMORY", 
					"drop temporary table if exists tempdb.X");
			
			db.commit(new StatementBlock<Void>() {
				public Void execute(ConnectionWrap cw) throws SQLException,	InterruptedException {
					db.removeInitStatementKey(false, sk);
					return null;
				}
			});
			db.addInitSqlWithCleanup(false,
					"create temporary table tempdb.X (id int unsigned ,v int unsigned not null) engine=MEMORY", 
					"drop temporary table if exists tempdb.X");
			db.commit(new StatementBlock<Void>() {
				public Void execute(ConnectionWrap cw) throws SQLException,	InterruptedException {
					System.out.println(""+cw.selectSingle("select now()"));
					return null;
				}
			});
			
			db.removeInitStatementKey(false, sk);
			
			CompletionCallback callback=new CompletionCallback() {
				public void errored(Workload workload, Throwable e, Object[] args) {
					e.printStackTrace();
					System.exit(1);
				}
				public void completed(Workload workload, Object ret, Object[] args) {}
			};
			
			long start=System.currentTimeMillis();
			for (long i=0;i<200000000;++i)
				engine.call("shoveIt", i, i);
				//engine.callWithCallback("shoveIt", callback,i, i);

			long last=engine.last();
			System.out.println("Last: "+last);
			long complete=engine.completeLast().get();
			long end=System.currentTimeMillis();
			System.out.println("Complete: "+complete+" in "+(end-start)+" ms.; perf "+(complete/(end-start)*1000f)+" rec/sec");
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
						cw.batchInsert("insert into tempdb.X values (?,?)", bulk);
						//cw.update("insert into workdb.t0 select * from tempdb.X",true);
						//cw.update("delete from tempdb.X",true);
						return null;
					}
				});
				reportAdd(bulk.size());
			}



			public int getMaxBulkSize() {return db.getBatchSize();}
			public long getWorkerReleaseTimeout() {return 30000;}
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
		}, 0, 4, TimeUnit.SECONDS);
	}
	static AtomicLong cnt=new AtomicLong();
	static long prevTs=System.currentTimeMillis(), nextTs=-1, prevNum;

	private static void reportAdd(int size) {
		long num=cnt.addAndGet(size);
	}
}
