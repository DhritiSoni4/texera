package edu.uci.ics.texera.sqlservice;

import edu.uci.ics.texera.sqlservice.resource.SqlResource;
import edu.uci.ics.texera.sqlservice.resource.DatasetPreviewResource;
import edu.uci.ics.texera.sqlservice.service.DatasetService;
import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

/**
 * Entry point for the SQL Service built using Dropwizard and Apache Calcite.
 * Registers resources for SQL query conversion and dataset preview functionality.
 */
public class CalciteApplication extends Application<CalciteConfiguration> {

    public static void main(String[] args) throws Exception {
        new CalciteApplication().run(args);
    }

    @Override
    public void initialize(Bootstrap<CalciteConfiguration> bootstrap) {
        // You can add initialization logic here, e.g., bundles or commands.
    }

    @Override
    public void run(CalciteConfiguration configuration, Environment environment) {
        // SQL conversion
        final SqlResource sqlResource = new SqlResource();
        environment.jersey().register(sqlResource);

        // Dataset preview
        final DatasetService datasetService = new DatasetService();
        final DatasetPreviewResource datasetPreviewResource = new DatasetPreviewResource(datasetService);
        environment.jersey().register(datasetPreviewResource);
    }

}
