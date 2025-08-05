package com.akrcode.akr.daoImpl;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

	List<String> expectedHeaders = Arrays.asList("category", "sub category", "code", "product name", "supplier",
			"budget qty", "budget value", "purcahsed qty", "purcahsed value", "min stock qty", "max stock qty", "moq",
			"lead time", "schedule", "stock in hand", "stock in hand value", "status", "remarks", "date");

	@Override
	public byte[] uploadPurcahseExcelFile(MultipartFile file) {
		String database = "sri_krishna_db";

		try (Workbook workbook = new XSSFWorkbook(file.getInputStream());
				ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
				HikariDataSource ds = customDataSource.dynamicDatabaseChange(database);
				Connection conn = ds.getConnection()) {
			Sheet sheet = workbook.getSheetAt(0);
			Iterator<Row> rows = sheet.iterator();

			if (!rows.hasNext())
				throw new IllegalArgumentException("Empty Excel");
			DataFormatter formatter = new DataFormatter();
			Row headerRow = null;
			while (rows.hasNext()) {
				Row current = rows.next();
				String firstCell = formatter.formatCellValue(current.getCell(0)).trim();
				if (firstCell.equalsIgnoreCase("Category")) {
					headerRow = current;
					break;
				}
			}
			if (headerRow == null) {
				throw new IllegalArgumentException("No valid header row found. Expected 'Category' as first column.");
			}

			int statusColIdx = headerRow.getLastCellNum();
			headerRow.createCell(statusColIdx).setCellValue("Status");

			while (rows.hasNext()) {
				Row row = rows.next();
				purchaseDto dto = mapRowToDto(row, formatter);
				String status = "Skipped";

				if (dto.getCode() == null || dto.getCode().isEmpty()) {
					row.createCell(statusColIdx).setCellValue("Missing Code");
					continue;
				}

				// Check if master record exists
				boolean masterExists = false;
				try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM product_tracker WHERE code = ?")) {
					ps.setString(1, dto.getCode());
					ResultSet rs = ps.executeQuery();
					masterExists = rs.next();
				}

				// Insert master record if not exists
				if (!masterExists) {
					try (PreparedStatement ps = conn.prepareStatement(
							"INSERT INTO product_tracker (category, sub_category, code, product_name, supplier) "
									+ "VALUES (?, ?, ?, ?, ?)")) {
						ps.setString(1, dto.getCategory());
						ps.setString(2, dto.getSub_category());
						ps.setString(3, dto.getCode());
						ps.setString(4, dto.getProduct_name());
						ps.setString(5, dto.getSupplier());
						ps.executeUpdate();
					}
					status = "Inserted Master";
				} else {
					status = "Exists";
				}

				// Check if history exists for same code + date
				boolean isToday = false;
				try (PreparedStatement selectStmt = conn.prepareStatement(
						"""
								    SELECT * FROM product_tracker_history
								    WHERE code = ?
								    ORDER BY GREATEST(COALESCE(created_at, '1900-01-01'), COALESCE(updated_at, '1900-01-01')) DESC
								    LIMIT 1
								""")) {
					selectStmt.setString(1, dto.getCode());
					try (ResultSet rs = selectStmt.executeQuery()) {
						if (rs.next()) {
							Date createdAt = rs.getDate("created_at");
							Date updatedAt = rs.getDate("updated_at");
							LocalDate today = LocalDate.now();
							isToday = (createdAt != null && createdAt.toLocalDate().isEqual(today))
									|| (updatedAt != null && updatedAt.toLocalDate().isEqual(today));
						}
					}
				}

				if (isToday) {
					// Update existing history record
					try (PreparedStatement ps = conn.prepareStatement("UPDATE product_tracker_history SET "
							+ "budget_qty=?, budget_value=?, purchased_qty=?, purchased_value=?, "
							+ "min_stock_qty=?, max_stock_qty=?, moq=?, lead_time=?, schedule=?, "
							+ "stock_in_hand=?, stock_in_hand_value=?, status=?, remarks=?, updated_at=NOW() "
							+ "WHERE code=? AND date=?")) {

						ps.setBigDecimal(1, dto.getBudget_qty());
						ps.setBigDecimal(2, dto.getBudget_value());
						ps.setObject(3, dto.getPurchsed_qty());
						ps.setObject(4, dto.getPurchsed_value());
						ps.setObject(5, dto.getMin_stock_qty());
						ps.setObject(6, dto.getMax_stock_qty());
						ps.setObject(7, dto.getMoq());
						ps.setObject(8, dto.getLead_time());
						ps.setInt(9, dto.getSchedule());
						ps.setObject(10, dto.getStock_in_hand());
						ps.setInt(11, dto.getStock_in_hand_value());
						ps.setString(12, dto.getStatus());
						ps.setString(13, dto.getRemarks());
						ps.setString(14, dto.getCode());
						ps.setDate(15, dto.getDate() != null ? new java.sql.Date(dto.getDate().getTime()) : null);
						ps.executeUpdate();
					}
					status += " + Updated Child";
				} else {
					// Insert new child record
					try (PreparedStatement ps = conn.prepareStatement("INSERT INTO product_tracker_history "
							+ "(code, budget_qty, budget_value, purchased_qty, purchased_value, "
							+ "min_stock_qty, max_stock_qty, moq, lead_time, schedule, stock_in_hand, "
							+ "stock_in_hand_value, status, remarks, date, created_at) "
							+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())")) {

						ps.setString(1, dto.getCode());
						ps.setBigDecimal(2, dto.getBudget_qty());
						ps.setBigDecimal(3, dto.getBudget_value());
						ps.setObject(4, dto.getPurchsed_qty());
						ps.setObject(5, dto.getPurchsed_value());
						ps.setObject(6, dto.getMin_stock_qty());
						ps.setObject(7, dto.getMax_stock_qty());
						ps.setObject(8, dto.getMoq());
						ps.setObject(9, dto.getLead_time());
						ps.setInt(10, dto.getSchedule());
						ps.setObject(11, dto.getStock_in_hand());
						ps.setInt(12, dto.getStock_in_hand_value());
						ps.setString(13, dto.getStatus());
						ps.setString(14, dto.getRemarks());
						ps.setDate(15, dto.getDate() != null ? new java.sql.Date(dto.getDate().getTime()) : null);
						ps.executeUpdate();
					}
					status += " + Inserted Child";
				}

				row.createCell(statusColIdx).setCellValue(status);
			}

			workbook.write(outputStream);
			return outputStream.toByteArray();

		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Upload failed: " + e.getMessage());
		}
	}

	private purchaseDto mapRowToDto(Row row, DataFormatter formatter) {
		purchaseDto dto = new purchaseDto();

		dto.setCategory(formatter.formatCellValue(row.getCell(0)).trim());
		dto.setSub_category(formatter.formatCellValue(row.getCell(1)).trim());
		dto.setCode(formatter.formatCellValue(row.getCell(2)).trim());
		dto.setProduct_name(formatter.formatCellValue(row.getCell(3)).trim());
		dto.setSupplier(formatter.formatCellValue(row.getCell(4)).trim());

		dto.setBudget_qty(toBigDecimalSafe(row.getCell(5))); // fixed: use toBigDecimalSafe
		dto.setBudget_value(toBigDecimalSafe(row.getCell(6)));
		dto.setPurchsed_qty(toIntSafe(row.getCell(7))); // spelling fixed
		dto.setPurchsed_value(toIntSafe(row.getCell(8)));
		dto.setMin_stock_qty(toIntSafe(row.getCell(9)));
		dto.setMax_stock_qty(toIntSafe(row.getCell(10)));
		dto.setMoq(toIntSafe(row.getCell(11)));
		dto.setLead_time(toIntSafe(row.getCell(12)));
		BigDecimal value1 = toBigDecimalSafe(row.getCell(12));
		dto.setSchedule(value1 != null ? value1.intValue() : 0);
		dto.setStock_in_hand(toIntSafe(row.getCell(14)));
		BigDecimal value = toBigDecimalSafe(row.getCell(15));
		dto.setStock_in_hand_value(value != null ? value.intValue() : 0);
		dto.setStatus(formatter.formatCellValue(row.getCell(16)).trim());
		dto.setRemarks(formatter.formatCellValue(row.getCell(17)).trim());

		try {
			Cell dateCell = row.getCell(18);
			if (dateCell != null) {
				if (dateCell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(dateCell)) {
					dto.setDate(Date.valueOf(dateCell.getLocalDateTimeCellValue().toLocalDate()));
				} else {
					String dateStr = formatter.formatCellValue(dateCell).trim();
					if (!dateStr.isEmpty()) {
						dto.setDate(Date.valueOf(LocalDate.parse(dateStr)));
					}
				}
			}
		} catch (Exception e) {
			dto.setDate(null);
		}

		return dto;
	}

	private BigDecimal toBigDecimalSafe(Cell cell) {
		try {
			if (cell == null || cell.getCellType() == CellType.BLANK)
				return BigDecimal.ZERO;
			return new BigDecimal(cell.getNumericCellValue());
		} catch (Exception e) {
			return BigDecimal.ZERO;
		}
	}

	private Integer toIntSafe(Cell cell) {
		try {
			if (cell == null || cell.getCellType() == CellType.BLANK)
				return 0;
			return (int) cell.getNumericCellValue();
		} catch (Exception e) {
			return 0;
		}
	}

	@Override
	public Map<String, Object> searchFilter(SearchKeys search) {
		Map<String, Object> response = new HashMap<>();
		List<purchaseDto> purchaseList = new ArrayList<>();
		String database = "sri_krishna_db";

		int page = Math.max(1, search.getPage());
		int limit = search.getLimit();
		int offset = (page - 1) * limit;

		HikariDataSource dataSource = null;

		try (Connection conn = (dataSource = customDataSource.dynamicDatabaseChange(database)).getConnection()) {
			List<String> conditions = new ArrayList<>();
			List<Object> params = new ArrayList<>();

			if (search.getCategory() != null && !search.getCategory().isBlank()) {
				conditions.add("pt.category = ?");
				params.add(search.getCategory().trim());
			}
			if (search.getSubcategory() != null && !search.getSubcategory().isBlank()) {
				conditions.add("pt.sub_category = ?");
				params.add(search.getSubcategory().trim());
			}
			if (search.getSupplier() != null && !search.getSupplier().isBlank()) {
				conditions.add("pt.supplier = ?");
				params.add(search.getSupplier().trim());
			}
			if (search.getStatus() != null && !search.getStatus().isBlank()) {
				conditions.add("ph.status = ?");
				params.add(search.getStatus().trim());
			}
			if (search.getDate() != null) {
				conditions.add("GREATEST(COALESCE(created_at, '1900-01-01'), COALESCE(updated_at, '1900-01-01'))::date =?");
				LocalDate localDate = LocalDate.parse(search.getDate());
				params.add(Date.valueOf(localDate));
			}

			String whereClause = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);

			// Count query (only count distinct product_tracker)
			String countSql = """
				    SELECT COUNT(*)
				    FROM product_tracker pt
				    LEFT JOIN (
				        SELECT DISTINCT ON (code) *
				        FROM (
				            SELECT *,
				                   GREATEST(COALESCE(created_at, '1900-01-01'), COALESCE(updated_at, '1900-01-01')) AS effective_date
				            FROM product_tracker_history
				        ) sub
				        ORDER BY code, effective_date DESC
				    ) ph ON pt.code = ph.code""" + whereClause;


			int total = 0;
			try (PreparedStatement ps = conn.prepareStatement(countSql)) {
				for (int i = 0; i < params.size(); i++) {
					ps.setObject(i + 1, params.get(i));
				}
				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next())
						total = rs.getInt(1);
				}
			}

			// Select query
			String sql = """
				    SELECT pt.id, pt.category, pt.sub_category, pt.code, pt.product_name, pt.supplier,
				           ph.budget_qty, ph.budget_value, ph.purchased_qty, ph.purchased_value,
				           ph.min_stock_qty, ph.max_stock_qty, ph.moq, ph.lead_time, ph.schedule,
				           ph.stock_in_hand, ph.stock_in_hand_value, ph.status, ph.remarks, ph.date,
				           ph.created_at, ph.updated_at
				    FROM product_tracker pt
				    LEFT JOIN (
				        SELECT DISTINCT ON (code) *
				        FROM (
				            SELECT *,
				                   GREATEST(COALESCE(created_at, '1900-01-01'), COALESCE(updated_at, '1900-01-01')) AS effective_date
				            FROM product_tracker_history
				        ) sub
				        ORDER BY code, effective_date DESC
				    ) ph ON pt.code = ph.code"""+whereClause+" ORDER BY pt.id  LIMIT ? OFFSET ?";


			try (PreparedStatement ps = conn.prepareStatement(sql)) {
				int idx = 1;
				for (Object param : params) {
					ps.setObject(idx++, param);
				}
				ps.setInt(idx++, limit);
				ps.setInt(idx, offset);

				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) {
						purchaseDto dto = new purchaseDto();

						dto.setId(rs.getLong("id"));
						dto.setCategory(rs.getString("category"));
						dto.setSub_category(rs.getString("sub_category"));
						dto.setCode(rs.getString("code"));
						dto.setProduct_name(rs.getString("product_name"));
						dto.setSupplier(rs.getString("supplier"));

						dto.setBudget_qty(rs.getBigDecimal("budget_qty"));
						dto.setBudget_value(rs.getBigDecimal("budget_value"));
						dto.setMin_stock_qty(rs.getObject("min_stock_qty", Integer.class));
						dto.setMax_stock_qty(rs.getObject("max_stock_qty", Integer.class));
						dto.setMoq(rs.getObject("moq", Integer.class));
						dto.setLead_time(rs.getObject("lead_time", Integer.class));
						dto.setSchedule(rs.getInt("schedule"));
						dto.setStatus(rs.getString("status"));
						dto.setRemarks(rs.getString("remarks"));
						dto.setDate(rs.getDate("date"));

						Date createdAt = rs.getDate("created_at");
						Date updatedAt = rs.getDate("updated_at");
						dto.setCreated_at(createdAt);
						dto.setUpdated_at(updatedAt);

						boolean	isShowQty =false;
							LocalDate today = LocalDate.now();
								isShowQty = (createdAt != null && createdAt.toLocalDate().isEqual(today))
									|| (updatedAt != null && updatedAt.toLocalDate().isEqual(today));
						
						if (isShowQty && search.getDate() != null) {
							dto.setPurchsed_qty(rs.getObject("purchased_qty", Integer.class));
							dto.setPurchsed_value(rs.getObject("purchased_value", Integer.class));
							dto.setStock_in_hand(rs.getObject("stock_in_hand", Integer.class));
							BigDecimal stockValue = rs.getBigDecimal("stock_in_hand_value");
							dto.setStock_in_hand_value(stockValue != null ? stockValue.intValue() : null);

						}else if (!isShowQty && search.getDate() != null) {
							dto.setPurchsed_qty(rs.getObject("purchased_qty", Integer.class));
							dto.setPurchsed_value(rs.getObject("purchased_value", Integer.class));
							dto.setStock_in_hand(rs.getObject("stock_in_hand", Integer.class));
							BigDecimal stockValue = rs.getBigDecimal("stock_in_hand_value");
							dto.setStock_in_hand_value(stockValue != null ? stockValue.intValue() : null);
						}else if (isShowQty) {
							dto.setPurchsed_qty(rs.getObject("purchased_qty", Integer.class));
							dto.setPurchsed_value(rs.getObject("purchased_value", Integer.class));
							dto.setStock_in_hand(rs.getObject("stock_in_hand", Integer.class));
							BigDecimal stockValue = rs.getBigDecimal("stock_in_hand_value");
							dto.setStock_in_hand_value(stockValue != null ? stockValue.intValue() : null);	
						}else {
							
						}

						purchaseList.add(dto);
					}
				}
			}

			// Upload timestamps
			String uploadTimeSql = """
					    SELECT
					        MAX(purchaseupload_at) AS latest_purchaseupload_at,
					        MAX(stockupload_at) AS latest_stockupload_at
					    FROM product_tracker_history
					""";

			try (PreparedStatement uploadStmt = conn.prepareStatement(uploadTimeSql);
					ResultSet uploadRs = uploadStmt.executeQuery()) {
				if (uploadRs.next()) {
					response.put("latest_purchaseupload_at", uploadRs.getTimestamp("latest_purchaseupload_at"));
					response.put("latest_stockupload_at", uploadRs.getTimestamp("latest_stockupload_at"));
				}
			}

			int totalPages = limit > 0 ? (int) Math.ceil((double) total / limit) : 1;

			response.put("purchaseList", purchaseList);
			response.put("total", total);
			response.put("limit", limit);
			response.put("currentPage", page);
			response.put("totalPages", totalPages);
			response.put("result", true);

		} catch (Exception e) {
			e.printStackTrace();
			response.put("result", false);
			response.put("message", "❌ Error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		} finally {
			if (dataSource != null)
				dataSource.close();
		}

		return response;
	}

	@Override
	public Map<String, Object> statusUpdate(PurchaseStatusUpdate purchase) {
		Map<String, Object> response = new HashMap<>();

		String database = "sri_krishna_db";
		HikariDataSource newDataSource = null;
		Connection conn = null;
		PreparedStatement stmt = null;

		try {
			newDataSource = customDataSource.dynamicDatabaseChange(database);
			conn = newDataSource.getConnection();

			String sql = "UPDATE product_tracker_history SET status = ?, remarks = ?, date = ? WHERE id = ?";
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
		String[] headers = { "Code", "Stock in Hand", "Stock in hand value" };

		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet("Stock");

			// Header style
			CellStyle headerStyle = workbook.createCellStyle();
			Font font = workbook.createFont();
			font.setBold(true);
			font.setColor(IndexedColors.WHITE.getIndex());
			headerStyle.setFont(font);
			headerStyle.setFillForegroundColor(IndexedColors.SEA_GREEN.getIndex());
			headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			headerStyle.setAlignment(HorizontalAlignment.CENTER);

			Row headerRow = sheet.createRow(0);
			for (int i = 0; i < headers.length; i++) {
				Cell cell = headerRow.createCell(i);
				cell.setCellValue(headers[i]);
				cell.setCellStyle(headerStyle);
				sheet.autoSizeColumn(i);
			}

			workbook.write(out);
			return out.toByteArray();
		} catch (IOException | java.io.IOException e) {
			throw new RuntimeException("Failed to generate Stock template: " + e.getMessage(), e);
		}
	}

	@Override
	public byte[] getSamplePurcahsedTemplate() {
		String[] headers = {  "Code", "Purcahsed Qty", "Purcahsed Value" };
		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet("Purchased");
			// Create header style
			CellStyle headerStyle = workbook.createCellStyle();
			Font font = workbook.createFont();
			font.setBold(true);
			font.setColor(IndexedColors.WHITE.getIndex());
			font.setFontHeightInPoints((short) 11);
			headerStyle.setFont(font);
			headerStyle.setFillForegroundColor(IndexedColors.SEA_GREEN.getIndex());
			headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			headerStyle.setAlignment(HorizontalAlignment.CENTER);

			// Create header row
			Row headerRow = sheet.createRow(0);
			for (int i = 0; i < headers.length; i++) {
				Cell cell = headerRow.createCell(i);
				cell.setCellValue(headers[i]);
				cell.setCellStyle(headerStyle);
				sheet.autoSizeColumn(i);
			}

			workbook.write(out);
			return out.toByteArray();
		} catch (IOException | java.io.IOException e) {
			throw new RuntimeException("Failed to generate Purchased template: " + e.getMessage(), e);
		}
	}

	@Override
	public byte[] uploadPurcahseExcel(InputStream inputStream) {
		String database = "sri_krishna_db";
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		try (Workbook workbook = new XSSFWorkbook(inputStream);
				HikariDataSource orgDataSource = customDataSource.dynamicDatabaseChange(database);
				Connection conn = orgDataSource.getConnection()) {

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

				try (PreparedStatement selectStmt = conn.prepareStatement(
						"""
								    SELECT * FROM product_tracker_history
								    WHERE code = ?
								    ORDER BY GREATEST(COALESCE(created_at, '1900-01-01'), COALESCE(updated_at, '1900-01-01')) DESC
								    LIMIT 1
								""")) {
					selectStmt.setString(1, code);
					try (ResultSet rs = selectStmt.executeQuery()) {
						if (rs.next()) {
							Date createdAt = rs.getDate("created_at");
							Date updatedAt = rs.getDate("updated_at");
							LocalDate today = LocalDate.now();
							boolean isToday = (createdAt != null && createdAt.toLocalDate().isEqual(today))
									|| (updatedAt != null && updatedAt.toLocalDate().isEqual(today));

							if (isToday) {
								try (PreparedStatement updateStmt = conn.prepareStatement(
										"""
												    UPDATE product_tracker_history
												    SET purchased_qty = ?, purchased_value = ?, updated_at = now(), purchaseupload_at = now()
												    WHERE id = ?
												""")) {
									updateStmt.setDouble(1, qty);
									updateStmt.setDouble(2, value);
									updateStmt.setInt(3, rs.getInt("id"));
									updateStmt.executeUpdate();
									row.createCell(headerRow.getLastCellNum()).setCellValue("Updated");
								}
							} else {
								try (PreparedStatement insertStmt = conn.prepareStatement("""
										    INSERT INTO product_tracker_history (
										        code, budget_qty, budget_value, purchased_qty, purchased_value,
										        min_stock_qty, max_stock_qty, moq, lead_time, schedule,
										        stock_in_hand, stock_in_hand_value, status, remarks,
										        date, created_at,purchaseupload_at
										    )
										    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, current_date, now(), now())
										""")) {
									insertStmt.setString(1, code);
									insertStmt.setBigDecimal(2, rs.getBigDecimal("budget_qty"));
									insertStmt.setBigDecimal(3, rs.getBigDecimal("budget_value"));
									insertStmt.setDouble(4, qty);
									insertStmt.setDouble(5, value);
									insertStmt.setObject(6, rs.getObject("min_stock_qty"));
									insertStmt.setObject(7, rs.getObject("max_stock_qty"));
									insertStmt.setObject(8, rs.getObject("moq"));
									insertStmt.setObject(9, rs.getObject("lead_time"));
									insertStmt.setString(10, rs.getString("schedule"));
									insertStmt.setObject(11, rs.getObject("stock_in_hand"));
									insertStmt.setObject(12, rs.getBigDecimal("stock_in_hand_value"));
									insertStmt.setString(13, rs.getString("status"));
									insertStmt.setString(14, rs.getString("remarks"));
									insertStmt.executeUpdate();
									row.createCell(headerRow.getLastCellNum()).setCellValue("Inserted");
								}

							}
						} else {
							row.createCell(headerRow.getLastCellNum()).setCellValue("Code not found");
						}
					}
				} catch (SQLException e) {
					row.createCell(headerRow.getLastCellNum()).setCellValue("Error: " + e.getMessage());
				}
			}

			workbook.write(out);
			return out.toByteArray();

		} catch (Exception e) {
			return out.toByteArray();
		}
	}

	@Override
	public byte[] uploadStocksExcel(InputStream inputStream) {
		String database = "sri_krishna_db";
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		try (Workbook workbook = new XSSFWorkbook(inputStream);
				HikariDataSource orgDataSource = customDataSource.dynamicDatabaseChange(database);
				Connection conn = orgDataSource.getConnection()) {

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

				try (PreparedStatement selectStmt = conn.prepareStatement(
						"""
								    SELECT * FROM product_tracker_history
								    WHERE code = ?
								    ORDER BY GREATEST(COALESCE(created_at, '1900-01-01'), COALESCE(updated_at, '1900-01-01')) DESC
								    LIMIT 1
								""")) {
					selectStmt.setString(1, code);
					try (ResultSet rs = selectStmt.executeQuery()) {
						if (rs.next()) {
							Date createdAt = rs.getDate("created_at");
							Date updatedAt = rs.getDate("updated_at");
							LocalDate today = LocalDate.now();
							boolean isToday = (createdAt != null && createdAt.toLocalDate().isEqual(today))
									|| (updatedAt != null && updatedAt.toLocalDate().isEqual(today));

							if (isToday) {
								try (PreparedStatement updateStmt = conn.prepareStatement(
										"""
												    UPDATE product_tracker_history
												    SET stock_in_hand = ?, stock_in_hand_value = ?, updated_at = now(), stockupload_at = now()
												    WHERE id = ?
												""")) {
									updateStmt.setDouble(1, stockQty);
									updateStmt.setDouble(2, stockValue);
									updateStmt.setInt(3, rs.getInt("id"));
									updateStmt.executeUpdate();
									row.createCell(headerRow.getLastCellNum()).setCellValue("Updated");
								}
							} else {
								try (PreparedStatement insertStmt = conn.prepareStatement("""
										    INSERT INTO product_tracker_history (
										        code, budget_qty, budget_value, purchased_qty, purchased_value,
										        min_stock_qty, max_stock_qty, moq, lead_time, schedule,
										        stock_in_hand, stock_in_hand_value, status, remarks,
										        date, created_at,stockupload_at
										    )
										    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, current_date, now(), now())
										""")) {
									insertStmt.setString(1, code);
									insertStmt.setBigDecimal(2, rs.getBigDecimal("budget_qty"));
									insertStmt.setBigDecimal(3, rs.getBigDecimal("budget_value"));
									insertStmt.setDouble(4, stockQty);
									insertStmt.setDouble(5, stockValue);
									insertStmt.setObject(6, rs.getObject("min_stock_qty"));
									insertStmt.setObject(7, rs.getObject("max_stock_qty"));
									insertStmt.setObject(8, rs.getObject("moq"));
									insertStmt.setObject(9, rs.getObject("lead_time"));
									insertStmt.setString(10, rs.getString("schedule"));
									insertStmt.setObject(11, rs.getObject("stock_in_hand"));
									insertStmt.setObject(12, rs.getBigDecimal("stock_in_hand_value"));
									insertStmt.setString(13, rs.getString("status"));
									insertStmt.setString(14, rs.getString("remarks"));
									insertStmt.executeUpdate();
									row.createCell(headerRow.getLastCellNum()).setCellValue("Inserted");
								}

							}
						} else {
							row.createCell(headerRow.getLastCellNum()).setCellValue("Code not found");
						}
					}
				} catch (SQLException e) {
					row.createCell(headerRow.getLastCellNum()).setCellValue("Error: " + e.getMessage());
				}
			}

			workbook.write(out);
			return out.toByteArray();

		} catch (Exception e) {
			return out.toByteArray();
		}
	}

	@Override
	public List<String> getSuggestions(String type, String query) {
		List<String> suggestions = new ArrayList<>();
		Set<String> allowed = Set.of("category", "sub_category", "supplier", "status");

		if (!allowed.contains(type)) {
			throw new IllegalArgumentException("Invalid type: " + type);
		}

		String database = "sri_krishna_db";
		HikariDataSource ds = null;
		try {
			ds = customDataSource.dynamicDatabaseChange(database);
			try (Connection conn = ds.getConnection()) {
				String sql = "SELECT DISTINCT " + type + " FROM product_tracker "
						+ (query == null || query.trim().isEmpty() ? "" : "WHERE LOWER(" + type + ") LIKE LOWER(?) ")
						+ "ORDER BY " + type + " LIMIT 10";

				try (PreparedStatement ps = conn.prepareStatement(sql)) {
					if (query != null && !query.trim().isEmpty()) {
						ps.setString(1, "%" + query.trim() + "%");
					}
					try (ResultSet rs = ps.executeQuery()) {
						while (rs.next()) {
							String value = rs.getString(1);
							if (value != null && !value.trim().isEmpty()) {
								suggestions.add(value.trim());
							}
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (ds != null)
				ds.close();
		}

		return suggestions;
	}

	@Override
	public Map<String, Object> getSummary(SearchKeys search) {
		Map<String, Object> response = new HashMap<>();
		List<purchaseDto> purchaseList = new ArrayList<>();

		String database = "sri_krishna_db";
		HikariDataSource newDataSource = null;
		Connection conn = null;
		ResultSet rs = null;

		try {
			newDataSource = customDataSource.dynamicDatabaseChange(database);
			conn = newDataSource.getConnection();

			List<String> conditions = new ArrayList<>();
			List<Object> params = new ArrayList<>();

			if (search.getCategory() != null && !search.getCategory().isBlank()) {
				conditions.add("pt.category = ?");
				params.add(search.getCategory().trim());
			}
			if (search.getSubcategory() != null && !search.getSubcategory().isBlank()) {
				conditions.add("pt.sub_category = ?");
				params.add(search.getSubcategory().trim());
			}
			if (search.getSupplier() != null && !search.getSupplier().isBlank()) {
				conditions.add("pt.supplier = ?");
				params.add(search.getSupplier().trim());
			}
			if (search.getStatus() != null && !search.getStatus().isBlank()) {
				conditions.add("ph.status = ?");
				params.add(search.getStatus().trim());
			}
			if (search.getDate() != null) {
				conditions.add("GREATEST(COALESCE(created_at, '1900-01-01'), COALESCE(updated_at, '1900-01-01'))::date =?");
				LocalDate localDate = LocalDate.parse(search.getDate());
				params.add(Date.valueOf(localDate));
			}

			String whereClause = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);

			String sql = """
				    SELECT pt.id, pt.category, pt.sub_category, pt.code, pt.product_name, pt.supplier,
				           ph.budget_qty, ph.budget_value, ph.purchased_qty, ph.purchased_value,
				           ph.min_stock_qty, ph.max_stock_qty, ph.moq, ph.lead_time, ph.schedule,
				           ph.stock_in_hand, ph.stock_in_hand_value, ph.status, ph.remarks,
				           ph.created_at, ph.updated_at,
				           GREATEST(COALESCE(ph.created_at, '1900-01-01'), COALESCE(ph.updated_at, '1900-01-01')) AS effective_date
				    FROM product_tracker pt
				    LEFT JOIN (
				        SELECT DISTINCT ON (code) *
				        FROM product_tracker_history
				        ORDER BY code, GREATEST(COALESCE(created_at, '1900-01-01'), COALESCE(updated_at, '1900-01-01')) DESC
				    ) ph ON pt.code = ph.code
				    """
				    + whereClause + " ORDER BY pt.id DESC";


			try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
				for (int i = 0; i < params.size(); i++) {
					pstmt.setObject(i + 1, params.get(i));
				}

				rs = pstmt.executeQuery();
				while (rs.next()) {
					purchaseDto model = new purchaseDto();
					model.setCode(rs.getString("code"));
					model.setSupplier(rs.getString("supplier"));
					model.setStatus(rs.getString("status"));
					model.setBudget_qty(rs.getBigDecimal("budget_qty"));
					model.setBudget_value(rs.getBigDecimal("budget_value"));
					model.setMin_stock_qty(rs.getInt("min_stock_qty"));
					model.setMax_stock_qty(rs.getInt("max_stock_qty"));
					model.setMoq(rs.getInt("moq"));
					Date createdAt = rs.getDate("created_at");
					Date updatedAt = rs.getDate("updated_at");
					model.setCreated_at(createdAt);
					model.setUpdated_at(updatedAt);

					LocalDate today = LocalDate.now();
					boolean isShowQty = (createdAt != null && createdAt.toLocalDate().isEqual(today))
							|| (updatedAt != null && updatedAt.toLocalDate().isEqual(today));

					if (isShowQty && search.getDate() != null) {
						model.setPurchsed_qty(rs.getObject("purchased_qty", Integer.class));
						model.setPurchsed_value(rs.getObject("purchased_value", Integer.class));
						model.setStock_in_hand(rs.getObject("stock_in_hand", Integer.class));
						BigDecimal stockValue = rs.getBigDecimal("stock_in_hand_value");
						model.setStock_in_hand_value(stockValue != null ? stockValue.intValue() : null);

					}else if (!isShowQty && search.getDate() != null) {
						model.setPurchsed_qty(rs.getObject("purchased_qty", Integer.class));
						model.setPurchsed_value(rs.getObject("purchased_value", Integer.class));
						model.setStock_in_hand(rs.getObject("stock_in_hand", Integer.class));
						BigDecimal stockValue = rs.getBigDecimal("stock_in_hand_value");
						model.setStock_in_hand_value(stockValue != null ? stockValue.intValue() : null);
					}else if (isShowQty) {
						model.setPurchsed_qty(rs.getObject("purchased_qty", Integer.class));
						model.setPurchsed_value(rs.getObject("purchased_value", Integer.class));
						model.setStock_in_hand(rs.getObject("stock_in_hand", Integer.class));
						BigDecimal stockValue = rs.getBigDecimal("stock_in_hand_value");
						model.setStock_in_hand_value(stockValue != null ? stockValue.intValue() : null);	
					}else {
						
					}

					purchaseList.add(model);
				}
			}

			// --- Same summary logic remains ---
			double totalBudget = 0, totalPurchased = 0, stockValue = 0;
			double unplanned = 0, shortfall = 0;
			int nilStock = 0, belowMinMOQ = 0, skuMin = 0, excessStock = 0;

			Set<String> supplierSet = new HashSet<>();
			Set<String> productSet = new HashSet<>();

			for (purchaseDto dto : purchaseList) {
				BigDecimal budQty = Optional.ofNullable(dto.getBudget_qty()).orElse(BigDecimal.ZERO);
				BigDecimal budVal = Optional.ofNullable(dto.getBudget_value()).orElse(BigDecimal.ZERO);
				int purQty = Optional.ofNullable(dto.getPurchsed_qty()).orElse(0);
				int purVal = Optional.ofNullable(dto.getPurchsed_value()).orElse(0);
				int stock = Optional.ofNullable(dto.getStock_in_hand()).orElse(0);
				int min = Optional.ofNullable(dto.getMin_stock_qty()).orElse(0);
				int max = Optional.ofNullable(dto.getMax_stock_qty()).orElse(0);
				int moq = Optional.ofNullable(dto.getMoq()).orElse(0);

				BigDecimal rate = (budQty.compareTo(BigDecimal.ZERO) > 0)
						? budVal.divide(budQty, 2, RoundingMode.HALF_UP)
						: BigDecimal.ZERO;

				totalBudget += budVal.doubleValue();
				totalPurchased += purVal;
				stockValue += rate.multiply(BigDecimal.valueOf(stock)).doubleValue();

				if (budQty.compareTo(BigDecimal.ZERO) == 0 && purVal > 0) {
					unplanned += purVal;
				}

				if (budQty.compareTo(BigDecimal.valueOf(purQty)) > 0) {
					BigDecimal shortfallQty = budQty.subtract(BigDecimal.valueOf(purQty));
					shortfall += rate.multiply(shortfallQty).doubleValue();
				}

				if (stock == 0)
					nilStock++;
				if (stock <= min && min > 0)
					skuMin++;
				if (moq > 0 && stock < moq && stock <= min)
					belowMinMOQ++;
				if (max > 0 && stock > max)
					excessStock++;

				Optional.ofNullable(dto.getSupplier()).ifPresent(supplierSet::add);
				Optional.ofNullable(dto.getCode()).ifPresent(productSet::add);
			}

			int utilization = (totalBudget > 0) ? (int) (totalPurchased * 100 / totalBudget) : 0;
			int planVsActual = utilization;

			Map<String, Integer> statusCounts = new HashMap<>();
			statusCounts.put("PO Pending", 0);
			statusCounts.put("PO Raised", 0);
			statusCounts.put("Vendor Issue", 0);
			statusCounts.put("Payment Issue", 0);
			statusCounts.put("Stock in Transit", 0);

			for (purchaseDto dto : purchaseList) {
				String status = Optional.ofNullable(dto.getStatus()).orElse("").trim();
				if (statusCounts.containsKey(status)) {
					statusCounts.put(status, statusCounts.get(status) + 1);
				}
			}

			response.put("statusCounts", statusCounts);
			response.put("totalBudget", totalBudget);
			response.put("totalPurchased", totalPurchased);
			response.put("difference", totalBudget - totalPurchased);
			response.put("utilization", utilization);
			response.put("unplanned", unplanned);
			response.put("shortfall", shortfall);
			response.put("planVsActual", planVsActual);
			response.put("stockValue", stockValue);
			response.put("suppliers", supplierSet.size());
			response.put("products", productSet.size());
			response.put("nilStock", nilStock);
			response.put("skuMin", skuMin);
			response.put("belowMinMOQ", belowMinMOQ);
			response.put("excessStock", excessStock);

		} catch (Exception e) {
			response.put("error", "Database error: " + e.getMessage());
		} finally {
			try {
				if (rs != null)
					rs.close();
				if (conn != null)
					conn.close();
			} catch (Exception ignored) {
			}
		}

		return response;
	}

	@Override
	public Map<String, Object> readAndSaveFromFile(MultipartFile file) {
		Map<String, Object> response = new HashMap<>();
		String database = "sri_krishna_db";

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

}
