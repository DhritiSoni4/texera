package edu.uci.ics.texera.sqlservice.resource;

import edu.uci.ics.texera.sqlservice.service.DatasetService;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@Path("/dataset-preview")
@Produces(MediaType.APPLICATION_JSON)
public class DatasetPreviewResource {

    private final DatasetService datasetService;

    // Constructor injection for DatasetService
    public DatasetPreviewResource(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    /**
     * Endpoint to get a CSV dataset preview.
     * Example:
     *   GET /dataset-preview?path=/tmp/sample.csv&rows=5&header=true
     */
    @GET
    public Response getDatasetPreview(
            @QueryParam("path") String datasetPath,
            @QueryParam("rows") @DefaultValue("5") int numRows,
            @QueryParam("header") @DefaultValue("true") boolean hasHeader
    ) {
        try {
            if (datasetPath == null || datasetPath.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Missing required query parameter: path")
                        .build();
            }

            List<String[]> preview = datasetService.getDatasetPreview(datasetPath, numRows, hasHeader);
            return Response.ok(preview).build();

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error generating preview: " + e.getMessage())
                    .build();
        }
    }
}
