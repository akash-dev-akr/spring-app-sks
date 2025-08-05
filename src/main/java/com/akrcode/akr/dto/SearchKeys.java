package com.akrcode.akr.dto;

public class SearchKeys {

	private String category;
	private String subcategory;
	private String supplier;
	private String status;
	private String date;
	private int limit;
	private int page;
	private String summaryKey;

	public String getSummaryKey() {
		return summaryKey;
	}

	public void setSummaryKey(String summaryKey) {
		this.summaryKey = summaryKey;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public String getSubcategory() {
		return subcategory;
	}

	public void setSubcategory(String subcategory) {
		this.subcategory = subcategory;
	}

	public String getSupplier() {
		return supplier;
	}

	public void setSupplier(String supplier) {
		this.supplier = supplier;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getDate() {
		return date;
	}

	public void setDate(String date) {
		this.date = date;
	}

	public int getLimit() {
		return limit;
	}

	public void setLimit(int limit) {
		this.limit = limit;
	}

	public int getPage() {
		return page;
	}

	public void setPage(int page) {
		this.page = page;
	}

	

}
