package arapp.etc;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Future;

import arapp.AppScope;
import arutils.async.AsyncEngine;
import arutils.async.Request;
import arutils.async.Service;
import arutils.async.ServiceBackend;
import arutils.db.ConnectionWrap;
import arutils.db.DB.Dialect;
import arutils.db.StatementBlock;
import arutils.util.DummyFuture;
import arutils.util.JsonUtils;
import arutils.util.Utils;


public class DictionaryBase {
	final String tableName;
	final String base;
	final AsyncEngine asyncEngine=AsyncEngine.create();
	final Service<DictionaryWord> lookupCreateByName;
	final Service<DictionaryWord> lookupById;
	final Service<Boolean> checkByName;
	final Service<Boolean> checkById;	
	final String selectByNameSql;
	final String selectSingleByNameSql;
	final String selectByIdSql;
	final String selectSingleByIdSql;
	final String insertNewSql;
	final String idFieldName;
	final int cacheSize;
	final IdCache idCache;
	final NameCache nameCache;
	final Object lock=new Object();
	final boolean mysqlDialect;
	final int MAX_WORD_LENGTH;
	final AppScope appScope;
	
	public DictionaryBase(AppScope appScope) {
		this(appScope, null);
	}
	public DictionaryBase(AppScope appScope, String _base) {
		this.appScope=appScope;
		if (Utils.isEmpty(_base)) {
			_base="word";
		} 
		base=_base;
		tableName=base+"_dictionary";	
		idFieldName=tableName+"_id";
		cacheSize=JsonUtils.getInteger(64535, appScope.getMeta(),"etc","dictionary",base,"cacheSize");
		idCache=new IdCache();
		nameCache=new NameCache();
		MAX_WORD_LENGTH=JsonUtils.getInteger(700, appScope.getMeta(),"etc","dictionary",base,"maxWordLength");
		lookupCreateByName=asyncEngine.register("lookupCreateByName", new ServiceBackend<DictionaryWord>() {	
			public void process(List<Request<DictionaryWord>> bulk) throws Exception {lookupCreateByNameBulk(bulk);}
			public int getMaxWorkers() {return JsonUtils.getInteger(3, appScope.getMeta(),"etc","dictionary",base,"concurrency");}
			public int getMaxQueuedRequests() {return JsonUtils.getInteger(10000, appScope.getMeta(),"etc","dictionary",base,"queueSize");}
			public int getMaxBulkSize() {return JsonUtils.getInteger(256, appScope.getMeta(),"etc","dictionary",base,"bulkSize");}
		});
		lookupById=asyncEngine.register("lookupById", new ServiceBackend<DictionaryWord>() {	
			public void process(List<Request<DictionaryWord>> bulk) throws Exception {lookupByIdBulk(bulk);}
			public int getMaxWorkers() {return JsonUtils.getInteger(3, appScope.getMeta(),"etc","dictionary",base,"concurrency");}
			public int getMaxQueuedRequests() {return JsonUtils.getInteger(10000, appScope.getMeta(),"etc","dictionary",base,"queueSize");}
			public int getMaxBulkSize() {return JsonUtils.getInteger(256, appScope.getMeta(),"etc","dictionary",base,"bulkSize");}
		});
		checkByName=asyncEngine.register("checkByName", new ServiceBackend<Boolean>() {	
			public void process(List<Request<Boolean>> bulk) throws Exception {checkByNameBulk(bulk);}
			public int getMaxWorkers() {return JsonUtils.getInteger(3, appScope.getMeta(),"etc","dictionary",base,"concurrency");}
			public int getMaxQueuedRequests() {return JsonUtils.getInteger(10000, appScope.getMeta(),"etc","dictionary",base,"queueSize");}
			public int getMaxBulkSize() {return JsonUtils.getInteger(256, appScope.getMeta(),"etc","dictionary",base,"bulkSize");}
		});
		checkById=asyncEngine.register("checkById", new ServiceBackend<Boolean>() {	
			public void process(List<Request<Boolean>> bulk) throws Exception {checkByIdBulk(bulk);}
			public int getMaxWorkers() {return JsonUtils.getInteger(2, appScope.getMeta(),"etc","dictionary",base,"concurrency");}
			public int getMaxQueuedRequests() {return JsonUtils.getInteger(10000, appScope.getMeta(),"etc","dictionary",base,"queueSize");}
			public int getMaxBulkSize() {return JsonUtils.getInteger(256, appScope.getMeta(),"etc","dictionary",base,"bulkSize");}
		});
		mysqlDialect=appScope.getFlexDB().getDialect()==Dialect.MYSQL || appScope.getFlexDB().getDialect()==Dialect.DRIZZLE_MYSQL || appScope.getFlexDB().getDialect()==Dialect.DRIZZLE;  
		String straightJoin=mysqlDialect?"straight_join":"join";
		
		selectByNameSql="select t.t1, d.id from common_tmp t "+straightJoin+" "+tableName+" d on (t.t1=d.word) ";
		selectByIdSql="select t.i1, d.id, d.word from common_tmp t left join "+tableName+" d on (t.i1=d.id) ";
		
		selectSingleByNameSql="select id from "+tableName+" where word=?";
		selectSingleByIdSql="select word from "+tableName+" where id=?";

		
		insertNewSql="insert into "+tableName+" (id, word, last_ms) values (?,?,?)";
	}
	
	
	
