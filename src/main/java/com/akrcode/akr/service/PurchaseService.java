package com.akrcode.akr.service;

import java.io.InputStream;
import java.util.Map;

import org.springframework.web.multipart.MultipartFile;

import com.akrcode.akr.dto.PurchaseStatusUpdate;
import com.akrcode.akr.dto.SearchKeys;

public interface PurchaseService {

	Map<String, Object> readAndSaveFromFile(MultipartFile file);

	Map<String, Object> searchFilter(SearchKeys test);

	Map<String, Object> statusUpdate(PurchaseStatusUpdate status);

	byte[] getSampleStockTemplate();

	byte[] getSamplePurcahsedTemplate();

	byte[] uploadPurcahseExcel(InputStream inputStream);

	byte[] uploadStocksExcel(InputStream inputStream);

	Map<String, Object> PurchaseUpload(MultipartFile file);

}
