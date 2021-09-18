package arapp;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

import com.google.gson.JsonObject;

import arapp.etc.DictionaryWord;
import arutils.db.DB;
import arutils.util.Utils;

public class AppEnv {
	private static EnvOverride defaultEnvOverride=null;
	
	
	public static void presetEnvName(String env) {
		if (defaultEnvOverride==null) defaultEnvOverride=new EnvOverride();
		defaultEnvOverride.presetEnvName(env);
	}
	public static void presetBootstrapResource(String boot) {
		if (defaultEnvOverride==null) defaultEnvOverride=new EnvOverride();
		defaultEnvOverride.presetBootstrapResource(boot);
	}	

	
	private static class Holder {
		final static AppScope appScope=AppScope.createNonDefaultAppScope(defaultEnvOverride);
	}
	public static AppScope getAppScope() {return Holder.appScope;}
	
	
	
	/*
	 * should never be called from inside SubSystem::init ever! Will wait for itself....
	 */
	public static void ready() {getAppScope().ready();}
	public static void destroy() {getAppScope().destroy();}
	
	

	public static boolean hasDB() {return getAppScope().hasDB();}
	public static DB flexDB() {return getAppScope().getFlexDB();}
	public static DB db() {return getAppScope().getBoundedDB();}
	
	public static DB boundedDB() {return getAppScope().getBoundedDB();}
	
	public static DB boundedDB(String dbName) {return getAppScope().getBoundedDB(dbName);}
	public static DB flexDB(String dbName) {return getAppScope().getFlexDB(dbName);}
	
	public static Long systemProcessId() {return getAppScope().getSystemProcessId();}
	public static boolean subSystemRegistered(String name) {return getAppScope().hasSubSystem(name);}
	public static <S> S subSystem(String name) {return getAppScope().getSubSystem(name);}
	public static Set<String> subSystemNames() {return getAppScope().getSubSystemNames();}
	
	public static Env env() {return getAppScope().getEnv();}
	public static Integer envId() {return env().getId();}
	public static Integer clusterMemberId() {return getAppScope().getClusterMemberId();}
	public static Long newId(String name) throws SQLException, InterruptedException {return getAppScope().newId(name);}
	public static JsonObject getMeta() {return getAppScope().getMeta();}
	
	
	
	public static DictionaryWord word(Number id) {return getAppScope().getDictionaryWord(id);}
	public static DictionaryWord word(String word) {return getAppScope().getDictionaryWord(word);}
	public static Future<Boolean> hasWord(String word) {return getAppScope().getHasWord(word);}
	public static Future<Boolean> hasWord(Number id) {return getAppScope().getHasWord(id);}
	public static boolean wordCached(String word) {return getAppScope().getWordCached(word);}
	public static boolean wordCached(Number id) {return getAppScope().getWordCached(id);}
	
	public static DictionaryWord word(String base,Number id) {return getAppScope().getDictionaryWord(base,id);}
	public static DictionaryWord word(String base, String word) {return getAppScope().getDictionaryWord(base,word);}
	public static Future<Boolean> hasWord(String base, String word) {return getAppScope().getHasWord(base, word);}
	public static Future<Boolean> hasWord(String base, Number id) {return getAppScope().getHasWord(base, id);}
	public static boolean wordCached(String base, String word) {return getAppScope().getWordCached(base, word);}
	public static boolean wordCached(String base, Number id) {return getAppScope().getWordCached(base, id);}
	
	public static long getTime() {return getAppScope().getTime();}
	
	
	public static void reloadEnvironment() {getAppScope().reloadEnvironment();}	
	public static void reloadSubSystems() {getAppScope().reloadSubSystems();}
	
	
	public static void logerr(String msg, Throwable e) {
		try {
			List<String> frames = Utils.getErrorFrames();
			if (frames.size()>0) frames.remove(0);
			getAppScope().getLogger().logerr(frames, msg, e);
		} catch (Throwable t) {
			Utils.rethrowRuntimeException("Failed to log oringal error with msg: "+msg+(e==null?"":"; and exception "+Utils.getStackTrace(e)), t);
		}
	}
	public static void logerr(String msg) {
		try {
			List<String> frames = Utils.getErrorFrames();
			if (frames.size()>0) frames.remove(0);
			getAppScope().getLogger().logerr(frames, msg, null);
		} catch (Throwable t) {
			throw new RuntimeException("Failed to log oringal error with msg: "+msg);
		}
	}
	
}
