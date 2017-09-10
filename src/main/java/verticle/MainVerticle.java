package verticle;

import com.github.rjeschke.txtmark.Processor;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.templ.FreeMarkerTemplateEngine;

import java.util.Date;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Created by Administrator on 2017/9/7.
 */
public class MainVerticle extends AbstractVerticle {

    private static final String SQL_CREATE_PAGES_TABLE = "create table if not exists Pages (Id integer identity primary key, Name varchar(255) unique, Content clob)";
    private static final String SQL_GET_PAGE = "select Id, Content from Pages where Name = ?"; //(1)
    private static final String SQL_CREATE_PAGE = "insert into Pages values (NULL, ?, ?)";
    private static final String SQL_SAVE_PAGE = "update Pages set Content = ? where Id = ?";
    private static final String SQL_ALL_PAGES = "select Name from Pages";
    private static final String SQL_DELETE_PAGE = "delete from Pages where Id = ?";

    private JDBCClient dbClient;

    private static final io.vertx.core.logging.Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class);

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        Future<Void> steps = prepareDatabase().compose(v -> startHttpServer());
        steps.setHandler(ar -> {
            if (ar.succeeded()) {
                startFuture.complete();
            } else {
                startFuture.fail(ar.cause());
            }
        });
    }

    private Future<Void> prepareDatabase() {
        Future<Void> future = Future.future();

        dbClient = JDBCClient.createShared(vertx, new JsonObject()  //(1)createShared创建一个共享连接，以便在vertx实例已知的角色之间共享
                .put("url", "jdbc:hsqldb:file:db/wiki")   //(2)JDBC客户端连接是通过传递一个Vert.x JSON对象来实现的。这url是JDBC URL。
                .put("driver_class", "org.hsqldb.jdbcDriver")   //(3)就像使用JDBC驱动程序一样url，driver_class指向驱动程序类。
                .put("max_pool_size", 30));   //(4)max_pool_size是并发连接数。我们在这里选择了30，但这只是一个任意数字。

        dbClient.getConnection(ar -> {    //(5)获取连接是一个异步操作，给我们一个AsyncResult<SQLConnection>。然后必须进行测试以查看连接是否可以建立（AsyncResult其实是超级接口Future）。
            if (ar.failed()) {
                LOGGER.error("Could not open a database connection", ar.cause());
                future.fail(ar.cause());    //(6)如果无法获取SQL连接，则通过该方法，未来的方法将AsyncResult通过提供的异常完成失败cause
            } else {
                SQLConnection connection = ar.result();   //(7)这SQLConnection是成功的结果AsyncResult。我们可以使用它来执行SQL查询。
                connection.execute(SQL_CREATE_PAGES_TABLE, create -> {
                    connection.close();   //(8)在检查SQL查询是否成功之前，我们必须通过调用释放它close，否则JDBC客户端连接池最终可能会耗尽。
                    if (create.failed()) {
                        LOGGER.error("Database preparation error", create.cause());
                        future.fail(create.cause());
                    } else {
                        future.complete();  //(9)我们完成了未来对象的方法。
                    }
                });
            }
        });

        return future;
    }

    private Future<Void> startHttpServer() {
        Future<Void> future = Future.future();
        HttpServer server = vertx.createHttpServer();   //(1)所述vertx上下文对象提供方法来创建HTTP服务器，客户端，TCP / UDP服务器和客户端

        Router router = Router.router(vertx);   //(2)
        router.get("/").handler(this::indexHandler);
        router.get("/wiki/:page").handler(this::pageRenderingHandler); //(3)路由有自己的处理程序，它们可以通过URL和/或HTTP方法定义。
        // 对于短处理程序，Java lambda是一个选项，但是对于更详细的处理程序来说，引用私有方法是一个好主意。请注意，URL可以是参数：
        // /wiki/:page将匹配一个请求/wiki/Hello，在这种情况下，page参数将可用于值Hello。
        router.post().handler(BodyHandler.create());  //(4)这使得所有HTTP POST请求都通过第一个处理程序io.vertx.ext.web.handler.BodyHandler。
        // 该处理程序自动从HTTP请求（例如，表单提交）解码正文，然后可以将其作为Vert.x缓冲区对象进行操作。
        router.post("/save").handler(this::pageUpdateHandler);
        router.post("/create").handler(this::pageCreateHandler);
        router.post("/delete").handler(this::pageDeletionHandler);

        server
                .requestHandler(router::accept)   //(5)路由器对象可以用作HTTP服务器处理程序，然后将其发送到如上定义的其他处理程序。
                .listen(8080, ar -> {   //(6)启动HTTP服务器是一个异步操作，因此AsyncResult<HttpServer>需要检查成功。顺便8080参数指定服务器使用的TCP端口。
                    if (ar.succeeded()) {
                        LOGGER.info("HTTP server running on port 8080");
                        future.complete();
                    } else {
                        LOGGER.error("Could not start a HTTP server", ar.cause());
                        future.fail(ar.cause());
                    }
                });

        return future;
    }


    //处理程序
    private final FreeMarkerTemplateEngine templateEngine = FreeMarkerTemplateEngine.create();

    private void indexHandler(RoutingContext context) {
        dbClient.getConnection(car -> {
            if (car.succeeded()) {
                SQLConnection connection = car.result();
                connection.query(SQL_ALL_PAGES, res -> {
                    connection.close();

                    if (res.succeeded()) {
                        List<String> pages = res.result() //(1)SQL查询结果被返回的实例JsonArray和JsonObject。
                                .getResults()
                                .stream()
                                .map(json -> json.getString(0))
                                .sorted()
                                .collect(Collectors.toList());

                        context.put("title", "Wiki home");  //(2)该RoutingContext实例可用于放置随后从模板或链接路由器处理程序可用的任意键/值数据。
                        context.put("pages", pages);
                        templateEngine.render(context, "templates", "/index.ftl", ar -> {   //(3)渲染模板是一种异步操作，使我们能够AsyncResult处理通常的处理模式。
                            if (ar.succeeded()) {
                                context.response().putHeader("Content-Type", "text/html");
                                context.response().end(ar.result());  //(4)在AsyncResult包含模板呈现为String在成功的情况下，我们可以结束与价值的HTTP响应流。
                            } else {
                                context.fail(ar.cause());
                            }
                        });

                    } else {
                        context.fail(res.cause());  //(5)在出现故障的情况下，该fail方法RoutingContext提供了一个明智的方法来将HTTP 500错误返回给HTTP客户端。
                    }
                });
            } else {
                context.fail(car.cause());
            }
        });
    }

    private static final String EMPTY_PAGE_MARKDOWN =
            "# A new page\n" +
                    "\n" +
                    "Feel-free to write in Markdown!\n";

    private void pageRenderingHandler(RoutingContext context) {
        String page = context.request().getParam("page");   //(1)URL参数（/wiki/:name这里）可以通过上下文请求对象访问。

        dbClient.getConnection(car -> {
            if (car.succeeded()) {

                SQLConnection connection = car.result();
                connection.queryWithParams(SQL_GET_PAGE, new JsonArray().add(page), fetch -> {  //(2)将参数值传递给SQL查询是通过使用SQL查询中符号JsonArray顺序的元素完成的?。
                    connection.close();
                    if (fetch.succeeded()) {

                        JsonArray row = fetch.result().getResults()
                                .stream()
                                .findFirst()
                                .orElseGet(() -> new JsonArray().add(-1).add(EMPTY_PAGE_MARKDOWN));
                        Integer id = row.getInteger(0);
                        String rawContent = row.getString(1);

                        context.put("title", page);
                        context.put("id", id);
                        context.put("newPage", fetch.result().getResults().size() == 0 ? "yes" : "no");
                        context.put("rawContent", rawContent);
                        context.put("content", Processor.process(rawContent));  //(3)本Processor类来自于txtmark我们使用降价渲染库。
                        context.put("timestamp", new Date().toString());

                        templateEngine.render(context, "templates", "/page.ftl", ar -> {
                            if (ar.succeeded()) {
                                context.response().putHeader("Content-Type", "text/html");
                                context.response().end(ar.result());
                            } else {
                                context.fail(ar.cause());
                            }
                        });
                    } else {
                        context.fail(fetch.cause());
                    }
                });

            } else {
                context.fail(car.cause());
            }
        });
    }

    private void pageCreateHandler(RoutingContext context) {
        String pageName = context.request().getParam("name");
        String location = "/wiki/" + pageName;
        if (pageName == null || pageName.isEmpty()) {
            location = "/";
        }
        context.response().setStatusCode(303);
        context.response().putHeader("Location", location);
        context.response().end();
    }

    private void pageUpdateHandler(RoutingContext context) {
        String id = context.request().getParam("id");   //(1)通过HTTP POST请求发送的表单参数可从RoutingContext对象获得。
                                                            // 请注意，BodyHandler在Router配置链中没有这些值将不可用，并且表单提交负载需要从HTTP POST请求有效载荷手动解码。
        String title = context.request().getParam("title");
        String markdown = context.request().getParam("markdown");
        boolean newPage = "yes".equals(context.request().getParam("newPage"));  //(2)我们依赖于page.ftlFreeMarker模板中呈现的表单隐藏字段，以了解是否正在更新现有页面或保存新页面。

        dbClient.getConnection(car -> {
            if (car.succeeded()) {
                SQLConnection connection = car.result();
                String sql = newPage ? SQL_CREATE_PAGE : SQL_SAVE_PAGE;
                JsonArray params = new JsonArray();   //(3)再次，使用参数准备SQL查询使用a JsonArray传递值。
                if (newPage) {
                    params.add(title).add(markdown);
                } else {
                    params.add(markdown).add(id);
                }
                connection.updateWithParams(sql, params, res -> {   //(4)该updateWithParams方法用于insert/ update/ deleteSQL查询。
                    connection.close();
                    if (res.succeeded()) {
                        context.response().setStatusCode(303);    //(5)成功后，我们只需重定向到已编辑的页面。
                        context.response().putHeader("Location", "/wiki/" + title);
                        context.response().end();
                    } else {
                        context.fail(res.cause());
                    }
                });
            } else {
                context.fail(car.cause());
            }
        });
    }

    private void pageDeletionHandler(RoutingContext context) {
        String id = context.request().getParam("id");
        dbClient.getConnection(car -> {
            if (car.succeeded()) {
                SQLConnection connection = car.result();
                connection.updateWithParams(SQL_DELETE_PAGE, new JsonArray().add(id), res -> {
                    connection.close();
                    if (res.succeeded()) {
                        context.response().setStatusCode(303);
                        context.response().putHeader("Location", "/");
                        context.response().end();
                    } else {
                        context.fail(res.cause());
                    }
                });
            } else {
                context.fail(car.cause());
            }
        });
    }
}
