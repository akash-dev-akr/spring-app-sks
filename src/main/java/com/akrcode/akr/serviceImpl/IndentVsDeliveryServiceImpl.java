package com.akrcode.akr.serviceImpl;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.akrcode.akr.dao.IndentVsDeliveryDao;
import com.akrcode.akr.service.IndentVsDeliveryService;

@Service
public class IndentVsDeliveryServiceImpl implements IndentVsDeliveryService {
	@Autowired
	IndentVsDeliveryDao indentVsDeliveryDao;

	@Override
	public String updateFieldByCode(String code, String field, String value, LocalDate reportDate) {

		return indentVsDeliveryDao.updateFieldByCode(code, field, value, reportDate);

	}

	@Override
	public Map<String, Object> listAllProductData(LocalDate reportDate, String category, String product, String section,
			String packFormat,Map<String, String>  differenceFilter) {

		return indentVsDeliveryDao.listAllProductData(reportDate, category, product, section, packFormat,differenceFilter);
	}

	@Override
	public String updateReason(String code, String field, String value, LocalDate reportDate) {
		return indentVsDeliveryDao.updateReason(code, field, value, reportDate);
	}

	@Override
	public byte[] uploadQuantity(MultipartFile file, String quantityType) {
		return indentVsDeliveryDao.uploadQuantity(file, quantityType);
	}

	@Override
	public ResponseEntity<ByteArrayResource> downloadQuantityTemplate(String type) {
		return indentVsDeliveryDao.downloadQuantityTemplate(type);
	}

	@Override
	public ResponseEntity<ByteArrayResource> overallIndentTemplate() {
		return indentVsDeliveryDao.overallIndentTemplate();
	}

}
