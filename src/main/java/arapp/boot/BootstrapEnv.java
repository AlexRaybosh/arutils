package arapp.boot;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import arapp.AppScope;
import arapp.Env;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import arutils.db.DB;
import arutils.util.JsonUtils;
import arutils.util.Utils;


public class BootstrapEnv {
	String envName;
	Properties properties;
	String shell;
	boolean abortOnError=false;
	boolean logErrors;
	private String dburl;
	private String dbuser;
	private String dbpassword;
	DB db;
	Env env;
	Future<AppSec> appSecFuture;
	boolean disableDB=false;
	private boolean bootstrapIsFile;
	private String lookupDir;
	
	public final boolean isDBDisabled() {
		return disableDB;
	}
	
	private void add(String name, Map<String, JsonObject> allConfigs, JsonObject conf) {
		if (conf==null) return;
		if (allConfigs.containsKey(name)) return;
		allConfigs.put(name, conf);
		for (JsonElement entry : JsonUtils.getJsonArrayIterable(conf, "include")) {
			String inc=JsonUtils.getString(entry);
			if (Utils.isEmpty(inc)) {
				logerr("Configuration "+name+" contains invalid include entry "+entry+", skipping it");
				continue;
			}
			if (allConfigs.containsKey(inc)) continue;
			try {
				String incName=lookupDir==null?inc:(lookupDir+"/"+inc);
				JsonObject incConfig=null;
				if (bootstrapIsFile) {
					incConfig=readBuildinJsonConfigFile(incName+".json");
					if (incConfig==null) incConfig=readBuildinJsonConfigFile(incName);//inc+".json");					
				} else {
					incConfig=readBuildinJsonConfig(incName+".json");//inc+".json");
					if (incConfig==null) incConfig=readBuildinJsonConfig(incName);
				}
				
				if (incConfig!=null) add(inc, allConfigs, incConfig);
				else {
					logerr("Configuration "+name+" contains invalid include entry "+entry+", skipping it");	
				}
			} catch (Exception e) {
				logerr("Configuration "+name+" contains invalid include entry "+entry+", failed to read it: "+e.getMessage());
				continue;
			}
		}
		conf.remove("include");
	}


