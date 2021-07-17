package arutils.util;

import java.io.StringReader;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;

public class JsonUtils {

	public enum JsonType {
		OBJECT, ARRAY,STRING, NUMBER, BOOLEAN, NULL
	}
	public static JsonType getType(JsonElement e) {
		if (e.isJsonObject())
			return JsonType.OBJECT;
		if (e.isJsonArray())
			return JsonType.ARRAY;
		if (e.isJsonNull())
			return JsonType.NULL;
		if (e.isJsonPrimitive()) {
			JsonPrimitive p = e.getAsJsonPrimitive();
			if (p.isBoolean())
				return JsonType.BOOLEAN;
			if (p.isString())
				return JsonType.STRING;
			if (p.isNumber())
				return JsonType.NUMBER;
		}
		return JsonType.NULL;
	}
	
	public static String prettyPrint(JsonElement root) {
		StringBuilder sb=new StringBuilder();
		prettyPrint(0,sb,null,root,true);
		return sb.toString();
	}
	public static void prettyPrint(int offset, StringBuilder sb, String attr, JsonElement root, boolean lastSibling) {
		Utils.printMargin(sb, offset);
		if (attr!=null) sb.append("\"").append(attr).append("\" : ");
		switch(getType(root)) {
			case OBJECT:
				sb.append("{\n");
				JsonObject obj=root.getAsJsonObject();
				Set<Entry<String, JsonElement>> children = obj.entrySet();
				int size=children.size(), c=1;
				for (Entry<String, JsonElement> child : children) {
					boolean childLastSibling= size==c;
					prettyPrint(offset+1,sb,child.getKey(), child.getValue(),childLastSibling);	
					++c;
				}
				Utils.printMargin(sb, offset);
				sb.append("}");

			break;
			case ARRAY:
				sb.append("[\n");
				JsonArray arr=root.getAsJsonArray();
				size=arr.size();
				c=1;
				for (Iterator<JsonElement> it = arr.iterator();it.hasNext();) {
					boolean childLastSibling= size==c;
					prettyPrint(offset+1,sb,null, it.next(),childLastSibling);	
					++c;
				}
				Utils.printMargin(sb, offset);
				sb.append("]");
			break;
		case STRING:
		case NUMBER:
		case BOOLEAN:
		case NULL:
			sb.append(root.toString());
		}
		if (!lastSibling) sb.append(",");
		sb.append("\n");     
	}
	

	public static Object _toObject(JsonElement jval) {
		if (jval==null) return null;
		switch (getType(jval)) {
		case NULL:
		case ARRAY:
		case OBJECT:
			return null;
		case NUMBER:
			return jval.getAsNumber();
		case BOOLEAN:
			return jval.getAsBoolean();
		default:
			return ((JsonPrimitive)jval).getAsString();
		}
	}



	
	public static JsonElement parseJsonElement(String str) {
		if (str==null) return null;
		JsonReader rd=new JsonReader(new StringReader(str));
		rd.setLenient(true);
		return JsonParser.parseReader(rd);
	}
	public static JsonObject parseJsonObject(String str) {
		JsonElement e = parseJsonElement(str);
		return e==null?null:e.getAsJsonObject();
	}
	
	public static JsonObject getJsonObject(JsonElement obj,String...path) {
		if (obj==null) return null;
		JsonElement curr=obj;
		for (String step : path) {
			if (!curr.isJsonObject()) return null;
			JsonElement next=curr.getAsJsonObject().get(step);
			if (next==null || !next.isJsonObject()) return null;
			curr=next.getAsJsonObject();
		}
		return curr.getAsJsonObject();
	}
	public static JsonElement getJsonElement(JsonElement obj,String... path) {
		if (path.length==0) return obj;
		if (obj==null || !obj.isJsonObject()) return null;
		JsonObject curr=obj.getAsJsonObject();
		int last=path.length-1;
		for (int i=0;i<last;++i) {
			String step=path[i];
			JsonElement next=curr.get(step);
			if (next==null || !next.isJsonObject()) return null;
			curr=next.getAsJsonObject();
		}
		return curr.get(path[last]);
	}
	public static Long getLong(JsonElement obj,String... path) {
		Number n=getNumber(obj, path);
		return n==null?null:n.longValue();
	}
	public static Integer getInteger(JsonElement obj,String... path) {
		Number n=getNumber(obj, path);
		return n==null?null:n.intValue();
	}	
	public static Number getNumber(JsonElement obj,String... path) {
		JsonElement e=getJsonElement(obj, path);
		return e==null?null:e.getAsNumber();
	}
	public static String getString(JsonElement obj,String... path) {
		JsonElement e=getJsonElement(obj, path);
		return e==null?null:e.getAsString();
	}
	public static Boolean getBoolean(JsonElement obj,String... path) {
		JsonElement e=getJsonElement(obj, path);
		return e==null?null:e.getAsBoolean();
	}
	public static boolean getBool(JsonElement obj,String... path) {
		Boolean b=getBoolean(obj, path);
		return b==null? false:b;
	}
	public static boolean isBoolean(JsonElement obj,String... path) {
		JsonElement e=getJsonElement(obj, path);
		return e==null?false: getType(e)==JsonType.BOOLEAN;
	}	

