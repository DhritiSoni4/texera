package edu.uci.ics.texera.sqlservice;

public class AggregateIntent {

    public final String function;   // count, sum, avg, min, max
    public final String column;     // age, salary, *
    public final String alias;      // num_users

    public AggregateIntent(String function, String column, String alias) {
        this.function = function.toLowerCase();
        this.column = column == null ? "*" : column.toLowerCase();
        this.alias = alias.toLowerCase();
    }
}
