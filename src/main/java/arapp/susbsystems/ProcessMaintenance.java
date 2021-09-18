package arapp.susbsystems;

import java.sql.SQLException;
import java.util.Objects;

import com.google.gson.JsonObject;

import arapp.AppScope;
import arapp.SubSystem;
import arutils.db.ConnectionWrap;
import arutils.db.StatementBlock;
import arutils.util.JsonUtils;
import arutils.util.ProcessLsof;
import arutils.util.Utils;

public class ProcessMaintenance extends SubSystem {

	final static String INSERT_SQL="INSERT INTO system_process ("
			+ "id, is_active, env_id, "
			+ "hostname, pid, cmd, "
			+ "cluster_member_id, start_ms, ping_ms, "
			+ "dead_ms) "
			+ "VALUES ("
			+ "? ,?, ?, "
			+ "?, ?, ?, "
			+ "?, ?, ?, ?)";
	
	
	final static String TOUCH="touch", MAINTAIN="maintenance", LSOF="lsof";
	
	private Long processId;
	private Long deadAfterMilliseconds;
	private Long removeDeadAfterMilliseconds;
	volatile Integer clusterMemberId;
	volatile ProcessLsof plsof;
	
	
	public ProcessMaintenance() {}
	@Override
	public boolean init(boolean initial, JsonObject conf) throws Exception {
		if (!getAppScope().hasDB()) return false; 
		
		final String hostName=Utils.getCanonicalHostName();
		final Long pid=Utils.getPid();
		final String cmdLine=Utils.getProcessCmdLine();
		final Long start=Utils.getProcessStartTime();
		
		deadAfterMilliseconds=JsonUtils.getLong(10000L, conf,  "deadAfterMilliseconds");
		if (deadAfterMilliseconds<=0) deadAfterMilliseconds=1000L;
		
		removeDeadAfterMilliseconds=JsonUtils.getLong(10000L, conf,  "removeDeadAfterMilliseconds");
		if (removeDeadAfterMilliseconds<=0) removeDeadAfterMilliseconds=1000L;
			
		Long oldId = (initial)?null:appScope.getSystemProcessId(); // <- will deadlock without a initial check
		this.processId=(oldId==null)? insertProcessId(hostName, pid, cmdLine, start) : oldId;
		
		return true;
	}
	
	private Long insertProcessId(final String hostName, final Long pid, final String cmdLine, final Long start)
			throws SQLException, InterruptedException {
		return getAppScope().getFlexDB().commit(new StatementBlock<Long>() {
			public Long execute(ConnectionWrap cw) throws SQLException, InterruptedException {
				Long processId=appScope.newId("system_process_id");
				long now=getAppScope().getTime();
				long dead=getDead(now);
				cw.update(INSERT_SQL, false, 
						processId, 1, appScope.getEnvId(), 
						hostName, pid, cmdLine, 
						clusterMemberId, start==null?now:start, now, dead);
				return processId;
			}
			@Override
			public boolean onError(ConnectionWrap cw, boolean willAttemptToRetry, SQLException ex, long start, long now) throws SQLException, InterruptedException {
				return super.onError(cw, willAttemptToRetry, ex, start, now);
			}
		});
	}
	
	final long getDead(long now) {
		return now+deadAfterMilliseconds;
	}
	final long getRemoveDead(long now) {
		return now-removeDeadAfterMilliseconds;
	}
	
	
	@Override
	public boolean tick(String tickName, Long lastRun) throws Exception {
		long now=getAppScope().getTime();
		
		if (TOUCH.equals(tickName)) {
			long deadMs = getDead(now);
			try {
				getAppScope().getFlexDB().update("update system_process set is_active=1, ping_ms=?, dead_ms=? where id=?", true, now, deadMs, processId);
			} catch (Exception e) {
				appScope.logerr("failed to touch system_process: "+processId,e);
			}
			return true;
		} else if (MAINTAIN.equals(tickName)) {
			try {
				long removeDeadMs=getRemoveDead(now);
				for (Object did : appScope.getFlexDB().selectFirstColumn("select id from system_process force index (process_dead_idx) where is_active=1 and dead_ms<?", true, now)) {
					appScope.getFlexDB().update("update system_process set is_active=0 where id=?", true, Utils.toLong(did));
				}
				appScope.getFlexDB().update("delete from system_process where is_active=0 and dead_ms<?", true, removeDeadMs);
			} catch (Exception e) {
				appScope.logerr("failed to maint system_process: "+processId,e);
			}
			
			return true;
		} else if (LSOF.equals(tickName)) {
			try {
				plsof=ProcessLsof.lsof();
				final Integer newClusterMemberId=deriveMemberId(appScope, plsof);
				if (!Objects.equals(newClusterMemberId, clusterMemberId)) {
					clusterMemberId=newClusterMemberId;
					appScope.getFlexDB().update("update system_process set cluster_member_id=? where id=?",false, newClusterMemberId, clusterMemberId);
				}	
			} catch (Exception e) {
				if (e!=null) appScope.logerr("failed to lsof system_process: "+processId,e);
			}
			return true;
		}
		
		return false;
	}
	

	
	
	


	private static Integer deriveMemberId(AppScope appScope, ProcessLsof plsof) throws SQLException, InterruptedException {
		Integer memberId=null;
		if (plsof!=null && plsof.getTcpIpListeningPorts().size()>0) {
			String myhost=Utils.getCanonicalHostName();
			StringBuilder ports=new StringBuilder();
			for (Integer p :  plsof.getTcpIpListeningPorts().keySet()) {
				if (ports.length()>0) ports.append(",").append(p);
				else ports.append(p);
			}
			memberId=Utils.toInteger(appScope.getFlexDB().selectSingle("select id from cluster_member where hostname=? and tcp_port in ("+ports+")", false, myhost));
		}
		return memberId;
	}




	public final Long getSystemProcessId() {
		return processId;
	}
	
	


	public void destroy() {
		try {
			if(appScope.hasDB()) 
				appScope.getFlexDB().update("update system_process set is_active=0 where id=?", true, processId);
		} catch (Exception e) {
			appScope.logerr("Failed to deactivate system_process: "+processId+" on destroy", e);
		}
	}

	public Integer getClusterMemberId() {
		return clusterMemberId;	
	}


	@Override
	public boolean onTick(String tickName, Long lastRun) {
		try {
			return tick(tickName, lastRun);
		} catch (Exception e) {
			appScope.logerr("ProcessMaintenence timer "+tickName+" error: "+Utils.getStackTrace(Utils.extraceCause(e)));
			return true;
		}
	}

	

	

}
