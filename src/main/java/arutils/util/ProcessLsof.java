package arutils.util;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class ProcessLsof {

	@Override
	public String toString() {
		StringBuilder sb=new StringBuilder("ProcessLsof [pid=" + pid + ", ppid=" + ppid + ", pgid=" + pgid + ", command=" + command + ", uid=" + uid
				+ ", login=" + login + ", tcpIpListeningPorts=" + tcpIpListeningPorts
				+ "]"
		);
		for (LsofEntry e : lsofEntries) {
			sb.append("\n\t").append(e);
		}

		return sb.toString();
	}
	private final long pid;
	private Long ppid;
	private Long pgid;
	private String command;
	private Long uid;
	private String login;
	private ProcessLsof(Long pid) {
		this.pid=pid;
	}
	private Set<LsofEntry> lsofEntries=null;
	private Map<Integer, Set<IP>> tcpIpListeningPorts=new TreeMap<>();
	//private String taskCmd;
	//private Long tid;

	public final long getPid() {return pid;}
	public final Long getPpid() {return ppid;}
	public final Long getPgid() {return pgid;}
	//public final Long getTid() {return tid;}
	
	public final String getCommand() {return command;}
	public final Long getUid() {return uid;}
	public final String getLogin() {return login;}

	//public final String getTaskCmd() {return taskCmd;}
	public final Set<LsofEntry> getLsofEntries() {
		return lsofEntries==null ? Collections.emptySet():lsofEntries;
	}


	public final Map<Integer, Set<IP>> getTcpIpListeningPorts() {
		return tcpIpListeningPorts;
	}
	public static class IP implements Comparable<IP> {
		final String ipName;
		final String protocol;
		public IP(String name, String protocol) {
			if (name!=null && name.startsWith("[") && name.endsWith("]")) {
				name=name.substring(1, name.length()-1);
			}
			this.ipName = name;
			this.protocol = protocol;
		}
		
		public final String getIpName() {return ipName;}
		public final String getProtocol() {return protocol;}

		@Override
		public int hashCode() {
			return Objects.hash(ipName, protocol);
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (obj == null) return false;
			if (getClass() != obj.getClass())return false;
			IP other = (IP) obj;
			return Objects.equals(ipName, other.ipName) && Objects.equals(protocol, other.protocol);
		}
		@Override
		public int compareTo(IP o) {
			int ret=protocol.compareTo(o.protocol);
			if (ret==0) return ipName.compareTo(o.ipName);
			return ret;
		}
		@Override
		public String toString() {
			return protocol + " " + ipName;
		}
		
	}
	
	public static class LsofEntry {
		final ProcessLsof processLsof;
		String entryType;
		String deviceString;
		String fileFlags;
		Integer fd;
		String fdFlags; //a
		String fdType; // FD/ or pseudo fd, mem, etc
		Long inode; //i
		Long size; // s
		String offString; // s
		String protocol; // p
		String name; // p
		Map<String,String> states=null;
		public final String getEntryType() {
			return entryType;
		}
		public final String getDeviceString() {
			return deviceString;
		}
		public final String getFileFlags() {
			return fileFlags;
		}
		public final Integer getFd() {
			return fd;
		}
		public final String getFdFlags() {
			return fdFlags;
		}
		public final String getFdType() {
			return fdType;
		}
		public final Long getInode() {
			return inode;
		}
		public final Long getSize() {
			return size;
		}
		public final String getOffString() {
			return offString;
		}
		public final String getProtocol() {
			return protocol;
		}
		public final String getName() {
			return name;
		}
		public final Map<String, String> getStates() {
			return states==null?Collections.emptyMap():states;
		}
		
		
		@Override
		public int hashCode() {
			return Objects.hash(deviceString, entryType, fd, fdFlags, fdType, fileFlags, inode, name, offString,
					protocol, size);
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			LsofEntry other = (LsofEntry) obj;
			return Objects.equals(deviceString, other.deviceString) && Objects.equals(entryType, other.entryType)
					&& Objects.equals(fd, other.fd) && Objects.equals(fdFlags, other.fdFlags)
					&& Objects.equals(fdType, other.fdType) && Objects.equals(fileFlags, other.fileFlags)
					&& Objects.equals(inode, other.inode) && Objects.equals(name, other.name)
					&& Objects.equals(offString, other.offString) && Objects.equals(protocol, other.protocol)
					&& Objects.equals(size, other.size);
		}
		@Override
		public String toString() {
			return "LsofEntry [entryType=" + entryType + ", fdType=" + fdType + (fd==null?"":", fd=" + fd) + (fdFlags==null?"":", fdFlags=" + fdFlags)
					+ (name==null?"":", name=" + name) + (protocol==null?"":", protocol=" + protocol) + (states==null?"":", states=" + states) + "]";
		}
		private LsofEntry(ProcessLsof processLsof, List<Field> fields) {
			//[Field [letter=f, value=cwd], Field [letter=a, value= ], Field [letter=l, value= ], Field [letter=t, value=DIR], Field [letter=D, value=0x801], Field [letter=s, value=4096], Field [letter=i, value=8521955], Field [letter=k, value=4], Field [letter=n, value=/home/alex/workspace/arweb/src/main/java]]
			this.processLsof=processLsof;
			for (Field f : fields) {
				switch (f.letter) {
				case 'f':
					if (f.getValue().length()>0) {
						char c=f.getValue().charAt(0);
						if (c >='0' && c<='9') {
							try {
								fd=Integer.parseInt(f.getValue());
								fdType="fd";
							} catch (Exception e) {
								fdType=f.getValue();
							}
						} else {
							fdType=f.getValue();
						}
					}
					break;
				case 'a':
					if (f.getValue()!=null && f.getValue().length()>0) fdFlags=f.getValue();
					break;
				case 't':
					entryType=f.getValue();
					break;
				case 'D':
				case 'd':					
					deviceString=f.getValue();
					break;
				case 'G':
					fileFlags=f.getValue();
					break;
				case 's':
					size = f.getValueLong();
					break;	
				case 'i':
					inode = f.getValueLong();
					break;
				case 'o':
					offString=f.getValue();
					break;
				case 'P':
					protocol=f.getValue();
					break;
				case 'n':
					name=f.getValue();
					break;
				case 'T':
					String stString=f.getValue();
					if (stString!=null) {
						if (states==null ) states=new HashMap<>();
						int idx=stString.indexOf('=');
						if (idx>0) {
							String stName=stString.substring(0, idx);
							String stVal= (idx<stString.length()-1) ? stString.substring(idx+1) : null;
							states.put(stName, stVal);
						}
					}
					break;		
				case 'k':
				case 'l':
					break;
				default:
					System.err.println("Unexpected lsof field ["+((char)f.letter)+"];  -  "+((int)f.letter));
					break;
				}
			}
			if ("TCP".equals(protocol) && ( "IPv4".equals(entryType) || "IPv6".equals(entryType) ) && name!=null && states!=null ) {
				if ("LISTEN".equals(states.get("ST"))) {
					// got a listner
					int idx=name.lastIndexOf(':');
					if (idx>0 && idx<name.length()-1) {
						String ipSpec=name.substring(0, idx);
						String _port=name.substring(idx+1);
						try {
							Integer port=Integer.parseInt(_port);
							processLsof.addListeningPort(port, new IP(ipSpec, entryType));
						} catch (Exception e) {}
					}
				}
			}			
		}
	}
	
	private void addFieldsLine(List<Field> fields) {
		if (lsofEntries==null) {
			// initial line, get ppid, gpid, command, login
			lsofEntries=new LinkedHashSet<>();
			for (Field f : fields) {
				switch (f.letter) {
				case 'g':
					pgid=f.getValueLong();
					break;
				case 'R':
					ppid=f.getValueLong();
					break;
				case 'c':
					command=f.getValue();
					break;
				case 'u':
					uid=f.getValueLong();
					break;
				case 'L':
					login=f.getValue();
					break;					
				case 'M':
//					taskCmd=f.getValue();
					break;
				case 'K':
					//tid=f.getValueLong();
					break;					
				default:
					System.err.println("Unexpected lsof initial field ["+((char)f.letter)+"];  -  "+((int)f.letter)+ " {"+f.getValue()+"}");
					break;
				}
			}
		} else {
			// new lsof entry
			lsofEntries.add(new LsofEntry(this, fields));
		}
		
	}
	private void addListeningPort(Integer port, IP ip) {
		Set<IP> ips = tcpIpListeningPorts.get(port);
		if (ips==null) {
			ips=new TreeSet<>();
			tcpIpListeningPorts.put(port,ips);
		}
		ips.add(ip);
	}
	static class Reader {
		private byte[] buf;
		int pos=0;
		Map<Long,ProcessLsof>  read(Collection<Long> pids) throws InterruptedException {
			String shArg="PATH=/usr/sbin:/sbin:/usr/bin:/bin:$PATH lsof -F0 -w -b -n -P";
			if (pids!=null && pids.size()>0) {
				//shArg+=" -p";
				for (Long p : new HashSet<>(pids)) {
					shArg+=" -p "+p;
				}
			}
			Map<Long,ProcessLsof> procs=new HashMap<>();
			ProcessBuilder pb=new ProcessBuilder("/bin/sh","-c",shArg);
			pb.redirectInput(Redirect.PIPE);
			pb.redirectOutput(Redirect.PIPE);
			pb.redirectError(new File("/dev/null"));
			try {
				Process process=pb.start();
				try (
					OutputStream out = process.getOutputStream();
					InputStream in=process.getInputStream();
				) {
					out.close();
					this.buf=Legacy.jdk9_readAllBytes(in);
					
					
					ProcessLsof current=null;
					List<Field> fields=new ArrayList<>();
					for (;;) {
						Field f=readField();
//						System.out.println(f);
						if (f==null) break;
						if (f.isProcessStart()) {
							if (current==null) {
								//start new process record
								current=new ProcessLsof(f.getValueLong());
							} else {
								procs.put(current.getPid(), current);
								current=new ProcessLsof(f.getValueLong());
							}
						} else {
							if (f.isLineBreak()) {
								// flush fields to the current
								if (current!=null) {
									current.addFieldsLine(fields);
									fields.clear();
								} else {
									// unexepected
									System.err.println("wtf is "+f);
								}
							} else {
								fields.add(f);
							}
						}						
					}
					if (current!=null) {
						if (fields.size()>0) current.addFieldsLine(fields); // last line
						procs.put(current.getPid(),current); 
					} 
				} finally {
					if (process.waitFor()!=0) throw new RuntimeException("lsof failed");
				}
			} catch (Exception e) {
				e=Utils.proceedUnlessInterrupted(e);
				return Utils.rethrowRuntimeException(e);
			}
			return procs;
		}
		private Field readField() {
			int cur=pos;
			if (pos>=buf.length) return null;
			byte b=buf[cur++];
			if (b==0xA) {
				pos=cur;
				return new Field();
			}
			int end=cur;
			for (;;++end) {
				if (end>=buf.length) return null;
				if (buf[end]==0x0) break;
			}
			// [cur, end) - the value
			String v=new String(buf, cur, end-cur, StandardCharsets.UTF_8);
			pos=end+1; // +1 to consume last 0x0
			return new Field(b, v);
		}
		
	}
	private static class Field {
		public Field() {
			this.isBreak=true;
			this.value=null;
			this.letter=0xA;
		}
		final String getValue() {
			return value;
		}
		final boolean isLineBreak() {
			return isBreak;
		}
		final Long getValueLong() {
			return Utils.isEmpty(value)?null:Long.parseLong(value);
		}
		final boolean isProcessStart() {
			return letter=='p';
		}
		public Field(byte b, String v) {
			this.letter=b;
			if (v!=null) v=v.trim();
			this.value=v;
			this.isBreak=false;
		}

		final byte letter;
		final String value;
		final boolean isBreak;
		@Override
		public String toString() {
			if (isBreak) return "LINE BREAK"; 
			else return "Field [letter=" + (char)letter + ", value=" + value + "]";
		}
	}
	
	public static Map<Long, ProcessLsof> lsofAll() throws InterruptedException {
		return new Reader().read(null);
	}
	 public static Map<Long,ProcessLsof> lsof(Collection<Long> pids) throws InterruptedException {
		 return new Reader().read(pids);
	 }
	 public static ProcessLsof lsof() throws InterruptedException {
		 Reader r=new Reader();
		 Long pid=Utils.getPid();
		 Map<Long, ProcessLsof> m = r.read(Arrays.asList(pid));
		 if (m.size()==0) throw new RuntimeException("Failed to lsof self");
		 return m.get(pid);
	 }


	public static void main(String[] args) throws Exception {
		//for (ProcessLsof l : lsof(Arrays.asList(9775L))) {
		System.out.println(lsof());
		for (ProcessLsof l : lsofAll().values()) {
			if (l.getTcpIpListeningPorts().size()>0) System.out.println(l.getTcpIpListeningPorts());	
		}
		
	}





}
