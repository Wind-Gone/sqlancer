package lama.sqlite3.gen.ddl;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import lama.IgnoreMeException;
import lama.Query;
import lama.QueryAdapter;
import lama.Randomly;
import lama.sqlite3.SQLite3Provider.SQLite3GlobalState;
import lama.sqlite3.SQLite3Visitor;
import lama.sqlite3.gen.SQLite3ExpressionGenerator;
import lama.sqlite3.gen.dml.SQLite3DeleteGenerator;
import lama.sqlite3.gen.dml.SQLite3InsertGenerator;
import lama.sqlite3.gen.dml.SQLite3UpdateGenerator;
import lama.sqlite3.schema.SQLite3Schema;
import lama.sqlite3.schema.SQLite3Schema.Table;

public class SQLite3CreateTriggerGenerator {

	private enum OnAction {
		INSERT, DELETE, UPDATE
	}

	private enum TriggerAction {
		INSERT, DELETE, UPDATE, RAISE
	}

	public static Query create(SQLite3GlobalState globalState) throws SQLException {
		SQLite3Schema s = globalState.getSchema();
		StringBuilder sb = new StringBuilder();
		Table table = s.getRandomTableOrBailout(t -> !t.isVirtual());
		sb.append("CREATE");
		if (table.isTemp()) {
			sb.append(" ");
			sb.append(Randomly.fromOptions("TEMP", "TEMPORARY"));
		}
		sb.append(" TRIGGER");
		sb.append(" IF NOT EXISTS ");
		sb.append("tr" + Randomly.smallNumber());
		sb.append(" ");
		if (table.isView()) {
			sb.append("INSTEAD OF");
		} else {
			sb.append(Randomly.fromOptions("BEFORE", "AFTER"));
		}
		sb.append(" ");

		OnAction randomAction = Randomly.fromOptions(OnAction.values());
		switch (randomAction) {
		case INSERT:
			sb.append("INSERT ON ");
			break;
		case DELETE:
			sb.append("DELETE ON ");
			break;
		case UPDATE:
			sb.append("UPDATE ");
			if (Randomly.getBoolean()) {
				sb.append("OF ");
				for (int i = 0; i < Randomly.smallNumber() + 1; i++) {
					if (i != 0) {
						sb.append(", ");
					}
					sb.append(table.getRandomColumn().getName());
				}
				sb.append(" ");
			}
			sb.append("ON ");
			break;
		}
		appendTableNameAndWhen(globalState, sb, table);

		Table randomActionTable = s.getRandomTableNoViewOrBailout();
		sb.append(" BEGIN ");
		for (int i = 0; i < Randomly.smallNumber() + 1; i++) {
			switch (Randomly.fromOptions(TriggerAction.values())) {
			case DELETE:
				sb.append(SQLite3DeleteGenerator.deleteContent(globalState, randomActionTable));
				break;
			case INSERT:
				sb.append(getQueryString(s, globalState));
				break;
			case UPDATE:
				sb.append(SQLite3UpdateGenerator.updateRow(globalState, randomActionTable));
				break;
			case RAISE:
				sb.append("SELECT RAISE(");
				if (Randomly.getBoolean()) {
					sb.append("IGNORE");
				} else {
					sb.append(Randomly.fromOptions("ROLLBACK", "ABORT", "FAIL"));
					sb.append(", 'asdf'");
				}
				sb.append(")");
				break;
			}
			sb.append(";");
		}
		sb.append("END");

		return new QueryAdapter(sb.toString(), Arrays.asList("parser stack overflow", "unsupported frame specification"));
	}

	private static void appendTableNameAndWhen(SQLite3GlobalState globalState, StringBuilder sb, Table table) {
		sb.append(table.getName());
		if (Randomly.getBoolean()) {
			sb.append(" FOR EACH ROW ");
		}
		if (Randomly.getBoolean()) {
			sb.append(" WHEN ");
			sb.append(SQLite3Visitor
					.asString(new SQLite3ExpressionGenerator(globalState).setColumns(table.getColumns()).getRandomExpression()));
		}
	}

	private static String getQueryString(SQLite3Schema s, SQLite3GlobalState globalState) throws SQLException {
		String q;
		do {
			q = SQLite3InsertGenerator.insertRow(globalState, getTableNotEqualsTo(s, s.getRandomTableNoViewOrBailout()))
					.getQueryString();
		} while (q.contains("DEFAULT VALUES"));
		return q;
	}

	private static Table getTableNotEqualsTo(SQLite3Schema s, Table table) {
		List<Table> tables = new ArrayList<>(s.getDatabaseTablesWithoutViews());
		tables.remove(table);
		if (tables.isEmpty()) {
			throw new IgnoreMeException();
		}
		return Randomly.fromList(tables);
	}

}
