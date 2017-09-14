package wiki;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class MainVerticleTest {


    @Test /*(timeout=5000)*/  //(8)异步测试用例有一个默认（long）超时，但可以通过JUnit @Test注释来覆盖它
    public void async_behavior(TestContext context) { //(1)TestContext 是赛跑者提供的参数。
        Vertx vertx = Vertx.vertx();  //(2)在单元测试中，我们需要创建一个Vert.x上下文
        context.assertEquals("foo", "foo");  //(3)这是一个基本TestContext断言的例子
        Async a1 = context.async();   //(4)我们得到一个Async可以稍后完成（或失败）的第一个对象
        Async a2 = context.async(3);  //(5)此Async对象作为在3次呼叫后成功完成的倒计时
        vertx.setTimer(100, n -> a1.complete());  //(6)当定时器触发时，我们完成
        vertx.setPeriodic(100, n -> a2.countDown());  //(7)每个周期性任务刻度触发倒计时。所有Async对象完成后，测试通过
    }
}
