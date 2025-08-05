package com.akrcode.akr.daoImpl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.Arrays;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.akrcode.akr.configure.CustomDataSource;
import com.akrcode.akr.dao.IndentVsDeliveryDao;
import com.akrcode.akr.dto.IndentDeliveryDataDto;
import com.zaxxer.hikari.HikariDataSource;

@Component
public class IndentVsDeliveryDaoImpl implements IndentVsDeliveryDao {
	@Autowired
	CustomDataSource customDataSource;

	class DataRow {
		String category, packFormat, section, code, product, dateStr;
		String indentQty, availableQty, requiredQty, plannedQty, packedQty, dispatchedQty, receivedQty;
		String indentDiff, reason, plannedDiff, plannedReason, packedDiff, packedReason, dispatchedDiff,
				dispatchedReason, receivedDiff, receivedReason;
		int rowNum;
	}
	public byte[] processExcelUploadReturnStatusExcel(InputStream inputStream) {
	    String database = "sri_krishna_db";

	    try (
	        HikariDataSource ds = customDataSource.dynamicDatabaseChange(database);
	        Connection conn = ds.getConnection();
	        Workbook workbook = new XSSFWorkbook(inputStream);
	        ByteArrayOutputStream out = new ByteArrayOutputStream()
	    ) {
	        Sheet sheet = workbook.getSheetAt(0);
	        Row header = sheet.getRow(0);
	        if (header == null) {
	            Row msgRow = sheet.createRow(1);
	            msgRow.createCell(0).setCellValue("Empty file!");
	            workbook.write(out);
	            return out.toByteArray();
	        }

	        String[] expectedHeaders = {
	            "DATE", "CATEGORY", "PACK FORMAT", "SECTION", "CODE", "PRODUCT",
	            "INDENT QUANTITY", "AVAILABLE QUANTITY", "REQUIRED QUANTITY", "INDENT DIFF", "REASON",
	            "PLANNED QUANTITY", "PLANNED DIFF", "PLANNED REASON",
	            "PACKED QUANTITY", "PACKED DIFF", "PACKED REASON",
	            "DISPATCHED QUANTITY", "DISPATCHED DIFF", "DISPATCHED REASON",
	            "RECEIVED QUANTITY", "RECEIVED DIFF", "RECEIVED REASON"
	        };

	        Map<String, Integer> headerMap = new HashMap<>();
	        for (int i = 0; i < header.getLastCellNum(); i++) {
	            Cell cell = header.getCell(i);
	            if (cell != null && cell.getCellType() == CellType.STRING) {
	                headerMap.put(cell.getStringCellValue().trim().toUpperCase(), i);
	            }
	        }

	        for (String expected : expectedHeaders) {
	            if (!headerMap.containsKey(expected)) {
	                Row msgRow = sheet.createRow(1);
	                msgRow.createCell(0).setCellValue("Missing header: " + expected);
	                workbook.write(out);
	                return out.toByteArray();
	            }
	        }

	        PreparedStatement psCheckProduct = conn.prepareStatement("SELECT id FROM indent_vs_delivery WHERE code = ?");
	        PreparedStatement psInsertProduct = conn.prepareStatement(
	            "INSERT INTO indent_vs_delivery(category, pack_format, section, code, product) VALUES(?,?,?,?,?) RETURNING id"
	        );
	        PreparedStatement psCheckData = conn.prepareStatement(
	            "SELECT 1 FROM indent_vs_delivery_data WHERE product_id = ? AND report_date = ?"
	        );
	        PreparedStatement psInsertData = conn.prepareStatement(
	            "INSERT INTO indent_vs_delivery_data (product_id, report_date, indent_qty, available_qty, required_qty," +
	            "planned_qty, packed_qty, dispatched_qty, received_qty, difference, reason, planned_difference, planned_reason," +
	            "packed_difference, packed_reason, dispatched_difference, dispatched_reason, received_difference, received_reason)" +
	            " VALUES (?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
	        );
	        PreparedStatement psUpdateData = conn.prepareStatement(
	            "UPDATE indent_vs_delivery_data SET indent_qty = ?::jsonb, available_qty = ?::jsonb, required_qty = ?::jsonb," +
	            "planned_qty = ?::jsonb, packed_qty = ?::jsonb, dispatched_qty = ?::jsonb, received_qty = ?::jsonb," +
	            "difference = ?, reason = ?, planned_difference = ?, planned_reason = ?, packed_difference = ?, packed_reason = ?," +
	            "dispatched_difference = ?, dispatched_reason = ?, received_difference = ?, received_reason = ?" +
	            " WHERE product_id = ? AND report_date = ?"
	        );

	        int statusCol = header.getLastCellNum();
	        header.createCell(statusCol).setCellValue("STATUS");

	        Set<String> processedRows = new HashSet<>();

	        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
	            Row row = sheet.getRow(i);
	            if (row == null) continue;

	            String status = "Error";

	            try {
	                // DATE parsing with fallback
	                Cell dateCell = row.getCell(headerMap.get("DATE"));
	                LocalDate reportDate;
	                if (dateCell == null) throw new IllegalArgumentException("Missing DATE");
	                if (dateCell.getCellType() == CellType.NUMERIC) {
	                    reportDate = dateCell.getLocalDateTimeCellValue().toLocalDate();
	                } else {
	                    String rawDate = getStringCellValue(row, headerMap.get("DATE"));
	                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
	                    reportDate = LocalDate.parse(rawDate, formatter);
	                }

	                String code = getStringCellValue(row, headerMap.get("CODE")).trim();
	                String uniqueKey = reportDate.toString() + "|" + code;
	                if (processedRows.contains(uniqueKey)) {
	                    row.createCell(statusCol).setCellValue("Skipped: Duplicate row");
	                    continue;
	                }
	                processedRows.add(uniqueKey);

	                String category = getStringCellValue(row, headerMap.get("CATEGORY"));
	                String pack = getStringCellValue(row, headerMap.get("PACK FORMAT"));
	                String section = getStringCellValue(row, headerMap.get("SECTION"));
	                String product = getStringCellValue(row, headerMap.get("PRODUCT"));

	                if (code.isEmpty() || product.isEmpty()) {
	                    throw new IllegalArgumentException("Code or Product is empty");
	                }

	                int productId;
	                psCheckProduct.setString(1, code);
	                try (ResultSet rs = psCheckProduct.executeQuery()) {
	                    if (rs.next()) {
	                        productId = rs.getInt(1);
	                    } else {
	                        psInsertProduct.setString(1, category);
	                        psInsertProduct.setString(2, pack);
	                        psInsertProduct.setString(3, section);
	                        psInsertProduct.setString(4, code);
	                        psInsertProduct.setString(5, product);
	                        try (ResultSet rs2 = psInsertProduct.executeQuery()) {
	                            if (!rs2.next()) throw new SQLException("Insert product failed");
	                            productId = rs2.getInt(1);
	                        }
	                    }
	                }

	                psCheckData.setInt(1, productId);
	                psCheckData.setDate(2, Date.valueOf(reportDate));
	                boolean exists;
	                try (ResultSet rs = psCheckData.executeQuery()) {
	                    exists = rs.next();
	                }

	                String indentQty = toJsonValue(row, headerMap.get("INDENT QUANTITY"));
	                String availableQty = toJsonValue(row, headerMap.get("AVAILABLE QUANTITY"));
	                String requiredQty = toJsonValue(row, headerMap.get("REQUIRED QUANTITY"));
	                String plannedQty = toJsonValue(row, headerMap.get("PLANNED QUANTITY"));
	                String packedQty = toJsonValue(row, headerMap.get("PACKED QUANTITY"));
	                String dispatchedQty = toJsonValue(row, headerMap.get("DISPATCHED QUANTITY"));
	                String receivedQty = toJsonValue(row, headerMap.get("RECEIVED QUANTITY"));

	                String diff = getStringCellValue(row, headerMap.get("INDENT DIFF"));
	                String reason = getStringCellValue(row, headerMap.get("REASON"));
	                String plDiff = getStringCellValue(row, headerMap.get("PLANNED DIFF"));
	                String plReason = getStringCellValue(row, headerMap.get("PLANNED REASON"));
	                String pcDiff = getStringCellValue(row, headerMap.get("PACKED DIFF"));
	                String pcReason = getStringCellValue(row, headerMap.get("PACKED REASON"));
	                String dcDiff = getStringCellValue(row, headerMap.get("DISPATCHED DIFF"));
	                String dcReason = getStringCellValue(row, headerMap.get("DISPATCHED REASON"));
	                String rcDiff = getStringCellValue(row, headerMap.get("RECEIVED DIFF"));
	                String rcReason = getStringCellValue(row, headerMap.get("RECEIVED REASON"));

	                if (exists) {
	                    psUpdateData.setString(1, indentQty);
	                    psUpdateData.setString(2, availableQty);
	                    psUpdateData.setString(3, requiredQty);
	                    psUpdateData.setString(4, plannedQty);
	                    psUpdateData.setString(5, packedQty);
	                    psUpdateData.setString(6, dispatchedQty);
	                    psUpdateData.setString(7, receivedQty);
	                    psUpdateData.setString(8, diff);
	                    psUpdateData.setString(9, reason);
	                    psUpdateData.setString(10, plDiff);
	                    psUpdateData.setString(11, plReason);
	                    psUpdateData.setString(12, pcDiff);
	                    psUpdateData.setString(13, pcReason);
	                    psUpdateData.setString(14, dcDiff);
	                    psUpdateData.setString(15, dcReason);
	                    psUpdateData.setString(16, rcDiff);
	                    psUpdateData.setString(17, rcReason);
	                    psUpdateData.setInt(18, productId);
	                    psUpdateData.setDate(19, Date.valueOf(reportDate));
	                    psUpdateData.executeUpdate();
	                    status = "Updated";
	                } else {
	                    psInsertData.setInt(1, productId);
	                    psInsertData.setDate(2, Date.valueOf(reportDate));
	                    psInsertData.setString(3, indentQty);
	                    psInsertData.setString(4, availableQty);
	                    psInsertData.setString(5, requiredQty);
	                    psInsertData.setString(6, plannedQty);
	                    psInsertData.setString(7, packedQty);
	                    psInsertData.setString(8, dispatchedQty);
	                    psInsertData.setString(9, receivedQty);
	                    psInsertData.setString(10, diff);
	                    psInsertData.setString(11, reason);
	                    psInsertData.setString(12, plDiff);
	                    psInsertData.setString(13, plReason);
	                    psInsertData.setString(14, pcDiff);
	                    psInsertData.setString(15, pcReason);
	                    psInsertData.setString(16, dcDiff);
	                    psInsertData.setString(17, dcReason);
	                    psInsertData.setString(18, rcDiff);
	                    psInsertData.setString(19, rcReason);
	                    psInsertData.executeUpdate();
	                    status = "Inserted";
	                }

	            } catch (Exception e) {
	                status = "Error: " + e.getMessage();
	                e.printStackTrace();
	            }

	            row.createCell(statusCol).setCellValue(status);
	        }

	        workbook.write(out);
	        return out.toByteArray();

	    } catch (Exception e) {
	        e.printStackTrace();
	        return null;
	    }
	}


	private String getStringCellValue(Row row, int cellIndex) {
	    Cell cell = row.getCell(cellIndex);
	    if (cell == null) return "";
	    switch (cell.getCellType()) {
	        case STRING: return cell.getStringCellValue().trim();
	        case NUMERIC: return String.valueOf((long) cell.getNumericCellValue()); // Safe long cast
	        case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
	        case FORMULA:
	            try {
	                return String.valueOf((long) cell.getNumericCellValue());
	            } catch (Exception e) {
	                return cell.getStringCellValue();
	            }
	        default: return "";
	    }
	}

	private String toJsonValue(Row row, int colIndex) {
	    String value = getStringCellValue(row, colIndex).replaceAll("[^0-9.]", "");
	    if (value.isEmpty()) value = "0";
	    try {
	        double val = Double.parseDouble(value);
	        return "{\"data\": " + val + "}";
	    } catch (NumberFormatException e) {
	        return "{\"data\": 0}";
	    }
	}

	@Override
	public Map<String, Object> listAllProductData(LocalDate reportDate, String category, String product, String section,
			String packFormat, Map<String, String> differenceFilter, int page, int limit) {
		Map<String, Object> response = new HashMap<>();
		List<IndentDeliveryDataDto> productList = new ArrayList<>();

		String database = "sri_krishna_db"; // Make dynamic if needed
		HikariDataSource newDataSource = null;

		try (Connection conn = (newDataSource = customDataSource.dynamicDatabaseChange(database)).getConnection()) {
			if (reportDate == null) {
				reportDate = getLatestReportDate(conn);
				if (reportDate == null) {
					response.put("result", false);
					response.put("message", "❌ No report data available.");
					return response;
				}
			}

			String baseSelect = """
					        SELECT p.id, p.report_date,
					               m.category, m.pack_format, m.section, m.code, m.product,
					               p.indent_qty, p.available_qty, p.required_qty,
					               p.planned_qty, p.packed_qty, p.dispatched_qty, p.received_qty,
					               p.reason, p.difference, p.planned_difference, p.planned_reason,
					               p.packed_difference, p.packed_reason,
					               p.dispatched_difference, p.dispatched_reason,
					               p.received_difference, p.received_reason
					          FROM indent_vs_delivery_data p
					          JOIN indent_vs_delivery m ON p.product_id = m.id
					""";

			String baseCount = """
					        SELECT COUNT(*)
					          FROM indent_vs_delivery_data p
					          JOIN indent_vs_delivery m ON p.product_id = m.id
					""";

			List<Object> params = new ArrayList<>();
			StringBuilder whereClause = new StringBuilder();
			Map<String, String> allowedFields = Map.of("difference", "p.difference", "planned_diff",
					"p.planned_difference", "packed_diff", "p.packed_difference", "dispatched_diff",
					"p.dispatched_difference", "received_diff", "p.received_difference");

			// Filters: Difference
			if (differenceFilter != null) {
				for (Map.Entry<String, String> entry : differenceFilter.entrySet()) {
					String column = allowedFields.get(entry.getKey());
					String filter = entry.getValue();
					if (column == null || filter == null || filter.isBlank())
						continue;
					whereClause.append(whereClause.length() == 0 ? " WHERE " : " AND ");
					switch (filter) {
					case "zero" ->
						whereClause.append("(").append(column).append(" IS NULL OR ").append(column).append(" = '0')");
					case "negative" ->
						whereClause.append("CAST(NULLIF(").append(column).append(", '') AS NUMERIC) < 0");
					case "positive" ->
						whereClause.append("CAST(NULLIF(").append(column).append(", '') AS NUMERIC) > 0");
					}
				}
			}

			// Filters: Date and others
			if (reportDate != null) {
				whereClause.append(whereClause.length() == 0 ? " WHERE " : " AND ").append("p.report_date = ?");
				params.add(reportDate);
			}
			if (category != null && !category.isBlank()) {
				whereClause.append(" AND LOWER(m.category) = LOWER(?)");
				params.add(category.trim());
			}
			if (product != null && !product.isBlank()) {
				whereClause.append(" AND LOWER(m.product) = LOWER(?)");
				params.add(product.trim());
			}
			if (section != null && !section.isBlank()) {
				whereClause.append(" AND LOWER(m.section) = LOWER(?)");
				params.add(section.trim());
			}
			if (packFormat != null && !packFormat.isBlank()) {
				whereClause.append(" AND LOWER(m.pack_format) = LOWER(?)");
				params.add(packFormat.trim());
			}

			// Pagination queries
			String finalSql = baseSelect + whereClause
					+ " ORDER BY p.report_date DESC, LOWER(m.product) ASC LIMIT ? OFFSET ?";
			String countSql = baseCount + whereClause;
			params.add(limit);
			params.add((page - 1) * limit);

			// Count total
			int totalItems = 0;
			try (PreparedStatement countStmt = conn.prepareStatement(countSql)) {
				for (int i = 0; i < params.size() - 2; i++) {
					countStmt.setObject(i + 1, params.get(i));
				}
				try (ResultSet rs = countStmt.executeQuery()) {
					if (rs.next())
						totalItems = rs.getInt(1);
				}
			}

			try (PreparedStatement stmt = conn.prepareStatement(finalSql)) {
				for (int i = 0; i < params.size(); i++) {
					stmt.setObject(i + 1, params.get(i));
				}
				try (ResultSet rs = stmt.executeQuery()) {
					while (rs.next()) {
						IndentDeliveryDataDto model = new IndentDeliveryDataDto();
						model.setId(rs.getInt("id"));
						model.setReportDate(rs.getDate("report_date").toLocalDate());
						model.setCategory(rs.getString("category"));
						model.setPackFormat(rs.getString("pack_format"));
						model.setSection(rs.getString("section"));
						model.setCode(rs.getString("code"));
						model.setProduct(rs.getString("product"));
						model.setIndentQtyJson(rs.getString("indent_qty"));
						model.setAvailableQtyJson(rs.getString("available_qty"));
						model.setRequiredQtyJson(rs.getString("required_qty"));
						model.setPlannedQtyJson(rs.getString("planned_qty"));
						model.setPackedQtyJson(rs.getString("packed_qty"));
						model.setDispatchedQtyJson(rs.getString("dispatched_qty"));
						model.setReceivedQtyJson(rs.getString("received_qty"));
						model.setReason(rs.getString("reason"));
						model.setDifference(rs.getString("difference"));
						model.setPlannedDifference(rs.getString("planned_difference"));
						model.setPlannedReason(rs.getString("planned_reason"));
						model.setPackedDifference(rs.getString("packed_difference"));
						model.setPackedReason(rs.getString("packed_reason"));
						model.setDispatchedDifference(rs.getString("dispatched_difference"));
						model.setDispatchedReason(rs.getString("dispatched_reason"));
						model.setReceivedDifference(rs.getString("received_difference"));
						model.setReceivedReason(rs.getString("received_reason"));
						productList.add(model);
					}
				}
			}

			Map<String, List<String>> suggestionMap = getAutoSuggestionValues(conn, reportDate);
			response.put("autoSuggestions", suggestionMap);
			response.put("indentdelivery", productList);
			response.put("totalItems", totalItems);
			response.put("currentPage", page);
			response.put("totalPages", (int) Math.ceil((double) totalItems / limit));
			response.put("result", true);

		} catch (Exception e) {
			e.printStackTrace();
			response.put("result", false);
			response.put("message", "❌ Error fetching report list: " + e.getMessage());
		} finally {
			if (newDataSource != null)
				newDataSource.close();
		}

		return response;
	}

	private LocalDate getLatestReportDate(Connection conn) throws SQLException {
		String sql = "SELECT MAX(report_date) FROM indent_vs_delivery_data";
		try (PreparedStatement stmt = conn.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
			if (rs.next()) {
				Date date = rs.getDate(1);
				return date != null ? date.toLocalDate() : null;
			}
		}
		return null;
	}

	private Map<String, List<String>> getAutoSuggestionValues(Connection conn, LocalDate reportDate)
			throws SQLException {
		Map<String, List<String>> suggestions = new HashMap<>();
		String sql = """
				    SELECT DISTINCT m.category, m.product, m.section, m.pack_format
				      FROM indent_vs_delivery_data p
				      JOIN indent_vs_delivery m ON p.product_id = m.id
				     WHERE p.report_date = ?
				""";

		try (PreparedStatement stmt = conn.prepareStatement(sql)) {
			stmt.setObject(1, reportDate);
			try (ResultSet rs = stmt.executeQuery()) {
				Set<String> categories = new TreeSet<>();
				Set<String> products = new TreeSet<>();
				Set<String> sections = new TreeSet<>();
				Set<String> packFormats = new TreeSet<>();

				while (rs.next()) {
					categories.add(rs.getString("category"));
					products.add(rs.getString("product"));
					sections.add(rs.getString("section"));
					packFormats.add(rs.getString("pack_format"));
				}

				suggestions.put("category", new ArrayList<>(categories));
				suggestions.put("product", new ArrayList<>(products));
				suggestions.put("section", new ArrayList<>(sections));
				suggestions.put("packFormat", new ArrayList<>(packFormats));
			}
		}
		return suggestions;
	}

	private double sumJsonArray(String json) {
		if (json == null || json.isBlank())
			return 0;

		try {
			JSONObject obj = new JSONObject(json);
			Object data = obj.opt("data");

			if (data instanceof JSONArray) {
				JSONArray arr = (JSONArray) data;
				double sum = 0;
				for (int i = 0; i < arr.length(); i++) {
					sum += arr.optDouble(i, 0);
				}
				return sum;
			} else if (data instanceof String) {
				try {
					return Double.parseDouble((String) data);
				} catch (NumberFormatException e) {
					return 0;
				}
			} else if (data instanceof Number) {
				return ((Number) data).doubleValue();
			}

			return 0;

		} catch (JSONException e) {
			e.printStackTrace();
			return 0;
		}
	}

	public double formatNumberRounded(double value) {
		return Math.round(value);
	}

	@Override
	public String updateFieldByCode(String code, String field, String value, LocalDate reportDateStr) {
		String response = "updated successfully";
		String database = "sri_krishna_db";

		Map<String, String> fieldMap = Map.of("indentQty", "indent_qty", "availableQty", "available_qty", "requiredQty",
				"required_qty", "plannedQty", "planned_qty", "packedQty", "packed_qty", "dispatchedQty",
				"dispatched_qty", "receivedQty", "received_qty");

		String dbField = fieldMap.get(field);
		if (dbField == null) {
			return "Invalid field name: " + field;
		}

		try (HikariDataSource ds = customDataSource.dynamicDatabaseChange(database);
				Connection conn = ds.getConnection()) {

			java.sql.Date sqlDate = java.sql.Date.valueOf(reportDateStr);

			// Step 1: Get product_id
			Integer productId = null;
			String findProductSql = "SELECT id FROM indent_vs_delivery WHERE code = ?";
			try (PreparedStatement findStmt = conn.prepareStatement(findProductSql)) {
				findStmt.setString(1, code);
				ResultSet rs = findStmt.executeQuery();
				if (rs.next()) {
					productId = rs.getInt("id");
				} else {
					return "No product found with code '" + code + "'";
				}
			}

			// Step 2: Update main field
			String updateSql = "UPDATE indent_vs_delivery_data SET " + dbField + " = ?::jsonb "
					+ "WHERE product_id = ? AND report_date = ?";
			try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
				updateStmt.setString(1, value);
				updateStmt.setInt(2, productId);
				updateStmt.setDate(3, sqlDate);
				int rows = updateStmt.executeUpdate();
				if (rows == 0) {
					return "No row found to update (check code and reportDate)";
				}
			}

			// Step 3: Fetch all quantity fields as numeric
			String selectSql = "SELECT(CASE WHEN jsonb_typeof(indent_qty->'data') = 'array' THEN (SELECT SUM(value::numeric)\r\n"
					+ "FROM jsonb_array_elements_text(indent_qty->'data') AS arr(value)) WHEN jsonb_typeof(indent_qty->'data') = 'number' THEN\r\n"
					+ "(indent_qty->>'data')::numeric ELSE 0 END) AS indent,(CASE\r\n"
					+ " WHEN jsonb_typeof(available_qty->'data') = 'array' THEN (\r\n"
					+ "SELECT SUM(value::numeric) FROM jsonb_array_elements_text(available_qty->'data') AS arr(value))\r\n"
					+ " WHEN jsonb_typeof(available_qty->'data') = 'number' THEN(available_qty->>'data')::numeric\r\n"
					+ " ELSE 0 END) AS available,(CASE WHEN jsonb_typeof(required_qty->'data') = 'array' THEN ( SELECT SUM(value::numeric)\r\n"
					+ "FROM jsonb_array_elements_text(required_qty->'data') AS arr(value))\r\n"
					+ " WHEN jsonb_typeof(required_qty->'data') = 'number' THEN\r\n"
					+ "(required_qty->>'data')::numeric ELSE 0 END ) AS required,(CASE\r\n"
					+ " WHEN jsonb_typeof(planned_qty->'data') = 'array' THEN ( SELECT SUM(value::numeric)\r\n"
					+ "FROM jsonb_array_elements_text(planned_qty->'data') AS arr(value))\r\n"
					+ " WHEN jsonb_typeof(planned_qty->'data') = 'number' THEN(planned_qty->>'data')::numeric ELSE 0\r\n"
					+ "END) AS planned,(CASE WHEN jsonb_typeof(packed_qty->'data') = 'array' THEN (\r\n"
					+ "SELECT SUM(value::numeric)FROM jsonb_array_elements_text(packed_qty->'data') AS arr(value))\r\n"
					+ " WHEN jsonb_typeof(packed_qty->'data') = 'number' THEN(packed_qty->>'data')::numeric\r\n"
					+ " ELSE 0 END) AS packed,(CASE WHEN jsonb_typeof(dispatched_qty->'data') = 'array' THEN (\r\n"
					+ "SELECT SUM(value::numeric) FROM jsonb_array_elements_text(dispatched_qty->'data') AS arr(value)) WHEN jsonb_typeof(dispatched_qty->'data') = 'number' THEN\r\n"
					+ "(dispatched_qty->>'data')::numeric ELSE 0 END) AS dispatched,(CASE\r\n"
					+ " WHEN jsonb_typeof(received_qty->'data') = 'array' THEN (SELECT SUM(value::numeric)\r\n"
					+ "FROM jsonb_array_elements_text(received_qty->'data') AS arr(value))\r\n"
					+ " WHEN jsonb_typeof(received_qty->'data') = 'number' THEN(received_qty->>'data')::numeric\r\n"
					+ " ELSE 0 END) AS received FROM indent_vs_delivery_data WHERE  product_id = ? AND report_date = ?";
			BigDecimal indent = BigDecimal.ZERO, available = BigDecimal.ZERO, required = BigDecimal.ZERO,
					planned = BigDecimal.ZERO, packed = BigDecimal.ZERO, dispatched = BigDecimal.ZERO,
					received = BigDecimal.ZERO;

			try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
				selectStmt.setInt(1, productId);
				selectStmt.setDate(2, sqlDate);
				ResultSet rs = selectStmt.executeQuery();
				if (rs.next()) {
					indent = rs.getBigDecimal("indent");
					available = rs.getBigDecimal("available");
					required = rs.getBigDecimal("required");
					planned = rs.getBigDecimal("planned");
					packed = rs.getBigDecimal("packed");
					dispatched = rs.getBigDecimal("dispatched");
					received = rs.getBigDecimal("received");
				}
			}

			// Step 4: Calculate Diffs
			BigDecimal indentDiff = required.add(available).subtract(indent);
			BigDecimal plannedDiff = planned.subtract(indent);
			BigDecimal packedDiff = packed.subtract(planned);
			BigDecimal dispatchedDiff = dispatched.subtract(packed);
			BigDecimal receivedDiff = received.subtract(dispatched);

			// Step 5: Update diff fields
			String updateDiffSql = "UPDATE indent_vs_delivery_data SET "
					+ "difference = ?, planned_difference = ?, packed_difference = ?, dispatched_difference = ?, received_difference = ? "
					+ "WHERE product_id = ? AND report_date = ?";
			try (PreparedStatement updateDiffStmt = conn.prepareStatement(updateDiffSql)) {
				updateDiffStmt.setBigDecimal(1, indentDiff);
				updateDiffStmt.setBigDecimal(2, plannedDiff);
				updateDiffStmt.setBigDecimal(3, packedDiff);
				updateDiffStmt.setBigDecimal(4, dispatchedDiff);
				updateDiffStmt.setBigDecimal(5, receivedDiff);
				updateDiffStmt.setInt(6, productId);
				updateDiffStmt.setDate(7, sqlDate);
				updateDiffStmt.executeUpdate();
			}

			response = "Field '" + dbField + "' updated and diffs recalculated for product ID " + productId;

		} catch (Exception e) {
			e.printStackTrace();
			response = "Error: " + e.getMessage();
		}

		return response;
	}

	@Override
	public String updateReason(String code, String field, String value, LocalDate reportDateStr) {
		String response = "updated successfully";
		String database = "sri_krishna_db";

		Map<String, String> fieldMap = Map.of("reason", "reason", "plannedReason", "planned_reason", "packedReason",
				"packed_reason", "dispatchedReason", "dispatched_reason", "receivedReason", "received_reason");

		String dbField = fieldMap.get(field);
		if (dbField == null) {
			return "Invalid field name: " + field;
		}

		try (HikariDataSource ds = customDataSource.dynamicDatabaseChange(database);
				Connection conn = ds.getConnection()) {

			java.sql.Date sqlDate = java.sql.Date.valueOf(reportDateStr);

			String findProductSql = "SELECT id FROM indent_vs_delivery WHERE code = ?";
			Integer productId = null;

			try (PreparedStatement findStmt = conn.prepareStatement(findProductSql)) {
				findStmt.setString(1, code);
				ResultSet rs = findStmt.executeQuery();
				if (rs.next()) {
					productId = rs.getInt("id");
				} else {
					return "No product found with code '" + code + "'";
				}
			}

			String updateSql = "UPDATE indent_vs_delivery_data " + "SET " + dbField + " = ?::text "
					+ "WHERE product_id = ? AND report_date = ?";
			try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
				updateStmt.setString(1, value);
				updateStmt.setInt(2, productId);
				updateStmt.setDate(3, sqlDate);
				int rows = updateStmt.executeUpdate();
				if (rows == 0) {
					response = "No row found to update (check code and reportDate)";
				} else {
					response = "Field '" + dbField + "' updated for product ID " + productId + " on " + reportDateStr;
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
			response = "❌ Error: " + e.getMessage();
		}

		return response;
	}

	@Override
	public byte[] uploadQuantity(MultipartFile file, String quantityType) {
		String database = "sri_krishna_db";
		String dbField = QUANTITY_FIELD_MAP.get(quantityType.toUpperCase());

		if (dbField == null) {
			throw new IllegalArgumentException("Invalid Quantity Type: " + quantityType);
		}

		try (Workbook workbook = new XSSFWorkbook(file.getInputStream());
				ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
				HikariDataSource ds = customDataSource.dynamicDatabaseChange(database);
				Connection conn = ds.getConnection()) {

			Sheet sheet = workbook.getSheetAt(0);
			Iterator<Row> rows = sheet.iterator();

			if (!rows.hasNext())
				throw new IllegalArgumentException("Empty Excel sheet");

			Row headerRow = rows.next();
			int dateIdx = -1, codeIdx = -1, qtyIdx = -1;

			for (int i = 0; i < headerRow.getLastCellNum(); i++) {
				String col = headerRow.getCell(i).getStringCellValue().trim();
				if ("Date".equalsIgnoreCase(col))
					dateIdx = i;
				else if ("Code".equalsIgnoreCase(col))
					codeIdx = i;
				else if (quantityType.equalsIgnoreCase(col))
					qtyIdx = i;
			}

			if (dateIdx == -1 || codeIdx == -1 || qtyIdx == -1) {
				throw new IllegalArgumentException("Missing required headers: Date, Code, " + quantityType);
			}

			// Add "Status" header
			Cell statusHeader = headerRow.createCell(headerRow.getLastCellNum());
			statusHeader.setCellValue("Status");

			DataFormatter formatter = new DataFormatter();

			while (rows.hasNext()) {
				Row row = rows.next();
				String status = "Skipped";

				String code = formatter.formatCellValue(row.getCell(codeIdx)).trim();
				String dateStr = formatter.formatCellValue(row.getCell(dateIdx)).trim();
				String qtyStr = formatter.formatCellValue(row.getCell(qtyIdx)).trim();

				if (code.isEmpty() || dateStr.isEmpty() || qtyStr.isEmpty()) {
					row.createCell(headerRow.getLastCellNum() - 1).setCellValue(status);
					continue;
				}

				LocalDate reportDate;
				try {
					reportDate = row.getCell(dateIdx).getLocalDateTimeCellValue().toLocalDate();
				} catch (Exception e) {
					row.createCell(headerRow.getLastCellNum() - 1).setCellValue(status);
					continue;
				}

				double qty;
				try {
					qty = Double.parseDouble(qtyStr.replace(",", ""));
				} catch (NumberFormatException e) {
					row.createCell(headerRow.getLastCellNum() - 1).setCellValue(status);
					continue;
				}

				Integer productId = null;
				try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM indent_vs_delivery WHERE code = ?")) {
					ps.setString(1, code);
					ResultSet rs = ps.executeQuery();
					if (rs.next())
						productId = rs.getInt("id");
					else {
						status = "Code Not Found";
						row.createCell(headerRow.getLastCellNum() - 1).setCellValue(status);
						continue;
					}
				}

				boolean exists;
				try (PreparedStatement ps = conn.prepareStatement(
						"SELECT 1 FROM indent_vs_delivery_data WHERE product_id = ? AND report_date = ?")) {
					ps.setInt(1, productId);
					ps.setDate(2, Date.valueOf(reportDate));
					exists = ps.executeQuery().next();
				}

				String jsonValue = "{\"data\": [" + qty + "]}";

				if (exists) {
					try (PreparedStatement ps = conn.prepareStatement("UPDATE indent_vs_delivery_data SET " + dbField
							+ " = ?::jsonb WHERE product_id = ? AND report_date = ?")) {
						ps.setString(1, jsonValue);
						ps.setInt(2, productId);
						ps.setDate(3, Date.valueOf(reportDate));
						ps.executeUpdate();
						status = "Updated";
					}
				} else {
					try (PreparedStatement ps = conn
							.prepareStatement("INSERT INTO indent_vs_delivery_data (product_id, report_date, " + dbField
									+ ") VALUES (?, ?, ?::jsonb)")) {
						ps.setInt(1, productId);
						ps.setDate(2, Date.valueOf(reportDate));
						ps.setString(3, jsonValue);
						ps.executeUpdate();
						status = "Inserted";
					}
				}

				// === Compute Differences ===
				// === Compute and Store Differences as TEXT ===
				try (PreparedStatement fetchStmt = conn.prepareStatement(
						"SELECT indent_qty, available_qty, required_qty, planned_qty, packed_qty, dispatched_qty, received_qty "
								+ "FROM indent_vs_delivery_data WHERE product_id = ? AND report_date = ?")) {

					fetchStmt.setInt(1, productId);
					fetchStmt.setDate(2, Date.valueOf(reportDate));
					ResultSet rs = fetchStmt.executeQuery();

					if (rs.next()) {
						BigDecimal indent = getQtyFromJson(rs.getString("indent_qty"));
						BigDecimal available = getQtyFromJson(rs.getString("available_qty"));
						BigDecimal required = getQtyFromJson(rs.getString("required_qty"));
						BigDecimal planned = getQtyFromJson(rs.getString("planned_qty"));
						BigDecimal packed = getQtyFromJson(rs.getString("packed_qty"));
						BigDecimal dispatched = getQtyFromJson(rs.getString("dispatched_qty"));
						BigDecimal received = getQtyFromJson(rs.getString("received_qty"));

						BigDecimal indentDiff = required.add(available).subtract(indent);
						BigDecimal plannedDiff = planned.subtract(indent);
						BigDecimal packedDiff = packed.subtract(planned);
						BigDecimal dispatchedDiff = dispatched.subtract(packed);
						BigDecimal receivedDiff = received.subtract(dispatched);

						// Save as text
						try (PreparedStatement updateDiff = conn.prepareStatement("UPDATE indent_vs_delivery_data SET "
								+ "difference = ?, planned_difference = ?, packed_difference = ?, dispatched_difference = ?, received_difference = ? "
								+ "WHERE product_id = ? AND report_date = ?")) {

							updateDiff.setString(1, indentDiff.toPlainString());
							updateDiff.setString(2, plannedDiff.toPlainString());
							updateDiff.setString(3, packedDiff.toPlainString());
							updateDiff.setString(4, dispatchedDiff.toPlainString());
							updateDiff.setString(5, receivedDiff.toPlainString());
							updateDiff.setInt(6, productId);
							updateDiff.setDate(7, Date.valueOf(reportDate));
							updateDiff.executeUpdate();
						}
					}
				}

				row.createCell(headerRow.getLastCellNum() - 1).setCellValue(status);
			}

			workbook.write(outputStream);
			return outputStream.toByteArray();

		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Upload failed: " + e.getMessage());
		}
	}

	private BigDecimal getQtyFromJson(String json) {
		if (json == null || json.isEmpty())
			return BigDecimal.ZERO;
		try {
			json = json.trim();
			if (json.startsWith("{") && json.contains("[") && json.contains("]")) {
				String insideArray = json.substring(json.indexOf("[") + 1, json.indexOf("]")).trim();
				return new BigDecimal(insideArray.isEmpty() ? "0" : insideArray);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return BigDecimal.ZERO;
	}

	private static final Map<String, String> QUANTITY_FIELD_MAP = Map.of("INDENT QUANTITY", "indent_qty",
			"AVAILABLE QUANTITY", "available_qty", "REQUIRED QUANTITY", "required_qty", "PLANNED QUANTITY",
			"planned_qty", "PACKED QUANTITY", "packed_qty", "DISPATCHED QUANTITY", "dispatched_qty",
			"RECEIVED QUANTITY", "received_qty");

	@Override
	public ResponseEntity<ByteArrayResource> downloadQuantityTemplate(String type) {
		// Validate input
		if (type == null || type.trim().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type parameter is required");
		}

		try (Workbook workbook = new XSSFWorkbook()) {
			Sheet sheet = workbook.createSheet("Quantity Template");

			// Create Header Style
			CellStyle headerStyle = workbook.createCellStyle();
			Font headerFont = workbook.createFont();
			headerFont.setBold(true);
			headerFont.setColor(IndexedColors.WHITE.getIndex());
			headerStyle.setFont(headerFont);
			headerStyle.setFillForegroundColor(IndexedColors.GREEN.getIndex());
			headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			headerStyle.setAlignment(HorizontalAlignment.CENTER);

			// Create Header Row
			Row header = sheet.createRow(0);
			String[] headers = { "Date", "Code", "Product", type.toUpperCase() };

			for (int i = 0; i < headers.length; i++) {
				Cell cell = header.createCell(i);
				cell.setCellValue(headers[i]);
				cell.setCellStyle(headerStyle);
				sheet.autoSizeColumn(i);
			}

			// Generate file in memory
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			workbook.write(out);
			byte[] fileBytes = out.toByteArray();

			// Build response
			String fileName = type.toLowerCase().replaceAll("\\s+", "_") + "_template.xlsx";

			return ResponseEntity.ok()
					.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
					.contentType(MediaType
							.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
					.contentLength(fileBytes.length).body(new ByteArrayResource(fileBytes));

		} catch (IOException e) {
			e.printStackTrace(); // This will show up in Render logs
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate template", e);
		}
	}

	@Override
	public ResponseEntity<ByteArrayResource> overallIndentTemplate() {
		try (Workbook workbook = new XSSFWorkbook()) {
			Sheet sheet = workbook.createSheet("Quantity Template");

			// ===== Header Style =====
			CellStyle headerStyle = workbook.createCellStyle();
			Font headerFont = workbook.createFont();
			headerFont.setBold(true);
			headerFont.setColor(IndexedColors.WHITE.getIndex());
			headerStyle.setFont(headerFont);
			headerStyle.setFillForegroundColor(IndexedColors.GREEN.getIndex());
			headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			headerStyle.setAlignment(HorizontalAlignment.CENTER);

			// ===== Create Header Row =====
			Row header = sheet.createRow(0);
			String[] headers = { "DATE", "CATEGORY", "PACK FORMAT", "SECTION", "CODE", "PRODUCT", "Indent Quantity",
					"Available Quantity", "Required Quantity", "Indent DIFF", "Reason", "Planned Quantity",
					"Planned DIFF", "Planned Reason", "Packed Quantity", "Packed DIFF", "Packed Reason",
					"Dispatched Quantity", "Dispatched DIFF", "Dispatched Reason", "Received Quantity", "Received DIFF",
					"Received Reason" };

			Map<String, Integer> colIndex = new HashMap<>();
			for (int i = 0; i < headers.length; i++) {
				colIndex.put(headers[i], i);
				Cell cell = header.createCell(i);
				cell.setCellValue(headers[i]);
				cell.setCellStyle(headerStyle);
				sheet.autoSizeColumn(i);
			}

			// ===== Excel Column Letter Mapping =====
			Function<Integer, String> colLetter = col -> {
				int dividend = col + 1;
				StringBuilder colRef = new StringBuilder();
				while (dividend > 0) {
					int modulo = (dividend - 1) % 26;
					colRef.insert(0, (char) (65 + modulo));
					dividend = (dividend - modulo - 1) / 26;
				}
				return colRef.toString();
			};

			int startRow = 1;
			int totalRows = 10;

			for (int r = 0; r < totalRows; r++) {
				Row row = sheet.createRow(startRow + r);
				for (int i = 0; i < headers.length; i++) {
					row.createCell(i).setCellValue("");
				}

				int excelRowNum = startRow + r + 1;

				row.getCell(colIndex.get("Indent DIFF"))
						.setCellFormula(String.format(
								"IF(AND(ISNUMBER(%s%d),ISNUMBER(%s%d),ISNUMBER(%s%d)),(%s%d+%s%d)-%s%d,\"\")",
								colLetter.apply(colIndex.get("Available Quantity")), excelRowNum,
								colLetter.apply(colIndex.get("Required Quantity")), excelRowNum,
								colLetter.apply(colIndex.get("Indent Quantity")), excelRowNum,
								colLetter.apply(colIndex.get("Available Quantity")), excelRowNum,
								colLetter.apply(colIndex.get("Required Quantity")), excelRowNum,
								colLetter.apply(colIndex.get("Indent Quantity")), excelRowNum));

				row.getCell(colIndex.get("Planned DIFF"))
						.setCellFormula(String.format("IF(AND(ISNUMBER(%s%d),ISNUMBER(%s%d)),%s%d-%s%d,\"\")",
								colLetter.apply(colIndex.get("Planned Quantity")), excelRowNum,
								colLetter.apply(colIndex.get("Indent Quantity")), excelRowNum,
								colLetter.apply(colIndex.get("Planned Quantity")), excelRowNum,
								colLetter.apply(colIndex.get("Indent Quantity")), excelRowNum));

				row.getCell(colIndex.get("Packed DIFF"))
						.setCellFormula(String.format("IF(AND(ISNUMBER(%s%d),ISNUMBER(%s%d)),%s%d-%s%d,\"\")",
								colLetter.apply(colIndex.get("Packed Quantity")), excelRowNum,
								colLetter.apply(colIndex.get("Planned Quantity")), excelRowNum,
								colLetter.apply(colIndex.get("Packed Quantity")), excelRowNum,
								colLetter.apply(colIndex.get("Planned Quantity")), excelRowNum));

				row.getCell(colIndex.get("Dispatched DIFF"))
						.setCellFormula(String.format("IF(AND(ISNUMBER(%s%d),ISNUMBER(%s%d)),%s%d-%s%d,\"\")",
								colLetter.apply(colIndex.get("Dispatched Quantity")), excelRowNum,
								colLetter.apply(colIndex.get("Packed Quantity")), excelRowNum,
								colLetter.apply(colIndex.get("Dispatched Quantity")), excelRowNum,
								colLetter.apply(colIndex.get("Packed Quantity")), excelRowNum));

				row.getCell(colIndex.get("Received DIFF"))
						.setCellFormula(String.format("IF(AND(ISNUMBER(%s%d),ISNUMBER(%s%d)),%s%d-%s%d,\"\")",
								colLetter.apply(colIndex.get("Received Quantity")), excelRowNum,
								colLetter.apply(colIndex.get("Dispatched Quantity")), excelRowNum,
								colLetter.apply(colIndex.get("Received Quantity")), excelRowNum,
								colLetter.apply(colIndex.get("Dispatched Quantity")), excelRowNum));
			}

			// ===== Return Response =====
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			workbook.write(out);
			byte[] fileBytes = out.toByteArray();

			return ResponseEntity.ok()
					.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"quantity_template.xlsx\"")
					.contentType(MediaType
							.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
					.contentLength(fileBytes.length).body(new ByteArrayResource(fileBytes));

		} catch (IOException e) {
			e.printStackTrace(); // Log in Render logs
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate template", e);
		}
	}

	public Map<String, Object> executeAnyQuery(String sql) {
		Map<String, Object> response = new HashMap<>();
		String database = "sri_krishna_db";
		HikariDataSource newDataSource = null;
		Connection conn = null;
		Statement stmt = null;

		try {
			newDataSource = customDataSource.dynamicDatabaseChange(database);
			conn = newDataSource.getConnection();
			stmt = conn.createStatement();

			stmt.execute(sql);

			response.put("result", true);
			response.put("message", "✅ Query executed successfully.");
		} catch (Exception e) {
			e.printStackTrace();
			response.put("result", false);
			response.put("message", "❌ Error: " + e.getMessage());
		} finally {
			try {
				if (stmt != null)
					stmt.close();
				if (conn != null)
					conn.close();
				if (newDataSource != null)
					newDataSource.close();
			} catch (Exception ignored) {
			}
		}

		return response;
	}

	@Override
	public Map<String, Object> fetchSummaryOnly(LocalDate reportDate) {
		Map<String, Object> response = new HashMap<>();
		Map<String, Object> pageSummary = new HashMap<>();
		Map<String, Object> totalSummary = new HashMap<>();

		String summarySql = """
				    SELECT SUM(val::NUMERIC) FILTER (WHERE key = 'indent_qty')        AS total_indent,
				           SUM(val::NUMERIC) FILTER (WHERE key = 'available_qty')    AS total_available,
				           SUM(val::NUMERIC) FILTER (WHERE key = 'required_qty')     AS total_required,
				           SUM(val::NUMERIC) FILTER (WHERE key = 'planned_qty')      AS total_planned,
				           SUM(val::NUMERIC) FILTER (WHERE key = 'packed_qty')       AS total_packed,
				           SUM(val::NUMERIC) FILTER (WHERE key = 'dispatched_qty')   AS total_dispatched,
				           SUM(val::NUMERIC) FILTER (WHERE key = 'received_qty')     AS total_received
				    FROM indent_vs_delivery_data p
				    JOIN indent_vs_delivery m ON p.product_id = m.id,
				    LATERAL (
				        SELECT 'indent_qty' AS key, jsonb_array_elements_text(CASE WHEN jsonb_typeof(p.indent_qty->'data') = 'array' THEN p.indent_qty->'data' ELSE jsonb_build_array(p.indent_qty->'data') END) AS val
				        UNION ALL
				        SELECT 'available_qty', jsonb_array_elements_text(CASE WHEN jsonb_typeof(p.available_qty->'data') = 'array' THEN p.available_qty->'data' ELSE jsonb_build_array(p.available_qty->'data') END)
				        UNION ALL
				        SELECT 'required_qty', jsonb_array_elements_text(CASE WHEN jsonb_typeof(p.required_qty->'data') = 'array' THEN p.required_qty->'data' ELSE jsonb_build_array(p.required_qty->'data') END)
				        UNION ALL
				        SELECT 'planned_qty', jsonb_array_elements_text(CASE WHEN jsonb_typeof(p.planned_qty->'data') = 'array' THEN p.planned_qty->'data' ELSE jsonb_build_array(p.planned_qty->'data') END)
				        UNION ALL
				        SELECT 'packed_qty', jsonb_array_elements_text(CASE WHEN jsonb_typeof(p.packed_qty->'data') = 'array' THEN p.packed_qty->'data' ELSE jsonb_build_array(p.packed_qty->'data') END)
				        UNION ALL
				        SELECT 'dispatched_qty', jsonb_array_elements_text(CASE WHEN jsonb_typeof(p.dispatched_qty->'data') = 'array' THEN p.dispatched_qty->'data' ELSE jsonb_build_array(p.dispatched_qty->'data') END)
				        UNION ALL
				        SELECT 'received_qty', jsonb_array_elements_text(CASE WHEN jsonb_typeof(p.received_qty->'data') = 'array' THEN p.received_qty->'data' ELSE jsonb_build_array(p.received_qty->'data') END)
				    ) AS expanded
				    WHERE p.report_date = ?
				""";

		String baseSelect = """
				    SELECT p.id, p.report_date,
				           m.category, m.pack_format, m.section, m.code, m.product,
				           p.indent_qty, p.available_qty, p.required_qty,
				           p.planned_qty, p.packed_qty, p.dispatched_qty, p.received_qty,
				           p.reason, p.difference, p.planned_difference, p.planned_reason,
				           p.packed_difference, p.packed_reason,
				           p.dispatched_difference, p.dispatched_reason,
				           p.received_difference, p.received_reason
				    FROM indent_vs_delivery_data p
				    JOIN indent_vs_delivery m ON p.product_id = m.id
				    WHERE p.report_date = ?
				""";

		String countSql = """
				    SELECT COUNT(*)
				    FROM indent_vs_delivery_data p
				    JOIN indent_vs_delivery m ON p.product_id = m.id
				    WHERE p.report_date = ?
				""";

		HikariDataSource dataSource = null;
		Connection conn = null;
		PreparedStatement ps = null;

		try {
			dataSource = customDataSource.dynamicDatabaseChange("test");
			conn = dataSource.getConnection();

			// --- Summary
			ps = conn.prepareStatement(summarySql);
			ps.setDate(1, Date.valueOf(reportDate));
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					totalSummary.put("totalIndent", formatNumberRounded(rs.getDouble("total_indent")));
					totalSummary.put("totalAvailable", formatNumberRounded(rs.getDouble("total_available")));
					totalSummary.put("totalRequired", formatNumberRounded(rs.getDouble("total_required")));
					totalSummary.put("totalPlanned", formatNumberRounded(rs.getDouble("total_planned")));
					totalSummary.put("totalPacked", formatNumberRounded(rs.getDouble("total_packed")));
					totalSummary.put("totalDispatched", formatNumberRounded(rs.getDouble("total_dispatched")));
					totalSummary.put("totalReceived", formatNumberRounded(rs.getDouble("total_received")));
				}
			}
			ps.close();

			// --- Count total items
			int totalItems = 0;
			ps = conn.prepareStatement(countSql);
			ps.setDate(1, Date.valueOf(reportDate));
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					totalItems = rs.getInt(1);
				}
			}
			ps.close();

			// --- Fetch paginated records and page summary
			double totalIndent = 0, totalAvailable = 0, totalRequired = 0;
			double totalPlanned = 0, totalPacked = 0, totalDispatched = 0, totalReceived = 0;
			int countIndent = 0, countAvailable = 0, countRequired = 0;
			int countPlanned = 0, countPacked = 0, countDispatched = 0, countReceived = 0;

			ps = conn.prepareStatement(baseSelect);
			ps.setDate(1, Date.valueOf(reportDate));
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {

					IndentDeliveryDataDto model = new IndentDeliveryDataDto();

					model.setIndentQtyJson(rs.getString("indent_qty"));
					model.setAvailableQtyJson(rs.getString("available_qty"));
					model.setRequiredQtyJson(rs.getString("required_qty"));
					model.setPlannedQtyJson(rs.getString("planned_qty"));
					model.setPackedQtyJson(rs.getString("packed_qty"));
					model.setDispatchedQtyJson(rs.getString("dispatched_qty"));
					model.setReceivedQtyJson(rs.getString("received_qty"));

					double indent = sumJsonArray(model.getIndentQtyJson());
					double available = sumJsonArray(model.getAvailableQtyJson());
					double required = sumJsonArray(model.getRequiredQtyJson());
					double planned = sumJsonArray(model.getPlannedQtyJson());
					double packed = sumJsonArray(model.getPackedQtyJson());
					double dispatched = sumJsonArray(model.getDispatchedQtyJson());
					double received = sumJsonArray(model.getReceivedQtyJson());

					totalIndent += indent;
					totalAvailable += available;
					totalRequired += required;
					totalPlanned += planned;
					totalPacked += packed;
					totalDispatched += dispatched;
					totalReceived += received;

					if (indent > 0)
						countIndent++;
					if (available > 0)
						countAvailable++;
					if (required > 0)
						countRequired++;
					if (planned > 0)
						countPlanned++;
					if (packed > 0)
						countPacked++;
					if (dispatched > 0)
						countDispatched++;
					if (received > 0)
						countReceived++;

				}
			}

			pageSummary.put("totalIndent", formatNumberRounded(totalIndent));
			pageSummary.put("totalAvailable", formatNumberRounded(totalAvailable));
			pageSummary.put("totalRequired", formatNumberRounded(totalRequired));
			pageSummary.put("totalPlanned", formatNumberRounded(totalPlanned));
			pageSummary.put("totalPacked", formatNumberRounded(totalPacked));
			pageSummary.put("totalDispatched", formatNumberRounded(totalDispatched));
			pageSummary.put("totalReceived", formatNumberRounded(totalReceived));
			pageSummary.put("productWithIndent", (double) countIndent);
			pageSummary.put("productWithAvailable", (double) countAvailable);
			pageSummary.put("productWithRequired", (double) countRequired);
			pageSummary.put("productWithPlanned", (double) countPlanned);
			pageSummary.put("productWithPacked", (double) countPacked);
			pageSummary.put("productWithDispatched", (double) countDispatched);
			pageSummary.put("productWithReceived", (double) countReceived);
			pageSummary.put("totalProducts", (double) totalItems);

			response.put("result", true);
			response.put("summary", totalSummary);
			response.put("pageSummary", pageSummary);
		} catch (Exception e) {
			e.printStackTrace();
			response.put("result", false);
			response.put("message", "Error occurred while fetching data.");
		} finally {
			try {
				if (ps != null)
					ps.close();
				if (conn != null)
					conn.close();
				if (dataSource != null)
					dataSource.close();
			} catch (SQLException ex) {
				ex.printStackTrace();
			}
		}

		return response;
	}

}