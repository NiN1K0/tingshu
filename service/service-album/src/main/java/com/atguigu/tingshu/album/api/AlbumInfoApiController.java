package com.atguigu.tingshu.album.api;

import com.atguigu.tingshu.album.service.AlbumInfoService;
import com.atguigu.tingshu.common.login.TSLogin;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.query.album.AlbumInfoQuery;
import com.atguigu.tingshu.vo.album.AlbumInfoVo;
import com.atguigu.tingshu.vo.album.AlbumListVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "专辑管理")
@RestController
@RequestMapping("api/album")
@SuppressWarnings({"all"})
public class AlbumInfoApiController {

	@Autowired
	private AlbumInfoService albumInfoService;
	@Autowired
	private AuthContextHolder authContextHolder;

	@Operation(summary = "内容创作者或者平台运营人员-保存专辑")
	@PostMapping("/albumInfo/saveAlbumInfo")
	@TSLogin
	public Result saveAlbumInfo(@RequestBody AlbumInfoVo albumInfoVo) {
		Long userId = authContextHolder.getUserId();
		albumInfoService.saveAlbumInfo(albumInfoVo, userId);
		return Result.ok();
	}

	@Operation(summary = "查看当前用户专辑分页列表")
	@TSLogin(required = true)
	@PostMapping("/albumInfo/findUserAlbumPage/{page}/{limit}")
	public Result<IPage<AlbumListVo>> findUserAlbumPage(@PathVariable Long page, @PathVariable Long limit,@RequestBody AlbumInfoQuery albumInfoQuery){
		Long userId = AuthContextHolder.getUserId();
		IPage<AlbumListVo> pageInfo = new Page<>(page, limit);
		albumInfoQuery.setUserId(userId);
		pageInfo = albumInfoService.findUserAlbumPage(pageInfo, albumInfoQuery);
		return Result.ok(pageInfo);
	}

	@Operation(summary = "根据ID删除专辑")
	@DeleteMapping("/albumInfo/removeAlbumInfo/{id}")
	public Result removeAlbumInfo(@PathVariable Long id) {
		albumInfoService.removeAlbumInfo(id);
		return Result.ok();
	}

	@Operation(summary = "根据ID查询专辑信息")
	@GetMapping("/albumInfo/getAlbumInfo/{id}")
	public Result getAlbumInfo(@PathVariable Long id) {
		AlbumInfo albumInfo = albumInfoService.getAlbumInfo(id);
		return Result.ok(albumInfo);
	}

	@Operation(summary = "修改专辑")
	@PutMapping("/albumInfo/updateAlbumInfo/{id}")
	public Result updateAlbumInfo(@PathVariable Long id, @RequestBody @Validated AlbumInfoVo albumInfoVo) {
		albumInfoService.updateAlbumInfo(id, albumInfoVo);
		return Result.ok();
	}


	@Operation(summary = "获取当前用户全部专辑列表")
	@GetMapping("/albumInfo/findUserAllAlbumList")
	@TSLogin
	public Result<List<AlbumInfo>> findUserAllAlbumList() {
		Long userId = AuthContextHolder.getUserId();
		List<AlbumInfo> list = albumInfoService.findUserAllAlbumList(userId);
		return Result.ok(list);
	}
}

