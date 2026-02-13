package edu.uci.ics.texera.sqlservice.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TexeraLink {

    @JsonProperty("fromOperator")
    private String fromOperator;

    @JsonProperty("fromPort")
    private int fromPort;

    @JsonProperty("toOperator")
    private String toOperator;

    @JsonProperty("toPort")
    private int toPort;

    @JsonProperty("linkID")
    private String linkID;

    // Getters & Setters
    public String getFromOperator() { return fromOperator; }
    public void setFromOperator(String fromOperator) { this.fromOperator = fromOperator; }

    public int getFromPort() { return fromPort; }
    public void setFromPort(int fromPort) { this.fromPort = fromPort; }

    public String getToOperator() { return toOperator; }
    public void setToOperator(String toOperator) { this.toOperator = toOperator; }

    public int getToPort() { return toPort; }
    public void setToPort(int toPort) { this.toPort = toPort; }

    public String getLinkID() { return linkID; }
    public void setLinkID(String linkID) { this.linkID = linkID; }
}

