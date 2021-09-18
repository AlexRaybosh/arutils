package arapp.boot;

import java.util.Properties;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import arutils.util.JsonUtils;
import arutils.util.Utils;

public abstract class BootEntry {
	public static BootEntry create(JsonElement entry) {
		String propertiesProviderType=JsonUtils.getString(entry,"propertiesEntryType");
		if (Utils.isEmpty(propertiesProviderType)) return null;
		if ("SHELL_EVAL".equals(propertiesProviderType)) return new BootEntryShellEval();
		if ("FILE_EXEC".equals(propertiesProviderType)) return new BootEntryFileExec();
		if ("FILE".equals(propertiesProviderType)) return new BootEntryFileRead();
		if ("BUILDIN".equals(propertiesProviderType)) return new BootEntryBuildin();
		return null;
	}
	
	public abstract String getEnvName();
	public abstract Properties getProperties();
	public abstract boolean eval(BootstrapEnv bootstrapEnv, JsonObject obj);

	

}
