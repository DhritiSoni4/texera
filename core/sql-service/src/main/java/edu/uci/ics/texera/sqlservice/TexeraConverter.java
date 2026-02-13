package edu.uci.ics.texera.sqlservice;

import org.apache.calcite.rel.type.RelDataTypeField;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.*;
import org.apache.calcite.rel.logical.*;
import org.apache.calcite.rex.*;
import org.apache.calcite.rel.RelCollation;
import org.apache.calcite.rel.RelFieldCollation;

import java.util.*;

public class TexeraConverter {
    private final Map<RelNode, List<String>> schemaMap = new HashMap<>();

    // True once we have passed an Aggregate node in traversal
    private boolean hasAggregate = false;
    // Store HAVING filter to apply after Aggregate
    private LogicalFilter havingFilter = null;

    private Integer limitValue = null;
    private Integer offsetValue = null;

    private LogicalProject finalProject = null;
    private String finalOperatorId = null;

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<ObjectNode> operators = new ArrayList<>();
    private final List<ObjectNode> links = new ArrayList<>();
    private final ObjectNode operatorPositions = mapper.createObjectNode();
    private String lastOperatorId = null;
    private final Map<String, CsvDynamicTable> tableBindings;

    // --- Positioning fields ---
    private int currentX = 150; // start x for first operator
    private final int yPosition = 100; // fixed y for all operators
    private final int xStep = 200; // spacing between operators

    private final List<AggregateIntent> aggregateIntents;

    public TexeraConverter(
            Map<String, CsvDynamicTable> tableBindings,
            List<AggregateIntent> aggregateIntents) {

        this.tableBindings = tableBindings;  // now field initialized correctly
        this.aggregateIntents = aggregateIntents;
    }





    public ObjectNode convert(RelNode relNode) {

        finalProject = null;

        // Defer final projection until after all other operators



        // Traverse the tree (TableScan → Aggregate → Filter/Sort/Join etc.)
        String lastId = traverse(relNode);
        // Apply HAVING AFTER Aggregate
        if (havingFilter != null) {
            lastId = handleFilter(havingFilter, lastId);
        }
        if (limitValue != null || offsetValue != null) {
            lastId = appendLimitOperator(lastId);
        }

        // Append final projection if exists
        if (finalProject != null && !hasAggregate) {
            lastId = appendFinalProjection(finalProject, lastId);
        }



        // Build workflow JSON
        ObjectNode workflow = mapper.createObjectNode();
        workflow.set("operators", mapper.valueToTree(operators));
        workflow.set("operatorPositions", operatorPositions);
        workflow.set("links", mapper.valueToTree(links));
        workflow.putArray("commentBoxes");

        ObjectNode settings = mapper.createObjectNode();
        settings.put("dataTransferBatchSize", 400);
        workflow.set("settings", settings);

        return workflow;
    }



    private String traverse(RelNode node) {

        if (node instanceof LogicalTableScan) {
            return handleTableScan((LogicalTableScan) node);
        }

        if (node instanceof LogicalFilter) {
            LogicalFilter filter = (LogicalFilter) node;

            // HAVING case: Filter on top of Aggregate
            if (filter.getInput() instanceof LogicalAggregate) {
                havingFilter = filter;                  // store for later
                return traverse(filter.getInput());     // skip it for now
            }

            // WHERE case: normal filter
            String inputId = traverse(filter.getInput());
            return handleFilter(filter, inputId);
        }


        if (node instanceof LogicalJoin) {
            String leftId = traverse(((LogicalJoin) node).getLeft());
            String rightId = traverse(((LogicalJoin) node).getRight());
            return handleJoin((LogicalJoin) node, leftId, rightId);
        }

        if (node instanceof LogicalAggregate) {
            String inputId = traverse(node.getInput(0)); // CSV → Filter → Join → Aggregate
            return handleAggregate((LogicalAggregate) node, inputId, node.getInput(0));
        }

        if (node instanceof LogicalProject) {
            LogicalProject proj = (LogicalProject) node;

            String childId = traverse(proj.getInput());

            // ALWAYS propagate schema through Project
            List<String> childSchema = schemaMap.get(proj.getInput());
            if (childSchema != null) {
                schemaMap.put(proj, childSchema);
            }

            // Ignore projection on top of aggregate (already handled)
            if (!(proj.getInput() instanceof LogicalAggregate)) {
                if (!isSelectStar(proj)) {
                    finalProject = proj;
                }
            }

            return childId;
        }




        if (node instanceof LogicalSort) {
            LogicalSort sort = (LogicalSort) node;

            // Extract LIMIT / OFFSET if present
            if (sort.fetch != null) {
                limitValue = ((RexLiteral) sort.fetch).getValueAs(Integer.class);
            }
            if (sort.offset != null) {
                offsetValue = ((RexLiteral) sort.offset).getValueAs(Integer.class);
            }

            // ORDER BY still needs to be applied
            if (!sort.getCollation().getFieldCollations().isEmpty()) {
                String inputId = traverse(sort.getInput());
                return handleSort(sort, inputId);
            }

            // Only LIMIT/OFFSET → skip for now
            return traverse(sort.getInput());
        }


        throw new UnsupportedOperationException("Unsupported RelNode: " + node.getClass().getSimpleName());
    }


