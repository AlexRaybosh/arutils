package arapp.boot;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import arapp.SubSystem;
import arapp.AppScope;
import arutils.util.JsonUtils;

public class SubSystemStub {

	class Timer {
		final ScheduledFuture<?> tickFuture;
		final String name;
		final long start;
		final long interval;
		volatile boolean tickIsRunning;
		volatile boolean stopped;
		Long lastRun=null;
		
		public Timer(String perName, long start, long interval) {
			this.name=perName;
			this.start=start;
			this.interval=interval;
			this.tickFuture=AppScope.getScheduledExecutorService().scheduleWithFixedDelay(this::schedTick, start, interval, TimeUnit.MILLISECONDS);
		}
		
		void schedTick() {
			AppScope.getExecutorService().submit(this::tick);
		}
		
		void tick() {
			synchronized(this) {
				if (stopped) return;
				if (tickIsRunning) return;
				tickIsRunning=true;
			}
			try {
				stopped=!subsystem.onTick(name, lastRun);
				if (stopped) {
					tickFuture.cancel(true);
				}
			} finally {
				lastRun=System.currentTimeMillis();
				tickIsRunning=false;
			}			
		}

		public final void destroy() {
			tickFuture.cancel(true);
		}
		
	}

	@SuppressWarnings("unchecked")
	public final <S> S getSubSystem() {
		return (S)subsystem;
	}
	final SubSystem subsystem;
	final Map<String,Timer> timersMap=new HashMap<>();

	public SubSystemStub(SubSystem sub) {
		this.subsystem=sub;
	}
	public void init(String name, JsonObject conf) {
		JsonObject timers = JsonUtils.getJsonObject(conf, "timers");
		if (timers!=null) for (Map.Entry<String,JsonElement>  e : timers.entrySet()) {
			String perName=e.getKey();
			JsonObject perConf = JsonUtils.getJsonObject(e.getValue());
			if (perConf==null) continue;
			long start=JsonUtils.getLong(0L, perConf, "startAfterMilliseconds");
			Long interval=JsonUtils.getLong(perConf, "milliseconds");
			if (interval==null || interval<=0) {
				BootstrapEnv.logerr("Ignore periodical "+perName+" in "+conf+", no valid 'milliseconds' property defined in: "+perConf);
				continue;
			}
			
			Timer p=new Timer(perName, start, interval);
			timersMap.put(perName, p);
		}
		
	}
	public final void destroy() {
		for (Timer p : timersMap.values()) {
			p.destroy();
		}
		subsystem.destroy();
	}

}
