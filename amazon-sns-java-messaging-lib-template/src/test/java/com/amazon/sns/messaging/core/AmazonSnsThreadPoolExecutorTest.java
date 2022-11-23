package com.amazon.sns.messaging.core;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.amazon.sns.messaging.lib.core.AmazonSnsThreadPoolExecutor;

// @formatter:off
public class AmazonSnsThreadPoolExecutorTest {

  @Test
  public void testSuccessCounters() {
    final AmazonSnsThreadPoolExecutor amazonSnsThreadPoolExecutor = new AmazonSnsThreadPoolExecutor(10);

    assertThat(amazonSnsThreadPoolExecutor.getActiveTaskCount(), is(0));
    assertThat(amazonSnsThreadPoolExecutor.getSucceededTaskCount(), is(0));
    assertThat(amazonSnsThreadPoolExecutor.getFailedTaskCount(), is(0));
    assertThat(amazonSnsThreadPoolExecutor.getCorePoolSize(), is(2));
  }

  @Test
  public void testSuccessSucceededTaskCount() throws InterruptedException {
    final AmazonSnsThreadPoolExecutor amazonSnsThreadPoolExecutor = new AmazonSnsThreadPoolExecutor(10);

    assertThat(amazonSnsThreadPoolExecutor.getSucceededTaskCount(), is(0));

    for(int i = 0; i < 300; i++) {
      amazonSnsThreadPoolExecutor.execute(() -> { });
    }

    amazonSnsThreadPoolExecutor.shutdown();

    if (!amazonSnsThreadPoolExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
      amazonSnsThreadPoolExecutor.shutdownNow();
    }

    assertThat(amazonSnsThreadPoolExecutor.getActiveTaskCount(), is(0));
    assertThat(amazonSnsThreadPoolExecutor.getSucceededTaskCount(), is(300));
    assertThat(amazonSnsThreadPoolExecutor.getFailedTaskCount(), is(0));
  }

  @Test
  public void testSuccessFailedTaskCount() throws InterruptedException {
    final AmazonSnsThreadPoolExecutor amazonSnsThreadPoolExecutor = new AmazonSnsThreadPoolExecutor(10);

    assertThat(amazonSnsThreadPoolExecutor.getSucceededTaskCount(), is(0));

    for(int i = 0; i < 300; i++) {
      amazonSnsThreadPoolExecutor.execute(() -> { throw new RuntimeException(); });
    }

    amazonSnsThreadPoolExecutor.shutdown();

    if (!amazonSnsThreadPoolExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
      amazonSnsThreadPoolExecutor.shutdownNow();
    }

    assertThat(amazonSnsThreadPoolExecutor.getActiveTaskCount(), is(0));
    assertThat(amazonSnsThreadPoolExecutor.getSucceededTaskCount(), is(0));
    assertThat(amazonSnsThreadPoolExecutor.getFailedTaskCount(), is(300));
  }

  @Test
  public void testSuccessActiveTaskCount() throws InterruptedException {
    final AmazonSnsThreadPoolExecutor amazonSnsThreadPoolExecutor = new AmazonSnsThreadPoolExecutor(10);

    assertThat(amazonSnsThreadPoolExecutor.getSucceededTaskCount(), is(0));

    for(int i = 0; i < 10; i++) {
      amazonSnsThreadPoolExecutor.execute(() -> { while(true); });
    }

    amazonSnsThreadPoolExecutor.shutdown();

    if (!amazonSnsThreadPoolExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
      amazonSnsThreadPoolExecutor.shutdownNow();
    }

    assertThat(amazonSnsThreadPoolExecutor.getActiveTaskCount(), is(10));
    assertThat(amazonSnsThreadPoolExecutor.getSucceededTaskCount(), is(0));
    assertThat(amazonSnsThreadPoolExecutor.getFailedTaskCount(), is(0));
  }

}
// @formatter:on
