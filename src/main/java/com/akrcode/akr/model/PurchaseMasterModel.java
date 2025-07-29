package com.akrcode.akr.model;

import java.math.BigDecimal;
import java.sql.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_tracker")
public class PurchaseMasterModel {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	private String category;
	private String sub_category;
	private String code;
	private String product_name;
	private String supplier;
	private BigDecimal budget_qty;
	private BigDecimal budget_value;
	private Integer purcahsed_qty;
	private Integer purcahsed_value;
	private Integer min_stock_qty;
	private Integer max_stock_qty;
	private Integer moq;
	private Integer lead_time;
	private Integer schedule;
	private Integer stock_in_hand;
	private Integer stock_in_hand_value;
	private String status;
	private String remarks;
	private Date updated_at;
	private Date created_at;
	private Date date;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public String getSub_category() {
		return sub_category;
	}

	public void setSub_category(String sub_category) {
		this.sub_category = sub_category;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getProduct_name() {
		return product_name;
	}

	public void setProduct_name(String product_name) {
		this.product_name = product_name;
	}

	public String getSupplier() {
		return supplier;
	}

	public void setSupplier(String supplier) {
		this.supplier = supplier;
	}

	public BigDecimal getBudget_qty() {
		return budget_qty;
	}

	public void setBudget_qty(BigDecimal budget_qty) {
		this.budget_qty = budget_qty;
	}

	public BigDecimal getBudget_value() {
		return budget_value;
	}

	public void setBudget_value(BigDecimal budget_value) {
		this.budget_value = budget_value;
	}

	public Integer getPurcahsed_qty() {
		return purcahsed_qty;
	}

	public void setPurcahsed_qty(Integer purcahsed_qty) {
		this.purcahsed_qty = purcahsed_qty;
	}

	public Integer getPurcahsed_value() {
		return purcahsed_value;
	}

	public void setPurcahsed_value(Integer purcahsed_value) {
		this.purcahsed_value = purcahsed_value;
	}

	public Integer getMin_stock_qty() {
		return min_stock_qty;
	}

	public void setMin_stock_qty(Integer min_stock_qty) {
		this.min_stock_qty = min_stock_qty;
	}

	public Integer getMax_stock_qty() {
		return max_stock_qty;
	}

	public void setMax_stock_qty(Integer max_stock_qty) {
		this.max_stock_qty = max_stock_qty;
	}

	public Integer getMoq() {
		return moq;
	}

	public void setMoq(Integer moq) {
		this.moq = moq;
	}

	public Integer getLead_time() {
		return lead_time;
	}

	public void setLead_time(Integer lead_time) {
		this.lead_time = lead_time;
	}

	public Integer getSchedule() {
		return schedule;
	}

	public void setSchedule(Integer schedule) {
		this.schedule = schedule;
	}

	public Integer getStock_in_hand() {
		return stock_in_hand;
	}

	public void setStock_in_hand(Integer stock_in_hand) {
		this.stock_in_hand = stock_in_hand;
	}

	public Integer getStock_in_hand_value() {
		return stock_in_hand_value;
	}

	public void setStock_in_hand_value(Integer stock_in_hand_value) {
		this.stock_in_hand_value = stock_in_hand_value;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getRemarks() {
		return remarks;
	}

	public void setRemarks(String remarks) {
		this.remarks = remarks;
	}

	public Date getUpdated_at() {
		return updated_at;
	}

	public void setUpdated_at(Date updated_at) {
		this.updated_at = updated_at;
	}

	public Date getCreated_at() {
		return created_at;
	}

	public void setCreated_at(Date created_at) {
		this.created_at = created_at;
	}

	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		this.date = date;
	}

}
