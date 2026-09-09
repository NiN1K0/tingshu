# [ CLAUDE.md](http://claude.md/) — 听书项目 (tingshu-parent)

> 本文件供 Claude Code 读取，用于理解项目结构、编码约定和技术栈，确保生成的代码与项目风格一致。

------

## 项目简介

仿喜马拉雅的音频平台（微服务版），包含专辑/声音管理、用户体系、搜索、订单、支付、账户余额等核心业务。
**教学项目**，大量 ServiceImpl 是空实现骨架，待逐步填充。

------

## 技术栈

| 分类          | 技术                                                         |
| ------------- | ------------------------------------------------------------ |
| 框架          | Spring Boot 3.0.5、Spring Cloud 2022.0.2、Spring Cloud Alibaba 2022.0.0.0-RC1 |
| 服务注册/配置 | Nacos 8848（地址：192.168.200.6:8848）                       |
| 持久层        | MyBatis-Plus 3.5.3.1 + MySQL 8                               |
| 缓存          | Redis + Redisson（分布式锁）                                 |
| 消息队列      | RabbitMQ（可靠投递机制，含延迟队列）                         |
| 搜索          | Elasticsearch（Spring Data ES，索引名 `albuminfo`，IK 分词） |
| 远程调用      | OpenFeign（含 Sentinel 降级）                                |
| 对象存储      | Minio（图片）、腾讯云 VOD（音频）                            |
| 定时任务      | xxl-job                                                      |
| 支付          | 微信支付 V3                                                  |
| 登录          | 微信小程序（wx-java-miniapp）                                |
| 接口文档      | Knife4j（OpenAPI 3）                                         |
| Java          | 17                                                           |

------

## 模块结构

```
tingshu-parent/
├── common/                        # 公共基础模块
│   ├── common-util/               # Result、工具类、AuthContextHolder
│   ├── common-log/                # 操作日志切面
│   ├── rabbit-util/               # RabbitMQ 可靠投递（RabbitService、MqConst）
│   └── service-util/              # 各微服务公共配置（Redis/MP/Knife4j/异常/登录切面）
├── model/                         # 所有实体类
│   ├── model/                     # ORM 实体（album/user/order/account/search...）
│   ├── vo/                        # 视图对象（传输用）
│   └── query/                     # 分页查询参数对象
├── service-client/                # OpenFeign 接口定义
│   ├── service-album-client/      # AlbumFeignClient
│   ├── service-user-client/       # UserFeignClient
│   ├── service-order-client/      # OrderFeignClient
│   ├── service-account-client/    # AccountFeignClient
│   └── service-search-client/     # SearchFeignClient
├── service/                       # 业务微服务
│   ├── service-album/             # 端口 8501：专辑、声音、分类、文件上传、内容审核
│   ├── service-user/              # 端口 ？：用户、微信登录、VIP、收藏、收听进度
│   ├── service-search/            # 端口 ？：ES 搜索、专辑上下架同步
│   ├── service-order/             # 端口 ？：订单、结算、优惠
│   ├── service-account/           # 端口 ？：余额、充值、扣款
│   ├── service-payment/           # 端口 ？：微信支付回调
│   └── service-dispatch/         # 端口 ？：xxl-job 定时任务
└── server-gateway/                # 网关（Spring Cloud Gateway），端口统一入口
```

------

## 关键编码约定

### 1. 统一返回值 `Result<T>`

所有 Controller 返回 `Result<T>`，禁止直接返回实体或 void。

```
// 成功（有数据）
return Result.ok(data);

// 成功（无数据，增删改）
return Result.ok();

// 业务失败（抛异常，全局处理器统一捕获）
throw new GuiguException(ResultCodeEnum.ARGUMENT_VALID_ERROR);
throw new GuiguException(400, "专辑不存在");
```

`Result` 结构：`{ code: int, message: string, data: T }`
成功 code = 200，失败 code 见 `ResultCodeEnum`。

------

### 2. 异常处理

- 业务异常统一抛 `GuiguException`（在 `common-util` 模块）。
- 全局处理器 `GlobalExceptionHandler` 在 `service-util` 中，已自动捕获 `GuiguException`、`BindException`、`MethodArgumentNotValidException`。
- **不要** 在 Service 里 try-catch 后吞掉异常，让它往上抛。
- 事务方法必须写 `@Transactional(rollbackFor = Exception.class)`，扩大回滚范围。

------

### 3. 登录鉴权

接口需要登录时，在 Controller 方法上加 `@GuiGuLogin` 注解（在 `service-util` 中）：