	void init(AppScope appScope) throws Exception {
		String bootstrap="bootstrap.json";
		JsonObject buildInConfig=null;
		if (appScope.getPresetBootstrapResource()!=null) {
			bootstrap=appScope.getPresetBootstrapResource();
			buildInConfig=readBuildinJsonConfig(bootstrap);
			if (buildInConfig==null) {
				//maybe its a resource directory
				if (!bootstrap.endsWith("/")) bootstrap+="/bootstrap.json";
				else bootstrap+="bootstrap.json";
				buildInConfig=readBuildinJsonConfig(bootstrap);
				if (buildInConfig==null) {
					// maybe it a file
					bootstrap=appScope.getPresetBootstrapResource();
					buildInConfig=readBuildinJsonConfigFile(bootstrap);
					if (buildInConfig==null) {
						if (!bootstrap.endsWith("/")) bootstrap+="/bootstrap.json";
						else bootstrap+="bootstrap.json";
						buildInConfig=readBuildinJsonConfigFile(bootstrap);
						if (buildInConfig==null) throw new RuntimeException("Failed to locate any reasonable bootstrap.json");
					}
					bootstrapIsFile=true;
				}
				
				
			}
		} else {
			buildInConfig=readBuildinJsonConfig(bootstrap);
			
		}
		int dirEnd=bootstrap.lastIndexOf('/');	
		if (dirEnd>0) {
			lookupDir=bootstrap.substring(0, dirEnd);
		}
		Map<String,JsonObject> allConfigs=new LinkedHashMap<>();
		add("bootstrap", allConfigs, buildInConfig);
		JsonObject[] arr=allConfigs.values().<JsonObject>toArray(new JsonObject[allConfigs.size()]);
		buildInConfig=JsonUtils.combine(arr);

		shell = JsonUtils.getString(buildInConfig, "bootstrap", "shell");
		if (Utils.isEmpty(shell)) shell="/bin/bash";

		String overrideEnvFromEnvironmentVariable=JsonUtils.getString(buildInConfig, "bootstrap", "overrideEnvFromEnvironmentVariable");
		String abortOnErrorIfShellEval=JsonUtils.getString(buildInConfig, "bootstrap", "abortOnErrorIfShellEval");
		logErrors=JsonUtils.getBool(buildInConfig, "bootstrap","logErrors");
		abortOnError=JsonUtils.getBool(buildInConfig, "bootstrap","abortOnError");

		if (!abortOnError && !Utils.isEmpty(abortOnErrorIfShellEval)) {
			abortOnError=checkShellEval(shell,abortOnErrorIfShellEval);
		}	
		
		
		BootEntry entry=null;
		for (JsonElement bobj : JsonUtils.getJsonArrayIterable(buildInConfig, "bootstrap", "entries")) {
			entry=BootEntry.create(bobj);
			if (entry==null) continue;
			try {
				boolean eval=entry.eval(BootstrapEnv.this, JsonUtils.getJsonObject(bobj));
				if (eval) break;
			} catch (Exception e) {
				if (logErrors) logerr(e.getMessage());
				if (abortOnError) throw e;
			}
		}
		if (entry!=null) {
			envName=entry.getEnvName();
			properties=entry.getProperties();
			
		}
		
		if (!Utils.isEmpty(appScope.getPresetEnvName())) {
			envName=appScope.getPresetEnvName();
		} else {
			if (!Utils.isEmpty(overrideEnvFromEnvironmentVariable)) {
				String v=System.getenv(overrideEnvFromEnvironmentVariable);
				if (!Utils.isEmpty(v)) envName=v;
			}
			if (envName==null) envName="undefined";		
		}

		
		
		
		JsonObject myConf = JsonUtils.getJsonObject(buildInConfig, "env", envName);
		JsonObject defConf= JsonUtils.getJsonObject(buildInConfig, "defaults");
		JsonObject envConf=JsonUtils.combine(new JsonObject(), defConf, myConf);
		
		dburl=(String)properties.get("dburl");
		dbuser=(String)properties.get("dbuser");
		dbpassword=(String)properties.get("dbpassword");
		
		
		String disableDatabaseEnvironmentVariable=JsonUtils.getString(buildInConfig, "bootstrap", "disableDatabaseEnvironmentVariable");
		if (!Utils.isEmpty(disableDatabaseEnvironmentVariable)) {
			String v=System.getenv(disableDatabaseEnvironmentVariable);
			
			if (!Utils.isEmpty(v) && !"0".equals(v) && !v.startsWith("n") && !v.startsWith("N")) {
				dburl=null;
				disableDB=true;
			}
		}
		
		if (!Utils.isEmpty(dburl) && JsonUtils.getJsonObject(envConf, "db", "core")!=null ) {
			db=DB.create(dburl, dbuser, dbpassword);
		}
		env=initEnv(db,envName,envConf);//Env.init(db, envName , envConf); 
		if (db!=null && JsonUtils.getJsonObject(env.getMeta(), "db", "core")==null) {
			db.close();
			db=null;
		}
		if (db!=null) {
			db=reinit(db,env.getMeta(), "core", dburl,  dbuser, dbpassword);
		}

		appSecFuture=AppScope.getExecutorService().submit(new Callable<AppSec>() {
			public AppSec call() throws Exception {
				return  new AppSec(appScope, properties, env);
			}
		});
		
	}


	private Env initEnv(DB db, String envName, JsonObject envConf) throws SQLException, InterruptedException {
		Integer id=null;
		if (db!=null) {
			id=Utils.toInteger(db.selectSingle("select id from env e where e.name=?",false,envName));
			if (id!=null) for (Object str : db.selectFirstColumn("select meta from env_config where env_id=? order by position",false,id)) {
				String cmetaStr=Utils.toString(str);
				if (!Utils.isEmpty(cmetaStr)) {
					JsonObject cmeta=JsonUtils.parseJsonObject(cmetaStr);
					if (cmeta!=null) {
						envConf=JsonUtils.combine(envConf,cmeta);
					}
				}
			}				
		}
		return new Env(id, envName, envConf);
	}



