package arapp.boot;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import arapp.AppScope;
import arapp.Env;
import arapp.etc.DictionaryBase;
import arapp.etc.DictionaryWord;
import arutils.db.DB;
import arutils.db.DBID;
import arutils.util.DummyErrorFuture;
import arutils.util.DummyFuture;
import arutils.util.JsonUtils;
import arutils.util.Utils;

public 	class Init {
	

	final DBID dbid;
	volatile Future<AppSec> appSecFuture;
	//ClusterMember clusterMember;
	final DBPair coreDB;
	final Map<String,DBPair> sideDBs=new HashMap<>();
	final Env env;
	final boolean hasDB;
	final AppScope appScope;
	
	public final AppSec getAppSec() {
		try {
			return appSecFuture.get();
		} catch (Exception e) {
			return Utils.rethrowRuntimeException(e);
		}
	}
	
	public void derefAppSec()  {
		try {
			appSecFuture=new DummyFuture<AppSec>(appSecFuture.get());
		} catch (Exception e) {
			BootstrapEnv.logerr("AppScope security initialization failed: ",e);
			appSecFuture=new DummyErrorFuture<>(e);
		}
		
	}
	
	public Init(AppScope appScope) throws Exception {
		this.appScope=appScope;
		BootstrapEnv bs=BootstrapEnv.bootstrap(appScope);
		hasDB=bs.getDB()!=null;
		env=bs.getEnv();
		appSecFuture=bs.getAppSecFuture();
		if (hasDB) {
			DB flexDB=bs.getDB();
			DB boundedDB=flexDB.clone();
			initDB("core",true, boundedDB);
			initDB("core",false, flexDB);
			dbid=new DBID(flexDB,"seq");
			coreDB=new DBPair(boundedDB, flexDB);
		} else {
			dbid=null;
			coreDB=null;
		}
		JsonObject allDBsConf = JsonUtils.getJsonObject(env.getMeta(), "db");

		if (null!=allDBsConf && !bs.isDBDisabled())for (Entry<String, JsonElement> e : allDBsConf.entrySet()) {
			String dbName=e.getKey();
			if ("core".equals(dbName)) continue;
			JsonObject dbConf = JsonUtils.getJsonObject(e.getValue());
			if (dbConf==null) continue;
			String dburl=JsonUtils.getString(dbConf,"properties", "dburl");
			if (dburl==null) {
				BootstrapEnv.logerr("Skipping DB "+dbName+", no dburl available");
				continue;
			}
			String dbuser=JsonUtils.getString(dbConf,"properties", "dbuser");
			String dbpassword=JsonUtils.getString(dbConf,"properties", "dbpassword");
			String dbpasswordBootstrapPropertyName=JsonUtils.getString(dbConf,"properties", "dbpasswordBootstrapPropertyName");
			
			if (dbpasswordBootstrapPropertyName!=null && bs.getProperties().containsKey(dbpasswordBootstrapPropertyName)) {
				dbpassword=bs.getProperties().getProperty(dbpasswordBootstrapPropertyName);
			}
			DB flexDB=DB.create(dburl, dbuser, dbpassword);
			flexDB=BootstrapEnv.reinit(flexDB, env.getMeta(), dbName, dburl, dbuser, dbpassword);
			DB boundedDB=flexDB.clone();
			initDB(dbName,true, boundedDB);
			initDB(dbName,false, flexDB);
			DBPair pair = new DBPair(boundedDB, flexDB);
			sideDBs.put(dbName, pair);
		}
		if (coreDB!=null) {
			sideDBs.put("core", coreDB);
		}
	}
	public final boolean hasDB() {			
		return hasDB;
	}

