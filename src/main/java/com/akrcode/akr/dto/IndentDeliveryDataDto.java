package com.akrcode.akr.dto;

import java.time.LocalDate;

public class IndentDeliveryDataDto {
	private int id;
	private LocalDate reportDate;

	private String category;
	private String packFormat;
	private String section;
	private String code;
	private String product;

	private String indentQtyJson;
	private String availableQtyJson;
	private String requiredQtyJson;
	private String plannedQtyJson;
	private String packedQtyJson;
	private String dispatchedQtyJson;
	private String receivedQtyJson;

	private String reason;
	private String difference;
	private String plannedDifference;
	private String plannedReason;
	private String packedDifference;
	private String packedReason;
	private String dispatchedDifference;
	private String dispatchedReason;
	private String receivedDifference;
	private String receivedReason;

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public LocalDate getReportDate() {
		return reportDate;
	}

	public void setReportDate(LocalDate localDate) {
		this.reportDate = localDate;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public String getPackFormat() {
		return packFormat;
	}

	public void setPackFormat(String packFormat) {
		this.packFormat = packFormat;
	}

	public String getSection() {
		return section;
	}

	public void setSection(String section) {
		this.section = section;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getProduct() {
		return product;
	}

	public void setProduct(String product) {
		this.product = product;
	}

	public String getIndentQtyJson() {
		return indentQtyJson;
	}

	public void setIndentQtyJson(String indentQtyJson) {
		this.indentQtyJson = indentQtyJson;
	}

	public String getAvailableQtyJson() {
		return availableQtyJson;
	}

	public void setAvailableQtyJson(String availableQtyJson) {
		this.availableQtyJson = availableQtyJson;
	}

	public String getRequiredQtyJson() {
		return requiredQtyJson;
	}

	public void setRequiredQtyJson(String requiredQtyJson) {
		this.requiredQtyJson = requiredQtyJson;
	}

	public String getPlannedQtyJson() {
		return plannedQtyJson;
	}

	public void setPlannedQtyJson(String plannedQtyJson) {
		this.plannedQtyJson = plannedQtyJson;
	}

	public String getPackedQtyJson() {
		return packedQtyJson;
	}

	public void setPackedQtyJson(String packedQtyJson) {
		this.packedQtyJson = packedQtyJson;
	}

	public String getDispatchedQtyJson() {
		return dispatchedQtyJson;
	}

	public void setDispatchedQtyJson(String dispatchedQtyJson) {
		this.dispatchedQtyJson = dispatchedQtyJson;
	}

	public String getReceivedQtyJson() {
		return receivedQtyJson;
	}

	public void setReceivedQtyJson(String receivedQtyJson) {
		this.receivedQtyJson = receivedQtyJson;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public String getDifference() {
		return difference;
	}

	public void setDifference(String difference) {
		this.difference = difference;
	}

	public String getPlannedDifference() {
		return plannedDifference;
	}

	public void setPlannedDifference(String plannedDifference) {
		this.plannedDifference = plannedDifference;
	}

	public String getPlannedReason() {
		return plannedReason;
	}

	public void setPlannedReason(String plannedReason) {
		this.plannedReason = plannedReason;
	}

	public String getPackedDifference() {
		return packedDifference;
	}

	public void setPackedDifference(String packedDifference) {
		this.packedDifference = packedDifference;
	}

	public String getPackedReason() {
		return packedReason;
	}

	public void setPackedReason(String packedReason) {
		this.packedReason = packedReason;
	}

	public String getDispatchedDifference() {
		return dispatchedDifference;
	}

	public void setDispatchedDifference(String dispatchedDifference) {
		this.dispatchedDifference = dispatchedDifference;
	}

	public String getDispatchedReason() {
		return dispatchedReason;
	}

	public void setDispatchedReason(String dispatchedReason) {
		this.dispatchedReason = dispatchedReason;
	}

	public String getReceivedDifference() {
		return receivedDifference;
	}

	public void setReceivedDifference(String receivedDifference) {
		this.receivedDifference = receivedDifference;
	}

	public String getReceivedReason() {
		return receivedReason;
	}

	public void setReceivedReason(String receivedReason) {
		this.receivedReason = receivedReason;
	}

}
