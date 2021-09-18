package testapp;

import arapp.AppEnv;
import arapp.etc.DictionaryWord;
import arutils.util.JsonUtils;


public class TestApp1 {

	public static void main(String[] strs) throws Exception {
		//AppEnv.presetEnvName("test1");
		//AppScope.presetBootstrapResource("/home/alex/workspace/arweb/src/main/java");
		System.out.println(AppEnv.systemProcessId());
		//System.exit(0);
		AppEnv.ready();
		
		AppEnv.logerr("test error", new RuntimeException("zzz"));
		
		System.out.println(JsonUtils.prettyPrint(AppEnv.getMeta()));
		
		DictionaryWord dw=AppEnv.word(3);
		System.out.println(dw.getWord());
		
		Thread.sleep(1000);
		AppEnv.reloadEnvironment();
		
		
		Thread.sleep(1000);
		AppEnv.reloadSubSystems();
		
		Thread.sleep(10000);
		AppEnv.destroy();
		//AppScope.getAppScope().destroy();
		
	}

}