	private void initDB(String name, boolean bounded, DB db) {
		String poolName=bounded?"bounded":"flex";
		db.setMaxCachedPreparedStatements(JsonUtils.getInteger(50, env.getMeta(),"db", name, "pool", poolName, "maxCachedPreparedStatements"));
		db.setMaxConnections(JsonUtils.getInteger(5, env.getMeta(), "db", name, "pool", poolName, "maxConnections"));
		db.setRetryTimeout(TimeUnit.MILLISECONDS, JsonUtils.getLong(20000L,env.getMeta(),"db", name, "pool", poolName, "retryTimeoutMilliseconds"));
		
		//"db":{"pool":{"flex":{"initStatements"
		for (JsonElement e : JsonUtils.getJsonArrayIterable(env.getMeta(), "db", name, "pool", poolName, "initStatements")) {
			String onOpen=JsonUtils.getString(e, "onOpen");
			String onClose=JsonUtils.getString(e, "onClose");
			boolean autoCommit=JsonUtils.getBool(e, "autoCommit");
			db.addInitSqlWithCleanup(autoCommit, onOpen, onClose);
		}
		db.allowOverborrow(!bounded);
	}



	public final DB getFlexDB() {
		if (!hasDB()) throw new RuntimeException("core DB is not available");
		return coreDB.flexDB;
	}
	public final DB getBoundedDB() {
		if (!hasDB()) throw new RuntimeException("core DB is not available");
		return coreDB.boundedDB;
	}
	public void destroy() {
		for (DBPair pair : sideDBs.values()) {
			if (pair!=null) {
				if (pair.boundedDB!=null) pair.boundedDB.close();
				if (pair.flexDB!=null) pair.flexDB.close();					
			}
		}
	}
	public final DB getFlexDB(String dbName) {
		DBPair pair = sideDBs.get(dbName);
		if (pair==null || pair.flexDB==null) throw new RuntimeException(dbName+" DB is not available");
		return pair.flexDB;
	}
	public final DB getBoundedDB(String dbName) {
		DBPair pair = sideDBs.get(dbName);
		if (pair==null || pair.boundedDB==null) throw new RuntimeException(dbName+" DB is not available");
		return pair.boundedDB;
	}
	public final Env getEnv() {
		return env;
	}
	public final DBID getDbid() {
		return dbid;
	}
	public final DictionaryWord getDictionaryWord(String word) {return getDefaultDictionaryBase().word(word);}
	public final DictionaryWord getDictionaryWord(Number id) {return getDefaultDictionaryBase().word(id);}
	Object lock=new Object(); 
	DictionaryBase defaultDictionaryBase;
	Map<String,DictionaryBase> dictionaryBaseMap;
	
	private DictionaryBase getDefaultDictionaryBase() {
		synchronized (lock) {
			if (defaultDictionaryBase==null) {
				if (!hasDB()) throw new RuntimeException("core DB is not available");
				defaultDictionaryBase=new DictionaryBase(appScope);
			}
			return defaultDictionaryBase;
		}
	}
	private DictionaryBase getDictionaryBase(String base) {
		if (base==null) return getDefaultDictionaryBase();
		synchronized (lock) {
			if (dictionaryBaseMap==null) dictionaryBaseMap=new HashMap<>();
			DictionaryBase dict=dictionaryBaseMap.get(base);
			if (dict==null) {
				if (!hasDB()) throw new RuntimeException("core DB is not available");
				dict=new DictionaryBase(appScope, base);
			}
			return dict;
		}
	}
	
	public Future<Boolean> hasWord(String word) {
		return getDefaultDictionaryBase().has(word);
	}
	public Future<Boolean> hasWord(Number id) {
		return getDefaultDictionaryBase().has(id);
	}
	public boolean wordCached(String word) {
		return getDefaultDictionaryBase().cached(word);
	}
	public boolean wordCached(Number id) {
		return getDefaultDictionaryBase().cached(id);
	}
	public DictionaryWord getDictionaryWord(String base, String word) {
		return getDictionaryBase(base).word(word); 
	}
	public DictionaryWord getDictionaryWord(String base, Number id) {
		return getDictionaryBase(base).word(id);
	}
	public Future<Boolean> hasWord(String base, String word) {
		return getDictionaryBase(base).has(word);
	}
	public Future<Boolean> hasWord(String base, Number id) {
		return getDictionaryBase(base).has(id);
	}
	public boolean wordCached(String base, String word) {
		return getDictionaryBase(base).cached(word);
	}
	public boolean wordCached(String base, Number id) {
		return getDictionaryBase(base).cached(id);
	}

}
