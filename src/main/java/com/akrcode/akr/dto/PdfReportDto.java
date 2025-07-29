package com.akrcode.akr.dto;

import java.util.List;

public class PdfReportDto {
	private String product;
	private String bom_code;
	private List<BomDetailsDto> bom_details;
	private List<BomCostComparisonDto> bom_cost_comparison;
	private List<FinishedGoodsVariantsDto> finished_goods_variants;

	public String getBom_code() {
		return bom_code;
	}

	public void setBom_code(String bom_code) {
		this.bom_code = bom_code;
	}

	public String getProduct() {
		return product;
	}

	public void setProduct(String product) {
		this.product = product;
	}

	public List<BomDetailsDto> getBom_details() {
		return bom_details;
	}

	public void setBom_details(List<BomDetailsDto> bom_details) {
		this.bom_details = bom_details;
	}

	public List<BomCostComparisonDto> getBom_cost_comparison() {
		return bom_cost_comparison;
	}

	public void setBom_cost_comparison(List<BomCostComparisonDto> bom_cost_comparison) {
		this.bom_cost_comparison = bom_cost_comparison;
	}

	public List<FinishedGoodsVariantsDto> getFinished_goods_variants() {
		return finished_goods_variants;
	}

	public void setFinished_goods_variants(List<FinishedGoodsVariantsDto> finished_goods_variants) {
		this.finished_goods_variants = finished_goods_variants;
	}

}
