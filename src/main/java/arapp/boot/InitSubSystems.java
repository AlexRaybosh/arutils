package arapp.boot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import arapp.AppScope;
import arapp.SubSystem;
import arutils.async.AsyncEngine;
import arutils.async.Request;
import arutils.async.Service;
import arutils.async.ServiceBackend;
import arutils.util.DummyErrorFuture;
import arutils.util.DummyFuture;
import arutils.util.JsonUtils;
import arutils.util.Utils;

public class InitSubSystems {

	final JsonObject entries;
	//ExecutorService threadPool;
	AtomicInteger tc=new AtomicInteger();
	final LinkedHashSet<String> initOrder=new LinkedHashSet<>();
	final Map<String,Set<String>> subDependency=new HashMap<>();
	final AppScope appScope;
	final AsyncEngine asyncEngine=AsyncEngine.create();
	final Service<SubSystemStub> initService;
	final Map<String, Future<SubSystemStub>> appSubSystems;
	final boolean initial;
	
	
	public InitSubSystems(AppScope appScope, Map<String, Future<SubSystemStub>> subsystems, boolean initial) {
		this.appScope=appScope;
		this.appSubSystems=subsystems;
		this.initial=initial;
		entries = JsonUtils.getJsonObject(appScope.getMeta(), "subsystems", "entries");
		if (entries==null || entries.entrySet().size()==0) {
			initService=null;
			return;
		}
		
		for (Entry<String, JsonElement> e : entries.entrySet()) {
			String name=e.getKey();
			JsonObject conf = JsonUtils.getJsonObject(e.getValue());
			if (conf==null) {
				continue;
			}
			Set<String> pathTraversed=new LinkedHashSet<>();
			subDependency.put(name, new HashSet<>());
			addToOrder(name, name, pathTraversed);
		}
		
		final int initConcurrency = JsonUtils.getInteger(1, appScope.getMeta(), "subsystems", "initConcurrency");
		
		final Map<String,Integer> actualInitOrder=new HashMap<>();
		int cnt=0;
		for (String sn : initOrder) {
			actualInitOrder.put(sn, cnt);
			++cnt;
		}
		
		initService=asyncEngine.register("init",new ServiceBackend<SubSystemStub>() {
			
			@Override
			public void process(List<Request<SubSystemStub>> bulk) throws Exception {
				for (Request<SubSystemStub> r : bulk) {
					String name=(String)r.getArgs()[0];
					if (initial) ss.get().setSubInitThread(name, true,subDependency);
					try {
						SubSystemStub s=initSubSystem(name);
						r.setResult(s);
					} finally {
						if (initial) ss.remove();
					}
				}
			}
			public int getMaxWorkers() {return initConcurrency;}
			public int getMaxQueuedRequests() {return 100;}
			public int getMaxBulkSize() {return 1;}
		});
		
	}
	static class SS {
		boolean sit=false;
		String name;
		Map<String, Set<String>> subDependency;
		public void setSubInitThread(String name, boolean v, Map<String, Set<String>> subDependency) {
			this.name=name;
			this.sit=v;
			this.subDependency=subDependency;
		}
		public boolean isSubInit() {
			return sit;
		}
	}
	private final static ThreadLocal<SS> ss = new ThreadLocal<SS>() {
		@Override
		protected SS initialValue() {
			return new SS();
		}
	};
	public static boolean isSubInitThread() {
		boolean val = ss.get().isSubInit();
		if (!val) ss.remove();
		return val;
	}
	public static String getSubInitThreadSubName() {
		boolean val = ss.get().isSubInit();
		if (!val) {
			ss.remove();
			return null;
		}
		return ss.get().name;
	}
	public static Map<String, Set<String>> getSubInitThreadDependency() {
		boolean val = ss.get().isSubInit();
		if (!val) {
			ss.remove();
			return null;
		}
		return ss.get().subDependency;
	}
	