    // ---------------- Handlers ----------------
    private String handleTableScan(LogicalTableScan scan) {

        String id = "CSVFileScan-operator-" + UUID.randomUUID();
        ObjectNode op = baseOperator(id, "CSVFileScan", "CSV File Scan");

        String tableName =
                scan.getTable().getQualifiedName()
                        .get(scan.getTable().getQualifiedName().size() - 1);

        CsvDynamicTable table = tableBindings.get(tableName);
        ObjectNode props = mapper.createObjectNode();

        props.put("fileEncoding", "UTF_8");
        props.put("customDelimiter", ",");

        if (table != null) {
            props.put("fileName", table.getDatasetPath());
            props.put("hasHeader", table.hasHeader());

            props.set("columnTypes",
                    mapper.valueToTree(
                            table.getColumnTypes() != null
                                    ? table.getColumnTypes()
                                    : Map.of()
                    ));

            props.set("inferredTypes",
                    mapper.valueToTree(
                            table.getInferredTypes() != null
                                    ? table.getInferredTypes()
                                    : Map.of()
                    ));
        }

        op.set("operatorProperties", props);
        operators.add(op);
        addPosition(id);

        // ✅ SCHEMA REGISTRATION
        List<String> schema = new ArrayList<>();
        for (RelDataTypeField f : scan.getRowType().getFieldList()) {
            schema.add(table.getOriginalColumnName(f.getName())); // exact CSV name
        }

        schemaMap.put(scan, schema);

        lastOperatorId = id;
        return id;
    }



    private String handleJoin(LogicalJoin join, String leftId, String rightId) {

        String id = "HashJoin-operator-" + UUID.randomUUID();
        ObjectNode op = baseOperator(id, "HashJoin", "Hash Join");

        List<String> leftKeys = new ArrayList<>();
        List<String> rightKeys = new ArrayList<>();
        extractJoinKeys(join, leftKeys, rightKeys);

        ObjectNode props = mapper.createObjectNode();
        props.put("joinType", join.getJoinType().name().toLowerCase());

        if (leftKeys.size() == 1) {
            props.put("buildAttributeName", rightKeys.get(0));
            props.put("probeAttributeName", leftKeys.get(0));
        } else {
            ArrayNode buildAttrs = mapper.createArrayNode();
            ArrayNode probeAttrs = mapper.createArrayNode();
            for (int i = 0; i < leftKeys.size(); i++) {
                buildAttrs.add(rightKeys.get(i));
                probeAttrs.add(leftKeys.get(i));
            }
            props.set("buildAttributeNames", buildAttrs);
            props.set("probeAttributeNames", probeAttrs);
        }

        op.set("operatorProperties", props);
        operators.add(op);
        addPosition(id);

        // Wiring
        addJoinLink(leftId, id, "input-0");
        addJoinLink(rightId, id, "input-1");

        // ✅ Schema = left + right
        List<String> joinedSchema = new ArrayList<>();
        joinedSchema.addAll(schemaMap.get(join.getLeft()));
        joinedSchema.addAll(schemaMap.get(join.getRight()));
        schemaMap.put(join, joinedSchema);

        lastOperatorId = id;
        return id;
    }


