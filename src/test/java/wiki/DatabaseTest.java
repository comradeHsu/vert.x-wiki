package wiki;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import wiki.database.WikiDatabaseService;
import wiki.database.WikiDatabaseVerticle;

@RunWith(VertxUnitRunner.class)
public class DatabaseTest {

    private Vertx vertx;
    private WikiDatabaseService service;

    @Before
    public void prepare(TestContext context) throws InterruptedException {
        vertx = Vertx.vertx();

        JsonObject conf = new JsonObject()  //(1)只覆盖一些verticle设置，其他的将具有默认值
                .put(WikiDatabaseVerticle.CONFIG_WIKIDB_JDBC_URL, "jdbc:hsqldb:mem:testdb;shutdown=true")
                .put(WikiDatabaseVerticle.CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE, 4);

        vertx.deployVerticle(new WikiDatabaseVerticle(), new DeploymentOptions().setConfig(conf),
                context.asyncAssertSuccess(id ->  //(2)asyncAssertSuccess有助于提供一个检查异步操作成功的处理程序。有一个没有参数的变体，和一个类似这样的变体，我们可以将结果链接到另一个处理程序
                        service = WikiDatabaseService.createProxy(vertx, WikiDatabaseVerticle.CONFIG_WIKIDB_QUEUE)));
    }

    @After
    public void finish(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    @Test
    public void crud_operations(TestContext context) {
        Async async = context.async();

        service.createPage("Test", "Some content", context.asyncAssertSuccess(v1 -> {

            service.fetchPage("Test", context.asyncAssertSuccess(json1 -> {
                context.assertTrue(json1.getBoolean("found"));
                context.assertTrue(json1.containsKey("id"));
                context.assertEquals("Some content", json1.getString("rawContent"));

                service.savePage(json1.getInteger("id"), "Yo!", context.asyncAssertSuccess(v2 -> {

                    service.fetchAllPages(context.asyncAssertSuccess(array1 -> {
                        context.assertEquals(1, array1.size());

                        service.fetchPage("Test", context.asyncAssertSuccess(json2 -> {
                            context.assertEquals("Yo!", json2.getString("rawContent"));

                            service.deletePage(json1.getInteger("id"), v3 -> {

                                service.fetchAllPages(context.asyncAssertSuccess(array2 -> {
                                    context.assertTrue(array2.isEmpty());
                                    async.complete();  //(1)这是唯一Async最终完成的地方。
                                }));
                            });
                        }));
                    }));
                }));
            }));
        }));
        async.awaitSuccess(5000); //(2)这是退出测试用例方法并依赖于JUnit超时的替代方法。这里，测试用例线程上的执行将等待直到Async完成或超时时间段过去
    }
}