```
// 必须登录（未登录返回 208 状态码，小程序引导登录）
@GuiGuLogin(required = true)
@PostMapping("/submitOrder")
public Result<Long> submitOrder(...) { ... }

// 可选登录（已登录则注入 userId，未登录则 userId 为 null）
@GuiGuLogin(required = false)
@GetMapping("/getAlbumInfo/{id}")
public Result<AlbumInfoVo> getAlbumInfo(...) { ... }
```

在 Service 中获取当前登录用户 ID：

```
Long userId = AuthContextHolder.getUserId(); // ThreadLocal，用完自动清理
```

**注意**：网关只透传 token，不拦截。真正的鉴权在各微服务的切面 `GuiGuLoginAspect` 中完成。

------

### 4. Redis 操作规范

**所有 Redis key 必须使用 RedisConstant 常量，禁止硬编码字符串。**

常用常量（`common.service-util/src/main/java/.../common/constant/RedisConstant.java`）：

```
RedisConstant.ALBUM_INFO_PREFIX         // "album:info:"
RedisConstant.USER_LOGIN_KEY_PREFIX     // "user:login:"
RedisConstant.ORDER_TRADE_NO_PREFIX     // "user:trade:"
RedisConstant.ACCOUNT_MUTIPLE_CHECK     // "account:rechecklock:"
RedisConstant.RANKING_KEY_PREFIX        // "ranking:"
// ... 详见完整类文件
```

缓存失效时间也使用常量：`RedisConstant.ALBUM_TIMEOUT`（1小时）、`RedisConstant.CACHE_TEMPORARY_TIMEOUT`（空对象10分钟）。

分布式锁使用 Redisson（已在 `service-util` 自动配置），不要用 Redis 的 `setIfAbsent` 手写锁。

------

### 5. RabbitMQ 消息发送规范

**必须使用 RabbitService 工具类发送消息**，禁止直接注入 `RabbitTemplate`。

```
@Autowired
private RabbitService rabbitService;

// 普通消息
rabbitService.sendMessage(MqConst.EXCHANGE_ALBUM, MqConst.ROUTING_ALBUM_UPPER, albumId);

// 延迟消息（如订单15分钟超时取消）
rabbitService.sendDelayMessage(
    MqConst.EXCHANGE_CANCEL_ORDER,
    MqConst.ROUTING_CANCEL_ORDER,
    orderId,
    MqConst.CANCEL_ORDER_DELAY_TIME  // 15 * 60 秒
);
```

消息对象必须实现 `Serializable`。`RabbitService` 内部使用 `GuiguCorrelationData` + Redis 实现可靠投递。

**MqConst 常量速查**（`common/rabbit-util/.../common/rabbit/constant/MqConst.java`）：

```
// 专辑上下架（album -> search）
EXCHANGE_ALBUM / ROUTING_ALBUM_UPPER / ROUTING_ALBUM_LOWER

// 专辑/声音统计更新（album -> album, album -> search）
ROUTING_ALBUM_STAT_UPDATE / ROUTING_ALBUM_ES_STAT_UPDATE / ROUTING_TRACK_STAT_UPDATE

// 订单超时取消（延迟队列，15分钟）
EXCHANGE_CANCEL_ORDER / ROUTING_CANCEL_ORDER

// 支付成功（payment -> order/account）
EXCHANGE_ORDER / ROUTING_ORDER_PAY_SUCCESS / ROUTING_RECHARGE_PAY_SUCCESS

// 账户余额操作（order -> account）
EXCHANGE_ACCOUNT / ROUTING_ACCOUNT_UNLOCK / ROUTING_ACCOUNT_MINUS

// 用户注册初始化账户（user -> account）
EXCHANGE_USER / ROUTING_USER_REGISTER

// 购买记录写入（order -> user）
ROUTING_USER_PAY_RECORD
```

------

### 6. OpenFeign 调用规范

微服务之间通过 Feign 调用，接口在 `service-client` 模块：

```
@Autowired
private AlbumFeignClient albumFeignClient;

@Autowired
private UserFeignClient userFeignClient;

// 调用后取 data，注意判空
AlbumInfo albumInfo = albumFeignClient.getAlbumInfo(albumId).getData();
UserInfoVo userInfoVo = userFeignClient.getUserInfoVo(userId).getData();
```

每个 Feign 接口都有对应的降级类（`XxxDegradeFeignClient`），降级时返回 `Result.fail()` 或空对象，不抛异常。
**新增 Feign 方法时，降级类也要同步实现，返回安全的空值。**

