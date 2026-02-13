package edu.uci.ics.texera.sqlservice.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.uci.ics.texera.sqlservice.*;
import edu.uci.ics.texera.sqlservice.model.DatasetBinding;
import edu.uci.ics.texera.sqlservice.model.SqlRequest;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.validate.SqlConformanceEnum;
import org.apache.calcite.tools.*;
import edu.uci.ics.texera.sqlservice.optimizer.LogicalPlanRewriter;


import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;
import java.util.stream.Collectors;

@Path("/api/sql")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SqlResource {

    private final ObjectMapper mapper = new ObjectMapper();

    @POST
    @Path("/convert")
    public Response convertSqlToWorkflow(SqlRequest request) {
        try {
            String sql = request.getSql();
            Map<String, DatasetBinding> bindings = request.getBindings();

            if (bindings == null || bindings.isEmpty()) {
                throw new IllegalArgumentException("No bindings provided");
            }

            Map<String, CsvDynamicTable> tableMap = new HashMap<>();
            FrameworkConfig config = buildFrameworkConfig(bindings, tableMap);
            Planner planner = Frameworks.getPlanner(config);

            SqlNode parsed = planner.parse(sql);
            parsed = AliasExpander.expand(parsed); // optional alias handling
            SqlNode validated = planner.validate(parsed);

            List<AggregateIntent> aggregateIntents = extractAggregateIntents(validated);
            RelNode relNode = planner.rel(validated).rel;

            System.out.println("=== Logical Plan (BEFORE Optimization) ===");
            System.out.println(RelOptUtil.toString(relNode));

//  Logical plan optimization pass
            LogicalPlanRewriter rewriter = new LogicalPlanRewriter();
            RelNode optimizedRelNode = rewriter.rewrite(relNode);

            System.out.println("=== Logical Plan (AFTER Optimization) ===");
            System.out.println(RelOptUtil.toString(optimizedRelNode));

// Pass optimized plan to Texera
            TexeraConverter converter = new TexeraConverter(tableMap, aggregateIntents);
            ObjectNode workflowJson = converter.convert(optimizedRelNode);


            return Response.ok(workflowJson).build();

        } catch (Exception e) {
            e.printStackTrace();
            ObjectNode error = mapper.createObjectNode().put("error", e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST).entity(error).build();
        }
    }

    private List<AggregateIntent> extractAggregateIntents(SqlNode validatedSql) {
        List<AggregateIntent> aggregates = new ArrayList<>();
        validatedSql.accept(new org.apache.calcite.sql.util.SqlShuttle() {
            @Override
            public SqlNode visit(SqlCall call) {
                if (call.getKind() == SqlKind.AS &&
                        call.operand(0) instanceof SqlCall) {

                    SqlCall aggCall = (SqlCall) call.operand(0);
                    SqlKind kind = aggCall.getKind();

                    if (EnumSet.of(
                            SqlKind.COUNT,
                            SqlKind.SUM,
                            SqlKind.AVG,
                            SqlKind.MIN,
                            SqlKind.MAX
                    ).contains(kind)) {

                        String function = kind.name().toLowerCase();
                        String alias = call.operand(1).toString();

                        String column = "*";
                        if (aggCall.operandCount() == 1 &&
                                aggCall.operand(0) instanceof SqlIdentifier) {
                            column = aggCall.operand(0).toString();
                        }

                        aggregates.add(new AggregateIntent(function, column, alias));
                    }
                }
                return super.visit(call);
            }
        });
        return aggregates;
    }

    private static FrameworkConfig buildFrameworkConfig(
            Map<String, DatasetBinding> bindings,
            Map<String, CsvDynamicTable> tableMap
    ) throws Exception {

        Connection connection = DriverManager.getConnection("jdbc:calcite:");
        CalciteConnection calciteConnection = connection.unwrap(CalciteConnection.class);
        var rootSchema = calciteConnection.getRootSchema();

        for (Map.Entry<String, DatasetBinding> entry : bindings.entrySet()) {
            String tableName = entry.getKey();
            DatasetBinding binding = entry.getValue();

            // Columns (uppercase)
            List<String> upperColumns = binding.getColumnNames().stream()
                    .map(String::toUpperCase)
                    .collect(Collectors.toList());

            // Static user-provided types (uppercase)
            Map<String, String> upperColumnTypes = binding.getColumnTypes() != null
                    ? binding.getColumnTypes().entrySet().stream()
                    .collect(Collectors.toMap(
                            e -> e.getKey().toUpperCase(),
                            Map.Entry::getValue
                    ))
                    : new HashMap<>();

            // Inferred types (uppercase)
            Map<String, String> upperInferredTypes = binding.getInferredTypes() != null
                    ? binding.getInferredTypes().entrySet().stream()
                    .collect(Collectors.toMap(
                            e -> e.getKey().toUpperCase(),
                            Map.Entry::getValue
                    ))
                    : new HashMap<>();

            // Create CsvDynamicTable
            CsvDynamicTable table = new CsvDynamicTable(
                    binding.getPath(),
                    binding.isHasHeader(),
                    upperColumns,
                    upperInferredTypes,
                    upperColumnTypes
            );

            // Add table to schema and tableMap
            rootSchema.add(tableName, table);
            tableMap.put(tableName, table);
        }

        SqlParser.Config parserConfig = SqlParser.config()
                .withConformance(SqlConformanceEnum.DEFAULT)
                .withCaseSensitive(false);

        return Frameworks.newConfigBuilder()
                .parserConfig(parserConfig)
                .defaultSchema(rootSchema)
                .build();
    }
}
