package arapp;

import com.google.gson.JsonObject;

public abstract class SubSystem {
	private String name;
	protected AppScope appScope;

	public final AppScope getAppScope() {return appScope;}
	
	public void setName(AppScope appScope, String name) {
		this.appScope=appScope;
		this.name=name;
	}
	public final String getName() {
		return name;
	}
	
	/**
	 * 
	 * @param initial - true - initial boostrap, false - reload
	 * @returns true on success - will be registered
	 * @throws Exception
	 */
	public abstract boolean init(boolean initial, JsonObject conf) throws Exception;
	public abstract void destroy();
	public boolean onTick(String tickName, Long lastRun) {
		try {
			return tick(tickName, lastRun);
		} catch (Exception e) {
			appScope.logerr("SubSystem "+getName()+" timer "+tickName+" error: ", e);
			return false;
		}
	}
	
	public abstract boolean tick(String tickName, Long lastRun) throws Exception;
}
