package com.akrcode.akr.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "packing_actual")
public class PackingActualModel {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	private String bp_name;
	private String fp_name;
	private String fp_uom;
	private Integer input_qty;
	private String input_uom;
	private Integer actual_output;
	private String bp_code;
	private Integer orgid;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getBp_name() {
		return bp_name;
	}

	public void setBp_name(String bp_name) {
		this.bp_name = bp_name;
	}

	public String getFp_name() {
		return fp_name;
	}

	public void setFp_name(String fp_name) {
		this.fp_name = fp_name;
	}

	public String getFp_uom() {
		return fp_uom;
	}

	public void setFp_uom(String fp_uom) {
		this.fp_uom = fp_uom;
	}

	public Integer getInput_qty() {
		return input_qty;
	}

	public void setInput_qty(Integer input_qty) {
		this.input_qty = input_qty;
	}

	public String getInput_uom() {
		return input_uom;
	}

	public void setInput_uom(String input_uom) {
		this.input_uom = input_uom;
	}

	public Integer getActual_output() {
		return actual_output;
	}

	public void setActual_output(Integer actual_output) {
		this.actual_output = actual_output;
	}

	public String getBp_code() {
		return bp_code;
	}

	public void setBp_code(String bp_code) {
		this.bp_code = bp_code;
	}

	public Integer getOrgid() {
		return orgid;
	}

	public void setOrgid(Integer orgid) {
		this.orgid = orgid;
	}

}