    private String appendLimitOperator(String childId) {
        String id = "Limit-operator-" + UUID.randomUUID();
        ObjectNode op = baseOperator(id, "Limit", "Limit");

        ObjectNode props = mapper.createObjectNode();

        if (limitValue != null) {
            props.put("limit", limitValue);
        }
        if (offsetValue != null) {
            props.put("offset", offsetValue);
        }

        op.set("operatorProperties", props);

        operators.add(op);
        addPosition(id);
        addLink(childId, id);
        lastOperatorId = id;

        return id;
    }

    private String handleSort(LogicalSort sort, String childId) {
        String id = "Sort-operator-" + UUID.randomUUID();
        ObjectNode op = baseOperator(id, "Sort", "Sort");

        ArrayNode sortAttrs = mapper.createArrayNode();

        List<String> inputSchema = schemaMap.get(sort.getInput());

        for (RelFieldCollation c : sort.getCollation().getFieldCollations()) {
            ObjectNode attr = mapper.createObjectNode();
            String col = inputSchema.get(c.getFieldIndex());
            attr.put("attribute", col.toLowerCase());
            attr.put("order", c.direction.isDescending() ? "DESC" : "ASC");
            sortAttrs.add(attr);
        }

        ObjectNode props = mapper.createObjectNode();
        props.set("sortAttributes", sortAttrs);
        op.set("operatorProperties", props);

        operators.add(op);
        addPosition(id);
        addLink(childId, id);
        lastOperatorId = id;

        // ✅ SCHEMA SAME AS INPUT
        schemaMap.put(sort, inputSchema);

        return id;
    }


    private String appendFinalProjection(LogicalProject project, String childId) {

        String id = "Projection-operator-" + UUID.randomUUID();
        ObjectNode op = baseOperator(id, "Projection", "Projection");

        ArrayNode attrs = mapper.createArrayNode();

        // Use the schema from the input operator
        List<String> inputSchema = schemaMap.get(project.getInput());
        if (inputSchema == null) {
            throw new RuntimeException("Schema not found for final projection input");
        }

        for (int i = 0; i < project.getProjects().size(); i++) {
            String col;
            RexNode expr = project.getProjects().get(i);

            if (expr instanceof RexInputRef) {
                int idx = ((RexInputRef) expr).getIndex();
                col = inputSchema.get(idx);  // correctly maps after join
            } else {
                col = expr.toString(); // fallback for expressions
            }
            attrs.add(col);
        }

        ObjectNode props = mapper.createObjectNode();
        props.set("attributes", attrs);
        op.set("operatorProperties", props);

        operators.add(op);
        addPosition(id);
        addLink(childId, id);
        lastOperatorId = id;

        // Update schema for Projection operator
        List<String> projSchema = new ArrayList<>();
        for (int i = 0; i < attrs.size(); i++) {
            projSchema.add(attrs.get(i).asText());
        }
        schemaMap.put(project, projSchema);

        return id;
    }







