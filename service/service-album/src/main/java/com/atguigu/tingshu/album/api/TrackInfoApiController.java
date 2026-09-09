package com.atguigu.tingshu.album.api;

import com.atguigu.tingshu.album.service.TrackInfoService;
import com.atguigu.tingshu.album.service.VodService;
import com.atguigu.tingshu.common.login.TSLogin;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.query.album.TrackInfoQuery;
import com.atguigu.tingshu.vo.album.TrackInfoVo;
import com.atguigu.tingshu.vo.album.TrackListVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Tag(name = "声音管理")
@RestController
@RequestMapping("api/album")
@SuppressWarnings({"all"})
public class TrackInfoApiController {

	@Autowired
	private TrackInfoService trackInfoService;

	@Autowired
	private VodService vodService;

	@Operation(summary = "上传声音")
	@PostMapping("/trackInfo/uploadTrack")
	public Result<Map<String,String>> uploadTrack(@RequestParam("file") MultipartFile file) {
		Map<String,String> map = vodService.uploadTrack(file);
		return Result.ok(map);
	}

	@Operation(summary = "保存声音")
	@PostMapping("/trackInfo/saveTrackInfo")
	@TSLogin
	public Result saveTrackInfo(@RequestBody @Validated TrackInfoVo trackInfoVo) {
		Long userId = AuthContextHolder.getUserId();
		trackInfoService.saveTrackInfo(trackInfoVo, userId);
		return Result.ok();
	}
	@Operation(summary = "获取当前用户声音分页列表")
	@PostMapping("/trackInfo/findUserTrackPage/{page}/{limit}")
	@TSLogin
	public Result findUserTrackPage(@PathVariable Long page, @PathVariable Long limit, @RequestBody TrackInfoQuery trackInfoQuery) {
		Long userId = AuthContextHolder.getUserId();
		trackInfoQuery.setUserId(userId);
		IPage<TrackListVo> pageInfo = new Page<>(page, limit);
		pageInfo = trackInfoService.findUserTrackPage(pageInfo, trackInfoQuery);
		return Result.ok(pageInfo);
	}

	@Operation(summary = "查询声音信息")
	@GetMapping("/trackInfo/getTrackInfo/{id}")
	public Result getTrackInfo(@PathVariable Long id) {
		TrackInfo trackInfo = trackInfoService.getById(id);
		return Result.ok(trackInfo);
	}

	@Operation(summary = "修改声音信息")
	@PutMapping("/trackInfo/updateTrackInfo/{id}")
	public Result updateTrackInfo(@PathVariable Long id, @RequestBody @Validated TrackInfoVo trackInfoVo) {
		trackInfoService.updateTrackInfo(id,trackInfoVo);
		return Result.ok();
	}

	@Operation(summary = "删除声音信息")
	@DeleteMapping("/trackInfo/removeTrackInfo/{id}")
	public Result removeTrackInfo(@PathVariable Long id) {
		trackInfoService.removeTrackInfo(id);
		return Result.ok();
	}
}

