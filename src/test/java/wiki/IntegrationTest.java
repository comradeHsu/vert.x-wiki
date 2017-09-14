package wiki;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class IntegrationTest {

    private Vertx vertx = Vertx.vertx();

    @Test
    public void start_http_server(TestContext context) {
        Async async = context.async();

        vertx.createHttpServer().requestHandler(req ->
                req.response().putHeader("Content-Type", "text/plain").end("Ok"))
                .listen(8080, context.asyncAssertSuccess(server -> {

                    WebClient webClient = WebClient.create(vertx);

                    webClient.get(8080, "localhost", "/").send(ar -> {
                        if (ar.succeeded()) {
                            HttpResponse<Buffer> response = ar.result();
                            context.assertTrue(response.headers().contains("Content-Type"));
                            context.assertEquals("text/plain", response.getHeader("Content-Type"));
                            context.assertEquals("Ok", response.body().toString());
                            webClient.close();
                            async.complete();
                        } else {
                            async.resolve(Future.failedFuture(ar.cause()));
                        }
                    });
                }));
    }
}
