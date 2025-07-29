package com.akrcode.akr.model;

import java.math.BigDecimal;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "yield_master")
public class YieldMasterModel {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	private String bp_code;
	private String bp_name;
	private BigDecimal yield_qty;
	private String yield_uom;
	private String input_code;
	private String input_name;
	private String input_qty;
	private Integer unput_uom;
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

	public BigDecimal getYield_qty() {
		return yield_qty;
	}

	public void setYield_qty(BigDecimal yield_qty) {
		this.yield_qty = yield_qty;
	}

	public String getYield_uom() {
		return yield_uom;
	}

	public void setYield_uom(String yield_uom) {
		this.yield_uom = yield_uom;
	}

	public String getInput_code() {
		return input_code;
	}

	public void setInput_code(String input_code) {
		this.input_code = input_code;
	}

	public String getInput_name() {
		return input_name;
	}

	public void setInput_name(String input_name) {
		this.input_name = input_name;
	}

	public String getInput_qty() {
		return input_qty;
	}

	public void setInput_qty(String input_qty) {
		this.input_qty = input_qty;
	}

	public Integer getUnput_uom() {
		return unput_uom;
	}

	public void setUnput_uom(Integer unput_uom) {
		this.unput_uom = unput_uom;
	}

	public Integer getOrgid() {
		return orgid;
	}

	public void setOrgid(Integer orgid) {
		this.orgid = orgid;
	}

}