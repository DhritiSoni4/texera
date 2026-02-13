package edu.uci.ics.texera.sqlservice.model;

import java.util.List;
import java.util.Map;

/**
 * DatasetBinding describes how a dataset (table) is bound to the SQL request.
 * Updated to support both user-defined columnTypes and system-inferredTypes.
 */
public class DatasetBinding {

    // --- Schema-based binding ---
    private List<String> columnNames;            // e.g., ["id", "name", "age"]
    private Map<String, String> columnTypes;     // User overrides (Static)
    private Map<String, String> inferredTypes;   // Automatically detected (Dynamic)

    // --- File-based binding ---
    private String path;                          // CSV file path
    private boolean hasHeader;                    // true if CSV has header row

    public DatasetBinding() {}

    public DatasetBinding(List<String> columnNames, Map<String, String> columnTypes, Map<String, String> inferredTypes) {
        this.columnNames = columnNames;
        this.columnTypes = columnTypes;
        this.inferredTypes = inferredTypes;
    }

    // --- Getters & Setters ---
    public List<String> getColumnNames() {
        return columnNames;
    }
    public void setColumnNames(List<String> columnNames) {
        this.columnNames = columnNames;
    }

    public Map<String, String> getColumnTypes() {
        return columnTypes;
    }
    public void setColumnTypes(Map<String, String> columnTypes) {
        this.columnTypes = columnTypes;
    }

    // NEW: Added inferredTypes to capture dynamic scan results
    public Map<String, String> getInferredTypes() {
        return inferredTypes;
    }
    public void setInferredTypes(Map<String, String> inferredTypes) {
        this.inferredTypes = inferredTypes;
    }

    public String getPath() {
        return path;
    }
    public void setPath(String path) {
        this.path = path;
    }

    public boolean isHasHeader() {
        return hasHeader;
    }
    public void setHasHeader(boolean hasHeader) {
        this.hasHeader = hasHeader;
    }

    @Override
    public String toString() {
        return "DatasetBinding{" +
                "columnNames=" + columnNames +
                ", columnTypes=" + columnTypes +
                ", inferredTypes=" + inferredTypes +
                ", path='" + path + '\'' +
                ", hasHeader=" + hasHeader +
                '}';
    }
}