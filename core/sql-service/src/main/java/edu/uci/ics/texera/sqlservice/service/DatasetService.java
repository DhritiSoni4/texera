package edu.uci.ics.texera.sqlservice.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * DatasetService provides utilities for working with CSV datasets,
 * such as retrieving column names and previewing a few rows of data.
 */
public class DatasetService {

    /**
     * Reads the first row of a CSV file to extract column names.
     * If the file has no header, generates generic column names (e.g., column-1, column-2, ...).
     *
     * @param path       The path to the CSV file.
     * @param hasHeader  Whether the CSV file contains a header row.
     * @return A list of column names.
     * @throws IOException if the file cannot be read.
     */
    public List<String> getCsvColumns(String path, boolean hasHeader) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8);

        if (lines.isEmpty()) {
            return List.of();
        }

        String firstRow = lines.get(0);
        String[] headers;

        if (hasHeader) {
            headers = firstRow.split(",");
        } else {
            int colCount = firstRow.split(",").length;
            headers = new String[colCount];
            for (int i = 0; i < colCount; i++) {
                headers[i] = "column-" + (i + 1);
            }
        }

        return Arrays.asList(headers);
    }

    /**
     * Returns a preview of the dataset as a list of rows.
     * Each row is represented as a String array of column values.
     *
     * @param path     The path to the CSV file.
     * @param numRows  Number of rows to preview (excluding the header if present).
     * @param hasHeader Whether the CSV has a header row.
     * @return A list of rows, where each row is an array of cell values.
     * @throws IOException if the file cannot be read.
     */
    public List<String[]> getDatasetPreview(String path, int numRows, boolean hasHeader) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8);

        if (lines.isEmpty()) {
            return List.of();
        }

        int startIndex = hasHeader ? 1 : 0;
        List<String[]> preview = new ArrayList<>();

        for (int i = startIndex; i < Math.min(lines.size(), startIndex + numRows); i++) {
            String line = lines.get(i);
            preview.add(line.split(","));
        }

        return preview;
    }
}
