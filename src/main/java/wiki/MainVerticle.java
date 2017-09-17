package wiki;


import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.rxjava.core.AbstractVerticle;
import rx.Single;

/**
 * Created by Administrator on 2017/9/7.
 */
public class MainVerticle extends AbstractVerticle {
    @Override
    public void start(Future<Void> startFuture) throws Exception {

        Single<String> dbVerticleDeployment = vertx.rxDeployVerticle(
                "wiki.database.WikiDatabaseVerticle");

        dbVerticleDeployment.flatMap(id -> {  //该flatMap运营商应用功能的结果dbVerticleDeployment。在这里它调度的部署HttpServerVerticle
            Single<String> httpVerticleDeployment = vertx.rxDeployVerticle(
                "wiki.http.HttpServerVerticle",new DeploymentOptions().setInstances(2));
            return httpVerticleDeployment;
        }).subscribe(id -> startFuture.complete(),startFuture::fail);//订阅时操作开始。成功或错误时，MainVerticle开始的未来要么完成，要么失败
    }
}