Feign 请求头透传（已在 `service-util` 的 `FeignInterceptor` 中配置，自动传递 `token` 头）。

------

### 7. MyBatis-Plus 使用规范

- Service 继承 `ServiceImpl<Mapper, Entity>`，可直接用 `save`、`updateById`、`removeById` 等。
- 复杂查询用 `LambdaQueryWrapper`，禁止写硬编码字段名字符串。
- 分页用 `IPage<T>` + `Page<T>` 对象，分页插件已在 `MybatisPlusConfig` 配置。
- 返回前端的分页数据用 `PageVo` 包装（在 `model/vo/base/PageVo.java`）。
- 逻辑删除字段 `is_deleted`，已在 `BaseEntity` 中配置 `@TableLogic`，查询自动过滤。

```
// 分页示例
IPage<AlbumInfo> page = new Page<>(pageNum, pageSize);
albumInfoMapper.selectPage(page, new LambdaQueryWrapper<AlbumInfo>()
    .eq(AlbumInfo::getUserId, userId)
    .orderByDesc(AlbumInfo::getCreateTime));
return new PageVo<>(page.getRecords(), page.getTotal());
```

------

### 8. 状态码常量

**所有状态字符串使用 SystemConstant 常量，禁止硬编码。**

常用常量（`common/service-util/.../common/constant/SystemConstant.java`）：

```
// 专辑付费类型
ALBUM_PAY_TYPE_FREE     // "0101" 免费
ALBUM_PAY_TYPE_VIPFREE  // "0102" VIP免费
ALBUM_PAY_TYPE_REQUIRE  // "0103" 付费

// 专辑审核状态
ALBUM_STATUS_PASS       // "0301" 审核通过
ALBUM_STATUS_NO_PASS    // "0302" 审核不通过
ALBUM_STATUS_MANUAL     // "0303" 人工审核

// 专辑统计类型
ALBUM_STAT_PLAY         // "0401"
ALBUM_STAT_SUBSCRIBE    // "0402"
ALBUM_STAT_BUY          // "0403"
ALBUM_STAT_COMMENT      // "0404"

// 声音状态
TRACK_STATUS_PASS       // "0501"
TRACK_STATUS_NO_PASS    // "0502"
TRACK_STATUS_REVIEWING  // "0504" 音频机审中

// 订单状态
ORDER_STATUS_UNPAID     // "0901"
ORDER_STATUS_PAID       // "0902"
ORDER_STATUS_CANCEL     // "0903"

// 订单付款项目类型
ORDER_ITEM_TYPE_ALBUM   // "1001"
ORDER_ITEM_TYPE_TRACK   // "1002"
ORDER_ITEM_TYPE_VIP     // "1003"

// 支付方式
ORDER_PAY_WAY_WEIXIN    // "1101"
ORDER_PAY_ACCOUNT       // "1103" 余额支付
```

------

## 各微服务待实现功能清单

### service-search（优先级最高，依赖最少）

- `ItemServiceImpl.getItemInfo(albumId)` — 专辑详情页数据聚合（从 album 服务获取 + ES 写入）
- `SearchServiceImpl.search(AlbumIndexQuery)` — ES 全文检索：关键词（albumTitle + albumIntro IK分词）、分类过滤（category1/2/3Id）、付费类型过滤、统计排序（播放量/订阅量/热度）、分页、高亮
- Receiver 类中监听 `QUEUE_ALBUM_UPPER/LOWER` 消息，实现专辑 ES 索引的上下架同步
- 监听 `QUEUE_ALBUM_ES_STAT_UPDATE` 更新 ES 中的统计字段

### service-order

- `OrderInfoServiceImpl.trade(userId)` — 结算预览：校验是否已购、查询价格、计算优惠（VIP折扣）
- `OrderInfoServiceImpl.submitOrder(userId, tradeVo)` — 下单：校验流水号防重、写 order_info/order_detail/order_derate、发 MQ 锁定账户余额（ROUTING_ACCOUNT_UNLOCK）、发延迟 MQ 取消订单（15分钟）
- `OrderInfoServiceImpl.cancelOrder(orderId)` — 取消订单：更新状态、发 MQ 解锁余额
- Receiver 监听支付成功消息 `QUEUE_ORDER_PAY_SUCCESS`，更新订单状态为已支付、发 MQ 通知扣减余额（ROUTING_ACCOUNT_MINUS）、发 MQ 写购买记录（ROUTING_USER_PAY_RECORD）

### service-account

