package arapp.boot;

import java.util.Map;
import java.util.Properties;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import arutils.util.JsonUtils;

public class BootEntryBuildin extends BootEntry {
	private Properties properties;
	@Override
	public boolean eval(BootstrapEnv bootstrapEnv, JsonObject conf) {
		JsonObject obj=JsonUtils.getJsonObject(conf, "properties");
		if (obj==null) return false;
		properties=new Properties();
		for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
			String name=e.getKey();
			String val=e.getValue().getAsString();
			if (val==null) continue;
			properties.put(name, val);
		}
		return true;
	}

	@Override
	public String getEnvName() {
		return properties.getProperty("env");
	}
	@Override
	public Properties getProperties() {
		return properties;
	}
}
