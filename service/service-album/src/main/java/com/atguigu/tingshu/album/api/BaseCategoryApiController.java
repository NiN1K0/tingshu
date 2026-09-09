package com.atguigu.tingshu.album.api;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.tingshu.album.service.BaseCategoryService;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.album.BaseAttributeValue;
import com.atguigu.tingshu.model.album.BaseCategoryView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@Tag(name = "分类管理")
@RestController
@RequestMapping(value="/api/album")
@SuppressWarnings({"all"})
public class BaseCategoryApiController {

	@Autowired
	private BaseCategoryService baseCategoryService;

	@Operation(summary = "获取所有一级分类（包含二级分类，三级分类）")
	@GetMapping("/category/getBaseCategoryList")
	public Result<List<JSONObject>> getBaseCategoryList() {
		List<JSONObject> list = baseCategoryService.getBaseCategoryList();
		return Result.ok(list);
	}
	@Operation(summary = "根据一级分类Id获取分类属性以及属性值（标签名，标签值）列表")
	@GetMapping("/category/findAttribute/{category1Id}")
	public Result<List<BaseAttributeValue>> getAttributeByCategory1Id(@PathVariable Long category1Id) {
		List<BaseAttributeValue> list = baseCategoryService.getAttributeByCategory1Id(category1Id);
		return Result.ok(list);
	}

	@Operation(summary = "根据三级分类Id 获取到分类信息")
	@GetMapping("/category/getCategoryView/{category3Id}")
	public Result<BaseCategoryView> getCategoryView(@PathVariable Long category3Id) {
		BaseCategoryView baseCategoryView = baseCategoryService.getCategoryView(category3Id);
		return Result.ok(baseCategoryView);
	}
}

