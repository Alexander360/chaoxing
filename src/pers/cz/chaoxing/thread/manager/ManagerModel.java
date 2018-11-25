package pers.cz.chaoxing.thread.manager;

import net.dongliu.requests.exception.RequestsException;
import pers.cz.chaoxing.thread.LimitedBlockingQueue;
import pers.cz.chaoxing.util.CompleteStyle;
import pers.cz.chaoxing.util.IOUtil;
import pers.cz.chaoxing.util.StringUtil;
import pers.cz.chaoxing.util.Try;

import java.io.Closeable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;

/**
 * @author 橙子
 * @since 2018/9/29
 */
public abstract class ManagerModel implements Runnable, Closeable {
    protected String baseUri;
    String uriModel;
    int threadCount;
    boolean hasSleep;
    boolean skipReview;
    CompleteStyle completeStyle;
    private int threadPoolSize;
    private ExecutorService threadPool;
    List<Map<String, String>> paramsList;
    CompletionService<Boolean> completionService;
    Semaphore semaphore;

    ManagerModel(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
        if (this.threadPoolSize > 0) {
            this.threadPool = new ThreadPoolExecutor(threadPoolSize, threadPoolSize, 0L, TimeUnit.MILLISECONDS, new LimitedBlockingQueue<>(1));
            this.completionService = new ExecutorCompletionService<>(threadPool);
        }
        threadCount = 0;
    }

    @Override
    public final void run() {
        if (this.threadPoolSize > 0)
            try {
                doJob();
            } catch (RequestsException e) {
                String message = StringUtil.subStringAfterFirst(e.getLocalizedMessage(), ":").trim();
                IOUtil.println("Net connection error: " + message);
                release();
            } catch (Exception ignored) {
                release();
            }
    }

    public abstract void doJob() throws Exception;

    void acquire() throws InterruptedException {
        Optional.ofNullable(semaphore).ifPresent(Try.once(Semaphore::acquire));
    }

    void release() {
        Optional.ofNullable(semaphore).ifPresent(Semaphore::release);
    }

    public void setBaseUri(String baseUri) {
        this.baseUri = baseUri;
    }

    public void setUriModel(String uriModel) {
        this.uriModel = uriModel;
    }

    public void setHasSleep(boolean hasSleep) {
        this.hasSleep = hasSleep;
    }

    public void setSkipReview(boolean skipReview) {
        this.skipReview = skipReview;
    }

    public void setCompleteStyle(CompleteStyle completeStyle) {
        this.completeStyle = completeStyle;
    }

    public void setParamsList(List<Map<String, String>> paramsList) {
        this.paramsList = paramsList;
    }

    public void setSemaphore(Semaphore semaphore) {
        this.semaphore = semaphore;
    }

    @Override
    public void close() {
        try {
            for (int i = 0; i < threadCount; i++)
                completionService.take().get();
        } catch (Exception ignored) {
        }
        if (this.threadPoolSize > 0)
            threadPool.shutdown();
    }
}