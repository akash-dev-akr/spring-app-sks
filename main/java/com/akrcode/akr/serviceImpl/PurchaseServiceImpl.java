package com.akrcode.akr.serviceImpl;

import java.io.InputStream;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.akrcode.akr.dao.PurchaseDao;
import com.akrcode.akr.dto.PurchaseStatusUpdate;
import com.akrcode.akr.dto.SearchKeys;
import com.akrcode.akr.service.PurchaseService;

@Service
public class PurchaseServiceImpl  implements  PurchaseService{
	@Autowired
	private PurchaseDao purchaseDao;

	@Override
	public Map<String, Object> readAndSaveFromFile(MultipartFile file) {
		// TODO Auto-generated method stub
		return purchaseDao.readAndSaveFromFile(file);
	}

	@Override
	public Map<String, Object> searchFilter(SearchKeys test) {
		return purchaseDao.searchFilter(test);
	}

	@Override
	public Map<String, Object> statusUpdate(PurchaseStatusUpdate status) {
		return purchaseDao.statusUpdate(status);
	}

	@Override
	public byte[] getSampleStockTemplate() {
		return purchaseDao.getSampleStockTemplate( );
	}	@Override
	public byte[] getSamplePurcahsedTemplate() {
		return purchaseDao.getSamplePurcahsedTemplate( );
	}
	@Override
	public byte[] uploadPurcahseExcel(InputStream inputStream) {
		return purchaseDao.uploadPurcahseExcel(inputStream);
	}

	@Override
	public byte[] uploadStocksExcel(InputStream inputStream) {
		return purchaseDao.uploadStocksExcel(inputStream);
	}

	@Override
	public Map<String, Object> PurchaseUpload(MultipartFile file) {
		return purchaseDao.PurchaseUpload(file);
	}

}
