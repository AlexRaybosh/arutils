package arutils.db.tests;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import arutils.async.AsyncEngine;
import arutils.async.Request;
import arutils.async.ServiceBackend;
import arutils.db.ConnectionWrap;
import arutils.db.DB;
import arutils.db.StatementBlock;
import arutils.util.EncodingUtils;



public class T4_2 {

	public static void main(String[] args) throws Exception {
		DB db=DB.create("jdbc:mysql://localhost:3306/?autoReconnect=true&allowMultiQueries=true&cacheResultSetMetadata=true&emptyStringsConvertToZero=false&useInformationSchema=true&useServerPrepStmts=true&rewriteBatchedStatements=true", "business", "business");
		//DB db=DB.create("jdbc:mysql://192.168.1.2:6666/?autoReconnect=true&allowMultiQueries=true&cacheResultSetMetadata=true&emptyStringsConvertToZero=false&useInformationSchema=true&useServerPrepStmts=true", "business", "business");
		AsyncEngine engine=AsyncEngine.create();
		engine.register("shoveIt", createShoveItBackend(db));
		String s="" + 
			//	"BEGIN;\n" + 
			//	"DECLARE x VARCHAR(60);\n"+
				"set @s='create table if not exists workdb.t2 (id smallint unsigned, v varchar(45), b binary(16))'\n" + 
				//" if "
				"PREPARE stmt1 FROM @s; \n" + 
				"EXECUTE stmt1; \n" + 
				"DEALLOCATE PREPARE stmt1; \n" + 
			//	"END" + 
				"";
		
		db.update(s);
		try {
			db.update("drop table if exists  workdb.t0 ");
			db.update("create table workdb.t0 ("
					+ "con_id bigint not null,"
					+ "action_id int not null,"
					+ "record_id int not null,"
					+ "col_id int not null,"
					+ "val_num bigint null,"
					+ "val_text text null,"
					+ "val_blob blob null,"
					+ "primary key(con_id, action_id, record_id, col_id)) engine=memory");
			db.setBatchSize(4096);
			db.setMaxConnections(40);
			
			UUID uuid=UUID.randomUUID();
			byte[] b = EncodingUtils.uuidToByteArray(uuid);
			for (long i=0;i<20000000;++i) {
				int c=(int) (i%5);
				if (c==0)
					engine.call("shoveIt", i, i, c,null,null,null);
				else if (c==1)
					engine.call("shoveIt", i, i, c,i,null,null);
				else if (c==2)
					engine.call("shoveIt", i, i, c,null,"hi",null);
				else if (c==3)
					engine.call("shoveIt", i, i, c,null,null,null,b);
				//else if (c==4)
				//	engine.call("shoveIt", i, i, c,null,null,null,b);

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
			public int getMaxBulkSize() {return db.getBatchSize();}
			public long getWorkerReleaseTimeout() {return 30000;}
			public int getMaxWorkers() {return db.getMaxConnections();}
			public int getMaxQueuedRequests() {return 100000;}
			public void process(final List<Request> bulk) throws Exception {

				db.commit(new StatementBlock<Void>() {
					public Void execute(ConnectionWrap cw) throws SQLException, InterruptedException {
						cw.batchInsert("insert into workdb.t0 values (CONNECTION_ID(),?,?,?, ?,?,?)", bulk);
						cw.update("delete from workdb.t0 where con_id=CONNECTION_ID()",true);
						return null;
					}
				});
				
				reportAdd(bulk.size());
			}
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
