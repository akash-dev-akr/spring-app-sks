package com.akrcode.akr.daoImpl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
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
import com.akrcode.akr.dto.BomCostComparisonDto;
import com.akrcode.akr.dto.BomDetailsDto;
import com.akrcode.akr.dto.FinishedGoodsVariantsDto;
import com.akrcode.akr.dto.PdfReportDto;
import com.akrcode.akr.dto.PurchaseStatusUpdate;
import com.akrcode.akr.dto.SearchKeys;
import com.akrcode.akr.model.ContactModel;
import com.akrcode.akr.model.PurchaseMasterModel;
import com.akrcode.akr.model.SetUsedModel;
import com.zaxxer.hikari.HikariDataSource;

import io.jsonwebtoken.io.IOException;

@Component
public class excelDaoimpl {
	@Autowired
	CustomDataSource customDataSource;

	String[] stocksTemplateHeader = { "Code", "Stock in Hand", "Stock in hand value" };
	String[] purcahsedTemplateHeader = { "Code", "Purcahsed Qty", "Purcahsed Value" };

	public Map<String, Object> readAndSaveAllSheetsFromPath() {
		Map<String, Object> response = new HashMap<>();
		String folderAddress = "D:\\upload";
		String folderPath = folderAddress + "\\fileIn";
		String processedPath = folderAddress + "\\fileOut";
		String database = "test";

		HikariDataSource dataSource = null;
		Connection conn = null;

		try {
			File folder = new File(folderPath);
			if (!folder.exists() || !folder.isDirectory()) {
				response.put("result", false);
				response.put("message", "❌ Folder does not exist: " + folderPath);
				return response;
			}

			File processedFolder = new File(processedPath);
			if (!processedFolder.exists())
				processedFolder.mkdirs();

			File[] excelFiles = folder.listFiles((dir, name) -> name.endsWith(".xlsx") || name.endsWith(".xls"));
			if (excelFiles == null || excelFiles.length == 0) {
				response.put("result", false);
				response.put("message", "❌ No Excel files found.");
				return response;
			}

			dataSource = customDataSource.dynamicDatabaseChange(database);
			conn = dataSource.getConnection();

			for (File file : excelFiles) {
				System.out.println("📄 Processing: " + file.getName());

				try (FileInputStream fis = new FileInputStream(file); Workbook workbook = WorkbookFactory.create(fis)) {

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

							String colName = headerCell.getStringCellValue().replaceAll("[^a-zA-Z0-9_]", "_")
									.toLowerCase();
							if (colName.isBlank() || colName.equalsIgnoreCase("sno"))
								continue; // ✅ skip SNo

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
									case NUMERIC:
										if (DateUtil.isCellDateFormatted(cell)) {
											columnTypeMap.put(colName, "DATE");
										} else {
											double val = cell.getNumericCellValue();
											columnTypeMap.put(colName, (val % 1 == 0) ? "INTEGER" : "NUMERIC");
										}
										break;
									case BOOLEAN:
										columnTypeMap.put(colName, "BOOLEAN");
										break;
									default:
										columnTypeMap.put(colName, "TEXT");
									}
								} catch (Exception e) {
									columnTypeMap.put(colName, "TEXT");
								}
							}
						}

						if (columnTypeMap.isEmpty())
							continue;

						List<String> columns = new ArrayList<>(columnTypeMap.keySet());

						// Create table if not exists
						boolean tableExists = false;
						try (PreparedStatement checkStmt = conn.prepareStatement(
								"SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?)")) {
							checkStmt.setString(1, sheetName);
							ResultSet rs = checkStmt.executeQuery();
							if (rs.next())
								tableExists = rs.getBoolean(1);
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

						// Prepare INSERT SQL
						StringBuilder insertSQL = new StringBuilder("INSERT INTO " + sheetName + " (");
						insertSQL.append(String.join(", ", columns)).append(", created_at) VALUES (");
						insertSQL.append("?,".repeat(columns.size())).append("CURRENT_TIMESTAMP)");
						PreparedStatement pstmt = conn.prepareStatement(insertSQL.toString());

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
									case "INTEGER":
										pstmt.setInt(c + 1, (int) cell.getNumericCellValue());
										break;
									case "NUMERIC":
										pstmt.setDouble(c + 1, cell.getNumericCellValue());
										break;
									case "BOOLEAN":
										pstmt.setBoolean(c + 1, cell.getBooleanCellValue());
										break;
									case "DATE":
										pstmt.setDate(c + 1, new java.sql.Date(cell.getDateCellValue().getTime()));
										break;
									default:
										cell.setCellType(CellType.STRING);
										pstmt.setString(c + 1, cell.getStringCellValue());
									}
								} catch (Exception ex) {
									pstmt.setNull(c + 1, Types.NULL);
								}
							}

							pstmt.executeUpdate();
						}
					}

				} catch (Exception e) {
					e.printStackTrace();
					System.err.println("❌ Error processing file: " + file.getName());
					continue;
				}

				// ✅ Move processed file
				try {
					Path source = file.toPath();
					Path target = Paths.get(processedPath, file.getName());
					Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException ioException) {
					System.err.println("⚠️ Could not move file: " + file.getName());
				}
			}

			response.put("result", true);
			response.put("message", "✅ All Excel files imported successfully.");
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

