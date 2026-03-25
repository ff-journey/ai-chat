package ff.pro.aichatali.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author: JianFuQiang
 * @date: 2021/6/25
 * @desc:
 */
@Configuration
@Slf4j
public class ThreadPoolHelper {

    public static ThreadPoolTaskExecutor EXECUTOR_SERVICE;

    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executorService = new ThreadPoolTaskExecutor();
        executorService.setCorePoolSize(12);
        executorService.setMaxPoolSize(24);
        executorService.setKeepAliveSeconds(60);
        executorService.setQueueCapacity(1000);
        executorService.setThreadNamePrefix("device-async-task-");
        executorService.setThreadFactory(new CustomThreadFactory("custom_thread"));
        executorService.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executorService.setTaskDecorator(task -> {
            // 捕获当前线程（父线程）的 RequestContext 值
            RequestContext parentRequestContext = RequestContext.cloneRequestContext();
            return () -> {
                try {
                    // 子线程设置父线程的上下文副本
                    RequestContext.setClone(parentRequestContext);
                    task.run(); // 执行原任务
                } catch (Exception e) {
                    log.error("⚠️⚠️⚠️⚠️线程执行异常⚠️⚠️⚠️⚠️", e);
                } finally {
                    // 清理上下文
                    RequestContext.removeRequestContext();
                }
            };
        });
        executorService.initialize();
        EXECUTOR_SERVICE = executorService;
        return executorService;
    }

    public static void execute(Runnable runnable) {
        EXECUTOR_SERVICE.execute(runnable);
    }

    public static Future<?> submit(Runnable runnable) {
        return EXECUTOR_SERVICE.submit(runnable);
    }

    public static <T> Future<T> submit(Callable<T> callable) {
        return EXECUTOR_SERVICE.submit(callable);
    }

    public static class CustomThreadFactory implements ThreadFactory {

        private final AtomicInteger counter = new AtomicInteger(0);
        private final String name;
        private final List<String> stats;

        public CustomThreadFactory(String name) {
            this.name = name;
            this.stats = new ArrayList<>();
        }

        @Override
        public Thread newThread(Runnable runnable) {

            Thread thread = new Thread(runnable, String.format("%s_%s", this.name, this.counter));
            this.counter.incrementAndGet();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            this.stats.add(String.format("Creat thread %s with name %s on %s\n",
                    thread.getId(), thread.getName(), dateFormat.format(new Date())));
            return thread;
        }
    }
}