	private void addToOrder(String top, String name, Set<String> pathTraversed) {
		JsonObject conf=JsonUtils.getJsonObject(entries, name);
		if (conf==null) return;
		if (initOrder.contains(name)) return;
		for (JsonElement el : JsonUtils.getJsonArrayIterable(conf, "depends")) {
			String depName=JsonUtils.getString(el);
			if (Utils.isEmpty(depName)) {
				BootstrapEnv.logerr("Ignoring invalid dependency \""+depName+"\" for subsystem \""+name+ "\" : "+el);
				continue;
			}
			JsonElement depEntry = entries.get(depName);
			if (depEntry==null) {
				BootstrapEnv.logerr("Ignoring non-existent dependency \""+depName+"\" for subsystem \""+name+ "\" : "+conf);
				continue;
			}
			if (depEntry.isJsonNull()) 
				continue;
			if (!depEntry.isJsonObject()) {
				BootstrapEnv.logerr("Ignoring invalid dependency \""+depName+"\" : \""+depEntry+"\" for subsystem \""+name+ "\" : "+conf);
				continue;
			}
			
			Set<String> deps = subDependency.get(top);
			deps.add(depName);
			
			if (initOrder.contains(depName)) {
				continue;
			}
			if (pathTraversed.contains(depName)) {
				String msg="Loop dependency: \""+depName+"\" detected for subsystem \""+name+ "\"; path: "+pathTraversed;
				BootstrapEnv.logerr(msg);
				throw new RuntimeException(msg);
			}
			pathTraversed.add(depName);
			addToOrder(top, depName, pathTraversed);
		}
		initOrder.add(name);
		
	}

	public void initialize() throws InterruptedException {		
		for (String name : initOrder) {
			Future<SubSystemStub> f = initService.callNoLimit(asyncEngine, name);
			appSubSystems.put(name, f);
		}
	}
	


	protected SubSystemStub initSubSystem(String name) throws Exception {
		JsonObject conf=JsonUtils.getJsonObject(entries, name);
		if (conf==null) {
			return null;
		}
		for (JsonElement el : JsonUtils.getJsonArrayIterable(conf, "depends")) {
			String depName=JsonUtils.getString(el);
			if (!Utils.isEmpty(depName)) {
				Future<SubSystemStub> ds = appSubSystems.get(depName);
				if (ds!=null) try {
					/*SubSystemStub depStub = */ds.get();
				} catch (Exception e) {
					return Utils.rethrowCause(e);
				}
			}
		}
		boolean enabled=JsonUtils.getBoolean(true, conf, "enabled");
		if (!enabled) {
			return null;
		}
		
		
		String clazz=JsonUtils.getString(conf, "class");
		if (Utils.isEmpty(clazz)) {
			//BootstrapEnv.logerr("Ignore subsystem "+name+ ", no valid class defined in : "+conf);
			return null;
		}
		Object s=Class.forName(clazz).getConstructor().newInstance();
		if (!(s instanceof SubSystem)) {
			String msg="class: "+clazz+" is not a SubSystem, subsystem "+name+ " : "+conf;
			BootstrapEnv.logerr(msg);
			throw new RuntimeException(msg);
		}
		SubSystem sub = (SubSystem)s;
		SubSystemStub stub=new SubSystemStub(sub);
		sub.setName(appScope, name);
		try {
			boolean success=sub.init(initial, conf);
			if (!success) {
				sub.destroy();
				return null;
			}
		} catch (Exception e) {
			try {stub.destroy();} catch (Exception exx) {}
			throw e;
		}
		stub.init(name,conf);
		
		return stub;
	}

	public Map<String, Future<SubSystemStub>> resolve() {
		Map<String, Future<SubSystemStub>> ret=new HashMap<>();
		for (Entry<String, Future<SubSystemStub>> e : appSubSystems.entrySet()) {
			String name=e.getKey();
			Future<SubSystemStub> f = e.getValue();
			try {
				SubSystemStub s=f.get();
				if (s==null) continue;
				DummyFuture<SubSystemStub> dummy=new DummyFuture<SubSystemStub>(s);
				ret.put(name, dummy);
			} catch (Exception ex) {
				DummyErrorFuture<SubSystemStub> dummy=new DummyErrorFuture<>(Utils.extraceCause(ex));
				ret.put(name, dummy);
			}
		}
		return ret;
	}

}
