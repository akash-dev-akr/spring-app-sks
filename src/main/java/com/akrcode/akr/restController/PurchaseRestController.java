package com.akrcode.akr.restController;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.akrcode.akr.configure.CustomDataSource;
import com.akrcode.akr.daoImpl.excelDaoimpl;
import com.akrcode.akr.dto.PurchaseStatusUpdate;
import com.akrcode.akr.dto.SearchKeys;
import com.akrcode.akr.model.ContactModel;
import com.akrcode.akr.service.PurchaseService;

@RestController
@RequestMapping("/api/purchase")
@CrossOrigin("*")
public class PurchaseRestController {
	@Autowired
	private PurchaseService purchaseService;

	public static final String XLSX_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
	public static final String XLS_TYPE = "application/vnd.ms-excel";
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

	   @PostMapping("/PurchaseUpload")
	    public ResponseEntity<Map<String, Object>> PurchaseUpload(@RequestParam("file") MultipartFile file) {
	        try {
	            Map<String, Object> result = purchaseService.PurchaseUpload(file);
	            return ResponseEntity.ok(result);
	        } catch (Exception e) {
	            e.printStackTrace();
	            Map<String, Object> error = new HashMap<>();
	            error.put("result", false);
	            error.put("message", "❌ Upload Failed: " + e.getMessage());
	            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
	        }
	    }
	@PostMapping("/search")
	public Map<String, Object> searchFilter() {
		SearchKeys test = new SearchKeys();
		return purchaseService.searchFilter(test);
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
	 


}
