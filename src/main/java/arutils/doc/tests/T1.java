package arutils.doc.tests;

import java.io.FileReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import arutils.doc.util.JsonUtils;






public class T1 {
	public static void main(String[] args) throws Exception {
		Reader r=new FileReader("/tmp/x");
		JsonElement jelement = new JsonParser().parse(r);
	    JsonObject  obj = jelement.getAsJsonObject();

		

		System.out.println(new JsonParser().parse(JsonUtils.prettyPrint(obj)));
		/*JsonReader reader=Json.createReader(new StringReader(prettyPrint(obj)));
		//JsonParser parser = Json.createParser(new StringReader(obj.toString()));
		JsonObject obj1 = reader.readObject();*/
	}
}