    private String handleAggregate(
            LogicalAggregate agg,
            String inputId,
            RelNode inputNode
    ) {
        String id = "Aggregate-operator-" + UUID.randomUUID();
        ObjectNode op = baseOperator(id, "Aggregate", "Aggregate");

        ObjectNode props = mapper.createObjectNode();
        ArrayNode groupByKeys = mapper.createArrayNode();

        // ✅ RESOLVE schema safely through wrappers
        List<String> inputSchema = schemaMap.get(inputNode);
        if (inputSchema == null) {
            inputSchema = schemaMap.get(getUnderlyingInput(inputNode));
        }
        if (inputSchema == null) {
            throw new RuntimeException(
                    "Schema not found for Aggregate input: " + inputNode.getClass().getSimpleName()
            );
        }

        // ---------- GROUP BY ----------
        for (int idx : agg.getGroupSet()) {
            groupByKeys.add(inputSchema.get(idx));
        }
        props.set("groupByKeys", groupByKeys);

        // ---------- AGGREGATIONS ----------
        ArrayNode aggregations = mapper.createArrayNode();

        Map<String, String> funcMapping = Map.of(
                "avg", "average",
                "count", "count",
                "sum", "sum",
                "min", "min",
                "max", "max"
        );

        for (AggregateCall call : agg.getAggCallList()) {
            String aggFunc = funcMapping.getOrDefault(
                    call.getAggregation().getName().toLowerCase(),
                    call.getAggregation().getName().toLowerCase()
            );

            String resultAttr =
                    call.getName() != null
                            ? call.getName()
                            : aggFunc + "_col";

            String attribute =
                    call.getArgList().isEmpty()
                            ? "*"
                            : inputSchema.get(call.getArgList().get(0));

            ObjectNode aggNode = mapper.createObjectNode();
            aggNode.put("aggFunction", aggFunc);
            aggNode.put("attribute", attribute);
            aggNode.put("result attribute", resultAttr);
            aggregations.add(aggNode);
        }

        props.set("aggregations", aggregations);
        op.set("operatorProperties", props);

        operators.add(op);
        addPosition(id);
        addLink(inputId, id);
        lastOperatorId = id;

        // ---------- SCHEMA PROPAGATION ----------
        List<String> aggSchema = new ArrayList<>();

        // group keys first
        for (int i = 0; i < groupByKeys.size(); i++) {
            aggSchema.add(groupByKeys.get(i).asText());
        }

        // then aggregate outputs
        for (int i = 0; i < aggregations.size(); i++) {
            aggSchema.add(
                    aggregations.get(i).get("result attribute").asText()
            );
        }

        schemaMap.put(agg, aggSchema);
        hasAggregate = true;

        return id;
    }







    private String resolveColumnFromInput(RelNode input, int fieldIndex) {
        if (input instanceof LogicalProject) {
            LogicalProject proj = (LogicalProject) input;
            RexNode expr = proj.getProjects().get(fieldIndex);
            if (expr instanceof RexInputRef) {
                int idx = ((RexInputRef) expr).getIndex();
                return resolveColumnFromInput(proj.getInput(), idx);
            } else {
                return expr.toString().toLowerCase();
            }
        }

        if (input instanceof LogicalFilter) {
            return resolveColumnFromInput(((LogicalFilter) input).getInput(), fieldIndex);
        }

        if (input instanceof LogicalJoin) {
            LogicalJoin join = (LogicalJoin) input;
            List<String> joinedSchema = schemaMap.get(join);
            if (joinedSchema == null) {
                throw new RuntimeException("Schema not found for join in resolveColumnAfterJoin");
            }
            return joinedSchema.get(fieldIndex);  // <-- pick column from joined schema
        }


        if (input instanceof LogicalTableScan) {
            LogicalTableScan scan = (LogicalTableScan) input;
            // Use your existing tableBindings map
            CsvDynamicTable table = tableBindings.get(scan.getTable().getQualifiedName().get(0));
            String colName = scan.getRowType().getFieldNames().get(fieldIndex);

            if (table != null) {
                return table.getOriginalColumnName(colName); // returns CSV column name
            } else {
                return colName.toLowerCase();
            }
        }

        // Fallback
        return input.getRowType().getFieldNames().get(fieldIndex).toLowerCase();
    }



    // Helper to map Calcite field name to original CSV column
    private String mapToOriginalColumn(RelNode input, String calciteColName) {
        if (calciteColName == null) return "";

        if (input instanceof LogicalTableScan) {
            LogicalTableScan scan = (LogicalTableScan) input;
            String tableName = scan.getTable().getQualifiedName().get(0);
            CsvDynamicTable table = tableBindings.get(tableName);
            if (table != null) {
                // ✅ Preserve exact CSV column name
                return table.getOriginalColumnName(calciteColName);
            }
        }

        // Fallback: return the Calcite name as-is (preserve case)
        return calciteColName;
    }




    private LogicalTableScan findTableScan(RelNode node) {
        if (node instanceof LogicalTableScan) {
            return (LogicalTableScan) node;
        }
        for (RelNode input : node.getInputs()) {
            LogicalTableScan scan = findTableScan(input);
            if (scan != null) return scan;
        }
        return null;
    }


