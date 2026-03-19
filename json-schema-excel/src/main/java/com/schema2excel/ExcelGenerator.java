package com.schema2excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates an Excel workbook from the parsed JSON Schema tree.
 * Sheet 1 ("Schema Hierarchy"): Flat table with Parent, Child, Field Name, Type, Required, Description, Path
 * Sheet 2 ("Complex Objects"): One section per complex object showing its fields
 */
public class ExcelGenerator {

    public void generate(SchemaNode root, String outputPath) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            createHierarchySheet(workbook, root);
            createComplexObjectsSheet(workbook, root);

            try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                workbook.write(fos);
            }
        }
    }

    // ---- Sheet 1: Flat hierarchy table ----

    private void createHierarchySheet(XSSFWorkbook workbook, SchemaNode root) {
        Sheet sheet = workbook.createSheet("Schema Hierarchy");
        CellStyle headerStyle = createHeaderStyle(workbook);

        String[] headers = {"Parent", "Child / Field", "Type", "Required", "Description", "Full Path"};
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        List<String[]> rows = new ArrayList<>();
        collectRows(root, rows);

        CellStyle complexStyle = createComplexRowStyle(workbook);

        int rowIdx = 1;
        for (String[] data : rows) {
            Row row = sheet.createRow(rowIdx++);
            boolean isComplex = "object".equals(data[2]) || "array".equals(data[2]);
            for (int i = 0; i < data.length; i++) {
                Cell cell = row.createCell(i);
                cell.setCellValue(data[i]);
                if (isComplex) {
                    cell.setCellStyle(complexStyle);
                }
            }
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
        sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.length - 1));
    }

    private void collectRows(SchemaNode node, List<String[]> rows) {
        for (SchemaNode child : node.getChildren()) {
            rows.add(new String[]{
                    child.getParent(),
                    child.getName(),
                    child.getType(),
                    child.isRequired() ? "Yes" : "No",
                    child.getDescription(),
                    child.getPath()
            });
            collectRows(child, rows);
        }
    }

    // ---- Sheet 2: Complex objects detail ----

    private void createComplexObjectsSheet(XSSFWorkbook workbook, SchemaNode root) {
        Sheet sheet = workbook.createSheet("Complex Objects");
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle sectionStyle = createSectionStyle(workbook);

        List<SchemaNode> complexNodes = new ArrayList<>();
        findComplexNodes(root, complexNodes);
        // Include root if it has children
        if (!root.getChildren().isEmpty()) {
            complexNodes.add(0, root);
        }

        int rowIdx = 0;
        for (SchemaNode complex : complexNodes) {
            // Section header
            Row sectionRow = sheet.createRow(rowIdx++);
            Cell sectionCell = sectionRow.createCell(0);
            sectionCell.setCellValue("Object: " + complex.getName() + " (" + complex.getPath() + ")");
            sectionCell.setCellStyle(sectionStyle);
            sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, 4));

            // Column headers
            String[] colHeaders = {"Field Name", "Type", "Required", "Description", "Path"};
            Row colHeaderRow = sheet.createRow(rowIdx++);
            for (int i = 0; i < colHeaders.length; i++) {
                Cell cell = colHeaderRow.createCell(i);
                cell.setCellValue(colHeaders[i]);
                cell.setCellStyle(headerStyle);
            }

            // Field rows (only direct children)
            for (SchemaNode field : complex.getChildren()) {
                Row fieldRow = sheet.createRow(rowIdx++);
                fieldRow.createCell(0).setCellValue(field.getName());
                fieldRow.createCell(1).setCellValue(field.getType());
                fieldRow.createCell(2).setCellValue(field.isRequired() ? "Yes" : "No");
                fieldRow.createCell(3).setCellValue(field.getDescription());
                fieldRow.createCell(4).setCellValue(field.getPath());
            }

            // Blank separator row
            rowIdx++;
        }

        for (int i = 0; i < 5; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void findComplexNodes(SchemaNode node, List<SchemaNode> result) {
        for (SchemaNode child : node.getChildren()) {
            if (child.isComplex() && !child.getChildren().isEmpty()) {
                result.add(child);
            }
            findComplexNodes(child, result);
        }
    }

    // ---- Styles ----

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private CellStyle createSectionStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createComplexRowStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }
}
