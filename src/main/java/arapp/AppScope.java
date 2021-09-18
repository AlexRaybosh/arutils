package arapp;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import com.google.gson.JsonObject;
import arapp.boot.BootstrapEnv;
import arapp.boot.Init;
import arapp.boot.InitSubSystems;
import arapp.boot.SubSystemStub;
import arapp.etc.DictionaryWord;
import arapp.susbsystems.ProcessMaintenance;
import arutils.async.AsyncEngine;
import arutils.db.DB;
import arutils.util.DummyErrorFuture;
import arutils.util.DummyFuture;
import arutils.util.Utils;

public class AppScope {
	private EnvOverride envOverride=null;

	public EnvOverride getEnvOverride() {return envOverride;}
	
	public String getPresetEnvName() {
		return envOverride==null?null:envOverride.getPresetEnvName();
	}
	public String getPresetBootstrapResource() {
		return envOverride==null?null:envOverride.getPresetBootstrapResource();
	}
	
	
	public final static String PROCESS_MAINTENANCE="processMaintenance";
	

	public static AppScope createNonDefaultAppScope() {
		return createNonDefaultAppScope(null);
	}
	public static AppScope createNonDefaultAppScope(EnvOverride envOverride) {
		AppScope appScope=new AppScope(envOverride);
		appScope.init();
		return appScope;
	}


