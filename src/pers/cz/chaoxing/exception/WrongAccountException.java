package pers.cz.chaoxing.exception;

public class WrongAccountException extends Exception {

    public WrongAccountException() {
        super("account error");
    }
}
