package com.akrcode.akr.dto;

import java.time.LocalDate;
import java.util.Map;

public class IndentVsDeliveryDataDto {
	private LocalDate reportDate;
	private Map<String, Object> indentQty;
	private Map<String, Object> availableQty;
	private Map<String, Object> requiredQty;
	private Map<String, Object> plannedQty;
	private Map<String, Object> packedQty;
	private Map<String, Object> dispatchedQty;
	private Map<String, Object> receivedQty;

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

	public LocalDate getReportDate() {
		return reportDate;
	}

	public void setReportDate(LocalDate reportDate) {
		this.reportDate = reportDate;
	}

	public Map<String, Object> getIndentQty() {
		return indentQty;
	}

	public void setIndentQty(Map<String, Object> indentQty) {
		this.indentQty = indentQty;
	}

	public Map<String, Object> getAvailableQty() {
		return availableQty;
	}

	public void setAvailableQty(Map<String, Object> availableQty) {
		this.availableQty = availableQty;
	}

	public Map<String, Object> getRequiredQty() {
		return requiredQty;
	}

	public void setRequiredQty(Map<String, Object> requiredQty) {
		this.requiredQty = requiredQty;
	}

	public Map<String, Object> getPlannedQty() {
		return plannedQty;
	}

	public void setPlannedQty(Map<String, Object> plannedQty) {
		this.plannedQty = plannedQty;
	}

	public Map<String, Object> getPackedQty() {
		return packedQty;
	}

	public void setPackedQty(Map<String, Object> packedQty) {
		this.packedQty = packedQty;
	}

	public Map<String, Object> getDispatchedQty() {
		return dispatchedQty;
	}

	public void setDispatchedQty(Map<String, Object> dispatchedQty) {
		this.dispatchedQty = dispatchedQty;
	}

	public Map<String, Object> getReceivedQty() {
		return receivedQty;
	}

	public void setReceivedQty(Map<String, Object> receivedQty) {
		this.receivedQty = receivedQty;
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