//	public String readAndSaveAllSheetsFromPath() {
//		String folderPath = "D:/New folder";
//		String processedPath = folderPath + "/processed";
//		String database = "test";
//		HikariDataSource dataSource = null;
//		Connection conn = null;
//
//		try {
//			File folder = new File(folderPath);
//			if (!folder.exists() || !folder.isDirectory()) {
//				return "Error: Folder does not exist at " + folderPath;
//			}
//
//			// Ensure processed folder exists
//			File processedFolder = new File(processedPath);
//			if (!processedFolder.exists()) {
//				processedFolder.mkdirs();
//			}
//
//			File[] excelFiles = folder.listFiles((dir, name) -> name.endsWith(".xlsx") || name.endsWith(".xls"));
//			if (excelFiles == null || excelFiles.length == 0) {
//				return "No Excel files found in the folder.";
//			}
//
//			dataSource = customDataSource.dynamicDatabaseChange(database);
//			conn = dataSource.getConnection();
//
//			for (File file : excelFiles) {
//				Workbook workbook = WorkbookFactory.create(file);
//
//				for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
//					Sheet sheet = workbook.getSheetAt(i);
//					String sheetName = sheet.getSheetName().replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
//
//					Row headerRow = sheet.getRow(0);
//					if (headerRow == null || headerRow.getLastCellNum() == 0)
//						continue;
//
//					Map<String, String> columnTypeMap = new LinkedHashMap<>();
//					int maxTypeScanRows = Math.min(sheet.getLastRowNum(), 10);
//
//					for (int c = 0; c < headerRow.getLastCellNum(); c++) {
//						Cell headerCell = headerRow.getCell(c);
//						if (headerCell == null)
//							continue;
//
//						String colName = headerCell.getStringCellValue().replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
//						if (colName.isBlank())
//							continue;
//
//						columnTypeMap.put(colName, "TEXT");
//
//						for (int r = 1; r <= maxTypeScanRows; r++) {
//							Row row = sheet.getRow(r);
//							if (row == null)
//								continue;
//							Cell cell = row.getCell(c);
//							if (cell == null)
//								continue;
//
//							switch (cell.getCellType()) {
//							case NUMERIC:
//								if (DateUtil.isCellDateFormatted(cell)) {
//									columnTypeMap.put(colName, "DATE");
//								} else {
//									double val = cell.getNumericCellValue();
//									if (val % 1 == 0) {
//										columnTypeMap.put(colName, "INTEGER");
//									} else {
//										columnTypeMap.put(colName, "NUMERIC");
//									}
//								}
//								break;
//							case BOOLEAN:
//								columnTypeMap.put(colName, "BOOLEAN");
//								break;
//							default:
//								columnTypeMap.put(colName, "TEXT");
//							}
//						}
//					}
//
//					if (columnTypeMap.isEmpty())
//						continue;
//
//					List<String> columns = new ArrayList<>(columnTypeMap.keySet());
//
//					boolean tableExists = false;
//					try (PreparedStatement checkStmt = conn.prepareStatement(
//							"SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?)")) {
//						checkStmt.setString(1, sheetName);
//						ResultSet rs = checkStmt.executeQuery();
//						if (rs.next())
//							tableExists = rs.getBoolean(1);
//					}
//
//					if (!tableExists) {
//						StringBuilder createSQL = new StringBuilder("CREATE TABLE " + sheetName + " (");
//						for (Map.Entry<String, String> entry : columnTypeMap.entrySet()) {
//							createSQL.append(entry.getKey()).append(" ").append(entry.getValue()).append(", ");
//						}
//						createSQL.setLength(createSQL.length() - 2);
//						createSQL.append(")");
//						conn.createStatement().execute(createSQL.toString());
//					}
//
//					StringBuilder insertSQL = new StringBuilder("INSERT INTO " + sheetName + " (");
//					insertSQL.append(String.join(", ", columns)).append(") VALUES (");
//					insertSQL.append("?,".repeat(columns.size()));
//					insertSQL.setLength(insertSQL.length() - 1);
//					insertSQL.append(")");
//
//					PreparedStatement pstmt = conn.prepareStatement(insertSQL.toString());
//
//					for (int r = 1; r <= sheet.getLastRowNum(); r++) {
//						Row row = sheet.getRow(r);
//						if (row == null)
//							continue;
//
//						for (int c = 0; c < columns.size(); c++) {
//							Cell cell = row.getCell(c);
//							String colType = columnTypeMap.get(columns.get(c));
//
//							if (cell == null) {
//								pstmt.setNull(c + 1, java.sql.Types.NULL);
//								continue;
//							}
//
//							switch (colType) {
//							case "INTEGER":
//								pstmt.setInt(c + 1, (int) cell.getNumericCellValue());
//								break;
//							case "NUMERIC":
//								pstmt.setDouble(c + 1, cell.getNumericCellValue());
//								break;
//							case "BOOLEAN":
//								pstmt.setBoolean(c + 1, cell.getBooleanCellValue());
//								break;
//							case "DATE":
//								pstmt.setDate(c + 1, new java.sql.Date(cell.getDateCellValue().getTime()));
//								break;
//							default:
//								cell.setCellType(CellType.STRING);
//								pstmt.setString(c + 1, cell.getStringCellValue());
//							}
//						}
//						pstmt.executeUpdate();
//					}
//				}
//
//				// Move file after successful processing
//				try {
//					Path source = file.toPath();
//					Path target = Paths.get(processedPath, file.getName());
//					Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
//				} catch (IOException moveEx) {
//					System.err.println("Failed to move file " + file.getName() + ": " + moveEx.getMessage());
//				}
//			}
//
//			return "All Excel files and sheets processed successfully.";
//		} catch (Exception e) {
//			e.printStackTrace();
//			return "Error: " + e.getMessage();
//		} finally {
//			try {
//				if (conn != null)
//					conn.close();
//				if (dataSource != null)
//					dataSource.close();
//			} catch (Exception ignored) {
//			}
//		}
//	}

	public ContactModel getExcel(Long id) {

		return null;
	}

	public Map<String, Object> searchPurchaseFilter(SearchKeys test) {
		Map<String, Object> response = new HashMap<>();
		List<SetUsedModel> SetUsedModelList = new ArrayList<>();
		String database = "test";
		HikariDataSource newDataSource = null;
		Connection conn = null;
		Statement stmt = null;
		ResultSet rs = null;

		try {
			newDataSource = customDataSource.dynamicDatabaseChange(database);
			conn = newDataSource.getConnection();
			stmt = conn.createStatement();
			String sql = "SELECT ROW_NUMBER() OVER (ORDER BY bom_code) AS id,bom_code,bom_name,set_uom,SUM(used_set_qty) AS used_set_qty\r\n"
					+ "FROM set_used GROUP BY bom_code, bom_name, set_uom;\r\n" + "";
			rs = stmt.executeQuery(sql);

			while (rs.next()) {
				SetUsedModel setUsedModel = new SetUsedModel();
				setUsedModel.setId(rs.getLong("id"));
				setUsedModel.setBom_code(rs.getString("bom_code"));
				setUsedModel.setBom_name(rs.getString("bom_name"));
				setUsedModel.setSet_uom(rs.getString("set_uom"));
				setUsedModel.setUsed_set_qty(rs.getInt("used_set_qty"));
				SetUsedModelList.add(setUsedModel);
			}
		} catch (Exception e) {
			e.printStackTrace();
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
		response.put("setUsedList", SetUsedModelList);
		response.put("result", true);
		return response;
	}

	public PdfReportDto getExcelPdfvalues(String bomCode) {
		PdfReportDto contact = null;
		String database = "test";
		HikariDataSource newDataSource = null;
		Connection conn = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;

		try {
			newDataSource = customDataSource.dynamicDatabaseChange(database);
			conn = newDataSource.getConnection();

			String sql = "SELECT * FROM set_used WHERE bom_code ILIKE ?";
			stmt = conn.prepareStatement(sql);
			stmt.setString(1, bomCode);
			rs = stmt.executeQuery();

			if (rs.next()) {
				contact = new PdfReportDto();
				contact.setBom_code(rs.getString("bom_code"));
				contact.setProduct(rs.getString("bom_name"));

				// Safe population of fields
				List<BomCostComparisonDto> costComparisonList = bomCostComparison(bomCode);
				contact.setBom_cost_comparison(costComparisonList);
				contact.setBom_details(bomDetails(bomCode));
				if (!costComparisonList.isEmpty()) {
					contact.setFinished_goods_variants(finishedGoodsVariants(costComparisonList.get(0).getId()));
				} else {
					contact.setFinished_goods_variants(new ArrayList<>());
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
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

		return contact;
	}

	public List<FinishedGoodsVariantsDto> finishedGoodsVariants(String bomCode) {
		List<FinishedGoodsVariantsDto> contactlist = new ArrayList<>();
		String database = "test";

		HikariDataSource newDataSource = null;
		Connection conn = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;

		try {
			newDataSource = customDataSource.dynamicDatabaseChange(database);
			conn = newDataSource.getConnection();

			String sql = "SELECT bm.fp_name, bm.input_code, SUM(bm.input_capacity) AS total_capacity, "
					+ "SUM(ba.input_qty) AS total_actual_qty, "
					+ "SUM(bm.input_capacity) - SUM(ba.input_qty) AS total_difference " + "FROM packing_master bm "
					+ "JOIN packing_actual ba ON bm.input_code = ba.bp_code AND bm.fp_name = ba.fp_name "
					+ "WHERE bm.input_code = ? " + "GROUP BY bm.fp_name, bm.input_code";

			stmt = conn.prepareStatement(sql);
			stmt.setString(1, bomCode);
			rs = stmt.executeQuery();

			while (rs.next()) {
				FinishedGoodsVariantsDto contact = new FinishedGoodsVariantsDto();
				contact.setVariant(rs.getString("fp_name"));
				contact.setUnits_packed(rs.getString("total_capacity"));
				contact.setUnit_weight(rs.getString("total_actual_qty"));
				contact.setTotal_weight(rs.getString("total_difference"));
				contactlist.add(contact);
			}
		} catch (Exception e) {
			e.printStackTrace();
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

		return contactlist;
	}

	public List<BomCostComparisonDto> bomCostComparison(String bomCode) {
		List<BomCostComparisonDto> contactlist = new ArrayList<>();
		String database = "test";

		HikariDataSource newDataSource = null;
		Connection conn = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;

		try {
			newDataSource = customDataSource.dynamicDatabaseChange(database);
			conn = newDataSource.getConnection();

			String sql = "SELECT ym.bp_code, bm.input_name, bm.bom_code, bm.input_qty, bm.rate, "
					+ "(bm.input_qty * bm.rate) AS bom_total, ba.qty_in_base_unit, ba.unit_rate, "
					+ "(ba.qty_in_base_unit * ba.unit_rate) AS actual_total, "
					+ "(bm.input_qty * bm.rate) - (ba.qty_in_base_unit * ba.unit_rate) AS difference "
					+ "FROM bom_master bm "
					+ "JOIN bom_actual ba ON bm.input_name = ba.product AND bm.bom_code = ba.finished_product_code "
					+ "LEFT JOIN yield_master ym ON bm.bom_code = ym.input_code " + "WHERE bm.bom_code = ? "
					+ "GROUP BY ym.bp_code, bm.input_name, bm.bom_code, bm.input_qty, bm.rate, ba.qty_in_base_unit, ba.unit_rate";

			stmt = conn.prepareStatement(sql);
			stmt.setString(1, bomCode);
			rs = stmt.executeQuery();

			while (rs.next()) {
				BomCostComparisonDto contact = new BomCostComparisonDto();
				contact.setId(rs.getString("bp_code"));
				contact.setIngredient(rs.getString("input_name"));
				contact.setRate(rs.getString("rate"));
				contact.setIdeal_qty(rs.getString("input_qty"));
				contact.setIdeal_cost(rs.getString("bom_total"));
				contact.setActual_qty(rs.getString("qty_in_base_unit"));
				contact.setActual_cost(rs.getString("unit_rate"));
				contact.setVariance(rs.getString("difference"));
				contactlist.add(contact);
			}
		} catch (Exception e) {
			e.printStackTrace();
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

		return contactlist;
	}

	public List<BomDetailsDto> bomDetails(String bomCode) {
		List<BomDetailsDto> contactlist = new ArrayList<>();
		String database = "test";

		HikariDataSource newDataSource = null;
		Connection conn = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;

		try {
			newDataSource = customDataSource.dynamicDatabaseChange(database);
			conn = newDataSource.getConnection();

			String sql = "SELECT bm.input_name, bm.input_qty, ba.qty_in_base_unit, "
					+ "bm.input_qty - ba.qty_in_base_unit AS difference " + "FROM bom_master bm "
					+ "JOIN bom_actual ba ON bm.input_name = ba.product AND bm.bom_code = ba.finished_product_code "
					+ "WHERE bm.bom_code = ? "
					+ "GROUP BY bm.input_name, bm.bom_code, bm.input_qty, bm.rate, ba.qty_in_base_unit, ba.unit_rate";

			stmt = conn.prepareStatement(sql);
			stmt.setString(1, bomCode);
			rs = stmt.executeQuery();

			while (rs.next()) {
				BomDetailsDto contact = new BomDetailsDto();
				contact.setIngredient(rs.getString("input_name"));
				contact.setIdeal_qty(rs.getString("input_qty"));
				contact.setActual_qty(rs.getString("qty_in_base_unit"));
				contact.setVariance(rs.getString("difference"));
				contactlist.add(contact);
			}
		} catch (Exception e) {
			e.printStackTrace();
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

		return contactlist;
	}

//	
//	public String readAndSaveAllSheetsFromPath() {
//		String folderPath = "D:/New folder";
//		String database = "test";
//		HikariDataSource dataSource = null;
//		Connection conn = null;
//
//		try {
//			File folder = new File(folderPath);
//			if (!folder.exists() || !folder.isDirectory()) {
//				return "Error: Folder does not exist at " + folderPath;
//			}
//
//			File[] excelFiles = folder.listFiles((dir, name) -> name.endsWith(".xlsx") || name.endsWith(".xls"));
//			if (excelFiles == null || excelFiles.length == 0) {
//				return "No Excel files found in the folder.";
//			}
//
//			dataSource = customDataSource.dynamicDatabaseChange(database);
//			conn = dataSource.getConnection();
//
//			for (File file : excelFiles) {
//				Workbook workbook = WorkbookFactory.create(file);
//
//				for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
//					Sheet sheet = workbook.getSheetAt(i);
//					String sheetName = sheet.getSheetName().replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
//
//					Row headerRow = sheet.getRow(0);
//					if (headerRow == null || headerRow.getLastCellNum() == 0)
//						continue;
//
//					Map<String, String> columnTypeMap = new LinkedHashMap<>();
//					int maxTypeScanRows = Math.min(sheet.getLastRowNum(), 10);
//
//					for (int c = 0; c < headerRow.getLastCellNum(); c++) {
//						Cell headerCell = headerRow.getCell(c);
//						if (headerCell == null)
//							continue;
//
//						String colName = headerCell.getStringCellValue().replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
//						if (colName.isBlank())
//							continue;
//
//						columnTypeMap.put(colName, "TEXT"); // default
//
//						for (int r = 1; r <= maxTypeScanRows; r++) {
//							Row row = sheet.getRow(r);
//							if (row == null)
//								continue;
//							Cell cell = row.getCell(c);
//							if (cell == null)
//								continue;
//
//							switch (cell.getCellType()) {
//							case NUMERIC:
//								if (DateUtil.isCellDateFormatted(cell)) {
//									columnTypeMap.put(colName, "DATE");
//								} else {
//									double val = cell.getNumericCellValue();
//									if (val % 1 == 0) {
//										columnTypeMap.put(colName, "INTEGER");
//									} else {
//										columnTypeMap.put(colName, "NUMERIC");
//									}
//								}
//								break;
//							case BOOLEAN:
//								columnTypeMap.put(colName, "BOOLEAN");
//								break;
//							default:
//								columnTypeMap.put(colName, "TEXT");
//							}
//						}
//					}
//
//					if (columnTypeMap.isEmpty())
//						continue;
//
//					List<String> columns = new ArrayList<>(columnTypeMap.keySet());
//
//					// Check table existence
//					boolean tableExists = false;
//					try (PreparedStatement checkStmt = conn.prepareStatement(
//							"SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?)")) {
//						checkStmt.setString(1, sheetName);
//						ResultSet rs = checkStmt.executeQuery();
//						if (rs.next())
//							tableExists = rs.getBoolean(1);
//					}
//
//					// Create table
//					if (!tableExists) {
//						StringBuilder createSQL = new StringBuilder("CREATE TABLE " + sheetName + " (");
//						for (Map.Entry<String, String> entry : columnTypeMap.entrySet()) {
//							createSQL.append(entry.getKey()).append(" ").append(entry.getValue()).append(", ");
//						}
//						createSQL.setLength(createSQL.length() - 2); // remove last comma
//						createSQL.append(")");
//						conn.createStatement().execute(createSQL.toString());
//					}
//
//					// Prepare insert statement
//					StringBuilder insertSQL = new StringBuilder("INSERT INTO " + sheetName + " (");
//					insertSQL.append(String.join(", ", columns)).append(") VALUES (");
//					insertSQL.append("?,".repeat(columns.size()));
//					insertSQL.setLength(insertSQL.length() - 1); // remove trailing comma
//					insertSQL.append(")");
//
//					PreparedStatement pstmt = conn.prepareStatement(insertSQL.toString());
//
//					for (int r = 1; r <= sheet.getLastRowNum(); r++) {
//						Row row = sheet.getRow(r);
//						if (row == null)
//							continue;
//
//						for (int c = 0; c < columns.size(); c++) {
//							Cell cell = row.getCell(c);
//							String colType = columnTypeMap.get(columns.get(c));
//
//							if (cell == null) {
//								pstmt.setNull(c + 1, java.sql.Types.NULL);
//								continue;
//							}
//
//							switch (colType) {
//							case "INTEGER":
//								pstmt.setInt(c + 1, (int) cell.getNumericCellValue());
//								break;
//							case "NUMERIC":
//								pstmt.setDouble(c + 1, cell.getNumericCellValue());
//								break;
//							case "BOOLEAN":
//								pstmt.setBoolean(c + 1, cell.getBooleanCellValue());
//								break;
//							case "DATE":
//								pstmt.setDate(c + 1, new java.sql.Date(cell.getDateCellValue().getTime()));
//								break;
//							default:
//								cell.setCellType(CellType.STRING);
//								pstmt.setString(c + 1, cell.getStringCellValue());
//							}
//						}
//						pstmt.executeUpdate();
//					}
//				}
//			}
//
//			return "All Excel files and sheets processed successfully.";
//		} catch (Exception e) {
//			e.printStackTrace();
//			return "Error: " + e.getMessage();
//		} finally {
//			try {
//				if (conn != null)
//					conn.close();
//				if (dataSource != null)
//					dataSource.close();
//			} catch (Exception ignored) {
//			}
//		}
//	}
//	public Map<String, Object> readAndSaveFromFile(MultipartFile file) {
//		Map<String, Object> response = new HashMap<>();
//		String database = "test";
//
//		HikariDataSource dataSource = null;
//		Connection conn = null;
//
//		try {
//			dataSource = customDataSource.dynamicDatabaseChange(database);
//			conn = dataSource.getConnection();
//
//			try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
//				for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
//					Sheet sheet = workbook.getSheetAt(i);
//					String sheetName = sheet.getSheetName().replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
//
//					Row headerRow = sheet.getRow(0);
//					if (headerRow == null || headerRow.getLastCellNum() == 0)
//						continue;
//
//					Map<String, String> columnTypeMap = new LinkedHashMap<>();
//					int maxTypeScanRows = Math.min(sheet.getLastRowNum(), 20);
//
//					for (int c = 0; c < headerRow.getLastCellNum(); c++) {
//						Cell headerCell = headerRow.getCell(c);
//						if (headerCell == null)
//							continue;
//
//						String colName = headerCell.getStringCellValue().replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
//						if (colName.isBlank() || colName.equalsIgnoreCase("sno"))
//							continue;
//
//						columnTypeMap.put(colName, "TEXT");
//
//						for (int r = 1; r <= maxTypeScanRows; r++) {
//							Row row = sheet.getRow(r);
//							if (row == null)
//								continue;
//
//							Cell cell = row.getCell(c);
//							if (cell == null)
//								continue;
//
//							try {
//								switch (cell.getCellType()) {
//								case NUMERIC -> {
//									if (DateUtil.isCellDateFormatted(cell))
//										columnTypeMap.put(colName, "DATE");
//									else
//										columnTypeMap.put(colName,
//												(cell.getNumericCellValue() % 1 == 0) ? "INTEGER" : "NUMERIC");
//								}
//								case BOOLEAN -> columnTypeMap.put(colName, "BOOLEAN");
//								default -> columnTypeMap.put(colName, "TEXT");
//								}
//							} catch (Exception e) {
//								columnTypeMap.put(colName, "TEXT");
//							}
//						}
//					}
//
//					if (columnTypeMap.isEmpty())
//						continue;
//					List<String> columns = new ArrayList<>(columnTypeMap.keySet());
//
//					// Check if table exists
//					boolean tableExists;
//					try (PreparedStatement checkStmt = conn.prepareStatement(
//							"SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?)");) {
//						checkStmt.setString(1, sheetName);
//						ResultSet rs = checkStmt.executeQuery();
//						tableExists = rs.next() && rs.getBoolean(1);
//					}
//
//					if (!tableExists) {
//						StringBuilder createSQL = new StringBuilder(
//								"CREATE TABLE " + sheetName + " (id SERIAL PRIMARY KEY, ");
//						for (Map.Entry<String, String> entry : columnTypeMap.entrySet()) {
//							createSQL.append(entry.getKey()).append(" ").append(entry.getValue()).append(", ");
//						}
//						createSQL.append("created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
//						conn.createStatement().execute(createSQL.toString());
//					} else {
//						PreparedStatement colCheckStmt = conn.prepareStatement(
//								"SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = ? AND column_name = 'created_at')");
//						colCheckStmt.setString(1, sheetName);
//						ResultSet colRs = colCheckStmt.executeQuery();
//						if (colRs.next() && !colRs.getBoolean(1)) {
//							conn.createStatement().execute("ALTER TABLE " + sheetName
//									+ " ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
//						}
//					}
//
//					String insertSQL = "INSERT INTO " + sheetName + " (" + String.join(", ", columns)
//							+ ", created_at) VALUES (" + "?, ".repeat(columns.size() - 1) + "?, CURRENT_TIMESTAMP)";
//					PreparedStatement pstmt = conn.prepareStatement(insertSQL);
//
//					for (int r = 1; r <= sheet.getLastRowNum(); r++) {
//						Row row = sheet.getRow(r);
//						if (row == null)
//							continue;
//
//						for (int c = 0; c < columns.size(); c++) {
//							Cell cell = row.getCell(c);
//							String colType = columnTypeMap.get(columns.get(c));
//
//							try {
//								if (cell == null || cell.getCellType() == CellType.BLANK) {
//									pstmt.setNull(c + 1, Types.NULL);
//									continue;
//								}
//
//								switch (colType) {
//								case "INTEGER" -> pstmt.setInt(c + 1, (int) cell.getNumericCellValue());
//								case "NUMERIC" -> pstmt.setDouble(c + 1, cell.getNumericCellValue());
//								case "BOOLEAN" -> pstmt.setBoolean(c + 1, cell.getBooleanCellValue());
//								case "DATE" ->
//									pstmt.setDate(c + 1, new java.sql.Date(cell.getDateCellValue().getTime()));
//								default -> {
//									cell.setCellType(CellType.STRING);
//									pstmt.setString(c + 1, cell.getStringCellValue());
//								}
//								}
//							} catch (Exception ex) {
//								pstmt.setNull(c + 1, Types.NULL);
//							}
//						}
//
//						pstmt.executeUpdate();
//					}
//				}
//			}
//
//			response.put("result", true);
//			response.put("message", "✅ File imported successfully.");
//			return response;
//
//		} catch (Exception e) {
//			e.printStackTrace();
//			response.put("result", false);
//			response.put("message", "❌ Error: " + e.getMessage());
//			return response;
//		} finally {
//			try {
//				if (conn != null)
//					conn.close();
//				if (dataSource != null)
//					dataSource.close();
//			} catch (Exception ignored) {
//			}
//		}
//	}
//
//	public Map<String, Object> searchFilter(SearchKeys test) {
//		Map<String, Object> response = new HashMap<>();
//		List<PurchaseMasterModel> purchaseList = new ArrayList<>();
//		String database = "test";
//		HikariDataSource newDataSource = null;
//		Connection conn = null;
//		Statement stmt = null;
//		ResultSet rs = null;
//
//		try {
//			newDataSource = customDataSource.dynamicDatabaseChange(database);
//			conn = newDataSource.getConnection();
//			stmt = conn.createStatement();
//
//			String sql = "SELECT * FROM product_tracker ORDER BY id";
//			rs = stmt.executeQuery(sql);
//
//			while (rs.next()) {
//				PurchaseMasterModel model = new PurchaseMasterModel();
//				model.setId(rs.getLong("id"));
//				model.setCategory(rs.getString("category"));
//				model.setSub_category(rs.getString("sub_category"));
//				model.setCode(rs.getString("code"));
//				model.setProduct_name(rs.getString("product_name"));
//				model.setSupplier(rs.getString("supplier"));
//				model.setBudget_qty(rs.getBigDecimal("budget_qty"));
//				model.setBudget_value(rs.getBigDecimal("budget_value"));
//				model.setPurcahsed_qty(rs.getInt("purcahsed_qty"));
//				model.setPurcahsed_value(rs.getInt("purcahsed_value"));
//				model.setMin_stock_qty(rs.getInt("min_stock_qty"));
//				model.setMax_stock_qty(rs.getInt("max_stock_qty"));
//				model.setMoq(rs.getInt("moq"));
//				model.setLead_time(rs.getInt("lead_time"));
//				model.setSchedule(rs.getInt("schedule"));
//				model.setStock_in_hand(rs.getInt("stock_in_hand"));
//				model.setStock_in_hand_value(rs.getInt("stock_in_hand_value"));
//				model.setStatus(rs.getString("status"));
//				model.setRemarks(rs.getString("remarks"));
////				model.setUpdated_at(rs.getDate("updated_at"));
////				model.setCreated_at(rs.getDate("created_at"));
//				model.setDate(rs.getDate("date"));
//				purchaseList.add(model);
//			}
//		} catch (Exception e) {
//			e.printStackTrace();
//			response.put("result", false);
//			response.put("message", "❌ Error: " + e.getMessage());
//			return response;
//		} finally {
//			try {
//				if (rs != null)
//					rs.close();
//				if (stmt != null)
//					stmt.close();
//				if (conn != null)
//					conn.close();
//				if (newDataSource != null)
//					newDataSource.close();
//			} catch (Exception ignored) {
//			}
//		}
//
//		response.put("purchaseList", purchaseList);
//		response.put("result", true);
//		return response;
//	}
//
//	public Map<String, Object> statusUpdate(PurchaseStatusUpdate purchase) {
//		Map<String, Object> response = new HashMap<>();
//		int orgid = 1;
//		String database = "test";
//		HikariDataSource newDataSource = null;
//		Connection conn = null;
//		PreparedStatement stmt = null;
//
//		try {
//			newDataSource = customDataSource.dynamicDatabaseChange(database);
//			conn = newDataSource.getConnection();
//
//			String sql = "UPDATE product_tracker SET status = ?, remarks = ?, date = ? WHERE id = ?";
//			stmt = conn.prepareStatement(sql);
//
//			stmt.setString(1, purchase.getStatus());
//			stmt.setString(2, purchase.getRemarks());
//			stmt.setDate(3, purchase.getDate());
//			stmt.setInt(4, purchase.getId());
//
//			int rows = stmt.executeUpdate();
//			if (rows > 0) {
//				response.put("result", true);
//				response.put("message", "✅ Status updated successfully.");
//			} else {
//				response.put("result", false);
//				response.put("message", "⚠️ No record updated. Check if ID is correct.");
//			}
//		} catch (Exception e) {
//			e.printStackTrace();
//			response.put("result", false);
//			response.put("message", "❌ Error: " + e.getMessage());
//		} finally {
//			try {
//				if (stmt != null)
//					stmt.close();
//				if (conn != null)
//					conn.close();
//				if (newDataSource != null)
//					newDataSource.close();
//			} catch (Exception ignored) {
//			}
//		}
//
//		return response;
//	}
//
//	public byte[] getSampleStockTemplate() {
//		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream();) {
//			Sheet sheet = workbook.createSheet("Lead");
//			Row headerRow = sheet.createRow(0);
//
//			CellStyle headerCellStyle = sheet.getWorkbook().createCellStyle();
//			// fill foreground color ...
//			headerCellStyle.setFillForegroundColor(IndexedColors.SEA_GREEN.index);
//			// and solid fill pattern produces solid grey cell fill
//			headerCellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
//
//			for (int i = 0; i < stocksTemplateHeader.length; i++) {
//				headerRow.createCell(i).setCellValue(stocksTemplateHeader[i]);
//				Cell cell = headerRow.getCell(i);
//				cell.setCellStyle(headerCellStyle);
//
//				Font redFont = workbook.createFont();
//				redFont.setColor(IndexedColors.WHITE.index);
//				redFont.setFontHeightInPoints((short) 11);
//				redFont.setBold(true);
//				headerCellStyle.setFont(redFont);
//				headerRow.getCell(i).setCellStyle(headerCellStyle);
//			}
//			for (int columnIndex = 0; columnIndex < 28; columnIndex++) {
//
//				sheet.autoSizeColumn(columnIndex);
//			}
//			workbook.write(out);
//
//			return out.toByteArray();
//		} catch (IOException | java.io.IOException e) {
//			throw new RuntimeException("Failed to parse excel file: " + e.getMessage());
//		}
//	}
//
//	public byte[] getSamplePurcahsedTemplate() {
//		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream();) {
//			Sheet sheet = workbook.createSheet("Lead");
//			Row headerRow = sheet.createRow(0);
//
//			CellStyle headerCellStyle = sheet.getWorkbook().createCellStyle();
//			// fill foreground color ...
//			headerCellStyle.setFillForegroundColor(IndexedColors.SEA_GREEN.index);
//			// and solid fill pattern produces solid grey cell fill
//			headerCellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
//
//			for (int i = 0; i < purcahsedTemplateHeader.length; i++) {
//				headerRow.createCell(i).setCellValue(purcahsedTemplateHeader[i]);
//				Cell cell = headerRow.getCell(i);
//				cell.setCellStyle(headerCellStyle);
//
//				Font redFont = workbook.createFont();
//				redFont.setColor(IndexedColors.WHITE.index);
//				redFont.setFontHeightInPoints((short) 11);
//				redFont.setBold(true);
//				headerCellStyle.setFont(redFont);
//				headerRow.getCell(i).setCellStyle(headerCellStyle);
//			}
//			for (int columnIndex = 0; columnIndex < 28; columnIndex++) {
//
//				sheet.autoSizeColumn(columnIndex);
//			}
//			workbook.write(out);
//
//			return out.toByteArray();
//		} catch (IOException | java.io.IOException e) {
//			throw new RuntimeException("Failed to parse excel file: " + e.getMessage());
//		}
//	}
//
//	public byte[] uploadPurcahseExcel(InputStream inputStream) {
//		int orgid = 1;
//		String database = "test";
//
//		try (Workbook workbook = new XSSFWorkbook(inputStream);
//				ByteArrayOutputStream out = new ByteArrayOutputStream();
//				HikariDataSource orgDataSource = customDataSource.dynamicDatabaseChange(database);
//				Connection connection = orgDataSource.getConnection()) {
//
//			Sheet sheet = workbook.getSheetAt(0);
//			Iterator<Row> rowIterator = sheet.iterator();
//			if (!rowIterator.hasNext()) {
//				sheet.createRow(1).createCell(0).setCellValue("Empty file!");
//				workbook.write(out);
//				return out.toByteArray();
//			}
//
//			Row headerRow = rowIterator.next();
//			int codeIdx = -1, stockIdx = -1, stockValueIdx = -1;
//
//			// Detect column indexes
//			for (int i = 0; i < headerRow.getLastCellNum(); i++) {
//				String header = headerRow.getCell(i).getStringCellValue().trim();
//				if ("Code".equalsIgnoreCase(header))
//					codeIdx = i;
//				else if ("Stock in Hand".equalsIgnoreCase(header))
//					stockIdx = i;
//				else if ("Stock in hand value".equalsIgnoreCase(header))
//					stockValueIdx = i;
//			}
//
//			if (codeIdx == -1 || stockIdx == -1 || stockValueIdx == -1) {
//				sheet.createRow(1).createCell(0)
//						.setCellValue("Missing headers: Code, Stock in Hand, or Stock in hand value");
//				workbook.write(out);
//				return out.toByteArray();
//			}
//
//			int rowNum = 1;
//			while (rowIterator.hasNext()) {
//				Row row = rowIterator.next();
//				Cell codeCell = row.getCell(codeIdx);
//				if (codeCell == null || codeCell.getCellType() != CellType.STRING) {
//					row.createCell(headerRow.getLastCellNum()).setCellValue("Invalid or empty code");
//					continue;
//				}
//
//				String code = codeCell.getStringCellValue().trim();
//				double stock = row.getCell(stockIdx) != null ? row.getCell(stockIdx).getNumericCellValue() : 0;
//				double stockValue = row.getCell(stockValueIdx) != null
//						? row.getCell(stockValueIdx).getNumericCellValue()
//						: 0;
//
//				try (PreparedStatement stmt = connection.prepareStatement(
//						"UPDATE product_tracker SET stock_in_hand = ?, stock_in_hand_value = ?,updated_at = now() WHERE code = ?")) {
//					stmt.setDouble(1, stock);
//					stmt.setDouble(2, stockValue);
//					stmt.setString(3, code);
//
//					int updated = stmt.executeUpdate();
//					if (updated == 0) {
//						row.createCell(headerRow.getLastCellNum()).setCellValue("Code not found");
//					} else {
//						row.createCell(headerRow.getLastCellNum()).setCellValue("Updated");
//					}
//				} catch (SQLException e) {
//					row.createCell(headerRow.getLastCellNum()).setCellValue("Error: " + e.getMessage());
//				}
//
//				rowNum++;
//			}
//
//			workbook.write(out);
//			return out.toByteArray();
//
//		} catch (IOException | SQLException e) {
//			throw new RuntimeException("Excel upload failed: " + e.getMessage(), e);
//		}
//	}
//
//	public byte[] uploadStocksExcel(InputStream inputStream) {
//		// TODO Auto-generated method stub
//		return null;
//	}

}
