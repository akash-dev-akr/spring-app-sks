package com.akrcode.akr.daoImpl;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.akrcode.akr.configure.CustomDataSource;
import com.akrcode.akr.dao.PurchaseDao;
import com.akrcode.akr.dto.PurchaseStatusUpdate;
import com.akrcode.akr.dto.SearchKeys;
import com.akrcode.akr.dto.purchaseDto;
import com.zaxxer.hikari.HikariDataSource;

import io.jsonwebtoken.io.IOException;

@Component
public class PurchaseDaoImpl implements PurchaseDao {
	@Autowired
	CustomDataSource customDataSource;

	String[] stocksTemplateHeader = { "Code", "Stock in Hand", "Stock in hand value" };
	String[] purcahsedTemplateHeader = { "Code", "Purcahsed Qty", "Purcahsed Value" };
	List<String> expectedHeaders = Arrays.asList("category", "sub category", "code", "product name", "supplier",
			"budget qty", "budget value", "purcahsed qty", "purcahsed value", "min stock qty", "max stock qty", "moq",
			"lead time", "schedule", "stock in hand", "stock in hand value", "status", "remarks", "date");

	@Override
	public Map<String, Object> readAndSaveFromFile(MultipartFile file) {
		Map<String, Object> response = new HashMap<>();
		String database = "test";

		HikariDataSource dataSource = null;
		Connection conn = null;

		try {
			dataSource = customDataSource.dynamicDatabaseChange(database);
			conn = dataSource.getConnection();

			try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
				for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
					Sheet sheet = workbook.getSheetAt(i);
					String sheetName = sheet.getSheetName().replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();

					Row headerRow = sheet.getRow(0);
					if (headerRow == null || headerRow.getLastCellNum() == 0)
						continue;

					Map<String, String> columnTypeMap = new LinkedHashMap<>();
					int maxTypeScanRows = Math.min(sheet.getLastRowNum(), 20);

					for (int c = 0; c < headerRow.getLastCellNum(); c++) {
						Cell headerCell = headerRow.getCell(c);
						if (headerCell == null)
							continue;

						String colName = headerCell.getStringCellValue().replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
						if (colName.isBlank() || colName.equalsIgnoreCase("sno"))
							continue;

						columnTypeMap.put(colName, "TEXT");

						for (int r = 1; r <= maxTypeScanRows; r++) {
							Row row = sheet.getRow(r);
							if (row == null)
								continue;

							Cell cell = row.getCell(c);
							if (cell == null)
								continue;

							try {
								switch (cell.getCellType()) {
								case NUMERIC -> {
									if (DateUtil.isCellDateFormatted(cell))
										columnTypeMap.put(colName, "DATE");
									else
										columnTypeMap.put(colName,
												(cell.getNumericCellValue() % 1 == 0) ? "INTEGER" : "NUMERIC");
								}
								case BOOLEAN -> columnTypeMap.put(colName, "BOOLEAN");
								default -> columnTypeMap.put(colName, "TEXT");
								}
							} catch (Exception e) {
								columnTypeMap.put(colName, "TEXT");
							}
						}
					}

					if (columnTypeMap.isEmpty())
						continue;
					List<String> columns = new ArrayList<>(columnTypeMap.keySet());

					// Check if table exists
					boolean tableExists;
					try (PreparedStatement checkStmt = conn.prepareStatement(
							"SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?)");) {
						checkStmt.setString(1, sheetName);
						ResultSet rs = checkStmt.executeQuery();
						tableExists = rs.next() && rs.getBoolean(1);
					}

					if (!tableExists) {
						StringBuilder createSQL = new StringBuilder(
								"CREATE TABLE " + sheetName + " (id SERIAL PRIMARY KEY, ");
						for (Map.Entry<String, String> entry : columnTypeMap.entrySet()) {
							createSQL.append(entry.getKey()).append(" ").append(entry.getValue()).append(", ");
						}
						createSQL.append("created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
						conn.createStatement().execute(createSQL.toString());
					} else {
						PreparedStatement colCheckStmt = conn.prepareStatement(
								"SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = ? AND column_name = 'created_at')");
						colCheckStmt.setString(1, sheetName);
						ResultSet colRs = colCheckStmt.executeQuery();
						if (colRs.next() && !colRs.getBoolean(1)) {
							conn.createStatement().execute("ALTER TABLE " + sheetName
									+ " ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
						}
					}

					String insertSQL = "INSERT INTO " + sheetName + " (" + String.join(", ", columns)
							+ ", created_at) VALUES (" + "?, ".repeat(columns.size() - 1) + "?, CURRENT_TIMESTAMP)";
					PreparedStatement pstmt = conn.prepareStatement(insertSQL);

					for (int r = 1; r <= sheet.getLastRowNum(); r++) {
						Row row = sheet.getRow(r);
						if (row == null)
							continue;

						for (int c = 0; c < columns.size(); c++) {
							Cell cell = row.getCell(c);
							String colType = columnTypeMap.get(columns.get(c));

							try {
								if (cell == null || cell.getCellType() == CellType.BLANK) {
									pstmt.setNull(c + 1, Types.NULL);
									continue;
								}

								switch (colType) {
								case "INTEGER" -> pstmt.setInt(c + 1, (int) cell.getNumericCellValue());
								case "NUMERIC" -> pstmt.setDouble(c + 1, cell.getNumericCellValue());
								case "BOOLEAN" -> pstmt.setBoolean(c + 1, cell.getBooleanCellValue());
								case "DATE" ->
									pstmt.setDate(c + 1, new java.sql.Date(cell.getDateCellValue().getTime()));
								default -> {
									cell.setCellType(CellType.STRING);
									pstmt.setString(c + 1, cell.getStringCellValue());
								}
								}
							} catch (Exception ex) {
								pstmt.setNull(c + 1, Types.NULL);
							}
						}

						pstmt.executeUpdate();
					}
				}
			}

			response.put("result", true);
			response.put("message", "✅ File imported successfully.");
			return response;

		} catch (Exception e) {
			e.printStackTrace();
			response.put("result", false);
			response.put("message", "❌ Error: " + e.getMessage());
			return response;
		} finally {
			try {
				if (conn != null)
					conn.close();
				if (dataSource != null)
					dataSource.close();
			} catch (Exception ignored) {
			}
		}
	}

