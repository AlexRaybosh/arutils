package arapp.boot;

import arutils.db.DB;

public class DBPair {
	final DB boundedDB;
	final DB flexDB;
	public DBPair(DB boundedDB, DB flexDB) {
		this.boundedDB = boundedDB;
		this.flexDB = flexDB;
	}		
}
