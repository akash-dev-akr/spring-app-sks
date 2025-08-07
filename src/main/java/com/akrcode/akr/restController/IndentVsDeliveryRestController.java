package com.akrcode.akr.restController;


import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.akrcode.akr.daoImpl.IndentVsDeliveryDaoImpl;
import com.akrcode.akr.service.IndentVsDeliveryService;

@RestController
@RequestMapping("/indentvsdelivery")
@CrossOrigin("*")
public class IndentVsDeliveryRestController {

	@Autowired
	IndentVsDeliveryService indentVsDeliveryService;

	@Autowired
	IndentVsDeliveryDaoImpl indentVsDeliverydaoimpl;
	
	@PostMapping("/uploadindentvsdelivery")
	public ResponseEntity<byte[]> uploadExcel(@RequestParam("file") MultipartFile file) {
	    try {
	        InputStream inputStream = file.getInputStream();
	        byte[] result = indentVsDeliverydaoimpl.processExcelUploadReturnStatusExcel(inputStream);

	        HttpHeaders headers = new HttpHeaders();
	        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
	        headers.setContentDisposition(ContentDisposition.attachment()
	            .filename("upload_status.xlsx").build());
	        headers.setCacheControl("no-store"); // Prevent browser resubmit

	        return new ResponseEntity<>(result, headers, HttpStatus.OK);
	    } catch (Exception e) {
	        e.printStackTrace();
	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
	                .body(("Error while uploading: " + e.getMessage()).getBytes());
	    }
	}

	@PostMapping("/search")
	public Map<String, Object> searchIndentVsDelivery(@RequestBody Map<String, Object> filters) {
	    String dateStr = (String) filters.get("reportDate");
	    String category = (String) filters.get("category");
	    String product = (String) filters.get("product");
	    String section = (String) filters.get("section");
	    String packFormat = (String) filters.get("packFormat");

	    // Extract pagination parameters
	    int page = filters.get("page") != null ? (int) filters.get("page") : 1;
	    int limit = filters.get("limit") != null ? (int) filters.get("limit") : 25;

	    // Extract difference filter
	    Map<String, String> differenceFilter = null;
	    Object filterObj = filters.get("differenceFilter");
	    if (filterObj instanceof Map<?, ?> map) {
	        differenceFilter = new HashMap<>();
	        for (Map.Entry<?, ?> entry : map.entrySet()) {
	            if (entry.getKey() != null && entry.getValue() != null) {
	                differenceFilter.put(entry.getKey().toString(), entry.getValue().toString());
	            }
	        }
	    }

	    // Parse date
	    LocalDate reportDate = null;
	    if (dateStr != null && !dateStr.isEmpty()) {
	        try {
	            reportDate = LocalDate.parse(dateStr);
	        } catch (DateTimeParseException e) {
	            e.printStackTrace(); // Or use a logger
	        }
	    }

	    // Call service with pagination parameters
	    return indentVsDeliveryService.listAllProductData(
	        reportDate, category, product, section, packFormat, differenceFilter, page, limit
	    );
	}
	@PostMapping("/summary")
	public Map<String, Object> fetchSummaryOnly(@RequestBody Map<String, Object> filters) {
	    String dateStr = (String) filters.get("reportdate");

	    LocalDate reportDate = null;
	    if (dateStr != null && !dateStr.isBlank()) {
	        try {
	            reportDate = LocalDate.parse(dateStr);
	        } catch (DateTimeParseException e) {
	            e.printStackTrace(); // Or better, use a logger
	        }
	    }

	    return indentVsDeliveryService.fetchSummaryOnly(reportDate);
	}


	@PostMapping("/updatefieldbycode")
	public ResponseEntity<String> updateFieldByCode(@RequestBody Map<String, String> payload) {
		String code = payload.get("code");
		String field = payload.get("field");
		String value = payload.get("value");
		String reportDateStr = payload.get("date").toString();
		LocalDate reportDate = LocalDate.parse(reportDateStr);
		String response = indentVsDeliveryService.updateFieldByCode(code, field, value, reportDate);
		System.out.println("Returning: " + response);
		 return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(response);

	}

	@PostMapping("/updatereason")
	public ResponseEntity<String> updateReason(@RequestBody Map<String, String> payload) {
	    String code = payload.get("code");
	    String field = payload.get("field");
	    String value = payload.get("value");
	    String reportDateStr = payload.get("date").toString();
	    LocalDate reportDate = LocalDate.parse(reportDateStr);

	    String response = indentVsDeliveryService.updateReason(code, field, value, reportDate);
	    return ResponseEntity.ok(response);
	}




	@PostMapping("/uploadqty")
	public ResponseEntity<byte[]> upload(@RequestParam MultipartFile file, @RequestParam String type) {
	    byte[] result = indentVsDeliveryService.uploadQuantity(file, type);
	    return ResponseEntity.ok()
	            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=upload_status.xlsx")
	            .contentType(MediaType.APPLICATION_OCTET_STREAM)
	            .body(result);
	}


	@GetMapping("/qtytemplate")
	public ResponseEntity<ByteArrayResource> downloadQuantityTemplate(@RequestParam String type) {

	return indentVsDeliveryService.downloadQuantityTemplate(type);
	}
	
	

	@GetMapping("/overalltemplate")
	public ResponseEntity<ByteArrayResource> overallIndentTemplate() {

	return indentVsDeliveryService.overallIndentTemplate();
	}
	
	@PostMapping("/execute")
    public ResponseEntity<Map<String, Object>> executeQuery(@RequestParam("sql") String sql) {
        Map<String, Object> response = indentVsDeliverydaoimpl.executeAnyQuery(sql);
        return ResponseEntity.ok(response);
    }
}