    private String handleFilter(LogicalFilter filter, String childId) {
        RexNode condition = filter.getCondition();
        String id = buildFilterChain(condition, filter.getInput(), childId);
        // Schema unchanged
        schemaMap.put(filter, schemaMap.get(filter.getInput()));
        return id;
    }

    private String buildFilterChain(RexNode condition, RelNode input, String childId) {
        if (condition instanceof RexCall) {
            RexCall call = (RexCall) condition;
            String op = call.getOperator().getName().toUpperCase();

            if (op.equals("AND")) {
                String left = buildFilterChain(call.operands.get(0), input, childId);
                return buildFilterChain(call.operands.get(1), input, left);
            }

            if (op.equals("OR")) {
                return createOrFilter(call.operands, input, childId);
            }

            if (op.equals("IN") && call.operands.stream().anyMatch(o -> o instanceof RexSubQuery)) {
                return handleInSubquery(call, input, childId);
            }

            return createSinglePredicateFilter(condition, input, childId);
        }
        return createSinglePredicateFilter(condition, input, childId);
    }


    private String createOrFilter(List<RexNode> operands, RelNode input, String childId) {
        String id = "Filter-operator-" + UUID.randomUUID();
        ObjectNode op = baseOperator(id, "Filter", "Filter");
        ArrayNode preds = mapper.createArrayNode();

        for (RexNode operand : operands) {
            SimplePredicate p = extractPredicate(operand, input);
            ObjectNode pred = mapper.createObjectNode();
            pred.put("attribute", p.attribute);
            pred.put("condition", p.condition);
            pred.put("value", p.value);
            preds.add(pred);
        }

        ObjectNode props = mapper.createObjectNode();
        props.set("predicates", preds);
        op.set("operatorProperties", props);

        operators.add(op);
        addPosition(id);
        addLink(childId, id);
        lastOperatorId = id;

        return id;
    }








    private String createSinglePredicateFilter(
            RexNode condition,
            RelNode input,
            String childId
    ) {
        SimplePredicate predicate = extractPredicate(condition, input);

        String id = "Filter-operator-" + UUID.randomUUID();
        ObjectNode op = baseOperator(id, "Filter", "Filter");

        ArrayNode preds = mapper.createArrayNode();
        ObjectNode pred = mapper.createObjectNode();
        pred.put("attribute", predicate.attribute);
        pred.put("condition", predicate.condition);
        pred.put("value", predicate.value);
        preds.add(pred);

        ObjectNode props = mapper.createObjectNode();
        props.set("predicates", preds);
        op.set("operatorProperties", props);

        operators.add(op);
        addPosition(id);
        addLink(childId, id);
        lastOperatorId = id;

        return id;
    }







    private String handleInSubquery(RexCall inCall, RelNode mainInput, String mainChildId) {
        // Outer column
        RexNode lhs = inCall.operands.get(0);
        String mainCol = resolveRexOperand(lhs, mainInput).toLowerCase();

        // Subquery
        RexSubQuery subQuery = (RexSubQuery) inCall.operands.get(1);
        RelNode subRel = subQuery.rel;

        // Convert subquery operators first
        String subQueryOutputId = traverse(subRel);

        // Get subquery projected column
        String subqueryCol = extractSubqueryOutputColumn(subRel);

        // Create HashJoin for IN (always single-column)
        String joinId = "HashJoin-operator-" + UUID.randomUUID();
        ObjectNode op = baseOperator(joinId, "HashJoin", "Hash Join (for IN)");

        ObjectNode props = mapper.createObjectNode();
        props.put("joinType", "inner");
        props.put("buildAttributeName", subqueryCol); // right input
        props.put("probeAttributeName", mainCol);     // left input
        op.set("operatorProperties", props);

        operators.add(op);
        addPosition(joinId);

        addLink(mainChildId, joinId, "input-0");
        addLink(subQueryOutputId, joinId, "input-1");

        lastOperatorId = joinId;
        return joinId;
    }



    // Unwrap a single LogicalProject if present
    private RelNode getUnderlyingInput(RelNode node) {
        if (node instanceof LogicalProject) {
            return ((LogicalProject) node).getInput();
        }
        return node;
    }