	protected void checkByNameBulk(List<Request<Boolean>> bulk) throws SQLException, InterruptedException {
		if (bulk.size()==1) {
			Request<Boolean> r = bulk.get(0);
			final String name=(String)r.getArgs()[0];
			Boolean exists=Utils.toLong( appScope.getFlexDB().selectSingle(selectSingleByNameSql, true, name)) !=null;
			r.setResult(exists);
			return;
		}
		
		final Map<String,List<Request<Boolean>>> map=new HashMap<>();  
		for (Request<Boolean> r : bulk) {
			String str=(String)r.getArgs()[0];
			List<Request<Boolean>> lst = map.get(str);
			if (lst==null) {lst=new ArrayList<>(1);	map.put(str, lst);}
			lst.add(r);
		}
		appScope.getFlexDB().commit(new StatementBlock<Void>() {
			public Void execute(ConnectionWrap cw) throws SQLException, InterruptedException {
				if (mysqlDialect) cw.update("delete from common_tmp", true); // only for mysql
				cw.batchInsertSingleColumn("insert into common_tmp (t1) values (?)", map.keySet());
				for (Object[] row : cw.select(selectByNameSql,true)) {
					String name=Utils.toString(row[0]);
					Long id=Utils.toLong(row[1]);
					if (id!=null) {
						List<Request<Boolean>> lst = map.remove(name);
						for (Request<Boolean> r : lst) r.setResult(true);
					}
				}
				if (mysqlDialect) cw.update("delete from common_tmp", true); // only for mysql
				return null;
			}
			public boolean onError(ConnectionWrap cw, boolean willAttemptToRetry, SQLException ex, long start, long now) throws SQLException, InterruptedException {
				return super.onError(cw, willAttemptToRetry, ex, start, now);
			}			
		});
		for (List<Request<Boolean>> lst : map.values()) {
			for (Request<Boolean> r : lst) r.setResult(false);
		}
	}
	final void lookupCreateByNameBulk(List<Request<DictionaryWord>> bulk) throws Exception {
		if (bulk.size()==1) {
			Request<DictionaryWord> r = bulk.get(0);
			final String name=(String)r.getArgs()[0];
			Long id=appScope.getFlexDB().commit(new StatementBlock<Long>() {
				public Long execute(ConnectionWrap cw) throws SQLException, InterruptedException {
					Long id=Utils.toLong( cw.selectSingle(selectSingleByNameSql, true, name) );
					if (id!=null) return id;
					id=appScope.newId(idFieldName);
					long now=appScope.getTime();
					cw.update(insertNewSql, true, id, name, now);
					return id;
				}
			});
			
			DictionaryWord dw=new DictionaryWord(id,name);
			r.setResult(dw);
			DummyFuture<DictionaryWord> f=new DummyFuture<DictionaryWord>(dw);
			synchronized (lock) {
				idCache.put(id, f);
			}
			return;
		}
		
		final Map<String,List<Request<DictionaryWord>>> map=new HashMap<>();  
		for (Request<DictionaryWord> r : bulk) {
			String str=(String)r.getArgs()[0];
			List<Request<DictionaryWord>> lst = map.get(str);
			if (lst==null) {lst=new ArrayList<>(1);	map.put(str, lst);}
			lst.add(r);
		}
		long now=appScope.getTime();
		final List<DictionaryWord> fresh=new ArrayList<>(map.size());
		Map<String, Long> nameToId = appScope.getFlexDB().commit(new StatementBlock<Map<String,Long>>() {
			public Map<String,Long> execute(ConnectionWrap cw) throws SQLException, InterruptedException {
				fresh.clear();
				if (mysqlDialect) cw.update("delete from common_tmp", true); // only for mysql
				cw.batchInsertSingleColumn("insert into common_tmp (t1) values (?)", map.keySet());
				for (Object[] row : cw.select(selectByNameSql,true)) {
					String name=Utils.toString(row[0]);
					Long id=Utils.toLong(row[1]);
					if (id!=null) {
						List<Request<DictionaryWord>> lst = map.remove(name);
						DictionaryWord dw=new DictionaryWord(id,name);
						for (Request<DictionaryWord> r : lst) {
							r.setResult(dw);
						}
						fresh.add(dw);
					}
				}
				
				Map<String,Long> ret=new HashMap<>(map.size());
				List<Object[]> inserts=new ArrayList<>(map.size());
				for (String name : map.keySet()) {
					Long id=appScope.newId(idFieldName);
					inserts.add(new Object[] {id, name, now});
					ret.put(name, id);
				}
				cw.batchInsert(insertNewSql, inserts);
				
				if (mysqlDialect) cw.update("delete from common_tmp", true); // only for mysql
				return ret;
			}
			public boolean onError(ConnectionWrap cw, boolean willAttemptToRetry, SQLException ex, long start, long now) throws SQLException, InterruptedException {
				return super.onError(cw, willAttemptToRetry, ex, start, now);
			}			
		});
		for (Entry<String, Long> e : nameToId.entrySet()) {
			String name=e.getKey();
			Long id=e.getValue();
			List<Request<DictionaryWord>> lst = map.remove(name);
			DictionaryWord dw=new DictionaryWord(id,name);
			for (Request<DictionaryWord> r : lst) r.setResult(dw);
			fresh.add(dw);
		}
		synchronized (lock) {
			for (DictionaryWord dw : fresh) {
				DummyFuture<DictionaryWord> f=new DummyFuture<DictionaryWord>(dw);
				idCache.put(dw.id, f );
			}
		}
	}

