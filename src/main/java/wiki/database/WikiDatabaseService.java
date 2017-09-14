package wiki.database;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;

import java.util.HashMap;
import java.util.List;

@ProxyGen //ProxyGen注释用于触发该服务的客户机的代理生成代码。
public interface WikiDatabaseService {

    @Fluent //该Fluent注释是可选的，但允许流畅，其中操作可以通过返回服务实例被链接的接口。当服务将从其他JVM语言使用时，这对于代码生成器是非常有用的。
    WikiDatabaseService fetchAllPages(Handler<AsyncResult<JsonArray>> resultHandler);

    @Fluent
    WikiDatabaseService fetchPage(String name, Handler<AsyncResult<JsonObject>> resultHandler);  //参数类型需要是字符串，Java原始类型，
    // JSON对象或数组，以前类型的枚举类型或java.util集合（List/ Set/ Map）。支持任意Java类的唯一方法是将它们作为Vert.x数据对象注释@DataObject。通过其他类型的最后机会是服务引用类型。

    @Fluent
    WikiDatabaseService createPage(String title, String markdown, Handler<AsyncResult<Void>> resultHandler);

    @Fluent
    WikiDatabaseService savePage(int id, String markdown, Handler<AsyncResult<Void>> resultHandler);

    @Fluent
    WikiDatabaseService deletePage(int id, Handler<AsyncResult<Void>> resultHandler);//由于服务提供异步的结果，服务的方法的最后一个参数需要是Handler<AsyncResult<T>>
    // 其中T是任何合适的用于代码生成的类型的，如上所述。

    @Fluent
    WikiDatabaseService fetchAllPagesData(Handler<AsyncResult<List<JsonObject>>> resultHandler);

    static WikiDatabaseService create(JDBCClient dbClient, HashMap<SqlQuery, String> sqlQueries, Handler<AsyncResult<WikiDatabaseService>> readyHandler) {
        return new WikiDatabaseServiceImpl(dbClient, sqlQueries, readyHandler);
    }
    // end::create[]

    // tag::proxy[]
    static WikiDatabaseService createProxy(Vertx vertx, String address) {
        return new WikiDatabaseServiceVertxEBProxy(vertx, address);
    }
}