    private String extractSubqueryOutputColumn(RelNode subRel) {
        if (subRel instanceof LogicalProject) {
            LogicalProject proj = (LogicalProject) subRel;
            return resolveColumnAfterJoin(proj.getInput(), ((RexInputRef) proj.getProjects().get(0)).getIndex());
        } else if (subRel instanceof LogicalAggregate) {
            return resolveColumnAfterJoin(subRel.getInput(0), 0);
        } else if (subRel instanceof LogicalTableScan) {
            return resolveColumnAfterJoin(subRel, 0);
        } else {
            return subRel.getRowType().getFieldNames().get(0).toLowerCase();
        }
    }



    private void extractJoinKeys(
            LogicalJoin join,
            List<String> leftKeys,
            List<String> rightKeys
    ) {
        RexNode condition = join.getCondition();

        if (!(condition instanceof RexCall)) {
            throw new RuntimeException("Unsupported join condition: " + condition);
        }

        RexCall call = (RexCall) condition;

        if (call.getOperands().size() != 2) {
            throw new RuntimeException("Only simple equi-joins supported");
        }

        RexInputRef leftRef = (RexInputRef) call.getOperands().get(0);
        RexInputRef rightRef = (RexInputRef) call.getOperands().get(1);

        int leftFieldCount = join.getLeft().getRowType().getFieldCount();

        int leftIdx = leftRef.getIndex();
        int rightIdx = rightRef.getIndex() - leftFieldCount;

        String leftAttr =
                join.getLeft()
                        .getRowType()
                        .getFieldList()
                        .get(leftIdx)
                        .getName();

        String rightAttr =
                join.getRight()
                        .getRowType()
                        .getFieldList()
                        .get(rightIdx)
                        .getName();

        leftKeys.add(mapToOriginalColumn(join.getLeft(), leftAttr));
        rightKeys.add(mapToOriginalColumn(join.getRight(), rightAttr));

    }



    private void addJoinLink(String sourceId, String targetId, String targetPort) {

        ObjectNode link = mapper.createObjectNode();
        link.put("linkID", "link-" + UUID.randomUUID());

        ObjectNode src = mapper.createObjectNode();
        src.put("operatorID", sourceId);
        src.put("portID", "output-0");

        ObjectNode tgt = mapper.createObjectNode();
        tgt.put("operatorID", targetId);
        tgt.put("portID", targetPort);

        link.set("source", src);
        link.set("target", tgt);

        links.add(link);
    }


    private SimplePredicate extractPredicate(RexNode condition, RelNode input) {
        if (!(condition instanceof RexCall)) throw new RuntimeException("Unsupported filter condition: " + condition);

        RexCall call = (RexCall) condition;
        RexNode lhs = call.getOperands().get(0);
        RexNode rhs = call.getOperands().get(1);

        SimplePredicate p = new SimplePredicate();
        p.condition = mapOperatorToSymbol(call.getOperator().getName());

        if (lhs instanceof RexInputRef) {
            int idx = ((RexInputRef) lhs).getIndex();
            p.attribute = resolveColumnAfterJoin(input, idx);
            p.value = resolveRexOperand(rhs, input);
        } else {
            int idx = ((RexInputRef) rhs).getIndex();
            p.attribute = resolveColumnAfterJoin(input, idx);
            p.value = resolveRexOperand(lhs, input);
            p.condition = flipOperator(p.condition);
        }
        // Do not lowercase. Use original CSV name
        p.attribute = p.attribute;

        return p;
    }







    class SimplePredicate {
        String attribute;
        String condition;
        String value;
    }