	public static DB reinit(DB db, JsonObject envConf, String name, String dburl, String dbuser, String dbpassword) {
		Integer socketTimeout=JsonUtils.getInteger(null,envConf, "db", name, "urlParams", "socketTimeout");
		if (socketTimeout!=null) {
			String newurl=dburl;
			Matcher m=Pattern.compile("(.*\\W)socketTimeout=(\\d+)(.*)").matcher(dburl);
			if (m.matches()) {
				String prefix=m.group(1);
				int urlTimeout=Integer.parseInt(m.group(2));
				String postfix=m.group(3);
				if (urlTimeout!=socketTimeout.intValue()) {
					newurl=prefix+"socketTimeout="+socketTimeout+postfix;
				}
			} else {
				newurl=dburl+"&socketTimeout="+socketTimeout;
			}
			if (!newurl.equals(dburl)) {
				db.close();
				dburl=newurl;
				db=DB.create(dburl, dbuser, dbpassword);
			}
		}
		return db;
	}

	private JsonObject readBuildinJsonConfig(String name) {
		InputStream is=null;
		try {
			is=AppScope.class.getClassLoader().getResourceAsStream(name);
			if (is==null) is=AppScope.class.getClassLoader().getResourceAsStream("/"+name);
			if (is==null) return null;
			JsonObject conf = JsonUtils.parseJsonObject(is);
			return conf;
		} catch (Exception e) {
			return Utils.rethrowRuntimeException(e);
		} finally {
			Utils.close(is);
		}
	}
	private JsonObject readBuildinJsonConfigFile(String name) {
		try {
			byte[] b=Files.readAllBytes(new File(name).toPath());
			JsonObject conf = JsonUtils.parseJsonObject(new String(b,StandardCharsets.UTF_8));
			return conf;
		} catch (Exception e) {
			return null;
		} 
	}


	boolean checkShellEval(String shell, String expr) {
		if (Utils.isEmpty(expr)) return false;
		List<String> lst=new ArrayList<>();
		lst.add(shell==null?"/bin/bash":shell);
		lst.add("-c");
		lst.add(expr);
		ProcessBuilder pb=new ProcessBuilder(lst.toArray(new String[lst.size()]));
		pb.redirectInput(new File("/dev/null"));
		pb.redirectOutput(Redirect.PIPE);
		pb.redirectError(Redirect.PIPE);
		
		
		int exit=-1;
		try {
			Process process=pb.start();
			try (
				OutputStream out = process.getOutputStream();
				InputStream in=process.getInputStream();
				InputStream err=process.getErrorStream();
			) {
				out.close();
				in.readAllBytes();
				String errMsg=Utils.trim(new String(err.readAllBytes(), StandardCharsets.UTF_8));
				if (logErrors && !Utils.isEmpty(errMsg)) logerr(errMsg);
			} finally {
				exit=process.waitFor();
			}
		} catch (Exception e) {
			return false;
		}
		return exit==0;
	}
	
	public static void logerr(String msg) {
		if (Utils.isEmpty(msg)) return;
		System.err.println("BOOTSTRAP: "+msg);
	}
	public final Properties getProperties() {
		return properties;
	}

	public static BootstrapEnv bootstrap(AppScope appScope) throws Exception {
		BootstrapEnv bootstrapEnv=new BootstrapEnv();
		bootstrapEnv.init(appScope);
		return bootstrapEnv;
	}
	
	public final DB getDB() {
		return db;
	}

	public final Env getEnv() {
		return env;
	}


	public static void logerr(String msg, Exception e) {
		if (e!=null) {
			e=Utils.extraceCause(e);
		}
		String m=msg==null?(e==null?"":e.getMessage()) : (msg+(e==null?"":(": "+e.getMessage())));
		logerr(m);
	}


	public final Future<AppSec> getAppSecFuture() {
		return appSecFuture;
	}
}