- `UserAccountServiceImpl.initAccount(userId, amount)` — 新用户初始化余额账户（监听 QUEUE_USER_REGISTER）
- `UserAccountServiceImpl.lockAccount(AccountLockVo)` — 锁定余额（下单时预扣）
- `UserAccountServiceImpl.unlockAccount(AccountLockVo)` — 解锁余额（取消订单时释放）
- `UserAccountServiceImpl.deductAccount(AccountDeductVo)` — 真正扣款（支付成功后）
- 幂等控制：使用 `RedisConstant.ACCOUNT_MUTIPLE_CHECK` + Redisson 分布式锁防重复扣款

### service-payment

- `WxPayServiceImpl.createOrder(paymentVo)` — 创建微信支付订单
- `WxPayApiController.notify()` — 微信支付回调：验签、更新 PaymentInfo 状态、发 MQ 支付成功消息

### service-user（部分待实现）

- `UserInfoServiceImpl.getUserPaidRecordList()` — 已购买专辑/声音列表
- Receiver 监听 `QUEUE_USER_PAY_RECORD` 写入购买记录

### service-dispatch

- `DispatchHandler` 中注册 xxl-job 任务处理器
- 统计数据定时更新：从 Redis 同步播放量/订阅量等统计到 MySQL 和 ES

------

## ES 索引说明

索引名：`albuminfo`
主要实体：`AlbumInfoIndex`（在 `model/src/main/java/.../model/search/AlbumInfoIndex.java`）

```
id, albumTitle(text,ik_max_word), albumIntro(text,ik_max_word),
announcerName(keyword), coverUrl, includeTrackCount, isFinished, payType,
createTime, category1Id, category2Id, category3Id,
playStatNum, subscribeStatNum, buyStatNum, commentStatNum, hotScore,
attributeValueIndexList(nested: attributeId, attributeValue)
```

Repository：`AlbumInfoIndexRepository extends ElasticsearchRepository<AlbumInfoIndex, Long>`

复杂查询（布尔查询、嵌套查询、分页、高亮）需要注入 `ElasticsearchRestTemplate` 或 `ElasticsearchClient`。

------

## 服务配置方式

每个微服务使用 `bootstrap.properties` 指定 Nacos 地址，具体业务配置在 Nacos 的 `{service-name}-dev.yaml` 中：

```
spring.cloud.nacos.config.server-addr=192.168.200.6:8848
spring.cloud.nacos.config.prefix=${spring.application.name}
spring.cloud.nacos.config.file-extension=yaml
spring.cloud.nacos.config.shared-configs[0].data-id=common.yaml
```

公共配置（MySQL、Redis、RabbitMQ 连接信息等）在 Nacos 的 `common.yaml` 中统一管理。

------

## 代码生成时的注意事项

1. **新增 Service 方法** → 先在 `XxxService` 接口中声明，再在 `XxxServiceImpl` 中实现。
2. **新增 Feign 方法** → 在 `service-client` 对应接口和降级类中同步添加。
3. **使用 Hutool** 做对象拷贝：`BeanUtil.copyProperties(source, TargetClass.class)`。
4. **集合判空** 用 `CollUtil.isNotEmpty(list)`（Hutool），不要用 `list != null && !list.isEmpty()`。
5. **字符串判空** 用 `StringUtils.isNotBlank(str)`（Apache Commons Lang3）。
6. **雪花 ID** 用 `IdUtil.getSnowflakeNextIdStr()`（Hutool）。
7. **Lombok 注解**：实体类用 `@Data`，Service/Controller 用 `@Slf4j` + `log.info/error`。
8. **Controller 类注解**：`@RestController + @RequestMapping + @Tag(name="xxx")`（Knife4j）。
9. **所有 ServiceImpl 加** `@SuppressWarnings({"all"})` — 这是项目惯例。

## 沟通方式

- 默认中文回复:代码、命令、变量名、文件路径保持英文
- 结论先行，简洁直接，不先铺垫背景
- 不谄媚，不夸"这是个很好的问题"，不以"当然可以"开头
- 给真实判断–方案有问题直接指出，发现更好做法主动说明

## Git

- 不会自动进行`git commit`或`git push`，除非我明确要求
- 提交前先展示将要提交的变更摘要
- 提交信息使用简洁中文

## 红线操作

以下操作即使在自动接受模式下也必须先问我:

- 删除文件、目录或git 历史
- 修改`.env`、密钥、token、证书、cI/cD配置
- `git reset --hard` 、`git push` 、`git rebase`、强制推送
- 公开发布(`npm publish`、生产部署等)

## 外部参考文档

写API、定义返回格式时 →必读`docs/api.md`