	public static boolean equalObjects(JsonObject a, JsonObject b) {
		if (a==null) return b==null;
		if (b==null) return false;
		if (a.entrySet().size()!=b.entrySet().size()) return false;
		for (Entry<String, JsonElement> e : a.entrySet()) {
			if (!equals(e.getValue(), b.get(e.getKey()))) return false;
		}
		return true;
	}
	
	public static boolean equalArrays(JsonArray a, JsonArray b) {
		if (a==null) return b==null;
		if (b==null) return false;
		if (a.size()!=b.size()) return false;
		for (int i=0;i<a.size();++i) {
			if (!equals(a.get(i), b.get(i))) return false;
		}
		return true;
	}
	public static boolean equalPrimitives(JsonPrimitive a, JsonPrimitive b) {
		if (getType(a)!=getType(b)) return false;
		switch (getType(a)) {
		case NULL:
			return true;
		case STRING:
			return a.getAsString().equals(b.getAsString());
		case BOOLEAN:
			return a.getAsBoolean()==b.getAsBoolean();
		case NUMBER:
			return a.getAsNumber().equals(b.getAsNumber());
		default:
			return false;
		}
	}

	public static boolean equals(JsonElement a, JsonElement b) {
		if (a==null) return b==null;
		if (b==null) return false;
		if (a.isJsonObject()) {
			return b.isJsonObject()?equalObjects(b.getAsJsonObject(), a.getAsJsonObject()):false;
		}
		if (a.isJsonArray()) {
			return b.isJsonArray()?equalArrays(b.getAsJsonArray(), a.getAsJsonArray()):false;
		}
		if (a.isJsonNull()) return b.isJsonNull();
		if (a.isJsonPrimitive() && b.isJsonPrimitive()) return equalPrimitives(a.getAsJsonPrimitive(),b.getAsJsonPrimitive());
		return false;
	}

	private static Iterator<JsonElement> emptyIterator=new Iterator<JsonElement> () {
		public boolean hasNext() {return false;}
		public JsonElement next() {return null;}
		
	};
	
	
	public static Iterator<JsonElement> getJsonArrayIterator(JsonElement e,String...path) {
		JsonElement arr=getJsonElement(e, path);
		if (arr==null || !arr.isJsonArray()) return emptyIterator;
		return arr.getAsJsonArray().iterator();
	}
	
	private static Iterable<JsonElement> emptyIterable=new Iterable<JsonElement>() {
		public Iterator<JsonElement> iterator() {return emptyIterator;}
	};
	
	public static Iterable<JsonElement> getJsonArrayIterable(JsonElement e,String...path) {
		final JsonElement arr=getJsonElement(e, path);
		if (arr==null || !arr.isJsonArray()) return emptyIterable;
		return new Iterable<JsonElement>() {
			public Iterator<JsonElement> iterator() {
				return arr.getAsJsonArray().iterator();
			}
		};
	}

	public static Object toObject(JsonElement jval,String... path) {
		JsonElement e=getJsonElement(jval, path);
		return _toObject(e);
		
	}
	
	public static JsonElement sortJsonObject(JsonElement src) {
		if (src==null) return null;
		if (!src.isJsonObject()) return src;
		List<String> keys=src.getAsJsonObject().keySet().stream().sorted().collect(Collectors.toList());
		JsonObject ret=new JsonObject();
		for (String key : keys) {
			JsonElement m = src.getAsJsonObject().get(key);
			ret.add(key, m.isJsonObject()?sortJsonObject(m):m);
		}
		return ret;
	}
	
	public static void main(String[] args) {
		JsonElement e = JsonUtils.parseJsonElement("{\"c\":1, \"a\":2}");
		System.out.println(prettyPrint(e));
		System.out.println(prettyPrint(sortJsonObject(e)));
	}



}
