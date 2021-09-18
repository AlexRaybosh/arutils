package testapp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Future;

import arapp.AppEnv;
import arapp.etc.DictionaryWord;


public class TestApp2 {

	public static void main(String[] args) throws Exception {
		
		AppEnv.presetEnvName("test1");
		
		Map<Integer, Future<Boolean>> hid=new HashMap<>();
		for (int i=-1000;i <1000; ++i) {
			Future<Boolean> f = AppEnv.hasWord("puke #"+i);//has(i);
			hid.put(i, f);
		}
		for (Entry<Integer, Future<Boolean>> e : hid.entrySet()) {
			System.out.println(e.getKey()+ " - "+e.getValue().get());
		}
		//Thread.sleep(1000000);
		/*DictionaryWord w1=b.word(1);
		DictionaryWord w2=b.word(2);
		System.out.println(w1);
		System.out.println(w2);
		*/
		DictionaryWord w = AppEnv.word("hello");
		System.out.println(w);
		w = AppEnv.word("world");
		System.out.println(w);
		
		List<DictionaryWord> lst=new ArrayList<>();
		for (int i=0;i <1000000; ++i) {
			AppEnv.hasWord(i);
			//w=b.word("puke #"+i);
			w=AppEnv.word(i);
			lst.add(w);
		}
		for (DictionaryWord z : lst) {
			try {
				System.out.println(z.getId()+" : "+z.getWord());
			} catch (Exception e) {
				//e.printStackTrace();
			}
		}
		System.out.println("done");
		
		Thread.sleep(10000);
	}

}
