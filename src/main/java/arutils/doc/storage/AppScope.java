package arutils.doc.storage;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Future;

import arutils.async.AsyncEngine;
import arutils.async.Request;
import arutils.async.ServiceBackend;
import arutils.db.ConnectionWrap;
import arutils.db.DB;
import arutils.db.DBID;
import arutils.db.StatementBlock;

public class AppScope {
	final private AsyncEngine asyncEngine=AsyncEngine.create();
	private DB db;
	final private Properties props;
	private DBID dbid;
	
	public final Properties getProperties() {
		return props;
	}

	public final AsyncEngine getAsyncEngine() {
		return asyncEngine;
	}

	public final DB getDB() {
		return db;
	}

	volatile Long drift;
	public final long getDBTime() throws SQLException, InterruptedException {
		calcDBDrift();
		return System.currentTimeMillis()+drift;
	}

	private void calcDBDrift() throws SQLException, InterruptedException {
		if (drift==null) {
			long dd=getDrift();
			for (int i=0;i<3;++i) {
				long d=getDrift();
				if (Math.abs(d)<Math.abs(dd)) dd=d;
			}
			drift=dd;
		}
	}


	public AppScope(Properties p) {
		if (p==null) {
			String dburl="jdbc:mysql://localhost:3306/doc?autoReconnect=true&allowMultiQueries=true&cacheResultSetMetadata=true&emptyStringsConvertToZero=false&useInformationSchema=true&useServerPrepStmts=true&rewriteBatchedStatements=true";
			String dbuser="arutils";
			String dbpasswd="bloomberg";
			props=new Properties();
			props.put("dburl",dburl);
			props.put("dbuser",dbuser);
			props.put("dbpasswd",dbpasswd);
		} else
			this.props=new Properties(p);
	}
	
	public static AppScope createAppScope(Properties props) throws SQLException, InterruptedException {
		AppScope appScope=new AppScope(props);
		appScope.init();
		return appScope;
	}
	
	private void init() throws SQLException, InterruptedException {
		db=DB.create((String)props.get("dburl"), (String)props.get("dbuser"),(String)props.get("dbpasswd"));
		dbid=new DBID(db, "seq");
		
		asyncEngine.register("SelectLabelMap", new ServiceBackend() {
			public void process(final List<Request> bulk) throws Exception {		
				for (Request req : bulk) {
					Object[] args=req.getArgs();
					String sql=(String)args[0];
					boolean cache=false;
					if (args.length>1)
						cache=(Boolean)args[1];

					Object[] sqlArgs=null;
					if (args.length>2) {
						sqlArgs=new Object[args.length-2];
						System.arraycopy(args, 2, sqlArgs, 0, args.length-2);
					} else {
						sqlArgs=new Object[]{};
					}
					
					req.setResult(db.selectLabelMap(sql, cache, sqlArgs));
				}

				
			}
			public int getMaxBulkSize() {return 1;}
			public long getWorkerReleaseTimeout() {return 3000;}
			public int getMaxWorkers() {return db.getMaxConnections();}
			public int getMaxQueuedRequests() {return 100*db.getMaxConnections();}
		});

	}
	
	public Future<List<Map<String, Object>>> selectLabelMap(String sql, boolean cache, Object... args) throws InterruptedException {
		Object[] callArgs=new Object[2+args.length];
		callArgs[0]=sql;
		callArgs[1]=cache;
		System.arraycopy(args, 0, callArgs, 2, args.length);
		@SuppressWarnings("rawtypes")
		Future f= asyncEngine.call("SelectLabelMap", callArgs);
		return f;
	}
	

	private long getDrift() throws SQLException, InterruptedException {
		return db.<Number>selectSingle("SELECT 1000*UNIX_TIMESTAMP(current_timestamp(3))-?",true, System.currentTimeMillis()).longValue();
	}

	public static void main(String[] args) throws Exception {

		
		AppScope appScope=createAppScope(null);
	}

	public long nextId(String name) throws SQLException, InterruptedException {
		return dbid.next(name);
	}
}
