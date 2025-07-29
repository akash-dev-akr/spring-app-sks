package com.akrcode.akr.dao;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

public interface IndentVsDeliveryDao {

	Map<String, Object> listAllProductData(LocalDate reportDate, String category, String product, String section,
			String packFormat, Map<String, String> differenceFilter);

	String updateFieldByCode(String code, String field, String value, LocalDate reportDate);

	String updateReason(String code, String field, String value, LocalDate reportDate);

	byte[] uploadQuantity(MultipartFile file, String quantityType);

	ResponseEntity<ByteArrayResource> downloadQuantityTemplate(String type);

	ResponseEntity<ByteArrayResource> overallIndentTemplate();

}
