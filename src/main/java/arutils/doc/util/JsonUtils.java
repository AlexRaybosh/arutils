package arutils.doc.util;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import arutils.util.Utils;

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
		//int offset, StringBuilder sb, String attr,
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
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

	public static String getString(JsonObject obj, String name) {
		if (obj==null) return null;
		JsonElement jval = obj.get(name);
		if (jval==null) return null;
		switch (getType(jval)) {
		case NULL:
		case ARRAY:
		case OBJECT:
			return null;
		default:
			return ((JsonPrimitive)jval).getAsString();
		}
		
	}

	public static Number getNumber(JsonElement jval) {
		if (jval==null) return null;
		switch (getType(jval)) {
		case NULL:
		case ARRAY:
		case OBJECT:
			return null;
		case NUMBER:
			return jval.getAsNumber();
		default:
			String str=((JsonPrimitive)jval).getAsString();
			return new BigDecimal(str);
		}
	}

	public static Integer getInteger(JsonObject obj, String name) {
		if (obj==null) return null;
		JsonElement jval = obj.get(name);
		if (jval==null) return null;
		switch (getType(jval)) {
		case NULL:
		case ARRAY:
		case OBJECT:
			return null;
		case STRING:
			String str=((JsonPrimitive)jval).getAsString();
			return new BigDecimal(str).intValue();
		case NUMBER:
			return ((JsonPrimitive)jval).getAsBigInteger().intValue();
		case BOOLEAN:
			return ((JsonPrimitive)jval).getAsBoolean()?1:0;
		default:
			throw new RuntimeException("Invalid type");
		}
	}

}
