package tests.db;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.UUID;

import arutils.db.ArrayRowHandler;
import arutils.db.ConnectionWrap;
import arutils.db.DB;
import arutils.db.DynamicTableSet;
import arutils.db.StatementBlock;
import arutils.db.DynamicTableSet.Table;
import arutils.db.impl.U;
import arutils.util.Utils;

public class TestDynamicTable {

	public static void main(String[] args) throws Exception {
		DB db=DB.create("jdbc:mysql://localhost:3306/?autoReconnect=true&allowMultiQueries=true&cacheResultSetMetadata=true&emptyStringsConvertToZero=false&useInformationSchema=true&useServerPrepStmts=true&rewriteBatchedStatements=true", "business", "business");
		
		final DynamicTableSet dt=db.createDynamicTableSet("conf.dynamic_table");
		Table a = dt.addTable("ins", "f1", "f2", "f3");
		a.add("hi",null,"there");
		a.add("no",2,"ways");
		a.add("two",3,"four");
		Table a0 = dt.addTable("e", "f1", "f2");
		Table a1 = dt.addTable("del", "f1", "f2");
		a1.add("yes",null);
		a1.add("da",222);
		//a1.add("two",3);
		dt.addTable("f", "f1", "f2").add(UUID.randomUUID(), UUID.randomUUID());
		
		final String asql=a.getSelectSQL();
		System.out.println(asql);
		
		
		db.commit(new StatementBlock<Void>() {
			public Void execute(ConnectionWrap cw) throws SQLException, InterruptedException {
				dt.insert(cw);
				
				final boolean[] printed=new boolean[] {false};	
				
				cw.select(dt.getTable("del").getSelectSQL(), false, new ArrayRowHandler() {
					public void reset() throws InterruptedException {}
					public void onRow(ResultSetMetaData md, Object[] row) throws SQLException, InterruptedException {
						if (!printed[0]) {
							for (int c=1;c<=md.getColumnCount();++c) {
								System.out.print(md.getColumnName(c));
								if (c<md.getColumnCount()) System.out.print("\t");
							}
							System.out.println("\n---------------------------------------------------");
							printed[0]=true;
						}
						System.out.println(U.printRow(row));
					}
				});
				
				return null;
			}
			
		});
		
	}


}