    private void collectPredicates(
            RexNode condition,
            RelNode input,
            List<SimplePredicate> out
    ) {
        if (!(condition instanceof RexCall)) return;

        RexCall call = (RexCall) condition;
        String op = call.getOperator().getName().toUpperCase();

        if (op.equals("AND")) {
            collectPredicates(call.operands.get(0), input, out);
            collectPredicates(call.operands.get(1), input, out);
            return;
        }

        if (op.equals("OR")) {
            throw new UnsupportedOperationException(
                    "OR conditions require branching and are not supported"
            );
        }

        if (call.operands.size() != 2) return;

        String symbol = mapOperatorToSymbol(op);

        RexNode lhs = call.operands.get(0);
        RexNode rhs = call.operands.get(1);

        boolean lhsCol = lhs instanceof RexInputRef;
        boolean rhsCol = rhs instanceof RexInputRef;

        SimplePredicate p = new SimplePredicate();

        if (!lhsCol && rhsCol) {
            p.attribute = resolveRexOperand(rhs, input).toLowerCase();
            p.value = resolveRexOperand(lhs, input);
            p.condition = flipOperator(symbol);
        } else {
            p.attribute = resolveRexOperand(lhs, input).toLowerCase();
            p.value = resolveRexOperand(rhs, input);
            p.condition = symbol;
        }

        out.add(p);
    }

    private String resolveRexOperand(RexNode expr, RelNode input) {
        if (expr instanceof RexInputRef) {
            int idx = ((RexInputRef) expr).getIndex();
            String calciteName = input.getRowType().getFieldList().get(idx).getName();

            // Map to original CSV column name if possible
            if (input instanceof LogicalTableScan) {
                LogicalTableScan scan = (LogicalTableScan) input;
                String tableName = scan.getTable().getQualifiedName().get(0);
                CsvDynamicTable table = tableBindings.get(tableName);
                if (table != null) {
                    // ✅ Preserve exact CSV case
                    return table.getOriginalColumnName(calciteName);
                }
            }

            // Fallback: return Calcite field name as-is (preserve case)
            return calciteName;
        }
        else if (expr instanceof RexLiteral) {
            Object value = ((RexLiteral) expr).getValue2();
            return value != null ? value.toString() : expr.toString();
        }
        else if (expr instanceof RexCall) {
            RexCall call = (RexCall) expr;
            // If CAST, just resolve inner operand
            if (call.getOperator().getName().equalsIgnoreCase("CAST") && call.operands.size() == 1) {
                return resolveRexOperand(call.operands.get(0), input);
            } else {
                // fallback for other calls: use toString()
                return expr.toString();
            }
        }
        else {
            // Generic fallback: just string representation
            return expr.toString();
        }
    }


    // --- Helper to check if projection is SELECT * ---
    private boolean isSelectStar(LogicalProject proj) {
        int projectedCols = proj.getProjects().size();
        int inputCols = proj.getInput().getRowType().getFieldCount();

        if (projectedCols != inputCols) {
            return false;
        }

        // Ensure projection is a direct pass-through in order
        for (int i = 0; i < projectedCols; i++) {
            RexNode expr = proj.getProjects().get(i);
            if (!(expr instanceof RexInputRef)) {
                return false;
            }
            if (((RexInputRef) expr).getIndex() != i) {
                return false;
            }
        }

        return true;
    }


    private String mapOperatorToSymbol(String calciteOpName) {
        switch (calciteOpName.toUpperCase()) {
            case "GREATER":
            case "GREATER_THAN":
                return ">";
            case "LESS":
            case "LESS_THAN":
                return "<";
            case "EQUALS":
            case "EQUAL":
                return "=";
            case "NOT_EQUALS":
                return "!=";
            case "GREATER_THAN_OR_EQUAL":
                return ">=";
            case "LESS_THAN_OR_EQUAL":
                return "<=";
            default:
                return calciteOpName;
        }
    }
    private String flipOperator(String op) {
        switch (op) {
            case ">":  return "<";
            case "<":  return ">";
            case ">=": return "<=";
            case "<=": return ">=";
            case "=":  return "=";
            case "!=": return "!=";
            default:   return op;
        }
    }