	protected void checkByIdBulk(List<Request<Boolean>> bulk) throws SQLException, InterruptedException {
		if (bulk.size()==1) {
			Request<Boolean> r = bulk.get(0);
			Long id=(Long)r.getArgs()[0];
			String name=Utils.toString( appScope.getFlexDB().selectSingle(selectSingleByIdSql, true, id) );
			r.setResult(name!=null);
			return;
		}
		final Map<Long,List<Request<Boolean>>> map=new HashMap<>();  
		for (Request<Boolean> r : bulk) {
			Long id=(Long)r.getArgs()[0];
			List<Request<Boolean>> lst = map.get(id);
			if (lst==null) {lst=new ArrayList<>(1);	map.put(id, lst);}
			lst.add(r);
		}
		 
		appScope.getFlexDB().commit(new StatementBlock<List<DictionaryWord> >() {
			public List<DictionaryWord>  execute(ConnectionWrap cw) throws SQLException, InterruptedException {
				if (mysqlDialect) cw.update("delete from common_tmp", true); // only for mysql
				cw.batchInsertSingleColumn("insert into common_tmp (i1) values (?)", map.keySet());
				for (Object[] row : cw.select(selectByIdSql,true)) {
					Long origId=Utils.toLong(row[0]);
					Long id=Utils.toLong(row[1]);				
					List<Request<Boolean>> lst = map.remove(origId);
					for (Request<Boolean> r : lst) r.setResult(id!=null);
				}
				if (mysqlDialect) cw.update("delete from common_tmp", true); // only for mysql
				return null;
			}
			public boolean onError(ConnectionWrap cw, boolean willAttemptToRetry, SQLException ex, long start, long now) throws SQLException, InterruptedException {
				return super.onError(cw, willAttemptToRetry, ex, start, now);
			}
		});
	}
	
	final void lookupByIdBulk(List<Request<DictionaryWord>> bulk) throws SQLException, InterruptedException {
		if (bulk.size()==1) {
			Request<DictionaryWord> r = bulk.get(0);
			Long id=(Long)r.getArgs()[0];
			String name=Utils.toString( appScope.getFlexDB().selectSingle(selectSingleByIdSql, true, id) );
			if (name==null) {
				RuntimeException ex = new RuntimeException("id "+id+" does not exist in "+tableName);
				r.errored(ex);
			} else {
				DictionaryWord dw=new DictionaryWord(id,name);
				r.setResult(dw);
				DummyFuture<DictionaryWord> f=new DummyFuture<DictionaryWord>(dw);
				synchronized (lock) {
					nameCache.put(name, f );
				}
			}
			return;
		}
		final Map<Long,List<Request<DictionaryWord>>> map=new HashMap<>();  
		for (Request<DictionaryWord> r : bulk) {
			Long id=(Long)r.getArgs()[0];
			List<Request<DictionaryWord>> lst = map.get(id);
			if (lst==null) {lst=new ArrayList<>(1);	map.put(id, lst);}
			lst.add(r);
		}
		 
		List<DictionaryWord> fresh=appScope.getFlexDB().commit(new StatementBlock<List<DictionaryWord> >() {
			public List<DictionaryWord>  execute(ConnectionWrap cw) throws SQLException, InterruptedException {
				List<DictionaryWord> fresh=new ArrayList<>(map.size());
				if (mysqlDialect) cw.update("delete from common_tmp", true); // only for mysql
				cw.batchInsertSingleColumn("insert into common_tmp (i1) values (?)", map.keySet());
				for (Object[] row : cw.select(selectByIdSql,true)) {
					Long origId=Utils.toLong(row[0]);
					Long id=Utils.toLong(row[1]);
					String name=Utils.toString(row[2]);
					List<Request<DictionaryWord>> lst = map.remove(origId);
					if (id!=null) {
						DictionaryWord dw=new DictionaryWord(id,name);
						for (Request<DictionaryWord> r : lst) r.setResult(dw);
						fresh.add(dw);
					} else {
						RuntimeException ex = new RuntimeException("id "+origId+" does not exist in "+tableName);
						for (Request<DictionaryWord> r : lst) r.errored(ex);
					}
				}
				if (mysqlDialect) cw.update("delete from common_tmp", true); // only for mysql
				return null;
			}
			public boolean onError(ConnectionWrap cw, boolean willAttemptToRetry, SQLException ex, long start, long now) throws SQLException, InterruptedException {
				return super.onError(cw, willAttemptToRetry, ex, start, now);
			}
		});
		synchronized (lock) {
			for (DictionaryWord dw : fresh) {
				DummyFuture<DictionaryWord> f=new DummyFuture<DictionaryWord>(dw);
				nameCache.put(dw.word, f );
			}
		}
	}
	
