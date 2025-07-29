package com.akrcode.akr.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "bom_master")
public class BomMasterModel {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	private String bom_code;
	private String bom_name;
	private Integer set_qty;
	private String set_uom;
	private String input_code;
	private String input_name;
	private Integer input_qty;
	private String input_uom;
	private Integer rate;
	private String amount;
	private Integer orgid;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getBom_code() {
		return bom_code;
	}

	public void setBom_code(String bom_code) {
		this.bom_code = bom_code;
	}

	public String getBom_name() {
		return bom_name;
	}

	public void setBom_name(String bom_name) {
		this.bom_name = bom_name;
	}

	public Integer getSet_qty() {
		return set_qty;
	}

	public void setSet_qty(Integer set_qty) {
		this.set_qty = set_qty;
	}

	public String getSet_uom() {
		return set_uom;
	}

	public void setSet_uom(String set_uom) {
		this.set_uom = set_uom;
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

	public Integer getRate() {
		return rate;
	}

	public void setRate(Integer rate) {
		this.rate = rate;
	}

	public String getAmount() {
		return amount;
	}

	public void setAmount(String amount) {
		this.amount = amount;
	}

	public Integer getOrgid() {
		return orgid;
	}

	public void setOrgid(Integer orgid) {
		this.orgid = orgid;
	}

}