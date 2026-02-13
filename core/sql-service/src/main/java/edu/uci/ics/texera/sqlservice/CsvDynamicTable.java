package edu.uci.ics.texera.sqlservice;

import org.apache.calcite.rel.type.*;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.calcite.sql.type.SqlTypeName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CsvDynamicTable extends AbstractTable {

    private final String datasetPath;
    private final boolean hasHeader;
    private final List<String> columnNames;
    private final Map<String, String> inferredTypes;   // dynamic inferred types
    private final Map<String, String> columnTypes;     // static user-defined types

    public CsvDynamicTable(String datasetPath,
                           boolean hasHeader,
                           List<String> columnNames,
                           Map<String, String> inferredTypes,
                           Map<String, String> columnTypes) {
        this.datasetPath = datasetPath;
        this.hasHeader = hasHeader;
        this.columnNames = columnNames;
        this.inferredTypes = inferredTypes != null ? inferredTypes : new HashMap<>();
        this.columnTypes = columnTypes != null ? columnTypes : new HashMap<>();
    }

    public List<String> getColumnNames() {
        return columnNames;
    }

    public Map<String, String> getInferredTypes() {
        return inferredTypes;
    }

    public Map<String, String> getColumnTypes() {
        return columnTypes;
    }

    public boolean hasHeader() {
        return hasHeader;
    }

    public String getDatasetPath() {
        return datasetPath;
    }

    public Map<String, String> getEffectiveColumnTypes() {
        Map<String, String> result = new HashMap<>(inferredTypes);
        result.putAll(columnTypes); // user-defined static types override inferred
        return result;
    }

    public String getOriginalColumnName(String colName) {
        // Convert to upper-case to match how you store types
        for (String col : columnNames) {
            if (col.equalsIgnoreCase(colName)) {
                return col; // return the original column name
            }
        }
        return colName; // fallback
    }

    @Override
    public RelDataType getRowType(RelDataTypeFactory typeFactory) {
        RelDataTypeFactory.Builder builder = typeFactory.builder();

        // Normalize inferred types to UPPERCASE keys
        Map<String, String> normalizedInferred = new HashMap<>();
        for (Map.Entry<String, String> e : inferredTypes.entrySet()) {
            normalizedInferred.put(e.getKey().toUpperCase(), e.getValue());
        }

        // Normalize user-defined types to UPPERCASE keys
        Map<String, String> normalizedStatic = new HashMap<>();
        for (Map.Entry<String, String> e : columnTypes.entrySet()) {
            normalizedStatic.put(e.getKey().toUpperCase(), e.getValue());
        }

        // inferred < static override
        Map<String, String> effectiveTypes = new HashMap<>(normalizedInferred);
        effectiveTypes.putAll(normalizedStatic);

        for (String col : columnNames) {
            String typeStr = effectiveTypes
                    .getOrDefault(col.toUpperCase(), "STRING")
                    .toUpperCase();

            SqlTypeName sqlType;
            switch (typeStr) {
                case "INT":
                case "INTEGER":
                    sqlType = SqlTypeName.INTEGER;
                    break;
                case "BIGINT":
                    sqlType = SqlTypeName.BIGINT;
                    break;
                case "FLOAT":
                    sqlType = SqlTypeName.FLOAT;
                    break;
                case "DOUBLE":
                    sqlType = SqlTypeName.DOUBLE;
                    break;
                case "BOOLEAN":
                    sqlType = SqlTypeName.BOOLEAN;
                    break;
                case "DATE":
                    sqlType = SqlTypeName.DATE;
                    break;
                case "TIMESTAMP":
                    sqlType = SqlTypeName.TIMESTAMP;
                    break;
                default:
                    sqlType = SqlTypeName.VARCHAR;
            }

            builder.add(col, sqlType);
        }

        return builder.build();
    }


}