	final static ScheduledExecutorService scheduledExecutorService=Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
		   @Override
		   public Thread newThread(Runnable r) {
		      Thread thread =  new Thread(r, "scheduler-thread");
		      thread.setDaemon(true);
		      return thread;
		   }
		});


	
	public static ScheduledExecutorService getScheduledExecutorService() {return scheduledExecutorService;}
	public static ExecutorService getExecutorService() {return AsyncEngine.getEngineExecutorService();}
	private AppScope(EnvOverride envOverride) {
		this.envOverride=envOverride;


	}
	
	volatile Future<Init> initFuture;	
	volatile Map<String,Future<SubSystemStub>> subsystems=new ConcurrentHashMap<>(); 
	volatile BasicLogger logger=new BasicLogger();
	public final void setLogger(BasicLogger bl) {
		if (bl==null) throw new RuntimeException("Logger can't be null");
		logger=bl;
	}
	
	
	private void init() {
		initFuture=getExecutorService().submit(new Callable<Init>() {
			public Init call() throws Exception {
				return new Init(AppScope.this);
			}
		});
		final InitSubSystems subInit=new InitSubSystems(AppScope.this, subsystems, true);
		try {
			subInit.initialize();
			AsyncEngine.getEngineExecutorService().submit(()->{
				
				try {
					Init myInit = initFuture.get();
					myInit.derefAppSec();
					initFuture=new DummyFuture<>(myInit);
				} catch (Exception e) {
					BootstrapEnv.logerr("AppScope initialization failed: ",e);
					initFuture=new DummyErrorFuture<>(e);
				}
				
				subsystems=subInit.resolve();
			});			
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
	
	public final void reloadEnvironment() {
		Future<Init> newInitFuture = getExecutorService().submit(new Callable<Init>() {
			public Init call() throws Exception {
				return new Init(AppScope.this);
			}
		});
		try {
			Init newInit = newInitFuture.get();
			newInit.derefAppSec();
			Init oldInit = initFuture.get();
			initFuture=new DummyFuture<>(newInit);
			oldInit.destroy();
		} catch (Exception e) {
			logerr("AppScope reload environment failed: ",Utils.extraceCause(e));
			Utils.rethrowRuntimeException(e);
		}
	}
	
	public final void reloadSubSystems() {
		Map<String,Future<SubSystemStub>> newSubSystems=new HashMap<>();
		InitSubSystems newSubInit=new InitSubSystems(AppScope.this, newSubSystems, false);
		
		try {
			newSubInit.initialize();
			newSubSystems=newSubInit.resolve();
			Map<String, Future<SubSystemStub>> oldSubSystems = subsystems;
			subsystems=newSubSystems;
			for (Entry<String, Future<SubSystemStub>> e : oldSubSystems.entrySet()) {
				String name=e.getKey();
				Future<SubSystemStub> f = e.getValue();
				try {
					SubSystemStub s=f.get();
					if (s!=null) s.destroy();
				} catch (Exception ex) {
					logerr("AppScope subsystem reload: "+name+" cleanup error", ex);
				}
			}
			
		} catch (Exception e) {
			Utils.rethrowRuntimeException(e);
		}
	}
	
	
	/*
	 * should never be called from inside SubSystem::init ever! Will wait for itself....
	 */
	public final void ready() {
		try {
			if (initFuture!=null) initFuture.get();
		} catch (Exception e) {
			Utils.rethrowRuntimeException("AppScope core initialization error", e);
		}
		for (Entry<String, Future<SubSystemStub>> e : subsystems.entrySet()) {
			String name=e.getKey();
			Future<SubSystemStub> f = e.getValue();
			try {
				f.get();
			} catch (Exception ex) {
				Utils.rethrowRuntimeException("AppScope subsystem: "+name+" initialization error", ex);
			}
		}
	}

	
	public final void destroy() {
		try {
			initFuture.get().destroy();		
			for (Entry<String, Future<SubSystemStub>> e : subsystems.entrySet()) {
				String name=e.getKey();
				Future<SubSystemStub> f = e.getValue();
				try {
					SubSystemStub s=f.get();
					if (s!=null) {
						s.destroy();
					}
				} catch (Exception ex) {
					logerr("AppScope subsystem: "+name+" destruction error: ", ex);
				}
			}
						
			getScheduledExecutorService().shutdownNow();
			getExecutorService().shutdownNow();
			
		} catch (Exception e) {
			logerr("Failed to destroy AppScope", e);
		}
		
		
	}

	
	public final boolean hasDB() {return getInit().hasDB();}
	public final DB getFlexDB() {return getInit().getFlexDB();}
	
	
	
	public final DB getFlexDB(String dbName) {return getInit().getFlexDB(dbName);}
	public final DB getBoundedDB(String dbName) {return getInit().getBoundedDB(dbName);}
	public final DB getBoundedDB() {return getInit().getBoundedDB();}
	
	public final Long getSystemProcessId() {
		if (hasSubSystem(PROCESS_MAINTENANCE)) {
			ProcessMaintenance pm = this.<ProcessMaintenance>getSubSystem(PROCESS_MAINTENANCE);
			return pm==null?null:pm.getSystemProcessId();
		}
		return null;
	}
	
	public final boolean hasSubSystem(String name) {
		return subsystems.containsKey(name);
	}
	
	public final <S> S getSubSystem(String name) {
		if (name==null) throw new RuntimeException("null subSystemName is not allowed");
		Future<SubSystemStub> stub = subsystems.get(name);
		try {
			if (stub!=null) {
				if (InitSubSystems.isSubInitThread()) {
					String callerName=InitSubSystems.getSubInitThreadSubName();
					Map<String, Set<String>> subDep = InitSubSystems.getSubInitThreadDependency();
					Set<String> callerDeps=subDep.get(callerName);
					if (name.equals(callerName)) {
						throw new RuntimeException("Subsystem \""+name+ "\" initialization deadlock: waiting on itself to initialize");	
					}
					// can only allow to proceed, if dependency was explicitly expressed
					if (!callerDeps.contains(name)) {
						throw new RuntimeException("Subsystem \""+callerName+ "\" initialization deadlock: waiting on \""+name+"\" to initialize without explicit dependency on it");
					}
				}				
				SubSystemStub s = stub.get();
				if (s!=null) return s.<S>getSubSystem();
			}
		} catch (Exception ex) {
			return Utils.rethrowRuntimeException(ex);
		}
		throw new RuntimeException("Subsystem "+name+" is not registered or failed to initialize");
	}
	
	
	
	
	public final Env getEnv() {return getInit().getEnv();}
	public final Integer getEnvId() {return getEnv().getId();}
	
	
	public final Integer getClusterMemberId() {
		return hasSubSystem(PROCESS_MAINTENANCE)?this.<ProcessMaintenance>getSubSystem(PROCESS_MAINTENANCE).getClusterMemberId():null;		
	}


	public final Long newId(String name) throws SQLException, InterruptedException {
		try {
			return initFuture.get().getDbid().next(name);
		} catch (Exception e) {
			e=Utils.proceedUnlessSQLOrInterrupted(e);
			return Utils.rethrowRuntimeException(e);
		}
	}
	
	
	private Init getInit() {
		try {
			return initFuture.get();
		} catch (Exception e) {
			return Utils.rethrowRuntimeException(e);
		}
	}
	
	
	
	public final Set<String> getSubSystemNames() {return subsystems.keySet();}

	
	public final long getTime() {return System.currentTimeMillis();}
	
	
	public void logerr(String msg, Throwable e) {
		try {
			List<String> frames = Utils.getErrorFrames();
			if (frames.size()>0) frames.remove(0);
			logger.logerr(frames, msg, e);
		} catch (Throwable t) {
			Utils.rethrowRuntimeException("Failed to log oringal error with msg: "+msg+(e==null?"":"; and exception "+Utils.getStackTrace(e)), t);
		}
	}
	public void logerr(String msg) {
		try {
			List<String> frames = Utils.getErrorFrames();
			if (frames.size()>0) frames.remove(0);
			logger.logerr(frames, msg, null);
		} catch (Throwable t) {
			throw new RuntimeException("Failed to log oringal error with msg: "+msg);
		}
	}
	
	
	
	public final DictionaryWord getDictionaryWord(Number id) {return getInit().getDictionaryWord(id);}	
	
	public final DictionaryWord getDictionaryWord(String word) {return getInit().getDictionaryWord(word);}	
	public final Future<Boolean> getHasWord(String word) {return getInit().hasWord(word);}
	public final Future<Boolean> getHasWord(Number id) {return getInit().hasWord(id);}
	public final boolean getWordCached(String word) {return getInit().wordCached(word);}
	public final boolean getWordCached(Number id) {return getInit().wordCached(id);}
	

	public final DictionaryWord getDictionaryWord(String base,Number id) {return getInit().getDictionaryWord(base,id);}	
	
	public final DictionaryWord getDictionaryWord(String base, String word) {return getInit().getDictionaryWord(base,word);}
	public final Future<Boolean> getHasWord(String base, String word) {return getInit().hasWord(base, word);}
	public final Future<Boolean> getHasWord(String base, Number id) {return getInit().hasWord(base, id);}
	public final boolean getWordCached(String base, String word) {return getInit().wordCached(base, word);}
	public final boolean getWordCached(String base, Number id) {return getInit().wordCached(base, id);}

	public final JsonObject getMeta() {return getEnv().getMeta();}

	public BasicLogger getLogger() {
		return logger;
	}
	
	

}
