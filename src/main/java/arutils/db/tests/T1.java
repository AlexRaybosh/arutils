package arutils.db.tests;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import arutils.db.ConnectionWrap;
import arutils.db.DB;
import arutils.db.DBID;
import arutils.db.StatementBlock;
import arutils.util.Utils;



public class T1 {

	public static void main(String[] args) throws Exception {
		DB db=DB.create("jdbc:mysql://localhost:3306/?autoReconnect=true&allowMultiQueries=true&cacheResultSetMetadata=true&emptyStringsConvertToZero=false&useInformationSchema=true&useServerPrepStmts=true&rewriteBatchedStatements=true", "business", "business");
//		DB db=DB.create("jdbc:oracle:thin:@(DESCRIPTION=(sdu=32000)(ADDRESS=(PROTOCOL=TCP)(HOST=192.168.1.2)(PORT=1521))(CONNECT_DATA=(SID=orcl)(SERVER=DEDICATED)))", "business", "business");

		DBID dbid=new DBID(db, "workdb.seq");
		
		try {
			db.update("create table if not exists workdb.T0 (id int, name varchar(60))");
			Object id=dbid.next("T0.id");
			Object name="Hello "+id;
			db.update("insert into workdb.T0 values (?,?)", true, id, name);
			
			for (Object[] row : db.select("select * from workdb.T0")) {
				System.out.println(Utils.csvList(row));
			}
			
			// Demonstrates transaction scope, with 2 db calls:
			// 1. select for update
			// 2. delete
			// implicit commit on success, or rollback/close/retry
			Number max=db.commit(new StatementBlock<Number>() {
				public Number execute(ConnectionWrap cw) throws SQLException, InterruptedException {
					
					Number max=cw.<Number>selectSingle("select max(id) from workdb.T0 for update");
					cw.update("delete from workdb.T0 where id=?", false, max);
					
					return max;
				}
			});
			System.out.println("Max deleted "+max);
			
		} finally {
			db.close();
		}
		System.out.println("done");
	}

}
