package arapp.boot;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import com.google.gson.JsonObject;

import arutils.util.JsonUtils;
import arutils.util.Utils;

public class BootEntryFileRead extends BootEntry {
	private Properties properties;
	@Override
	public boolean eval(BootstrapEnv bootstrapEnv, JsonObject conf) {
		String fileName=JsonUtils.getString(conf, "file");
		if (fileName==null) return false;
		Path path=Paths.get(fileName);
		boolean abortOnFileMissing=JsonUtils.getBool(conf, "abortOnFileMissing");
		boolean abortOnLoadError=JsonUtils.getBool(conf, "abortOnLoadError");
		

		if (!Files.exists(path)) {
			if (abortOnFileMissing) throw new RuntimeException(fileName+" in "+conf+" is missing");
			else if (bootstrapEnv.logErrors) BootstrapEnv.logerr(fileName+" in "+conf+" is missing");
			return false;
		}
		
		try {
			properties=new Properties();
			properties.load(new StringReader(new String(Files.readAllBytes(path), StandardCharsets.UTF_8)));
		} catch (Exception e) {
			if (abortOnLoadError) Utils.rethrowRuntimeException(fileName+" in "+conf+" failed to read", e);
			else if (bootstrapEnv.logErrors) BootstrapEnv.logerr(fileName+" in "+conf+" failed to read: "+e.getMessage());
			return false;
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
