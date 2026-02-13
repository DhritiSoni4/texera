package edu.uci.ics.texera.sqlservice;

import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.linq4j.Linq4j;
import org.apache.calcite.rel.type.*;
import org.apache.calcite.schema.ScannableTable;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.calcite.sql.type.SqlTypeName;

import java.util.List;
import java.util.Map;

public class SchemaOnlyTable extends AbstractTable implements ScannableTable {

    private final List<String> columnNames;
    private final Map<String, String> columnTypes;

    public SchemaOnlyTable(List<String> columnNames, Map<String, String> columnTypes) {
        this.columnNames = columnNames;
        this.columnTypes = columnTypes;
    }

    @Override
    public RelDataType getRowType(RelDataTypeFactory typeFactory) {
        RelDataTypeFactory.Builder builder = typeFactory.builder();
        for (String col : columnNames) {
            builder.add(col, toSqlType(typeFactory, columnTypes.get(col)));
        }
        return builder.build();
    }

    private RelDataType toSqlType(RelDataTypeFactory f, String t) {
        if (t == null) {
            return f.createSqlType(SqlTypeName.VARCHAR);
        }

        switch (t.toLowerCase()) {
            case "integer":
                return f.createSqlType(SqlTypeName.INTEGER);
            case "double":
                return f.createSqlType(SqlTypeName.DOUBLE);
            case "boolean":
                return f.createSqlType(SqlTypeName.BOOLEAN);
            case "timestamp":
                return f.createSqlType(SqlTypeName.TIMESTAMP);
            default:
                return f.createSqlType(SqlTypeName.VARCHAR);
        }
    }


    @Override
    public Enumerable<Object[]> scan(org.apache.calcite.DataContext root) {
        return Linq4j.emptyEnumerable();
    }
}
