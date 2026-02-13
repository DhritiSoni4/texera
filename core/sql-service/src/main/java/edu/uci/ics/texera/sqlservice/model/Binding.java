package edu.uci.ics.texera.sqlservice.model;

import java.util.Map;

/**
 * Represents parameter and dataset bindings for an SQL query.
 * Example:
 *   SQL: SELECT * FROM users WHERE age > :minAge
 *   Parameters: { "minAge": 25 }
 *   Datasets:   { "path": "/path/to/users.csv", "hasHeader": true }
 */
public class Binding {

    private Map<String, Object> parameters;   // named params for query
    private Map<String, Object> datasets;     // dataset info, e.g., path, hasHeader

    public Binding() {}

    public Binding(Map<String, Object> parameters, Map<String, Object> datasets) {
        this.parameters = parameters;
        this.datasets = datasets;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public Map<String, Object> getDatasets() {
        return datasets;
    }

    public void setDatasets(Map<String, Object> datasets) {
        this.datasets = datasets;
    }

    @Override
    public String toString() {
        return "Binding{" +
                "parameters=" + parameters +
                ", datasets=" + datasets +
                '}';
    }
}
