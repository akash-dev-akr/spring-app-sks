package com.akrcode.akr.dto;

public class BomCostComparisonDto {
	private String ingredient;
	private String rate;
	private String ideal_qty;
	private String ideal_cost;
	private String actual_qty;
	private String actual_cost;
	private String variance;
	private String id;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getIngredient() {
		return ingredient;
	}

	public void setIngredient(String ingredient) {
		this.ingredient = ingredient;
	}

	public String getRate() {
		return rate;
	}

	public void setRate(String rate) {
		this.rate = rate;
	}

	public String getIdeal_qty() {
		return ideal_qty;
	}

	public void setIdeal_qty(String ideal_qty) {
		this.ideal_qty = ideal_qty;
	}

	public String getIdeal_cost() {
		return ideal_cost;
	}

	public void setIdeal_cost(String ideal_cost) {
		this.ideal_cost = ideal_cost;
	}

	public String getActual_qty() {
		return actual_qty;
	}

	public void setActual_qty(String actual_qty) {
		this.actual_qty = actual_qty;
	}

	public String getActual_cost() {
		return actual_cost;
	}

	public void setActual_cost(String actual_cost) {
		this.actual_cost = actual_cost;
	}

	public String getVariance() {
		return variance;
	}

	public void setVariance(String variance) {
		this.variance = variance;
	}

}