	@Override
	public Map<String, Object> searchFilter(SearchKeys test) {
		Map<String, Object> response = new HashMap<>();
		List<purchaseDto> purchaseList = new ArrayList<>();
		String database = "test";
		HikariDataSource newDataSource = null;
		Connection conn = null;
		Statement stmt = null;
		ResultSet rs = null;

		// These flags help us check if the columns exist before fetching them
		boolean hasStockUpdatedAt = false;
		boolean hasPurchaseUpdatedAt = false;

		try {
			newDataSource = customDataSource.dynamicDatabaseChange(database);
			conn = newDataSource.getConnection();

			// 🔍 Check if the columns exist in `product_tracker`
			DatabaseMetaData metaData = conn.getMetaData();
			ResultSet columns = metaData.getColumns(null, null, "product_tracker", null);
			while (columns.next()) {
				String columnName = columns.getString("COLUMN_NAME");
				if ("stockupdated_at".equalsIgnoreCase(columnName)) {
					hasStockUpdatedAt = true;
				}
				if ("purchaseupdated_at".equalsIgnoreCase(columnName)) {
					hasPurchaseUpdatedAt = true;
				}
			}
			columns.close();

			stmt = conn.createStatement();
			String sql = "SELECT * FROM product_tracker ORDER BY id";
			rs = stmt.executeQuery(sql);

			while (rs.next()) {
				purchaseDto model = new purchaseDto();
				model.setId(rs.getLong("id"));
				model.setCategory(rs.getString("category"));
				model.setSub_category(rs.getString("sub_category"));
				model.setCode(rs.getString("code"));
				model.setProduct_name(rs.getString("product_name"));
				model.setSupplier(rs.getString("supplier"));
				model.setBudget_qty(rs.getBigDecimal("budget_qty"));
				model.setBudget_value(rs.getBigDecimal("budget_value"));
				model.setPurcahsed_qty(rs.getInt("purcahsed_qty"));
				model.setPurcahsed_value(rs.getInt("purcahsed_value"));
				model.setMin_stock_qty(rs.getInt("min_stock_qty"));
				model.setMax_stock_qty(rs.getInt("max_stock_qty"));
				model.setMoq(rs.getInt("moq"));
				model.setLead_time(rs.getInt("lead_time"));
				model.setSchedule(rs.getInt("schedule"));
				model.setStock_in_hand(rs.getInt("stock_in_hand"));
				model.setStock_in_hand_value(rs.getInt("stock_in_hand_value"));
				model.setStatus(rs.getString("status"));
				model.setRemarks(rs.getString("remarks"));
				model.setDate(rs.getDate("date"));

				// ✅ Conditionally set timestamps if columns exist
				if (hasStockUpdatedAt) {
					model.setStockupdated_at(rs.getDate("stockupdated_at"));
				}
				if (hasPurchaseUpdatedAt) {
					model.setPurchaseupdated_at(rs.getDate("purchaseupdated_at"));
				}

				purchaseList.add(model);
			}
		} catch (Exception e) {
			e.printStackTrace();
			response.put("result", false);
			response.put("message", "❌ Error: " + e.getMessage());
			return response;
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

		response.put("purchaseList", purchaseList);
		response.put("result", true);
		return response;
	}

	@Override
	public Map<String, Object> statusUpdate(PurchaseStatusUpdate purchase) {
		Map<String, Object> response = new HashMap<>();
		int orgid = 1;
		String database = "test";
		HikariDataSource newDataSource = null;
		Connection conn = null;
		PreparedStatement stmt = null;

		try {
			newDataSource = customDataSource.dynamicDatabaseChange(database);
			conn = newDataSource.getConnection();

			String sql = "UPDATE product_tracker SET status = ?, remarks = ?, date = ? WHERE id = ?";
			stmt = conn.prepareStatement(sql);

			stmt.setString(1, purchase.getStatus());
			stmt.setString(2, purchase.getRemarks());
			stmt.setDate(3, purchase.getDate());
			stmt.setInt(4, purchase.getId());

			int rows = stmt.executeUpdate();
			if (rows > 0) {
				response.put("result", true);
				response.put("message", "✅ Status updated successfully.");
			} else {
				response.put("result", false);
				response.put("message", "⚠️ No record updated. Check if ID is correct.");
			}
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
	public byte[] getSampleStockTemplate() {
		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream();) {
			Sheet sheet = workbook.createSheet("Lead");
			Row headerRow = sheet.createRow(0);

			CellStyle headerCellStyle = sheet.getWorkbook().createCellStyle();
			// fill foreground color ...
			headerCellStyle.setFillForegroundColor(IndexedColors.SEA_GREEN.index);
			// and solid fill pattern produces solid grey cell fill
			headerCellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

			for (int i = 0; i < stocksTemplateHeader.length; i++) {
				headerRow.createCell(i).setCellValue(stocksTemplateHeader[i]);
				Cell cell = headerRow.getCell(i);
				cell.setCellStyle(headerCellStyle);

				Font redFont = workbook.createFont();
				redFont.setColor(IndexedColors.WHITE.index);
				redFont.setFontHeightInPoints((short) 11);
				redFont.setBold(true);
				headerCellStyle.setFont(redFont);
				headerRow.getCell(i).setCellStyle(headerCellStyle);
			}
			for (int columnIndex = 0; columnIndex < 28; columnIndex++) {

				sheet.autoSizeColumn(columnIndex);
			}
			workbook.write(out);

			return out.toByteArray();
		} catch (IOException | java.io.IOException e) {
			throw new RuntimeException("Failed to parse excel file: " + e.getMessage());
		}
	}

	@Override
	public byte[] getSamplePurcahsedTemplate() {
		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream();) {
			Sheet sheet = workbook.createSheet("Lead");
			Row headerRow = sheet.createRow(0);

			CellStyle headerCellStyle = sheet.getWorkbook().createCellStyle();
			// fill foreground color ...
			headerCellStyle.setFillForegroundColor(IndexedColors.SEA_GREEN.index);
			// and solid fill pattern produces solid grey cell fill
			headerCellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

			for (int i = 0; i < purcahsedTemplateHeader.length; i++) {
				headerRow.createCell(i).setCellValue(purcahsedTemplateHeader[i]);
				Cell cell = headerRow.getCell(i);
				cell.setCellStyle(headerCellStyle);

				Font redFont = workbook.createFont();
				redFont.setColor(IndexedColors.WHITE.index);
				redFont.setFontHeightInPoints((short) 11);
				redFont.setBold(true);
				headerCellStyle.setFont(redFont);
				headerRow.getCell(i).setCellStyle(headerCellStyle);
			}
			for (int columnIndex = 0; columnIndex < 28; columnIndex++) {

				sheet.autoSizeColumn(columnIndex);
			}
			workbook.write(out);

			return out.toByteArray();
		} catch (IOException | java.io.IOException e) {
			throw new RuntimeException("Failed to parse excel file: " + e.getMessage());
		}
	}

	@Override
	public byte[] uploadPurcahseExcel(InputStream inputStream) {
		int orgid = 1;
		String database = "test";
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		try (Workbook workbook = new XSSFWorkbook(inputStream);
				HikariDataSource orgDataSource = customDataSource.dynamicDatabaseChange(database);
				Connection conn = orgDataSource.getConnection()) {

			// ✅ Ensure purchaseupdated_at column exists
			ensureColumnExists(conn, "product_tracker", "purchaseupdated_at", "TIMESTAMP");

			Sheet sheet = workbook.getSheetAt(0);
			Iterator<Row> rowIterator = sheet.iterator();

			if (!rowIterator.hasNext()) {
				sheet.createRow(1).createCell(0).setCellValue("Empty file!");
				workbook.write(out);
				return out.toByteArray();
			}

			Row headerRow = rowIterator.next();
			int codeIdx = -1, qtyIdx = -1, valueIdx = -1;

			for (int i = 0; i < headerRow.getLastCellNum(); i++) {
				String header = headerRow.getCell(i).getStringCellValue().trim();
				if ("Code".equalsIgnoreCase(header))
					codeIdx = i;
				else if ("Purcahsed Qty".equalsIgnoreCase(header))
					qtyIdx = i;
				else if ("Purcahsed Value".equalsIgnoreCase(header))
					valueIdx = i;
			}

			if (codeIdx == -1 || qtyIdx == -1 || valueIdx == -1) {
				sheet.createRow(1).createCell(0)
						.setCellValue("Missing headers: Code, Purchased Qty, or Purchased Value");
				workbook.write(out);
				return out.toByteArray();
			}

			while (rowIterator.hasNext()) {
				Row row = rowIterator.next();
				Cell codeCell = row.getCell(codeIdx);
				if (codeCell == null || codeCell.getCellType() != CellType.STRING) {
					row.createCell(headerRow.getLastCellNum()).setCellValue("Invalid or empty code");
					continue;
				}

				String code = codeCell.getStringCellValue().trim();
				double qty = row.getCell(qtyIdx) != null ? row.getCell(qtyIdx).getNumericCellValue() : 0;
				double value = row.getCell(valueIdx) != null ? row.getCell(valueIdx).getNumericCellValue() : 0;

				try (PreparedStatement stmt = conn.prepareStatement(
						"UPDATE product_tracker SET purcahsed_qty = ?, purcahsed_value = ?, purchaseupdated_at = now() WHERE code = ?")) {
					stmt.setDouble(1, qty);
					stmt.setDouble(2, value);
					stmt.setString(3, code);

					int updated = stmt.executeUpdate();
					if (updated == 0) {
						row.createCell(headerRow.getLastCellNum()).setCellValue("Code not found");
					} else {
						row.createCell(headerRow.getLastCellNum()).setCellValue("Updated");
					}
				} catch (SQLException e) {
					row.createCell(headerRow.getLastCellNum()).setCellValue("Error: " + e.getMessage());
				}
			}

			workbook.write(out);
			return out.toByteArray();

		} catch (Exception e) {
			try {
				out.write(("Error: " + e.getMessage()).getBytes());
			} catch (IOException ignored) {
			} catch (java.io.IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			return out.toByteArray();
		}
	}

	@Override
	public byte[] uploadStocksExcel(InputStream inputStream) {

		String database = "test";
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		try (Workbook workbook = new XSSFWorkbook(inputStream);
				HikariDataSource orgDataSource = customDataSource.dynamicDatabaseChange(database);
				Connection conn = orgDataSource.getConnection()) {

			// ✅ Ensure stockupdated_at column exists
			ensureColumnExists(conn, "product_tracker", "stockupdated_at", "TIMESTAMP");

			Sheet sheet = workbook.getSheetAt(0);
			Iterator<Row> rowIterator = sheet.iterator();

			if (!rowIterator.hasNext()) {
				sheet.createRow(1).createCell(0).setCellValue("Empty file!");
				workbook.write(out);
				return out.toByteArray();
			}

			Row headerRow = rowIterator.next();
			int codeIdx = -1, stockIdx = -1, valueIdx = -1;

			for (int i = 0; i < headerRow.getLastCellNum(); i++) {
				String header = headerRow.getCell(i).getStringCellValue().trim();
				if ("Code".equalsIgnoreCase(header))
					codeIdx = i;
				else if ("Stock in Hand".equalsIgnoreCase(header))
					stockIdx = i;
				else if ("Stock in hand value".equalsIgnoreCase(header))
					valueIdx = i;
			}

			if (codeIdx == -1 || stockIdx == -1 || valueIdx == -1) {
				sheet.createRow(1).createCell(0)
						.setCellValue("Missing headers: Code, Stock in Hand, or Stock in hand value");
				workbook.write(out);
				return out.toByteArray();
			}

			while (rowIterator.hasNext()) {
				Row row = rowIterator.next();
				Cell codeCell = row.getCell(codeIdx);
				if (codeCell == null || codeCell.getCellType() != CellType.STRING) {
					row.createCell(headerRow.getLastCellNum()).setCellValue("Invalid or empty code");
					continue;
				}

				String code = codeCell.getStringCellValue().trim();
				double stockQty = row.getCell(stockIdx) != null ? row.getCell(stockIdx).getNumericCellValue() : 0;
				double stockValue = row.getCell(valueIdx) != null ? row.getCell(valueIdx).getNumericCellValue() : 0;

				try (PreparedStatement stmt = conn.prepareStatement(
						"UPDATE product_tracker SET stock_in_hand = ?, stock_in_hand_value = ?, stockupdated_at = now() WHERE code = ?")) {
					stmt.setDouble(1, stockQty);
					stmt.setDouble(2, stockValue);
					stmt.setString(3, code);

					int updated = stmt.executeUpdate();
					if (updated == 0) {
						row.createCell(headerRow.getLastCellNum()).setCellValue("Code not found");
					} else {
						row.createCell(headerRow.getLastCellNum()).setCellValue("Updated");
					}
				} catch (SQLException e) {
					row.createCell(headerRow.getLastCellNum()).setCellValue("Error: " + e.getMessage());
				}
			}

			workbook.write(out);
			return out.toByteArray();

		} catch (Exception e) {
			try {
				out.write(("Error: " + e.getMessage()).getBytes());
			} catch (IOException ignored) {
			} catch (java.io.IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			return out.toByteArray();
		}
	}

	private void ensureColumnExists(Connection conn, String tableName, String columnName, String columnType)
			throws SQLException {
		DatabaseMetaData meta = conn.getMetaData();
		try (ResultSet rs = meta.getColumns(null, null, tableName, columnName)) {
			if (!rs.next()) {
				try (Statement stmt = conn.createStatement()) {
					stmt.executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnType);
				}
			}
		}
	}

	@Override
	public Map<String, Object> PurchaseUpload(MultipartFile file) {
		Map<String, Object> response = new HashMap<>();
		String database = "test";

		HikariDataSource dataSource = null;
		Connection conn = null;

		// Expected headers in order (lowercase)
		List<String> expectedHeaders = Arrays.asList("category", "sub category", "code", "product name", "supplier",
				"budget qty", "budget value", "purcahsed qty", "purcahsed value", "min stock qty", "max stock qty",
				"moq", "lead time", "schedule", "stock in hand", "stock in hand value", "status", "remarks", "date");

		try {
			dataSource = customDataSource.dynamicDatabaseChange(database);
			conn = dataSource.getConnection();

			try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
				Sheet sheet = workbook.getSheetAt(0);
				if (sheet == null || sheet.getLastRowNum() == 0) {
					response.put("result", false);
					response.put("message", "❌ Excel sheet is empty.");
					return response;
				}

				Row headerRow = sheet.getRow(0);
				if (headerRow == null) {
					response.put("result", false);
					response.put("message", "❌ Header row is missing.");
					return response;
				}

				// Map header names to column indexes
				Map<String, Integer> columnIndexMap = new HashMap<>();
				for (int c = 0; c < headerRow.getLastCellNum(); c++) {
					Cell cell = headerRow.getCell(c);
					if (cell == null)
						continue;
					String header = cell.getStringCellValue().trim().toLowerCase();
					columnIndexMap.put(header, c);
				}

				// Validate headers
				for (String expected : expectedHeaders) {
					if (!columnIndexMap.containsKey(expected)) {
						response.put("result", false);
						response.put("message", "❌ Missing required header: " + expected);
						return response;
					}
				}

				// Read and insert/update each row
				for (int r = 1; r <= sheet.getLastRowNum(); r++) {
					Row row = sheet.getRow(r);
					if (row == null)
						continue;

					purchaseDto model = new purchaseDto();
					try {
						model.setCategory(getCellValue(row, columnIndexMap.get("category")));
						model.setSub_category(getCellValue(row, columnIndexMap.get("sub category")));
						model.setCode(getCellValue(row, columnIndexMap.get("code")));
						model.setProduct_name(getCellValue(row, columnIndexMap.get("product name")));
						model.setSupplier(getCellValue(row, columnIndexMap.get("supplier")));
						model.setBudget_qty(getBigDecimalValue(row, columnIndexMap.get("budget qty")));
						model.setBudget_value(getBigDecimalValue(row, columnIndexMap.get("budget value")));
						model.setPurcahsed_qty(getIntValue(row, columnIndexMap.get("purcahsed qty")));
						model.setPurcahsed_value(getIntValue(row, columnIndexMap.get("purcahsed value")));
						model.setMin_stock_qty(getIntValue(row, columnIndexMap.get("min stock qty")));
						model.setMax_stock_qty(getIntValue(row, columnIndexMap.get("max stock qty")));
						model.setMoq(getIntValue(row, columnIndexMap.get("moq")));
						model.setLead_time(getIntValue(row, columnIndexMap.get("lead time")));
						model.setSchedule(getIntValue(row, columnIndexMap.get("schedule")));
						model.setStock_in_hand(getIntValue(row, columnIndexMap.get("stock in hand")));
						model.setStock_in_hand_value(getIntValue(row, columnIndexMap.get("stock in hand value")));
						model.setStatus(getCellValue(row, columnIndexMap.get("status")));
						model.setRemarks(getCellValue(row, columnIndexMap.get("remarks")));
						model.setDate(getDateValue(row, columnIndexMap.get("date")));
					} catch (Exception e) {
						continue; // Skip this row if parsing fails
					}

					// Skip if code is missing
					if (model.getCode() == null || model.getCode().isBlank())
						continue;

					// Check if record exists
					PreparedStatement checkStmt = conn
							.prepareStatement("SELECT id FROM product_tracker WHERE code = ?");
					checkStmt.setString(1, model.getCode());
					ResultSet rs = checkStmt.executeQuery();

					if (rs.next()) {
						// UPDATE
						long id = rs.getLong("id");
						PreparedStatement updateStmt = conn.prepareStatement(
								"UPDATE product_tracker SET category=?, sub_category=?, product_name=?, supplier=?, "
										+ "budget_qty=?, budget_value=?, purcahsed_qty=?, purcahsed_value=?, min_stock_qty=?, "
										+ "max_stock_qty=?, moq=?, lead_time=?, schedule=?, stock_in_hand=?, stock_in_hand_value=?, "
										+ "status=?, remarks=?, date=? WHERE id=?");
						updateStmt.setString(1, model.getCategory());
						updateStmt.setString(2, model.getSub_category());
						updateStmt.setString(3, model.getProduct_name());
						updateStmt.setString(4, model.getSupplier());
						updateStmt.setBigDecimal(5, model.getBudget_qty());
						updateStmt.setBigDecimal(6, model.getBudget_value());
						updateStmt.setInt(7, model.getPurcahsed_qty());
						updateStmt.setInt(8, model.getPurcahsed_value());
						updateStmt.setInt(9, model.getMin_stock_qty());
						updateStmt.setInt(10, model.getMax_stock_qty());
						updateStmt.setInt(11, model.getMoq());
						updateStmt.setInt(12, model.getLead_time());
						updateStmt.setInt(13, model.getSchedule());
						updateStmt.setInt(14, model.getStock_in_hand());
						updateStmt.setInt(15, model.getStock_in_hand_value());
						updateStmt.setString(16, model.getStatus());
						updateStmt.setString(17, model.getRemarks());
						updateStmt.setDate(18, model.getDate());
						updateStmt.setLong(19, id);
						updateStmt.executeUpdate();
						updateStmt.close();
					} else {
						// INSERT
						PreparedStatement insertStmt = conn.prepareStatement(
								"INSERT INTO product_tracker (category, sub_category, code, product_name, supplier, "
										+ "budget_qty, budget_value, purcahsed_qty, purcahsed_value, min_stock_qty, max_stock_qty, moq, "
										+ "lead_time, schedule, stock_in_hand, stock_in_hand_value, status, remarks, date) "
										+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
						insertStmt.setString(1, model.getCategory());
						insertStmt.setString(2, model.getSub_category());
						insertStmt.setString(3, model.getCode());
						insertStmt.setString(4, model.getProduct_name());
						insertStmt.setString(5, model.getSupplier());
						insertStmt.setBigDecimal(6, model.getBudget_qty());
						insertStmt.setBigDecimal(7, model.getBudget_value());
						insertStmt.setInt(8, model.getPurcahsed_qty());
						insertStmt.setInt(9, model.getPurcahsed_value());
						insertStmt.setInt(10, model.getMin_stock_qty());
						insertStmt.setInt(11, model.getMax_stock_qty());
						insertStmt.setInt(12, model.getMoq());
						insertStmt.setInt(13, model.getLead_time());
						insertStmt.setInt(14, model.getSchedule());
						insertStmt.setInt(15, model.getStock_in_hand());
						insertStmt.setInt(16, model.getStock_in_hand_value());
						insertStmt.setString(17, model.getStatus());
						insertStmt.setString(18, model.getRemarks());
						insertStmt.setDate(19, model.getDate());
						insertStmt.executeUpdate();
						insertStmt.close();
					}
					rs.close();
					checkStmt.close();
				}

				response.put("result", true);
				response.put("message", "✅ File processed successfully.");
				return response;

			} catch (Exception e) {
				response.put("result", false);
				response.put("message", "❌ Error: " + e.getMessage());
				return response;
			}

		} catch (Exception ex) {
			ex.printStackTrace();
			response.put("result", false);
			response.put("message", "❌ Database connection failed.");
			return response;

		} finally {
			try {
				if (conn != null)
					conn.close();
				if (dataSource != null)
					dataSource.close();
			} catch (Exception ignored) {
			}
		}
	}

	// Utility to get String value from cell
	private String getCellValue(Row row, Integer colIdx) {
		if (colIdx == null)
			return null;
		Cell cell = row.getCell(colIdx);
		if (cell == null)
			return null;
		cell.setCellType(CellType.STRING);
		return cell.getStringCellValue().trim();
	}

	// Utility to get int value from cell
	private int getIntValue(Row row, Integer colIdx) {
		if (colIdx == null)
			return 0;
		Cell cell = row.getCell(colIdx);
		if (cell == null || cell.getCellType() == CellType.BLANK)
			return 0;
		return (int) cell.getNumericCellValue();
	}

	// Utility to get BigDecimal value from cell
	private BigDecimal getBigDecimalValue(Row row, Integer colIdx) {
		if (colIdx == null)
			return BigDecimal.ZERO;
		Cell cell = row.getCell(colIdx);
		if (cell == null || cell.getCellType() == CellType.BLANK)
			return BigDecimal.ZERO;
		return BigDecimal.valueOf(cell.getNumericCellValue());
	}

	// Utility to get java.sql.Date value from cell
	private java.sql.Date getDateValue(Row row, Integer colIdx) {
		if (colIdx == null)
			return null;
		Cell cell = row.getCell(colIdx);
		if (cell == null || cell.getCellType() != CellType.NUMERIC || !DateUtil.isCellDateFormatted(cell))
			return null;
		return new java.sql.Date(cell.getDateCellValue().getTime());
	}

}