	@SuppressWarnings("serial")
	class IdCache extends LinkedHashMap<Long,Future<DictionaryWord>> {
		protected boolean removeEldestEntry(final Map.Entry<Long,Future<DictionaryWord>> eldest) {return super.size() > cacheSize;}
	    public IdCache() {super(cacheSize+1,1F, true);}
	};
	@SuppressWarnings("serial")
	class NameCache extends LinkedHashMap<String,Future<DictionaryWord>> {
		protected boolean removeEldestEntry(final Map.Entry<String,Future<DictionaryWord>> eldest) {return super.size() > cacheSize;}
	    public NameCache() {super(cacheSize+1,1F, true);}

	};
	private Future<DictionaryWord> nameCacheGet(String word) {
		synchronized (lock) {
			Future<DictionaryWord> stored=nameCache.get(word);
			if (stored!=null) return stored;
		}
		try {
			Future<DictionaryWord> ret = lookupCreateByName.call(asyncEngine, word);
			synchronized (lock) {
				nameCache.put(word, ret);
			}
			return ret;
		} catch (InterruptedException e) {
			return Utils.rethrowRuntimeException(e);
		}		
	}	
	private Future<DictionaryWord> idCacheGet(Long id) {
		synchronized (lock) {
			Future<DictionaryWord> stored=idCache.get(id);
			if (stored!=null) return stored;
		}
		try {
			Future<DictionaryWord> ret = lookupById.call(asyncEngine, id);
			synchronized (lock) {
				idCache.put(id, ret);
			}
			return ret;
		} catch (InterruptedException e) {
			return Utils.rethrowRuntimeException(e);
		}
		
	}
	

	public final Future<Boolean> has(String word) {
		if (word==null) return new DummyFuture<Boolean>(false);
		try {
			return checkByName.call(asyncEngine, word);
		} catch (InterruptedException e) {
			return Utils.rethrowRuntimeException(e);
		}
	}
	
	public final Future<Boolean> has(Number id) {
		if (id==null || id.longValue()==0) return new DummyFuture<Boolean>(false);
		try {
			return checkById.call(asyncEngine, id.longValue());
		} catch (InterruptedException e) {
			return Utils.rethrowRuntimeException(e);
		}
	}
	public final boolean cached(String word) {
		if (word==null) return false;
		synchronized (lock) {
			return nameCache.containsKey(word);
		}
	}
	
	public final boolean cached(Number id) {
		if (id==null || id.longValue()==0) return false;
		synchronized (lock) {
			return idCache.containsKey(id);
		}
	}
	

	public final DictionaryWord word(String word) {
		if (word==null) return new DictionaryWord(0L,null);
		if (word.length()>=MAX_WORD_LENGTH) throw new RuntimeException("word size limit is "+MAX_WORD_LENGTH+", word's lenght \""+word+"\" is "+word.length());
	
		Future<DictionaryWord> f=nameCacheGet(word);
		DictionaryWord dw=new DictionaryWord(word, f);
		return dw;
	}
	public final DictionaryWord word(Number n) {
		if (n==null) return null;
		Long id=n.longValue();
		if (id==0L) return new DictionaryWord(0L,null);
		Future<DictionaryWord> f=idCacheGet(id);
		DictionaryWord dw=new DictionaryWord(f);
		return dw;
	}


}
