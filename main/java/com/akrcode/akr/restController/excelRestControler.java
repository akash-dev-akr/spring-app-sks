package com.akrcode.akr.restController;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.akrcode.akr.configure.CustomDataSource;
import com.akrcode.akr.daoImpl.excelDaoimpl;
import com.akrcode.akr.dto.PdfReportDto;
import com.akrcode.akr.dto.SearchKeys;
import com.akrcode.akr.service.PurchaseService;

@RestController
@RequestMapping("/api/excel")
@CrossOrigin("*")
public class excelRestControler {

	@Autowired
	private excelDaoimpl excelDaoimpl;

	@Autowired
	private PurchaseService purchaseService;


	@PostMapping("/upload") 
	public Map<String, Object> readAndSaveAllSheetsFromPath() {
		return excelDaoimpl.readAndSaveAllSheetsFromPath();
	}

	@GetMapping("/{bom_code}")
	public PdfReportDto getExcelPdfvalues(@PathVariable("bom_code") String bomCode) {
		return excelDaoimpl.getExcelPdfvalues(bomCode);
	}

//	@PostMapping("/search")
//	public Map<String, Object> searchFilter() {
//		SearchKeys test = new SearchKeys();
//		return excelDaoimpl.searchFilter(test);
//	}

}