    private ObjectNode baseOperator(String id, String type, String displayName) {

        ObjectNode op = mapper.createObjectNode();
        op.put("operatorID", id);
        op.put("operatorType", type);
        op.put("operatorVersion", "N/A");

        ArrayNode inputPorts = mapper.createArrayNode();

        if (type.equals("HashJoin")) {

            // input-0 (left)
            ObjectNode left = mapper.createObjectNode();
            left.put("portID", "input-0");
            left.put("displayName", "left");
            left.put("allowMultiInputs", false);
            left.put("isDynamicPort", false);
            left.set("dependencies", mapper.createArrayNode());
            inputPorts.add(left);

            // input-1 (right, depends on input-0)
            ObjectNode right = mapper.createObjectNode();
            right.put("portID", "input-1");
            right.put("displayName", "right");
            right.put("allowMultiInputs", false);
            right.put("isDynamicPort", false);

            ArrayNode deps = mapper.createArrayNode();
            ObjectNode dep = mapper.createObjectNode();
            dep.put("id", 0);
            dep.put("internal", false);
            deps.add(dep);

            right.set("dependencies", deps);
            inputPorts.add(right);

        } else if (!type.equals("CSVFileScan")) {

            ObjectNode inPort = mapper.createObjectNode();
            inPort.put("portID", "input-0");
            inPort.put("displayName", "");
            inPort.put("allowMultiInputs", false);
            inPort.put("isDynamicPort", false);
            inPort.set("dependencies", mapper.createArrayNode());
            inputPorts.add(inPort);
        }

        op.set("inputPorts", inputPorts);

        // output
        ArrayNode outputPorts = mapper.createArrayNode();
        ObjectNode outPort = mapper.createObjectNode();
        outPort.put("portID", "output-0");
        outPort.put("displayName", "");
        outPort.put("allowMultiInputs", false);
        outPort.put("isDynamicPort", false);
        outputPorts.add(outPort);

        op.set("outputPorts", outputPorts);

        op.put("showAdvanced", false);
        op.put("isDisabled", false);
        op.put("customDisplayName", displayName);
        op.put("dynamicInputPorts", false);
        op.put("dynamicOutputPorts", false);

        return op;
    }
    private String resolveColumnAfterJoin(RelNode input, int fieldIndex) {

        if (input instanceof LogicalProject) {
            LogicalProject proj = (LogicalProject) input;
            RexNode expr = proj.getProjects().get(fieldIndex);
            if (expr instanceof RexInputRef) {
                return resolveColumnAfterJoin(proj.getInput(), ((RexInputRef) expr).getIndex());
            } else {
                return mapToOriginalColumn(proj.getInput(), expr.toString());
            }
        }

        if (input instanceof LogicalFilter) {
            return resolveColumnAfterJoin(((LogicalFilter) input).getInput(), fieldIndex);
        }

        if (input instanceof LogicalJoin) {
            List<String> schema = schemaMap.get(input);
            if (schema == null) throw new RuntimeException("Schema not found for join");
            return schema.get(fieldIndex);
        }

        if (input instanceof LogicalAggregate) {
            LogicalAggregate agg = (LogicalAggregate) input;
            List<String> schema = schemaMap.get(input);
            if (schema == null) throw new RuntimeException("Schema not found for aggregate");
            return schema.get(fieldIndex);
        }

        if (input instanceof LogicalTableScan) {
            LogicalTableScan scan = (LogicalTableScan) input;
            CsvDynamicTable table = tableBindings.get(scan.getTable().getQualifiedName().get(0));
            String colName = scan.getRowType().getFieldNames().get(fieldIndex);
            return table != null ? table.getOriginalColumnName(colName) : colName;
        }

        return input.getRowType().getFieldNames().get(fieldIndex);
    }







    // --- simplified position ---
    private void addPosition(String operatorId) {
        ObjectNode pos = mapper.createObjectNode();
        pos.put("x", currentX);
        pos.put("y", yPosition + (operators.size() * 40));
        operatorPositions.set(operatorId, pos);
        currentX += xStep;
    }

    private void addLink(String sourceId, String targetId) {
        // default input port = "input-0"
        addLink(sourceId, targetId, "input-0");
    }

    private void addLink(String sourceId, String targetId, String targetPort) {
        if (sourceId == null) return;

        ObjectNode link = mapper.createObjectNode();
        link.put("linkID", "link-" + UUID.randomUUID());

        ObjectNode src = mapper.createObjectNode();
        src.put("operatorID", sourceId);
        src.put("portID", "output-0"); // always output-0 for source
        link.set("source", src);

        ObjectNode tgt = mapper.createObjectNode();
        tgt.put("operatorID", targetId);
        tgt.put("portID", targetPort); // now we can specify input port
        link.set("target", tgt);

        links.add(link);
    }

}
