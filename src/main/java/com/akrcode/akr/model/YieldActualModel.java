package com.akrcode.akr.model;

import java.math.BigDecimal;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "yield_actual")
public class YieldActualModel {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	private String bp_code;
	private String bp_name;
	private String yield_uom;
	private BigDecimal actual_yield_qty;
	private Integer orgid;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getBp_code() {
		return bp_code;
	}

	public void setBp_code(String bp_code) {
		this.bp_code = bp_code;
	}

	public String getBp_name() {
		return bp_name;
	}

	public void setBp_name(String bp_name) {
		this.bp_name = bp_name;
	}

	public String getYield_uom() {
		return yield_uom;
	}

	public void setYield_uom(String yield_uom) {
		this.yield_uom = yield_uom;
	}

	public BigDecimal getActual_yield_qty() {
		return actual_yield_qty;
	}

	public void setActual_yield_qty(BigDecimal actual_yield_qty) {
		this.actual_yield_qty = actual_yield_qty;
	}

	public Integer getOrgid() {
		return orgid;
	}

	public void setOrgid(Integer orgid) {
		this.orgid = orgid;
	}

}