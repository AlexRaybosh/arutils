package db;

import java.sql.SQLException;
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
import arutils.db.DBID;
import arutils.db.StatementBlock;





public class T4_ORA {
	static ScheduledExecutorService se=Executors.newSingleThreadScheduledExecutor();
	static {
		se.scheduleWithFixedDelay(new Runnable(){
			public void run() {
				if (cnt.get()>0) {
					long num=cnt.get();
					long delta=num-prevNum;
					nextTs=System.currentTimeMillis();
				
					float secs=(nextTs-prevTs)/1000f;

					float speed=delta/secs;
					System.out.printf("shoved %d records, speed %.02f rec a second\n", num, speed);
					
					prevTs=nextTs;
					prevNum=num;

				}
			}
		}, 0, 1, TimeUnit.SECONDS);
	}
	static AtomicLong cnt=new AtomicLong();
	static long prevTs=System.currentTimeMillis(), nextTs=-1, prevNum;

	public static void main(String[] args) throws Exception {
		DB db=DB.create("jdbc:oracle:thin:@(DESCRIPTION=(sdu=32000)(ADDRESS=(PROTOCOL=TCP)(HOST=192.168.1.2)(PORT=1521))(CONNECT_DATA=(SID=orcl)(SERVER=DEDICATED)))", "model", "oracle");
		//System.in.read();
		AsyncEngine engine=AsyncEngine.create();
		engine.register("shoveIt", createShoveItBackend(db));
		DBID dbid=new DBID(db,"workdb.seq");
		try {
			//DB db=DB.create("jdbc:mysql://192.168.1.2:6666/?autoReconnect=true&allowMultiQueries=true&cacheResultSetMetadata=true&emptyStringsConvertToZero=false&useInformationSchema=true&useServerPrepStmts=true", "business", "business");
	//		
	//		DB db=DB.create("jdbc:oracle:thin:@(DESCRIPTION=(sdu=32000)(ADDRESS=(PROTOCOL=TCP)(HOST=192.168.1.2)(PORT=1521))(CONNECT_DATA=(SID=orcl)(SERVER=DEDICATED)))", "business", "business");
	//		DB db=DB.create("jdbc:oracle:thin:@(DESCRIPTION=(sdu=32000)(ADDRESS=(PROTOCOL=TCP)(HOST=192.168.1.2)(PORT=1521))(CONNECT_DATA=(SID=orcl)(SERVER=DEDICATED)))", "model", "oracle");
			db.setBatchSize(4096);
			
			db.autocommit(new StatementBlock<Void>() {
				public Void execute(ConnectionWrap cw) throws SQLException, InterruptedException {
					if (0<cw.<Number>selectSingle("select count(*) from all_tables where lower(TABLE_NAME)='t0' and lower(OWNER)='workdb'").intValue()) {
						cw.update("drop table workdb.t0");
					}
					cw.update("create table workdb.t0 ("
							+ "id int not null,"
							+ "v1 int not null,"
							+ "v2 int not null,"
							+ "v3 int not null,"
							+ "v4 int not null"
							+ ") \n"
							+ "PCTFREE 0 INITRANS 64 STORAGE (INITIAL 128M NEXT 256M)\n"+
							"PARALLEL NOLOGGING"
							);					 
					cw.update("create index workdb.t0_idx on workdb.t0 (id) PCTFREE 0 INITRANS 64 STORAGE (INITIAL 64M NEXT 128M) PARALLEL NOLOGGING");

					return null;
				}
			});
			
			//db.update("insert into workdb.t1 values (?,?)",false, 0,0);
			db.setBatchSize(4096);
			//db.setBatchSize(8);
			db.setMaxConnections(30);
			//db.setBatchSize(1024);
			//db.setBatchSize(2048);
			//db.setMaxConnections(30);
			

			CompletionCallback callback=new CompletionCallback() {
				public void errored(Workload workload, Throwable e, Object[] args) {
					e.printStackTrace();
					System.exit(1);
				}
				public void completed(Workload workload, Object ret, Object[] args) {}
			};
			
			
			for (long i=0;i<100000000;++i)
				//engine.call("shoveIt",dbid.next("myid"), i, i, i, i);
				engine.callWithCallback("shoveIt",callback, dbid.next("myid"), i, i, i, i);

			long last=engine.last();
			System.out.println("Last: "+last);
			long complete=engine.completeLast().get();
			System.out.println("Complete: "+complete);
			System.out.println("done");
			
		} finally {
			db.close();
			//engine.shutdownNow();
			se.shutdownNow();
		}
	}

	private static ServiceBackend createShoveItBackend(final DB db) {
		ServiceBackend backend=new ServiceBackend<Void>() {
			public void process(final List<Request<Void>> bulk) throws Exception {
				//String threadName=Thread.currentThread().getName();
				//System.out.println(threadName+" : "+bulk.size());
				db.commit(new StatementBlock<Void>() {
					public Void execute(ConnectionWrap cw) throws SQLException, InterruptedException {
						cw.batchInsertRequests("insert into workdb.t0 values (?,?,?,?,?)", bulk);
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
	private static void reportAdd(int size) {
		cnt.addAndGet(size);
	}
	



}
