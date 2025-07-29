package com.akrcode.akr.daoImpl;

import java.io.File;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.akrcode.akr.configure.CustomDataSource;
import com.akrcode.akr.dao.ContactDao;
import com.akrcode.akr.dto.SearchKeys;
import com.akrcode.akr.model.ContactModel;
import com.akrcode.akr.security.AppConfig;
import com.zaxxer.hikari.HikariDataSource;

@Component
public class ContactDaoImpl implements ContactDao {

	@Autowired
	CustomDataSource customDataSource;

	@Override
	public String saveContact(ContactModel contact) {
		String response = "Saved Successfully";
		int orgid = (int) AppConfig.session.get("orgId");
//		String database = (String) AppConfig.session.get("databasename");
		String database = "test";
		HikariDataSource newDataSource = null;
		Connection conn = null;
		PreparedStatement stmt = null;

		try {
			newDataSource = customDataSource.dynamicDatabaseChange(database);
			conn = newDataSource.getConnection();
			String sql = "INSERT INTO contact (name, email, phone, orgid, contactcode, company, job_title, address, birthday, notes, website) "
					+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
			stmt = conn.prepareStatement(sql);
			stmt.setString(1, contact.getName());
			stmt.setString(2, contact.getEmail());
			stmt.setString(3, contact.getPhone());
			stmt.setInt(4, orgid);
			stmt.setString(5, autoGeneraterId());
			stmt.setString(6, contact.getCompany());
			stmt.setString(7, contact.getJobTitle());
			stmt.setString(8, contact.getAddress());
			stmt.setDate(9, contact.getBirthday() != null ? Date.valueOf(contact.getBirthday()) : null);
			stmt.setString(10, contact.getNotes());
			stmt.setString(11, contact.getWebsite());
			stmt.executeUpdate();

		} catch (Exception e) {
			e.printStackTrace();
			response = "Error: " + e.getMessage();
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
	public Map<String, Object> searchFilter(SearchKeys keyvalues) {
		Map<String, Object> response = new HashMap<>();
		List<ContactModel> contacts = new ArrayList<>();

		String database = "test";
//				(String) AppConfig.session.get("databasename");

		HikariDataSource newDataSource = null;
		Connection conn = null;
		Statement stmt = null;
		ResultSet rs = null;

		try {
			newDataSource = customDataSource.dynamicDatabaseChange(database);
			conn = newDataSource.getConnection();
			stmt = conn.createStatement();
			String sql = "SELECT * FROM contact";
			rs = stmt.executeQuery(sql);

			while (rs.next()) {
				ContactModel contact = new ContactModel();
				contact.setId(rs.getLong("id"));
				contact.setName(rs.getString("name"));
				contact.setContactcode(rs.getString("contactcode"));
				contact.setEmail(rs.getString("email"));
				contact.setPhone(rs.getString("phone"));
				contact.setCompany(rs.getString("company"));
				contact.setJobTitle(rs.getString("job_title"));
				contact.setAddress(rs.getString("address"));
				Date birthday = rs.getDate("birthday");
				contact.setBirthday(birthday != null ? birthday.toLocalDate() : null);
				contact.setNotes(rs.getString("notes"));
				contact.setWebsite(rs.getString("website"));
				contacts.add(contact);
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
		response.put("contactlist", contacts);
		response.put("result", true);
		return response;
	}

	@Override
	public ContactModel getContactById(Long id) {
		ContactModel contact = null;
//		String database = (String) AppConfig.session.get("databasename");
		String database = "test";

		HikariDataSource newDataSource = null;
		Connection conn = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;

		try {
			newDataSource = customDataSource.dynamicDatabaseChange(database);
			conn = newDataSource.getConnection();
			String sql = "SELECT * FROM contact WHERE id = ?";
			stmt = conn.prepareStatement(sql);
			stmt.setLong(1, id);
			rs = stmt.executeQuery();

			if (rs.next()) {
				contact = new ContactModel();
				contact.setId(rs.getLong("id"));
				contact.setContactcode(rs.getString("contactcode"));
				contact.setName(rs.getString("name"));
				contact.setEmail(rs.getString("email"));
				contact.setPhone(rs.getString("phone"));
				contact.setCompany(rs.getString("company"));
				contact.setJobTitle(rs.getString("job_title"));
				contact.setAddress(rs.getString("address"));
				Date birthday = rs.getDate("birthday");
				contact.setBirthday(birthday != null ? birthday.toLocalDate() : null);
				contact.setNotes(rs.getString("notes"));
				contact.setWebsite(rs.getString("website"));
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

	@Override
	public String deleteContact(Long id) {
		String result = "Deleted Successfully";
//		String database = (String) AppConfig.session.get("databasename");
		String database = "test";

		HikariDataSource newDataSource = null;
		Connection conn = null;
		PreparedStatement stmt = null;

		try {
			newDataSource = customDataSource.dynamicDatabaseChange(database);
			conn = newDataSource.getConnection();
			String sql = "DELETE FROM contact WHERE id = ?";
			stmt = conn.prepareStatement(sql);
			stmt.setLong(1, id);
			stmt.executeUpdate();
		} catch (Exception e) {
			result = "Error: " + e.getMessage();
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

		return result;
	}

	@Override
	public String autoGeneraterId() {
		String AutoGenId = "";
		Statement stmt = null;
		int orgid = 1;
//				(int) AppConfig.session.get("orgId");
//		String database = (String) AppConfig.session.get("databasename");
		String database = "test";
		HikariDataSource newDataSource = null;

		try {
			String autogenid = "SELECT contactcode FROM contact WHERE orgid = " + orgid + " ORDER BY id DESC LIMIT 1";
			newDataSource = customDataSource.dynamicDatabaseChange(database);
			Connection conn = newDataSource.getConnection();
			stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery(autogenid);
			String contactcode = "";
			boolean found = false;

			while (rs.next()) {
				found = true;
				contactcode = rs.getString("contactcode");
			}

			int year = Calendar.getInstance().get(Calendar.YEAR);

			if (found) {
				int input = contactcode.length();
				AutoGenId = contactcode.substring(input - 3);
				long autoId = Long.parseLong(AutoGenId);
				autoId++;
				AutoGenId = "AKR/" + year + "/CON" + String.format("%03d", autoId);
			} else {
				AutoGenId = "AKR/" + year + "/CON001";
			}

		} catch (Exception e) {
			AutoGenId = "Error Generating ID: " + e.getMessage();
		} finally {
			try {
				if (stmt != null)
					stmt.close();
				if (newDataSource != null)
					newDataSource.close();
			} catch (Exception ignored) {
			}
		}

		return AutoGenId;

	}

	@Override
	public String readExcel() {
		return null;
	}

	public String insertExcelData(MultipartFile file) {
		String response = "Saved Successfully";
		int orgid = 1;
		String database = "test"; // or get from session
		HikariDataSource newDataSource = null;
		Connection conn = null;
		PreparedStatement stmt = null;

		try {
			newDataSource = customDataSource.dynamicDatabaseChange(database);
			conn = newDataSource.getConnection();

			// Read Excel File
			Workbook workbook = WorkbookFactory.create(file.getInputStream());
			Sheet sheet = workbook.getSheetAt(0);
			Row headerRow = sheet.getRow(0);

			if (headerRow == null)
				return "No header found in Excel";

			int columnCount = headerRow.getLastCellNum();
			List<String> columns = new ArrayList<>();

			for (int i = 0; i < columnCount; i++) {
				columns.add(headerRow.getCell(i).getStringCellValue());
			}

			// Build SQL
			StringBuilder sql = new StringBuilder("INSERT INTO excel (");
			sql.append(String.join(", ", columns)).append(") VALUES (");
			sql.append("?,".repeat(columnCount));
			sql.deleteCharAt(sql.length() - 1).append(")");

			stmt = conn.prepareStatement(sql.toString());

			// Loop rows
			for (int i = 1; i <= sheet.getLastRowNum(); i++) {
				Row row = sheet.getRow(i);
				if (row == null)
					continue;

				for (int j = 0; j < columnCount; j++) {
					Cell cell = row.getCell(j);
					if (cell == null) {
						stmt.setString(j + 1, null);
					} else {
						cell.setCellType(CellType.STRING);
						stmt.setString(j + 1, cell.getStringCellValue());
					}
				}

				stmt.executeUpdate();
			}

		} catch (Exception e) {
			e.printStackTrace();
			response = "Error: " + e.getMessage();
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

}
