package edu.uci.ics.texera.sqlservice.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TexeraOperator {

    @JsonProperty("operatorID")
    private String operatorID;

    @JsonProperty("operatorType")
    private String operatorType;

    @JsonProperty("operatorVersion")
    private String operatorVersion;

    @JsonProperty("operatorProperties")
    private Map<String, Object> operatorProperties;

    @JsonProperty("inputPorts")
    private List<Map<String, Object>> inputPorts;

    @JsonProperty("outputPorts")
    private List<Map<String, Object>> outputPorts;

    @JsonProperty("showAdvanced")
    private boolean showAdvanced;

    @JsonProperty("isDisabled")
    private boolean isDisabled;

    @JsonProperty("customDisplayName")
    private String customDisplayName;

    @JsonProperty("dynamicInputPorts")
    private boolean dynamicInputPorts;

    @JsonProperty("dynamicOutputPorts")
    private boolean dynamicOutputPorts;

    // Getters & Setters
    public String getOperatorID() { return operatorID; }
    public void setOperatorID(String operatorID) { this.operatorID = operatorID; }

    public String getOperatorType() { return operatorType; }
    public void setOperatorType(String operatorType) { this.operatorType = operatorType; }

    public String getOperatorVersion() { return operatorVersion; }
    public void setOperatorVersion(String operatorVersion) { this.operatorVersion = operatorVersion; }

    public Map<String, Object> getOperatorProperties() { return operatorProperties; }
    public void setOperatorProperties(Map<String, Object> operatorProperties) { this.operatorProperties = operatorProperties; }

    public List<Map<String, Object>> getInputPorts() { return inputPorts; }
    public void setInputPorts(List<Map<String, Object>> inputPorts) { this.inputPorts = inputPorts; }

    public List<Map<String, Object>> getOutputPorts() { return outputPorts; }
    public void setOutputPorts(List<Map<String, Object>> outputPorts) { this.outputPorts = outputPorts; }

    public boolean isShowAdvanced() { return showAdvanced; }
    public void setShowAdvanced(boolean showAdvanced) { this.showAdvanced = showAdvanced; }

    public boolean isDisabled() { return isDisabled; }
    public void setDisabled(boolean disabled) { isDisabled = disabled; }

    public String getCustomDisplayName() { return customDisplayName; }
    public void setCustomDisplayName(String customDisplayName) { this.customDisplayName = customDisplayName; }

    public boolean isDynamicInputPorts() { return dynamicInputPorts; }
    public void setDynamicInputPorts(boolean dynamicInputPorts) { this.dynamicInputPorts = dynamicInputPorts; }

    public boolean isDynamicOutputPorts() { return dynamicOutputPorts; }
    public void setDynamicOutputPorts(boolean dynamicOutputPorts) { this.dynamicOutputPorts = dynamicOutputPorts; }
}

