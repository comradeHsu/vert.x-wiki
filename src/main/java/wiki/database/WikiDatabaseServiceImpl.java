package wiki.database;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.sql.ResultSet;
import io.vertx.rx.java.RxHelper;
import io.vertx.rxjava.ext.jdbc.JDBCClient;
import io.vertx.rxjava.ext.sql.SQLConnection;
import rx.Observable;
import rx.Single;

import java.util.HashMap;
import java.util.List;

/**
 * @author <a href="https://julien.ponge.org/">Julien Ponge</a>
 */
public class WikiDatabaseServiceImpl implements WikiDatabaseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WikiDatabaseServiceImpl.class);

    private final HashMap<SqlQuery, String> sqlQueries;
    private final JDBCClient dbClient;

    WikiDatabaseServiceImpl(io.vertx.ext.jdbc.JDBCClient dbClient, HashMap<SqlQuery, String> sqlQueries, Handler<AsyncResult<WikiDatabaseService>> readyHandler) {
        this.dbClient = new JDBCClient(dbClient);
        this.sqlQueries = sqlQueries;

        getConnection()
                .flatMap(conn -> conn.rxExecute(sqlQueries.get(SqlQuery.CREATE_PAGES_TABLE)))
                .map(v -> this)
                .subscribe(RxHelper.toSubscriber(readyHandler));
    }

    // tag::rx-get-connection[]
    private Single<SQLConnection> getConnection() {
        return dbClient.rxGetConnection().flatMap(conn -> {
            Single<SQLConnection> connectionSingle = Single.just(conn); // <1>连接后，我们将其包装成一个 Single
            return connectionSingle.doOnUnsubscribe(conn::close); // <2>将Single被修改以调用close时取消订阅
        });
    }
    // end::rx-get-connection[]

    @Override
    // tag::rx-data-flow[]
    public WikiDatabaseService fetchAllPages(Handler<AsyncResult<JsonArray>> resultHandler) {
        getConnection()
                .flatMap(conn -> conn.rxQuery(sqlQueries.get(SqlQuery.ALL_PAGES)))
                .flatMapObservable(res -> {  // <1>随着flatMapObservable我们将从... Observable发出的项目创建一个Single<Result>
                    List<JsonArray> results = res.getResults();
                    return Observable.from(results); // <2>from将数据库results可迭代转换为Observable发出数据库行项目
                })
                .map(json -> json.getString(0)) // <3>由于我们只需要页面名称，我们可以将map每一JsonObject行都放到第一列
                .sorted() // <4>客户期望数据sorted按字母顺序排列
                .collect(JsonArray::new, JsonArray::add) // <5>事件总线服务回复由一个单一的JsonArray。collect创建一个新的JsonArray::new，随后添加与它们一起发送的项目JsonArray::add
                .subscribe(RxHelper.toSubscriber(resultHandler));
        return this;
    }
    // end::rx-data-flow[]

    @Override
    public WikiDatabaseService fetchPage(String name, Handler<AsyncResult<JsonObject>> resultHandler) {
        getConnection()
                .flatMap(conn -> conn.rxQueryWithParams(sqlQueries.get(SqlQuery.GET_PAGE), new JsonArray().add(name)))
                .map(result -> {
                    if (result.getNumRows() > 0) {
                        JsonArray row = result.getResults().get(0);
                        return new JsonObject()
                                .put("found", true)
                                .put("id", row.getInteger(0))
                                .put("rawContent", row.getString(1));
                    } else {
                        return new JsonObject().put("found", false);
                    }
                })
                .subscribe(RxHelper.toSubscriber(resultHandler));
        return this;
    }

    @Override
    public WikiDatabaseService fetchPageById(int id, Handler<AsyncResult<JsonObject>> resultHandler) {
        Single<SQLConnection> connection = getConnection();
        // tag::rx-execute-query-with-params[]
        Single<ResultSet> resultSet = connection
                .flatMap(conn -> conn.rxQueryWithParams(sqlQueries.get(SqlQuery.FETCH_PAGE_BY_ID), new JsonArray().add(id)));
        // end::rx-execute-query-with-params[]
        resultSet
                .map(result -> {
                    if (result.getNumRows() > 0) {
                        JsonObject row = result.getRows().get(0);
                        return new JsonObject()
                                .put("found", true)
                                .put("id", row.getInteger("ID"))
                                .put("name", row.getString("NAME"))
                                .put("content", row.getString("CONTENT"));
                    } else {
                        return new JsonObject().put("found", false);
                    }
                })
                .subscribe(RxHelper.toSubscriber(resultHandler));
        return this;
    }

    @Override
    public WikiDatabaseService createPage(String title, String markdown, Handler<AsyncResult<Void>> resultHandler) {
        getConnection()
                .flatMap(conn -> conn.rxUpdateWithParams(sqlQueries.get(SqlQuery.CREATE_PAGE), new JsonArray().add(title).add(markdown)))
                .map(res -> (Void) null)
                .subscribe(RxHelper.toSubscriber(resultHandler));
        return this;
    }

    @Override
    public WikiDatabaseService savePage(int id, String markdown, Handler<AsyncResult<Void>> resultHandler) {
        getConnection()
                .flatMap(conn -> conn.rxUpdateWithParams(sqlQueries.get(SqlQuery.SAVE_PAGE), new JsonArray().add(markdown).add(id)))
                .map(res -> (Void) null)
                .subscribe(RxHelper.toSubscriber(resultHandler));
        return this;
    }

    @Override
    public WikiDatabaseService deletePage(int id, Handler<AsyncResult<Void>> resultHandler) {
        getConnection()
                .flatMap(connection -> {
                    JsonArray data = new JsonArray().add(id);
                    return connection.rxUpdateWithParams(sqlQueries.get(SqlQuery.DELETE_PAGE), data);
                })
                .map(res -> (Void) null)
                .subscribe(RxHelper.toSubscriber(resultHandler));
        return this;
    }

    @Override
    // tag::rxhelper-to-subscriber[]
    public WikiDatabaseService fetchAllPagesData(Handler<AsyncResult<List<JsonObject>>> resultHandler) { // <1>异步服务代理操作，用Handler<AsyncResult<List<JsonObject>>>回调定义
        getConnection()
                .flatMap(connection -> connection.rxQuery(sqlQueries.get(SqlQuery.ALL_PAGES_DATA)))
                .map(ResultSet::getRows)
                .subscribe(RxHelper.toSubscriber(resultHandler));  // <2>该toSubscriber方法适应resultHandler于a Subscriber<List<JsonObject>>，以便在发出行列表时调用处理程序
        return this;
    }
    // end::rxhelper-to-subscriber[]
}