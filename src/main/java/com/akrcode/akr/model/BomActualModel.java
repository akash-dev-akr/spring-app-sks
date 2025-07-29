package com.akrcode.akr.model;

import java.math.BigDecimal;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "bom_actual")
public class BomActualModel {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	private String finished_product_code;
	private String finished_product;
	private String product;
	private String base_unit;
	private BigDecimal qty_in_base_unit;
	private BigDecimal unit_rate;
	private BigDecimal gross_amount;
	private Integer orgid;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getFinished_product_code() {
		return finished_product_code;
	}

	public void setFinished_product_code(String finished_product_code) {
		this.finished_product_code = finished_product_code;
	}

	public String getFinished_product() {
		return finished_product;
	}

	public void setFinished_product(String finished_product) {
		this.finished_product = finished_product;
	}

	public String getProduct() {
		return product;
	}

	public void setProduct(String product) {
		this.product = product;
	}

	public String getBase_unit() {
		return base_unit;
	}

	public void setBase_unit(String base_unit) {
		this.base_unit = base_unit;
	}

	public BigDecimal getQty_in_base_unit() {
		return qty_in_base_unit;
	}

	public void setQty_in_base_unit(BigDecimal qty_in_base_unit) {
		this.qty_in_base_unit = qty_in_base_unit;
	}

	public BigDecimal getUnit_rate() {
		return unit_rate;
	}

	public void setUnit_rate(BigDecimal unit_rate) {
		this.unit_rate = unit_rate;
	}

	public BigDecimal getGross_amount() {
		return gross_amount;
	}

	public void setGross_amount(BigDecimal gross_amount) {
		this.gross_amount = gross_amount;
	}

	public Integer getOrgid() {
		return orgid;
	}

	public void setOrgid(Integer orgid) {
		this.orgid = orgid;
	}

}