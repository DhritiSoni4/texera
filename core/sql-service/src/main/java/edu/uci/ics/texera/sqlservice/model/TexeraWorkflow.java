package edu.uci.ics.texera.sqlservice.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TexeraWorkflow {

    @JsonProperty("operators")
    private List<TexeraOperator> operators;

    @JsonProperty("links")
    private List<TexeraLink> links;

    @JsonProperty("operatorPositions")
    private Map<String, Map<String, Double>> operatorPositions;

    @JsonProperty("settings")
    private Map<String, Object> settings;

    @JsonProperty("commentBoxes")
    private List<Object> commentBoxes;

    // Getters & Setters
    public List<TexeraOperator> getOperators() { return operators; }
    public void setOperators(List<TexeraOperator> operators) { this.operators = operators; }

    public List<TexeraLink> getLinks() { return links; }
    public void setLinks(List<TexeraLink> links) { this.links = links; }

    public Map<String, Map<String, Double>> getOperatorPositions() { return operatorPositions; }
    public void setOperatorPositions(Map<String, Map<String, Double>> operatorPositions) { this.operatorPositions = operatorPositions; }

    public Map<String, Object> getSettings() { return settings; }
    public void setSettings(Map<String, Object> settings) { this.settings = settings; }

    public List<Object> getCommentBoxes() { return commentBoxes; }
    public void setCommentBoxes(List<Object> commentBoxes) { this.commentBoxes = commentBoxes; }
}
