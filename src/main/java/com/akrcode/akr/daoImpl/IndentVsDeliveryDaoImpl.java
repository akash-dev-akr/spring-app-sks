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
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		try (Workbook workbook = new XSSFWorkbook(inputStream);
				HikariDataSource orgDataSource = customDataSource.dynamicDatabaseChange(database);
				Connection conn = orgDataSource.getConnection()) {
			Sheet sheet = workbook.getSheetAt(0);
			if (sheet.getPhysicalNumberOfRows() < 2) {
				sheet.createRow(1).createCell(0).setCellValue("Empty file!");
				workbook.write(out);
				return out.toByteArray();
			}

			Row headerRow = sheet.getRow(0);
			Map<String, Integer> headerMap = new HashMap<>();
			for (int i = 0; i < headerRow.getLastCellNum(); i++) {
				String header = headerRow.getCell(i).getStringCellValue().trim().toUpperCase();
				headerMap.put(header, i);
			}

			List<String> requiredHeaders = Arrays.asList("DATE", "CATEGORY", "PACK FORMAT", "SECTION", "CODE",
					"PRODUCT");
			for (String h : requiredHeaders) {
				if (!headerMap.containsKey(h)) {
					sheet.createRow(1).createCell(0).setCellValue("Missing header: " + h);
					workbook.write(out);
					return out.toByteArray();
				}
			}

			List<DataRow> dataRows = parseExcelData(sheet, headerMap);
			persistData(dataRows, sheet, headerMap, conn);

			workbook.write(out);
			return out.toByteArray();

		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	private void persistData(List<DataRow> rows, Sheet sheet, Map<String, Integer> headerMap, Connection conn)
			throws SQLException {
		int resultColIndex = headerMap.size();

		Map<String, Integer> codeToProductId = new HashMap<>();
		PreparedStatement checkProductStmt = conn.prepareStatement("SELECT id FROM indent_vs_delivery WHERE code = ?");
		PreparedStatement insertProductStmt = conn.prepareStatement(
				"INSERT INTO indent_vs_delivery (category, pack_format, section, code, product) VALUES (?, ?, ?, ?, ?) RETURNING id");

		for (DataRow data : rows) {
			Row row = sheet.getRow(data.rowNum);
			LocalDate reportDate;
			try {
				reportDate = LocalDate.parse(data.dateStr, DateTimeFormatter.ofPattern("dd-MM-yyyy"));
			} catch (Exception e) {
				row.createCell(resultColIndex).setCellValue("Invalid Date Format");
				continue;
			}

			// 1. Get or Insert product
			int productId;
			if (codeToProductId.containsKey(data.code)) {
				productId = codeToProductId.get(data.code);
			} else {
				checkProductStmt.setString(1, data.code);
				ResultSet rs = checkProductStmt.executeQuery();
				if (rs.next()) {
					productId = rs.getInt("id");
				} else {
					insertProductStmt.setString(1, data.category);
					insertProductStmt.setString(2, data.packFormat);
					insertProductStmt.setString(3, data.section);
					insertProductStmt.setString(4, data.code);
					insertProductStmt.setString(5, data.product);
					ResultSet rsInsert = insertProductStmt.executeQuery();
					if (!rsInsert.next()) {
						row.createCell(resultColIndex).setCellValue("Error inserting product");
						continue;
					}
					productId = rsInsert.getInt("id");
				}
				codeToProductId.put(data.code, productId);
			}

			// 2. Check if data exists
			boolean exists;
			try (PreparedStatement check = conn.prepareStatement(
					"SELECT 1 FROM indent_vs_delivery_data WHERE product_id = ? AND report_date = ?")) {
				check.setInt(1, productId);
				check.setDate(2, Date.valueOf(reportDate));
				ResultSet rs = check.executeQuery();
				exists = rs.next();
			}

			// 3. Prepare insert or update
			PreparedStatement ps;
			String[] jsonFields = { data.indentQty, data.availableQty, data.requiredQty, data.plannedQty,
					data.packedQty, data.dispatchedQty, data.receivedQty };
			String[] textFields = { data.indentDiff, data.reason, data.plannedDiff, data.plannedReason, data.packedDiff,
					data.packedReason, data.dispatchedDiff, data.dispatchedReason, data.receivedDiff,
					data.receivedReason };

			if (exists) {
				ps = conn.prepareStatement("UPDATE indent_vs_delivery_data SET "
						+ "indent_qty = ?::jsonb, available_qty = ?::jsonb, required_qty = ?::jsonb, "
						+ "planned_qty = ?::jsonb, packed_qty = ?::jsonb, dispatched_qty = ?::jsonb, received_qty = ?::jsonb, "
						+ "difference = ?, reason = ?, planned_difference = ?, planned_reason = ?, "
						+ "packed_difference = ?, packed_reason = ?, dispatched_difference = ?, dispatched_reason = ?, "
						+ "received_difference = ?, received_reason = ? " + "WHERE product_id = ? AND report_date = ?");
			} else {
				ps = conn.prepareStatement(
						"INSERT INTO indent_vs_delivery_data (product_id, report_date, indent_qty, available_qty, required_qty, "
								+ "planned_qty, packed_qty, dispatched_qty, received_qty, difference, reason, planned_difference, "
								+ "planned_reason, packed_difference, packed_reason, dispatched_difference, dispatched_reason, "
								+ "received_difference, received_reason) "
								+ "VALUES (?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
			}

			int i = 1;
			if (!exists) {
				ps.setInt(i++, productId);
				ps.setDate(i++, Date.valueOf(reportDate));
			}

			// JSON fields
			for (String val : jsonFields)
				ps.setString(i++, val);

			// Text fields
			for (String val : textFields)
				ps.setString(i++, val);

			if (exists) {
				ps.setInt(i++, productId);
				ps.setDate(i++, Date.valueOf(reportDate));
			}

			ps.executeUpdate();
			row.createCell(resultColIndex).setCellValue(exists ? "Updated" : "Inserted");
		}
	}

	private List<DataRow> parseExcelData(Sheet sheet, Map<String, Integer> headerMap) {
		List<DataRow> dataList = new ArrayList<>();
		Iterator<Row> iterator = sheet.iterator();
		if (iterator.hasNext())
			iterator.next(); // skip header

		while (iterator.hasNext()) {
			Row row = iterator.next();
			DataRow data = new DataRow();
			data.rowNum = row.getRowNum();
			data.category = getCellString(row, headerMap.get("CATEGORY"));
			data.packFormat = getCellString(row, headerMap.get("PACK FORMAT"));
			data.section = getCellString(row, headerMap.get("SECTION"));
			data.code = getCellString(row, headerMap.get("CODE"));
			data.product = getCellString(row, headerMap.get("PRODUCT"));
			data.dateStr = getCellString(row, headerMap.get("DATE"));

			data.indentQty = toJsonValue(row, headerMap.get("INDENT QUANTITY"));
			data.availableQty = toJsonValue(row, headerMap.get("AVAILABLE QUANTITY"));
			data.requiredQty = toJsonValue(row, headerMap.get("REQUIRED QUANTITY"));
			data.plannedQty = toJsonValue(row, headerMap.get("PLANNED QUANTITY"));
			data.packedQty = toJsonValue(row, headerMap.get("PACKED QUANTITY"));
			data.dispatchedQty = toJsonValue(row, headerMap.get("DISPATCHED QUANTITY"));
			data.receivedQty = toJsonValue(row, headerMap.get("RECEIVED QUANTITY"));

			data.indentDiff = getCellString(row, headerMap.get("INDENT DIFF"));
			data.reason = getCellString(row, headerMap.get("REASON"));
			data.plannedDiff = getCellString(row, headerMap.get("PLANNED DIFF"));
			data.plannedReason = getCellString(row, headerMap.get("PLANNED REASON"));
			data.packedDiff = getCellString(row, headerMap.get("PACKED DIFF"));
			data.packedReason = getCellString(row, headerMap.get("PACKED REASON"));
			data.dispatchedDiff = getCellString(row, headerMap.get("DISPATCHED DIFF"));
			data.dispatchedReason = getCellString(row, headerMap.get("DISPATCHED REASON"));
			data.receivedDiff = getCellString(row, headerMap.get("RECEIVED DIFF"));
			data.receivedReason = getCellString(row, headerMap.get("RECEIVED REASON"));

			dataList.add(data);
		}

		return dataList;
	}

	private String getCellString(Row row, Integer cellIndex) {
		if (cellIndex == null)
			return "";
		Cell cell = row.getCell(cellIndex);
		if (cell == null)
			return "";

		switch (cell.getCellType()) {
		case STRING:
			return cell.getStringCellValue().trim();
		case NUMERIC:
			if (DateUtil.isCellDateFormatted(cell)) {
				return new SimpleDateFormat("dd-MM-yyyy").format(cell.getDateCellValue());
			} else {
				double num = cell.getNumericCellValue();
				return (num == (long) num) ? String.valueOf((long) num) : String.valueOf(num);
			}
		case BOOLEAN:
			return String.valueOf(cell.getBooleanCellValue());
		case FORMULA:
			return cell.getCellFormula();
		default:
			return "";
		}
	}

	private String toJsonValue(Row row, Integer cellIndex) {
		if (cellIndex == null)
			return "{\"data\":0}";
		Cell cell = row.getCell(cellIndex);
		if (cell == null || cell.getCellType() == CellType.BLANK)
			return "{\"data\":0}";

		double value = 0;
		if (cell.getCellType() == CellType.NUMERIC) {
			value = cell.getNumericCellValue();
		} else if (cell.getCellType() == CellType.STRING) {
			try {
				value = Double.parseDouble(cell.getStringCellValue().trim());
			} catch (NumberFormatException ignored) {
			}
		}
		return "{\"data\":" + value + "}";
	}

	@Override
	public Map<String, Object> listAllProductData(LocalDate reportDate, String category, String product, String section,
			String packFormat, Map<String, String> differenceFilter) {

		Map<String, Object> response = new HashMap<>();
		List<IndentDeliveryDataDto> productList = new ArrayList<>();

		double totalIndent = 0;
		double totalAvailable = 0;
		double totalRequired = 0;
		double totalPlanned = 0;
		double totalPacked = 0;
		double totalDispatched = 0;
		double totalReceived = 0;

		int productWithIndentCount = 0;
		int productWithAvailableCount = 0;
		int productWithRequiredCount = 0;
		int productWithPlannedCount = 0;
		int productWithPackedCount = 0;
		int productWithDispatchedCount = 0;
		int productWithReceivedCount = 0;

		String database = "sri_krishna_db";
		HikariDataSource newDataSource = null;
		Connection conn = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;

		try {
			newDataSource = customDataSource.dynamicDatabaseChange(database);
			conn = newDataSource.getConnection();

			StringBuilder sql = new StringBuilder("SELECT p.id, p.report_date, "
					+ "m.category, m.pack_format, m.section, m.code, m.product, "
					+ "p.indent_qty, p.available_qty, p.required_qty, "
					+ "p.planned_qty, p.packed_qty, p.dispatched_qty, p.received_qty, " + "p.reason, p.difference, "
					+ "p.planned_difference, p.planned_reason, " + "p.packed_difference, p.packed_reason, "
					+ "p.dispatched_difference, p.dispatched_reason, " + "p.received_difference, p.received_reason "
					+ "FROM indent_vs_delivery_data p " + "JOIN indent_vs_delivery m ON p.product_id = m.id");

			List<Object> params = new ArrayList<>();
			boolean hasWhere = false;
			Map<String, String> allowedFields = Map.of("difference", "p.difference", "planned_diff",
					"p.planned_difference", "packed_diff", "p.packed_difference", "dispatched_diff",
					"p.dispatched_difference", "received_diff", "p.received_difference");

			if (differenceFilter != null) {
				for (Map.Entry<String, String> entry : differenceFilter.entrySet()) {
					String key = entry.getKey();
					String filter = entry.getValue();
					String column = allowedFields.get(key);

					if (column == null || filter == null || filter.isBlank())
						continue;

					sql.append(hasWhere ? " AND " : " WHERE ");
					switch (filter) {
					case "zero":
						sql.append("(").append(column).append(" IS NULL OR ").append(column).append(" = '0') ");
						break;
					case "negative":
						sql.append("CAST(NULLIF(").append(column).append(", '') AS NUMERIC) < 0 ");
						break;
					case "positive":
						sql.append("CAST(NULLIF(").append(column).append(", '') AS NUMERIC) > 0 ");
						break;
					}
					hasWhere = true;
				}
			}

			if (reportDate != null) {
				sql.append(hasWhere ? " AND" : " WHERE").append(" p.report_date = ? ");
				params.add(reportDate);
				hasWhere = true;
			}

			if (category != null && !category.isBlank()) {
				sql.append(hasWhere ? " AND" : " WHERE").append(" LOWER(m.category) = LOWER(?) ");
				params.add(category.trim());
				hasWhere = true;
			}

			if (product != null && !product.isBlank()) {
				sql.append(hasWhere ? " AND" : " WHERE").append(" LOWER(m.product) = LOWER(?) ");
				params.add(product.trim());
				hasWhere = true;
			}

			if (section != null && !section.isBlank()) {
				sql.append(hasWhere ? " AND" : " WHERE").append(" LOWER(m.section) = LOWER(?) ");
				params.add(section.trim());
				hasWhere = true;
			}

			if (packFormat != null && !packFormat.isBlank()) {
				sql.append(hasWhere ? " AND" : " WHERE").append(" LOWER(m.pack_format) = LOWER(?) ");
				params.add(packFormat.trim());
				hasWhere = true;
			}

			sql.append(" ORDER BY p.report_date DESC, LOWER(m.product) ASC");

			stmt = conn.prepareStatement(sql.toString());
			for (int i = 0; i < params.size(); i++) {
				stmt.setObject(i + 1, params.get(i));
			}

			rs = stmt.executeQuery();

			while (rs.next()) {
				IndentDeliveryDataDto model = new IndentDeliveryDataDto();
				model.setId(rs.getInt("id"));
				model.setReportDate(rs.getDate("report_date").toLocalDate());
				model.setCategory(rs.getString("category"));
				model.setPackFormat(rs.getString("pack_format"));
				model.setSection(rs.getString("section"));
				model.setCode(rs.getString("code"));
				model.setProduct(rs.getString("product"));

				// Process quantities only once per row
				String indentQty = rs.getString("indent_qty");
				String availableQty = rs.getString("available_qty");
				String requiredQty = rs.getString("required_qty");
				String plannedQty = rs.getString("planned_qty");
				String packedQty = rs.getString("packed_qty");
				String dispatchedQty = rs.getString("dispatched_qty");
				String receivedQty = rs.getString("received_qty");

				model.setIndentQtyJson(indentQty);
				model.setAvailableQtyJson(availableQty);
				model.setRequiredQtyJson(requiredQty);
				model.setPlannedQtyJson(plannedQty);
				model.setPackedQtyJson(packedQty);
				model.setDispatchedQtyJson(dispatchedQty);
				model.setReceivedQtyJson(receivedQty);

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

				// Sum values only once
				double indentSum = sumJsonArray(indentQty);
				double availableSum = sumJsonArray(availableQty);
				double requiredSum = sumJsonArray(requiredQty);
				double plannedSum = sumJsonArray(plannedQty);
				double packedSum = sumJsonArray(packedQty);
				double dispatchedSum = sumJsonArray(dispatchedQty);
				double receivedSum = sumJsonArray(receivedQty);

				totalIndent += indentSum;
				totalAvailable += availableSum;
				totalRequired += requiredSum;
				totalPlanned += plannedSum;
				totalPacked += packedSum;
				totalDispatched += dispatchedSum;
				totalReceived += receivedSum;

				if (indentSum > 0)
					productWithIndentCount++;
				if (availableSum > 0)
					productWithAvailableCount++;
				if (requiredSum > 0)
					productWithRequiredCount++;
				if (plannedSum > 0)
					productWithPlannedCount++;
				if (packedSum > 0)
					productWithPackedCount++;
				if (dispatchedSum > 0)
					productWithDispatchedCount++;
				if (receivedSum > 0)
					productWithReceivedCount++;

				productList.add(model);
			}

			Map<String, Double> summary = new HashMap<>();
			summary.put("totalIndent", formatNumberRounded(totalIndent));
			summary.put("totalAvailable", formatNumberRounded(totalAvailable));
			summary.put("totalRequired", formatNumberRounded(totalRequired));
			summary.put("totalPlanned", formatNumberRounded(totalPlanned));
			summary.put("totalPacked", formatNumberRounded(totalPacked));
			summary.put("totalDispatched", formatNumberRounded(totalDispatched));
			summary.put("totalReceived", formatNumberRounded(totalReceived));

			summary.put("productWithIndent", (double) productWithIndentCount);
			summary.put("productWithAvailable", (double) productWithAvailableCount);
			summary.put("productWithRequired", (double) productWithRequiredCount);
			summary.put("productWithPlanned", (double) productWithPlannedCount);
			summary.put("productWithPacked", (double) productWithPackedCount);
			summary.put("productWithDispatched", (double) productWithDispatchedCount);
			summary.put("productWithReceived", (double) productWithReceivedCount);
			summary.put("totalProducts", (double) productList.size());

			response.put("summary", summary);
			response.put("result", true);
			response.put("indentdelivery", productList);

		} catch (Exception e) {
			e.printStackTrace();
			response.put("result", false);
			response.put("message", "❌ Error fetching report list: " + e.getMessage());
		} finally {
			try {
				if (rs != null)
					rs.close();
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
				        "SELECT indent_qty, available_qty, required_qty, planned_qty, packed_qty, dispatched_qty, received_qty " +
				        "FROM indent_vs_delivery_data WHERE product_id = ? AND report_date = ?")) {

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
				        try (PreparedStatement updateDiff = conn.prepareStatement(
				                "UPDATE indent_vs_delivery_data SET " +
				                        "difference = ?, planned_difference = ?, packed_difference = ?, dispatched_difference = ?, received_difference = ? " +
				                        "WHERE product_id = ? AND report_date = ?")) {

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
	    if (json == null || json.isEmpty()) return BigDecimal.ZERO;
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

			String[] headers = { "Date", "Code", "Product", type.toUpperCase() };

			for (int i = 0; i < headers.length; i++) {
				Cell cell = header.createCell(i);
				cell.setCellValue(headers[i]);
				cell.setCellStyle(headerStyle);
				sheet.autoSizeColumn(i);
			}

			// ===== Return Response =====
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			workbook.write(out);

			return ResponseEntity.ok()
					.header(HttpHeaders.CONTENT_DISPOSITION,
							"attachment; filename=" + type.toLowerCase().replace(" ", "_") + "_template.xlsx")
					.contentType(MediaType.APPLICATION_OCTET_STREAM).body(new ByteArrayResource(out.toByteArray()));

		} catch (IOException e) {
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

			// Map for header name to index
			Map<String, Integer> colIndex = new HashMap<>();
			for (int i = 0; i < headers.length; i++) {
				colIndex.put(headers[i], i);
				Cell cell = header.createCell(i);
				cell.setCellValue(headers[i]);
				cell.setCellStyle(headerStyle);
				sheet.autoSizeColumn(i);
			}

			// ===== Utility to get Excel column letter =====
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

			// ===== Add Sample Rows with Formulas =====
			int startRow = 1;
			int totalRows = 10; // You can increase this as needed

			for (int r = 0; r < totalRows; r++) {
				Row row = sheet.createRow(startRow + r);
				for (int i = 0; i < headers.length; i++) {
					row.createCell(i).setCellValue("");
				}

				int excelRowNum = startRow + r + 1; // Excel rows are 1-based

				// Indent DIFF = (Available + Required) - Indent
				// ===== Updated Formula Cells With IF Conditions =====
				row.getCell(colIndex.get("Indent DIFF"))
						.setCellFormula("IF(AND(ISNUMBER(" + colLetter.apply(colIndex.get("Available Quantity"))
								+ excelRowNum + "),ISNUMBER(" + colLetter.apply(colIndex.get("Required Quantity"))
								+ excelRowNum + "),ISNUMBER(" + colLetter.apply(colIndex.get("Indent Quantity"))
								+ excelRowNum + ")),(" + colLetter.apply(colIndex.get("Available Quantity"))
								+ excelRowNum + "+" + colLetter.apply(colIndex.get("Required Quantity")) + excelRowNum
								+ ")-" + colLetter.apply(colIndex.get("Indent Quantity")) + excelRowNum + ",\"\")");

				row.getCell(colIndex.get("Planned DIFF"))
						.setCellFormula("IF(AND(ISNUMBER(" + colLetter.apply(colIndex.get("Planned Quantity"))
								+ excelRowNum + "),ISNUMBER(" + colLetter.apply(colIndex.get("Indent Quantity"))
								+ excelRowNum + ")), " + colLetter.apply(colIndex.get("Planned Quantity")) + excelRowNum
								+ "-" + colLetter.apply(colIndex.get("Indent Quantity")) + excelRowNum + ",\"\")");

				row.getCell(colIndex.get("Packed DIFF"))
						.setCellFormula("IF(AND(ISNUMBER(" + colLetter.apply(colIndex.get("Packed Quantity"))
								+ excelRowNum + "),ISNUMBER(" + colLetter.apply(colIndex.get("Planned Quantity"))
								+ excelRowNum + ")), " + colLetter.apply(colIndex.get("Packed Quantity")) + excelRowNum
								+ "-" + colLetter.apply(colIndex.get("Planned Quantity")) + excelRowNum + ",\"\")");

				row.getCell(colIndex.get("Dispatched DIFF"))
						.setCellFormula("IF(AND(ISNUMBER(" + colLetter.apply(colIndex.get("Dispatched Quantity"))
								+ excelRowNum + "),ISNUMBER(" + colLetter.apply(colIndex.get("Packed Quantity"))
								+ excelRowNum + ")), " + colLetter.apply(colIndex.get("Dispatched Quantity"))
								+ excelRowNum + "-" + colLetter.apply(colIndex.get("Packed Quantity")) + excelRowNum
								+ ",\"\")");

				row.getCell(colIndex.get("Received DIFF"))
						.setCellFormula("IF(AND(ISNUMBER(" + colLetter.apply(colIndex.get("Received Quantity"))
								+ excelRowNum + "),ISNUMBER(" + colLetter.apply(colIndex.get("Dispatched Quantity"))
								+ excelRowNum + ")), " + colLetter.apply(colIndex.get("Received Quantity"))
								+ excelRowNum + "-" + colLetter.apply(colIndex.get("Dispatched Quantity")) + excelRowNum
								+ ",\"\")");

			}

			// ===== Return Excel File =====
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			workbook.write(out);

			return ResponseEntity.ok()
					.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=quantity_template.xlsx")
					.contentType(MediaType.APPLICATION_OCTET_STREAM).body(new ByteArrayResource(out.toByteArray()));

		} catch (IOException e) {
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
	            if (stmt != null) stmt.close();
	            if (conn != null) conn.close();
	            if (newDataSource != null) newDataSource.close();
	        } catch (Exception ignored) {}
	    }

	    return response;
	}


}