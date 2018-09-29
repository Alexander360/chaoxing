package pers.cz.chaoxing.util;

import pers.cz.chaoxing.callback.CallBack;
import pers.cz.chaoxing.callback.CallBackData;
import pers.cz.chaoxing.common.quiz.data.exam.ExamQuizConfig;
import pers.cz.chaoxing.common.quiz.data.homework.HomeworkQuizConfig;
import pers.cz.chaoxing.exception.CheckCodeException;
import pers.cz.chaoxing.exception.WrongAccountException;

import java.util.function.Consumer;

/**
 * lambda checked exception throw cheat util
 *
 * @author 橙子
 * @date 2018/9/28
 */
public final class Try {

    @FunctionalInterface
    public interface ExceptionConsumer<T, E extends Exception> {
        void accept(T t) throws E;
    }

    @FunctionalInterface
    public interface ExceptionRunnable<E extends Exception> {
        void run() throws E, CheckCodeException;
    }

    @FunctionalInterface
    public interface ExceptionSupplier<T, E extends Exception> {
        T get() throws E, CheckCodeException;
    }

    public static <T, E extends Exception> Consumer<T> once(ExceptionConsumer<T, E> consumer) throws E {
        return t -> {
            try {
                consumer.accept(t);
            } catch (Exception e) {
                throwAsUnchecked(e);
            }
        };
    }

    public static void ever(ExceptionRunnable<CheckCodeException> runnable, CallBack<?> callBack) {
        while (true)
            try {
                runnable.run();
                break;
            } catch (CheckCodeException e) {
                callBack.call(e.getSession(), e.getUri());
            }
    }

    public static <T> T ever(ExceptionSupplier<T, CheckCodeException> supplier, CallBack<?> callBack) {
        while (true)
            try {
                return supplier.get();
            } catch (CheckCodeException e) {
                callBack.call(e.getSession(), e.getUri());
            }
    }

    public static <T> T ever(ExceptionSupplier<T, WrongAccountException> supplier, CallBack<?> callBack, HomeworkQuizConfig homeworkQuizConfig, String... extra) throws WrongAccountException {
        while (true)
            try {
                return supplier.get();
            } catch (CheckCodeException e) {
                homeworkQuizConfig.setEnc(((CallBackData) callBack.call(e.getSession(), e.getUri(), extra[0], extra[1], extra[2])).getEnc());
            } catch (Exception e) {
                throwAsUnchecked(e);
            }
    }

    public static <T> T ever(ExceptionSupplier<T, CheckCodeException> supplier, CallBack<?> callBack, ExamQuizConfig examQuizConfig, String... extra) {
        while (true)
            try {
                return supplier.get();
            } catch (CheckCodeException e) {
                examQuizConfig.setEnc(((CallBackData) callBack.call(e.getSession(), e.getUri(), extra[0], extra[1], extra[2])).getEnc());
            }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Exception> void throwAsUnchecked(Exception exception) throws E {
        throw (E) exception;
    }
}
