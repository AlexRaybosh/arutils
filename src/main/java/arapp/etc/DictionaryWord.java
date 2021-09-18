package arapp.etc;

import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import arutils.util.Utils;

public class DictionaryWord implements Comparable<DictionaryWord> {
	
	final Future<DictionaryWord> resolved;
	final Long id;
	final String word;
	
	DictionaryWord(String word, Future<DictionaryWord> f) {
		this.id=null;
		this.word=word;
		this.resolved=f;
	}

	DictionaryWord(Long id, String word) {
		resolved=null;
		this.id=id;
		this.word=word;
	}
	
	DictionaryWord(Future<DictionaryWord> f) {
		this.resolved=f;
		this.word=null;
		this.id=null;
	}

	public final Long getId() throws SQLException, InterruptedException {
		try {
			return resolved!=null?resolved.get().id : id;
		} catch (Exception e) {
			e=Utils.proceedUnlessSQLOrInterrupted(e);
			return Utils.rethrowRuntimeException(e);
		} 
	}

	
	public final String getWord() {
		if (word!=null) return word;
		if (id!=null && id==0L) return null;
		try {
			return resolved.get().word;
		} catch (Exception e) {
			return Utils.rethrowRuntimeException(e);
		} 
	}

	@Override
	public int hashCode() {
		return Objects.hash(word);
	}
	

	@Override
	public String toString() {
		Future<DictionaryWord> r = resolved;
		Long i=id;
		String n=word;
		if ((id==null || word==null) && r!=null) {
			try {
				DictionaryWord rw = r.get(100, TimeUnit.MILLISECONDS);
				i=rw.id;
				n=rw.word;
			} catch (TimeoutException e) {
			} catch (Exception e) {
				return Utils.rethrowRuntimeException(e);
			}
		}
		return "DictionaryWord [id=" + i + ", word=" + n + "]";
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		DictionaryWord other = (DictionaryWord) obj;
		return Objects.equals(getWord(), other.getWord());
	}

	@Override
	public int compareTo(DictionaryWord o) {
		return word.compareTo(o.word);
	}
	
	
}
