package com.akrcode.akr.restController;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.akrcode.akr.configure.CustomDataSource;
import com.akrcode.akr.dto.PurchaseStatusUpdate;
import com.akrcode.akr.dto.SearchKeys;
import com.akrcode.akr.service.PurchaseService;
import com.zaxxer.hikari.HikariDataSource;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/purchase")
@CrossOrigin("*")
public class PurchaseRestController {

	@Autowired
	CustomDataSource customDataSource;
	@Autowired
	private PurchaseService purchaseService;

	public static final String XLSX_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
	public static final String XLS_TYPE = "application/vnd.ms-excel";

	@PostMapping("/search")
	public Map<String, Object> searchFilter(@RequestBody SearchKeys search) {
		return purchaseService.searchFilter(search);
	}

	@PostMapping("/upload")
	public ResponseEntity<Map<String, Object>> uploadExcel(@RequestParam("file") MultipartFile file) {
		try {
			Map<String, Object> result = purchaseService.readAndSaveFromFile(file);
			return ResponseEntity.ok(result);
		} catch (Exception e) {
			e.printStackTrace();
			Map<String, Object> error = new HashMap<>();
			error.put("result", false);
			error.put("message", "❌ Upload Failed: " + e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
		}
	}

	@PostMapping("/statusupdate")
	public Map<String, Object> statusUpdate(@RequestBody PurchaseStatusUpdate status) {
		return purchaseService.statusUpdate(status);
	}

	@GetMapping(value = "/downloadstocktemplete", produces = {
			"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" })
	public byte[] getSampleStockTemplate() {

		return purchaseService.getSampleStockTemplate();
	}

	@GetMapping(value = "/downloadpurcahsedtemplete", produces = {
			"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" })
	public byte[] getSamplePurcahsedTemplate() {

		return purchaseService.getSamplePurcahsedTemplate();
	}

	@PostMapping(value = "/purcahseupload", produces = {
			"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" })
	public byte[] uploadPurcahseExcel(@RequestParam("file") MultipartFile file) {

		byte[] emptyarray = new byte[0];

		try {
			if (XLSX_TYPE.equals(file.getContentType()) || XLS_TYPE.equals(file.getContentType())) {
				return purchaseService.uploadPurcahseExcel(file.getInputStream());
			} else {
				return emptyarray;
			}

		} catch (IOException e) {
			return emptyarray;
		}

	}

	@PostMapping(value = "/stocksupload", produces = {
			"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" })
	public byte[] uploadStocksExcel(@RequestParam("file") MultipartFile file) {

		byte[] emptyarray = new byte[0];

		try {
			if (XLSX_TYPE.equals(file.getContentType()) || XLS_TYPE.equals(file.getContentType())) {
				return purchaseService.uploadStocksExcel(file.getInputStream());
			} else {
				return emptyarray;
			}

		} catch (IOException e) {
			return emptyarray;
		}

	}

	@PostMapping("/summary")
	public Map<String, Object> getSummary(@RequestBody SearchKeys search) {
		return purchaseService.getSummary(search);
	}

	@GetMapping("/autosuggest/{key}")
	public ResponseEntity<?> getSuggestions(@PathVariable("key") String key,
			@RequestParam(value = "q", required = false) String query) {
		try {
			List<String> suggestions = purchaseService.getSuggestions(key, query);
			return ResponseEntity.ok(suggestions);
		} catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Collections.singletonMap("error", "Failed to fetch suggestions"));
		}
	}

	@PostMapping(value = "/purcahseuploadfile", produces = {
			"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" })
	public ResponseEntity<byte[]> uploadPurchaseExcelFile(@RequestParam("file") MultipartFile file) {
		String contentType = file.getContentType();
		if (XLSX_TYPE.equals(contentType) || XLS_TYPE.equals(contentType)) {
			byte[] result = purchaseService.uploadPurchaseExcelFile(file);
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.parseMediaType(XLSX_TYPE));
			headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"purchase-upload-result.xlsx\"");
			return new ResponseEntity<>(result, headers, HttpStatus.OK);
		} else {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body("Only Excel files (.xlsx, .xls) are supported.".getBytes());
		}
	}

	@GetMapping("/download")
	public void downloadPurchaseExcel(@ModelAttribute SearchKeys search, HttpServletResponse response) {
		String database = "sri_krishna_db";
		HikariDataSource dataSource = null;

		try {
			dataSource = customDataSource.dynamicDatabaseChange(database);
			if (dataSource == null) {
				System.err.println("Failed to get DataSource for DB: " + database);
				response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				return;
			}

			try (Connection conn = dataSource.getConnection()) {

				List<String> conditions = new ArrayList<>();
				List<Object> params = new ArrayList<>();

				if (search.getCategory() != null && !search.getCategory().isBlank()) {
					conditions.add("pt.category = ?");
					params.add(search.getCategory().trim());
				}
				if (search.getSupplier() != null && !search.getSupplier().isBlank()) {
					conditions.add("pt.supplier = ?");
					params.add(search.getSupplier().trim());
				}
				if (search.getStatus() != null && !search.getStatus().isBlank()) {
					conditions.add("ph.status = ?");
					params.add(search.getStatus().trim());
				}

				String whereClause = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);

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
						) ph ON pt.code = ph.code
						"""
						+ whereClause + " ORDER BY pt.id";

				XSSFWorkbook workbook = new XSSFWorkbook();
				XSSFSheet sheet = workbook.createSheet("Purchase Report");

				int rowNum = 0;
				String[] columns = { "Category", "Sub Category", "Code", "Product Name", "Supplier", "Budget Qty",
						"Budget Value", "Purchased Qty", "Purchased Value", "Min Stock Qty", "Max Stock Qty", "MOQ",
						"Lead Time", "Schedule", "Stock In Hand", "Stock In Hand Value", "Status", "Remarks", "Date" };

				// Header style
				XSSFCellStyle headerStyle = workbook.createCellStyle();
				XSSFFont headerFont = workbook.createFont();
				headerFont.setBold(true);
				headerFont.setColor(IndexedColors.WHITE.getIndex());
				headerStyle.setFont(headerFont);
				headerStyle.setFillForegroundColor(IndexedColors.GREEN.getIndex());
				headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

				// Create header row
				Row header = sheet.createRow(rowNum++);
				for (int i = 0; i < columns.length; i++) {
					Cell cell = header.createCell(i);
					cell.setCellValue(columns[i]);
					cell.setCellStyle(headerStyle);
				}

				try (PreparedStatement ps = conn.prepareStatement(sql)) {
					for (int i = 0; i < params.size(); i++) {
						ps.setObject(i + 1, params.get(i));
					}

					try (ResultSet rs = ps.executeQuery()) {
						while (rs.next()) {
							Row row = sheet.createRow(rowNum++);
							int col = 0;

							row.createCell(col++).setCellValue(rs.getString("category"));
							row.createCell(col++).setCellValue(rs.getString("sub_category"));
							row.createCell(col++).setCellValue(rs.getString("code"));
							row.createCell(col++).setCellValue(rs.getString("product_name"));
							row.createCell(col++).setCellValue(rs.getString("supplier"));

							row.createCell(col++).setCellValue(getDouble(rs.getBigDecimal("budget_qty")));
							row.createCell(col++).setCellValue(getDouble(rs.getBigDecimal("budget_value")));
							row.createCell(col++).setCellValue(getDouble(rs.getBigDecimal("purchased_qty")));
							row.createCell(col++).setCellValue(getDouble(rs.getBigDecimal("purchased_value")));

							row.createCell(col++).setCellValue(valueOf(rs.getObject("min_stock_qty")));
							row.createCell(col++).setCellValue(valueOf(rs.getObject("max_stock_qty")));
							row.createCell(col++).setCellValue(valueOf(rs.getObject("moq")));
							row.createCell(col++).setCellValue(valueOf(rs.getObject("lead_time")));
							row.createCell(col++).setCellValue(valueOf(rs.getObject("schedule")));
							row.createCell(col++).setCellValue(valueOf(rs.getObject("stock_in_hand")));
							row.createCell(col++).setCellValue(getDouble(rs.getBigDecimal("stock_in_hand_value")));
							row.createCell(col++).setCellValue(rs.getString("status"));
							row.createCell(col++).setCellValue(rs.getString("remarks"));

							// Date formatting
							Cell dateCell = row.createCell(col++);
							Date date = rs.getDate("date");
							if (date != null) {
								dateCell.setCellValue(date);
								CellStyle dateCellStyle = workbook.createCellStyle();
								CreationHelper createHelper = workbook.getCreationHelper();
								dateCellStyle.setDataFormat(createHelper.createDataFormat().getFormat("dd-MMM-yyyy"));
								dateCell.setCellStyle(dateCellStyle);
							} else {
								dateCell.setCellValue("");
							}
						}
					}
				}

				// Auto-size all columns
				for (int i = 0; i < columns.length; i++) {
					sheet.autoSizeColumn(i);
				}

				// Set response headers
				response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
				response.setHeader("Content-Disposition", "attachment; filename=purchase_report.xlsx");

				try (ServletOutputStream out = response.getOutputStream()) {
					workbook.write(out);
					workbook.close();
				}

			}

		} catch (Exception e) {
			System.err.println("Exception during Excel download: " + e.getMessage());
			e.printStackTrace();
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		} finally {
			if (dataSource != null) {
				dataSource.close();
			}
		}
	}

	private String valueOf(Object obj) {
		return obj != null ? obj.toString() : "";
	}

	private double getDouble(BigDecimal bd) {
		return bd != null ? bd.doubleValue() : 0.0;
	}

}
