package edu.uci.ics.amber.operator.source.scan

import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonProperty}
import edu.uci.ics.amber.core.executor.OpExecWithClassName
import edu.uci.ics.amber.core.tuple.{Attribute, AttributeType, Schema}
import edu.uci.ics.amber.core.virtualidentity.{ExecutionIdentity, WorkflowIdentity}
import edu.uci.ics.amber.core.workflow.{PhysicalOp, SchemaPropagationFunc}
import edu.uci.ics.amber.operator.source.scan.text.TextSourceOpDesc
import edu.uci.ics.amber.util.JSONUtils.objectMapper
import scala.collection.mutable

@JsonIgnoreProperties(ignoreUnknown = true)
class FileScanSourceOpDesc extends ScanSourceOpDesc with TextSourceOpDesc {

  @JsonProperty var columnTypes: Map[String, String] = Map.empty
  @JsonProperty var inferredTypes: Map[String, String] = Map.empty

  @JsonProperty(defaultValue = "true") var hasHeader: Boolean = true
  @JsonProperty(defaultValue = "false") var outputFileName: Boolean = false
  @JsonProperty(defaultValue = "false") var extract: Boolean = false

  fileTypeName = Option("")

  override def getPhysicalOp(workflowId: WorkflowIdentity, executionId: ExecutionIdentity): PhysicalOp = {
    PhysicalOp
      .sourcePhysicalOp(
        workflowId,
        executionId,
        operatorIdentifier,
        OpExecWithClassName(
          "edu.uci.ics.amber.operator.source.scan.FileScanSourceOpExec",
          objectMapper.writeValueAsString(this)
        )
      )
      .withInputPorts(operatorInfo.inputPorts)
      .withOutputPorts(operatorInfo.outputPorts)
      .withPropagateSchema(
        SchemaPropagationFunc(_ => Map(operatorInfo.outputPorts.head.id -> sourceSchema()))
      )
  }

  override def sourceSchema(): Schema = {
    val merged = mutable.LinkedHashMap[String, String]()

    // 1️⃣ start with inferred types
    inferredTypes.foreach { case (k, v) =>
      merged.put(k.toLowerCase, v.toLowerCase)
    }

    // 2️⃣ user overrides ALWAYS win (even string)
    columnTypes.foreach { case (k, v) =>
      merged.put(k.toLowerCase, v.toLowerCase)
    }

    val attributes = mutable.ListBuffer[Attribute]()
    if (outputFileName) attributes += new Attribute("filename", AttributeType.STRING)

    merged.foreach { case (name, typeStr) =>
      val attrType = FileAttributeType.values()
        .find(v => v.getName.equalsIgnoreCase(typeStr))
        .map(_.getType)
        .getOrElse(AttributeType.STRING)

      attributes += new Attribute(name, attrType)
    }

    Schema(attributes.toList)
  }




}
