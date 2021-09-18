package arapp;

public class EnvOverride {
	private String presetEnv=null;
	private String presetBootstrapResource=null;
	
	public EnvOverride withPresetEnv(String env) {
		EnvOverride n=new EnvOverride();
		n.presetBootstrapResource=presetBootstrapResource;
		n.presetEnv=env;
		return n;
	}
	public EnvOverride withPresetBootstrapResource(String bs) {
		EnvOverride n=new EnvOverride();
		n.presetBootstrapResource=bs;
		n.presetEnv=presetEnv;
		return n;
	}
	
	public void presetEnvName(String env) {
		presetEnv=env;
	}
	public void presetBootstrapResource(String boot) {
		presetBootstrapResource=boot;
	}	
	public String getPresetEnvName() {
		return presetEnv;
	}
	public String getPresetBootstrapResource() {
		return presetBootstrapResource;
	}
	
}
