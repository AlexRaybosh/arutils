package arapp;

import java.util.Date;
import java.util.List;

import arutils.util.Utils;

public class BasicLogger {
	
	public void logerr(List<String> frames, String msg, Throwable e) {
		StringBuilder sb=new StringBuilder(Utils.formatLocalDateTime(new Date()));
		sb.append(": ");
		if (msg!=null) sb.append(msg+"\n");
		if (frames!=null && frames.size()>0) {
			sb.append("Logging Location:\n");
			appendFrames(sb,frames);
			//+frames+" ");
		}
		sb.append(e==null?"": ("Error: "+Utils.getStackTrace(e)));
		System.err.println(sb.toString());
	}

	private void appendFrames(StringBuilder sb, List<String> frames) {
		for (String f : frames) {
			sb.append("\t").append(f).append("\n");
		}
	}


	
		
}
