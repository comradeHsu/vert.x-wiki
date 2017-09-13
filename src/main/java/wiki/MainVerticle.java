package wiki;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import wiki.database.WikiDatabaseVerticle;

/**
 * Created by Administrator on 2017/9/7.
 */
public class MainVerticle extends AbstractVerticle {
    @Override
    public void start(Future<Void> startFuture) throws Exception {

        Future<String> dbVerticleDeployment = Future.future();  //(1)部署一个verticle是一个异步操作，所以我们需要一个Future。该String参数的类型是因为成功地部署当verticle得到的标识符。
        vertx.deployVerticle(new WikiDatabaseVerticle(), dbVerticleDeployment.completer());  //(2)一个选择是创建一个verticle实例new，并将对象引用传递给该deploy方法。该completer返回值是简单地完成其未来的处理程序

        dbVerticleDeployment.compose(id -> {  //(3)顺序组合compose允许在另一个之后运行一个异步操作。当初始未来成功完成时，将调用组合函数

            Future<String> httpVerticleDeployment = Future.future();
            vertx.deployVerticle(
                    "wiki.http.HttpServerVerticle",  //(4)作为字符串的类名称也是指定要部署的垂直线的选项。对于其他JVM语言，基于字符串的约定允许指定模块/脚本。
            new DeploymentOptions().setInstances(2),   // (5)在DeploymentOption类允许指定的若干参数，特别是实例来部署的数目。
            httpVerticleDeployment.completer());

            return httpVerticleDeployment;  //(6)组合函数返回下一个未来。其完成将触发复合操作的完成。

        }).setHandler(ar -> {   //(7)我们定义一个处理程序，最终完成MainVerticle开始的未来。
            if (ar.succeeded()) {
                startFuture.complete();
            } else {
                startFuture.fail(ar.cause());
            }
        });
    }
}
