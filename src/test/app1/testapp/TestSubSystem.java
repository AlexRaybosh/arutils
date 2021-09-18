package testapp;

import java.util.concurrent.atomic.AtomicLong;
import com.google.gson.JsonObject;
import arapp.SubSystem;

public class TestSubSystem extends SubSystem {

	@Override
	public boolean init(boolean initial, JsonObject conf) {
		System.out.println(getName()+" : "+conf);
		System.out.println(getAppScope().getSystemProcessId());
		return true;
	}

	@Override
	public void destroy() {
	}

	@Override
	public boolean tick(String tickName, Long lastRun) throws Exception {
		System.out.println(tickName+" : "+lastRun+"\t"+cnt.incrementAndGet());
		return true;
	}
	AtomicLong cnt=new AtomicLong();

}

