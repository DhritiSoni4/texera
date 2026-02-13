package edu.uci.ics.texera.sqlservice.model;

import java.util.Map;

/**
 * Represents an incoming SQL request from the frontend.
 * Contains the SQL query string and an optional mapping of dataset names
 * to their bindings (schema information).
 */
public class SqlRequest {

    private String sql;  // The SQL query string
    private Map<String, DatasetBinding> bindings;  // Optional: dataset schema info

    // --- Constructors ---

    /** Default constructor */
    public SqlRequest() {}

    /** Constructor with only SQL */
    public SqlRequest(String sql) {
        this.sql = sql;
    }

    /** Constructor with SQL and bindings */
    public SqlRequest(String sql, Map<String, DatasetBinding> bindings) {
        this.sql = sql;
        this.bindings = bindings;
    }

    // --- Getters and Setters ---

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public Map<String, DatasetBinding> getBindings() {
        return bindings;
    }

    public void setBindings(Map<String, DatasetBinding> bindings) {
        this.bindings = bindings;
    }

    // --- toString ---

    @Override
    public String toString() {
        return "SqlRequest{" +
                "sql='" + sql + '\'' +
                ", bindings=" + bindings +
                '}';
    }
}
