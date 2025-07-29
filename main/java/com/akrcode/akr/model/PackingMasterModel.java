package com.akrcode.akr.model;

import java.math.BigDecimal;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "packing_master")
public class PackingMasterModel {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	private Integer fp_code;
	private String fp_name;
	private Integer fp_qty;
	private String fp_uom;
	private String input_code;
	private String input_name;
	private BigDecimal input_capacity;
	private String input_uom;
	private Integer orgid;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Integer getFp_code() {
		return fp_code;
	}

	public void setFp_code(Integer fp_code) {
		this.fp_code = fp_code;
	}

	public String getFp_name() {
		return fp_name;
	}

	public void setFp_name(String fp_name) {
		this.fp_name = fp_name;
	}

	public Integer getFp_qty() {
		return fp_qty;
	}

	public void setFp_qty(Integer fp_qty) {
		this.fp_qty = fp_qty;
	}

	public String getFp_uom() {
		return fp_uom;
	}

	public void setFp_uom(String fp_uom) {
		this.fp_uom = fp_uom;
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

	public BigDecimal getInput_capacity() {
		return input_capacity;
	}

	public void setInput_capacity(BigDecimal input_capacity) {
		this.input_capacity = input_capacity;
	}

	public String getInput_uom() {
		return input_uom;
	}

	public void setInput_uom(String input_uom) {
		this.input_uom = input_uom;
	}

	public Integer getOrgid() {
		return orgid;
	}

	public void setOrgid(Integer orgid) {
		this.orgid = orgid;
	}

}
